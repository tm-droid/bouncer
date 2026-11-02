import Foundation

/// A live view of one subscriber's events. Cancel by calling `RealtimeProviding.unsubscribe`.
struct RealtimeSubscription: Sendable {
    let id: UUID
    let events: AsyncStream<RealtimeEvent>
}

protocol RealtimeProviding: Sendable {
    func subscribe(channels: Set<String>) async -> RealtimeSubscription
    func unsubscribe(_ id: UUID) async
}

/// A single WebSocket connection, abstracted so the client's reconnect logic is testable.
protocol RealtimeConnection: Sendable {
    func receive() async throws -> String
    func send(_ text: String) async throws
    func close()
}

protocol RealtimeTransport: Sendable {
    func connect(url: URL) -> RealtimeConnection
}

struct URLSessionRealtimeTransport: RealtimeTransport {
    private final class Connection: RealtimeConnection, @unchecked Sendable {
        let task: URLSessionWebSocketTask
        init(task: URLSessionWebSocketTask) { self.task = task; task.resume() }
        func receive() async throws -> String {
            switch try await task.receive() {
            case let .string(s): return s
            case let .data(d): return String(decoding: d, as: UTF8.self)
            @unknown default: return ""
            }
        }
        func send(_ text: String) async throws { try await task.send(.string(text)) }
        func close() { task.cancel(with: .goingAway, reason: nil) }
    }

    func connect(url: URL) -> RealtimeConnection {
        Connection(task: URLSession(configuration: .ephemeral).webSocketTask(with: url))
    }
}

/// One refcounted WebSocket shared by every screen. The connected channel set is the union of all
/// subscribers' channels; the socket opens with the first subscriber and closes with the last.
/// Failures reconnect with backoff 2s -> 60s (+jitter) and resubscribe to the same channels.
actor RealtimeClient: RealtimeProviding {
    static let shared = RealtimeClient()
    static let pingInterval: TimeInterval = 20
    /// Coalesces a burst of subscribe/unsubscribe calls into a single reconnect.
    static let changeDebounce: TimeInterval = 0.25

    private struct Subscriber {
        let channels: Set<String>
        let continuation: AsyncStream<RealtimeEvent>.Continuation
    }

    private let transport: RealtimeTransport
    private let backoff: @Sendable (Int) -> TimeInterval
    private var subscribers: [UUID: Subscriber] = [:]
    private var runner: Task<Void, Never>?
    /// Identifies the live run loop so a cancelled one cannot touch state a newer one owns.
    private var runID = 0
    private var connection: RealtimeConnection?
    private var activeChannels: Set<String> = []
    private var desiredChannels: Set<String> = []
    private var hasConnectedBefore = false
    private var lastLost = false

    init(transport: RealtimeTransport = URLSessionRealtimeTransport(),
         backoff: @escaping @Sendable (Int) -> TimeInterval = { RealtimeBackoff.delay(attempt: $0) }) {
        self.transport = transport
        self.backoff = backoff
    }

    var subscriberCount: Int { subscribers.count }
    var isRunning: Bool { runner != nil }

    func subscribe(channels: Set<String>) -> RealtimeSubscription {
        let id = UUID()
        let (stream, continuation) = AsyncStream<RealtimeEvent>.makeStream(bufferingPolicy: .bufferingNewest(256))
        subscribers[id] = Subscriber(channels: channels, continuation: continuation)
        continuation.onTermination = { [weak self] _ in
            Task { await self?.unsubscribe(id) }
        }
        // A subscriber joining an already-open socket needs its own `connected` once the channel set
        // covers it; the run loop sends it after the (re)connect. If the set is unchanged, say so now.
        recomputeChannels()
        if runner == nil {
            startRunner()
        } else if desiredChannels.isSubset(of: activeChannels), connection != nil {
            continuation.yield(.connected(isRecovery: false))
        } else {
            reconnectForChannelChange()
        }
        return RealtimeSubscription(id: id, events: stream)
    }

    func unsubscribe(_ id: UUID) {
        guard let sub = subscribers.removeValue(forKey: id) else { return }
        sub.continuation.finish()
        if subscribers.isEmpty {
            runner?.cancel()
            runner = nil
            runID += 1
            connection?.close()
            connection = nil
            activeChannels = []
            desiredChannels = []
            hasConnectedBefore = false
            lastLost = false
        } else {
            recomputeChannels()
            // Dropping channels needs no reconnect; the socket just carries a few extra frames until
            // the next natural reconnect. Reconnect only when channels were added.
        }
    }

    // MARK: Internals

    private func recomputeChannels() {
        desiredChannels = subscribers.values.reduce(into: Set<String>()) { $0.formUnion($1.channels) }
    }

    private func startRunner() {
        runID += 1
        let id = runID
        runner = Task { [weak self] in await self?.run(id) }
    }

    private func reconnectForChannelChange() {
        // Closing the connection makes `receive()` throw in the run loop, which sees the changed
        // channel set and reconnects immediately without backoff.
        lastLost = false
        connection?.close()
    }

    static func url(channels: Set<String>) -> URL? {
        var components = URLComponents(string: AppwriteConfig.realtimeEndpoint)
        components?.queryItems = [URLQueryItem(name: "project", value: AppwriteConfig.projectId)]
            + channels.sorted().map { URLQueryItem(name: "channels[]", value: $0) }
        return components?.url
    }

    private func run(_ id: Int) async {
        var failures = 0
        while !Task.isCancelled, !subscribers.isEmpty, id == runID {
            try? await Task.sleep(nanoseconds: UInt64(Self.changeDebounce * 1_000_000_000))
            if Task.isCancelled || subscribers.isEmpty || id != runID { break }
            let channels = desiredChannels
            guard let url = Self.url(channels: channels) else { return }
            let conn = transport.connect(url: url)
            connection = conn
            activeChannels = channels
            let ping = Task { [weak self] in await self?.pingLoop(conn) }
            var connectedThisRound = false
            do {
                while true {
                    let text = try await conn.receive()
                    switch RealtimeFrame.parse(text) {
                    case .connected:
                        connectedThisRound = true
                        failures = 0
                        let recovery = hasConnectedBefore && lastLost
                        hasConnectedBefore = true
                        lastLost = false
                        broadcast(.connected(isRecovery: recovery))
                    case let .message(message):
                        dispatch(message)
                    case .error, .pong, .ignored:
                        break
                    }
                }
            } catch {
                // fall through to reconnect handling
            }
            ping.cancel()
            conn.close()
            if id != runID { break }
            connection = nil
            if Task.isCancelled || subscribers.isEmpty { break }
            if desiredChannels != activeChannels {
                // Deliberate re-subscribe for a changed channel set: reconnect now.
                continue
            }
            lastLost = true
            if !connectedThisRound { failures += 1 } else { failures = max(failures, 1) }
            let wait = backoff(max(failures, 1))
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
        }
    }

    private func pingLoop(_ conn: RealtimeConnection) async {
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: UInt64(Self.pingInterval * 1_000_000_000))
            if Task.isCancelled { return }
            do { try await conn.send("{\"type\":\"ping\"}") } catch { conn.close(); return }
        }
    }

    private func broadcast(_ event: RealtimeEvent) {
        for sub in subscribers.values { sub.continuation.yield(event) }
    }

    private func dispatch(_ message: RealtimeMessage) {
        for sub in subscribers.values where !sub.channels.isDisjoint(with: message.channels) {
            sub.continuation.yield(.message(message))
        }
    }
}
