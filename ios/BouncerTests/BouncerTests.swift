import Foundation
import Testing
import Appwrite
@testable import Bouncer

@MainActor
struct BouncerTests {
    private func fixture() throws -> SharedMatchEnvelope {
        let bundle = Bundle(for: TestBundleMarker.self)
        let url = try #require(bundle.url(forResource: "match-detail", withExtension: "json"))
        return try JSONDecoder().decode(SharedMatchEnvelope.self, from: Data(contentsOf: url))
    }
    private func gateway(now: @escaping () -> Date = Date.init, defaults: UserDefaults? = nil, directory: URL? = nil) -> SharedGateway {
        SharedGateway(directory: directory ?? FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString), defaults: defaults ?? UserDefaults(suiteName: UUID().uuidString)!, now: now)
    }
    private func finalFixture() throws -> SharedMatchEnvelope {
        let bundle = Bundle(for: TestBundleMarker.self)
        let url = try #require(bundle.url(forResource: "final-match", withExtension: "json"))
        return try JSONDecoder().decode(SharedMatchEnvelope.self, from: Data(contentsOf: url))
    }
    @Test func sharedRouteUsesExecutionContractAndScoped20SecondTimeout() async throws {
        let envelope = try finalFixture()
        let encoded = try JSONEncoder().encode(envelope)
        let response = try JSONSerialization.data(withJSONObject: ["status": "completed", "responseStatusCode": 200,
            "responseBody": String(decoding: encoded, as: UTF8.self)])
        FunctionURLProtocol.configure(response)
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [FunctionURLProtocol.self]
        let repository = SharedMatchRepository(configuration: try .bundled(), gateway: gateway(), session: URLSession(configuration: config))
        let match = try #require(envelope.summary)
        async let first = repository.matchDetail(match)
        async let second = repository.matchDetail(match)
        let responses = try await [first, second]
        #expect(responses.first?.summary?.status == "India won by 7 wickets")
        let requests = FunctionURLProtocol.requests()
        #expect(requests.count == 1)
        let request = try #require(requests.first)
        #expect(request.timeoutInterval == 20)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path.hasSuffix("/functions/bouncer-api/executions") == true)
        #expect(request.value(forHTTPHeaderField: "X-Appwrite-Key") == nil)
        let bytes = try #require(request.httpBody)
        let body = try #require(JSONSerialization.jsonObject(with: bytes) as? [String: Any])
        #expect(body["path"] as? String == "/match-detail")
        #expect(body["method"] as? String == "POST")
        #expect(body["async"] as? Bool == false)
        let inner = try #require(body["body"] as? String)
        #expect(try JSONDecoder().decode([String: String].self, from: Data(inner.utf8))["matchId"] == match.id)
    }
    @Test func nestedMetadataUsesCanonicalFieldsAndOmitsEmpty() throws {
        let rows = SharedMetadataRow.rows(try finalFixture().meta)
        #expect(rows.first { $0.label == "Venue" }?.value == "Wankhede Stadium, Mumbai, India")
        #expect(rows.first { $0.label == "Toss" }?.value == "Australia · batting")
        #expect(rows.contains { $0.label == "Starts" && !$0.value.isEmpty })
        #expect(SharedMetadataRow.rows(.object([:])).isEmpty)
    }
    @Test func detailUpdateKeepsOtherSectionTimesAndStaleness() throws {
        let previous = try finalFixture()
        let value = previous.applyingDetail(.object(["score": .string("160/3")]), fetchedAt: 1_793_404_800_000,
            now: Date(timeIntervalSince1970: 1_793_404_800), ttl: 60)
        #expect(value.availability["scorecard"]?.fetchedAt == 1000)
        #expect(value.availability["scorecard"]?.stale == true)
        #expect(value.availability["meta"]?.fetchedAt == previous.availability["meta"]?.fetchedAt)
        #expect(value.scorecard == previous.scorecard)
        #expect(value.stale)
    }
    @Test func completedListSummaryBeatsSavedLiveState() throws {
        let server = StubMatchService(envelope: try fixture())
        let completed = try #require(finalFixture().summary)
        let model = SharedMatchScreenModel(match: completed, repository: server)
        #expect(model.displayMatch.status == "India won by 7 wickets")
        #expect(!model.shouldFollowLive)
    }
    @Test func finalSummaryWinsOverOldLiveDetailAndStopsPresence() async throws {
        let server = StubMatchService(envelope: try fixture())
        server.detailResponse = try finalFixture()
        let match = try #require(server.envelope.summary)
        let model = SharedMatchScreenModel(match: match, repository: server)
        await model.runVisible()
        await server.waitForClose()
        #expect(model.displayMatch.status == "India won by 7 wickets")
        #expect(!model.shouldFollowLive)
        #expect(server.presenceCount == 0)
        #expect(server.closeCount == 1)
    }
    @Test func cancelingOnlyWaiterCancelsUnderlyingOperation() async {
        let gate = gateway()
        let started = TestSignal()
        let cancelled = TestSignal()
        let task = Task {
            do {
                _ = try await gate.load("cancel.one", quota: .executions, ttl: 0) {
                    started.fire()
                    do { try await Task.sleep(for: .seconds(60)) }
                    catch { cancelled.fire(); throw error }
                    return Data()
                }
            } catch { }
        }
        await started.wait()
        task.cancel()
        await cancelled.wait()
        await task.value
        #expect(gate.saved("cancel.one") == nil)
    }
    @Test func cancelingOneWaiterPreservesSharedOperation() async throws {
        let gate = gateway()
        let started = TestSignal()
        let joined = TestSignal()
        let release = TestSignal()
        var calls = 0
        let first = Task {
            try await gate.load("cancel.shared", quota: .executions, ttl: 0) {
                calls += 1
                started.fire()
                await release.wait()
                try Task.checkCancellation()
                return Data("shared".utf8)
            }
        }
        await started.wait()
        let second = Task {
            joined.fire()
            return try await gate.load("cancel.shared", quota: .executions, ttl: 0) {
                Issue.record("Duplicate operation")
                return Data()
            }
        }
        await joined.wait()
        first.cancel()
        release.fire()
        let value = try await second.value
        do { _ = try await first.value; Issue.record("Cancelled waiter returned data") } catch { }
        #expect(value == Data("shared".utf8))
        #expect(calls == 1)
    }
    @Test func sharedEnvelopeRetainsMissingSectionsAndRawProvenance() throws {
        let value = try fixture()
        #expect(value.schemaVersion == 1)
        #expect(value.matchId == "12345")
        #expect(value.scorecard == nil)
        #expect(value.availability["scorecard"]?.available == false)
        #expect(value.availability["scorecard"]?.fetchedAt == nil)
        #expect(value.detail?["score"].text == "140/3 (18)")
        #expect(value.commentary?.first?["source"].text == "cricbuzz")
        #expect(value.commentary?.first?["aiGenerated"] == .bool(false))
    }
    @Test func summaryOnlyEnvelopeAcceptsNullFetchTime() throws {
        let bundle = Bundle(for: TestBundleMarker.self)
        let url = try #require(bundle.url(forResource: "match-detail", withExtension: "json"))
        var json = try #require(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        json["fetchedAt"] = NSNull()
        json["detail"] = NSNull()
        json["commentary"] = NSNull()
        let value = try JSONDecoder().decode(SharedMatchEnvelope.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(value.fetchedAt == nil)
        #expect(value.summary?.id == "12345")
        #expect(value.detail == nil)
    }
    @Test func rawBallMetricPreservesLabelInningsAndTimestampAndTableWins() throws {
        let value = try fixture()
        let raw = try #require(value.commentary?.first)
        let line = try #require(SharedCommentary.fromRaw(raw, matchId: value.matchId))
        #expect(line.ball == "18.1")
        #expect(line.innings == 2)
        #expect(line.sort_key == 1)
        #expect(line.source == "cricbuzz")
        #expect(line.ai_generated == false)
        let table = SharedCommentary(commentary: "Server commentary", sort_key: 1, ball: "18.1", innings: 2)
        let merged = SharedCommentary.merging(table: [table], raw: [line])
        #expect(merged.count == 1)
        #expect(merged.first?.commentary == "Server commentary")
        let hundred = try SharedJSON.parse(#"{"timestamp":2,"commText":"A run","ballMetric":{"label":"Ball 25","over":null,"ball":25},"innings":1,"source":"cricbuzz","aiGenerated":false}"#)
        #expect(SharedCommentary.fromRaw(hundred, matchId: value.matchId)?.ball == "Ball 25")
    }
    @Test func partialSectionsKeepOriginalTimestamp() throws {
        var previous = try fixture()
        previous.scorecard = .object(["innings": .array([])])
        previous.availability["scorecard"] = SharedAvailability(available: true, fetchedAt: 1000, stale: false)
        let merged = try fixture().preservingLastGood(previous)
        #expect(merged.scorecard != nil)
        #expect(merged.availability["scorecard"]?.fetchedAt == 1000)
        #expect(merged.availability["scorecard"]?.stale == true)
        #expect(merged.stale)
    }
    @Test func listRetainsOldSummariesAndDetectsUpstreamAge() throws {
        let raw = #"{"data":{"matches":[{"id":"1","title":"A vs B","status":"Scheduled"}]},"fetchedAt":1000}"#
        let value = try JSONDecoder().decode(SharedMatchList.self, from: Data(raw.utf8))
        #expect(value.data.matches.first?.teams == nil)
        #expect(value.isStale(.live, now: Date(timeIntervalSince1970: 32)))
        #expect(!value.isStale(.upcoming, now: Date(timeIntervalSince1970: 32)))
    }
    @Test func cachePrecedesQuotaAndSurvivesRelaunch() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = UserDefaults(suiteName: UUID().uuidString)!
        let gate = gateway(defaults: store, directory: directory)
        let data = Data("saved".utf8)
        gate.remember(data, key: "list.live")
        gate.failure(SharedBackendError.http(402), quota: .bandwidth)
        let restored = gateway(defaults: store, directory: directory)
        let loaded = try await restored.load("list.live", quota: .bandwidth, ttl: 30) { Issue.record("Warm cache made a billed call"); return Data() }
        #expect(loaded == data)
        #expect(throws: SharedBackendError.self) { try restored.permit(.bandwidth) }
    }
    @Test func concurrentRequestsUseOneOperationAndFailurePreservesLastGood() async throws {
        let gate = gateway()
        var calls = 0
        let operation: () async throws -> Data = {
            calls += 1
            await Task.yield()
            return Data("good".utf8)
        }
        async let first = gate.load("detail.1", quota: .executions, ttl: 60, operation: operation)
        async let second = gate.load("detail.1", quota: .executions, ttl: 60, operation: operation)
        let values = try await [first, second]
        #expect(calls == 1)
        #expect(values[0] == values[1])
        do {
            _ = try await gate.load("detail.1", quota: .executions, ttl: 0, force: true) { throw SharedBackendError.http(503) }
            Issue.record("Expected failure")
        } catch { }
        #expect(gate.saved("detail.1")?.data == Data("good".utf8))
    }
    @Test func quota402IsPersistentScopedAnd429IsTransient() throws {
        var now = Date(timeIntervalSince1970: 1_800_000_000)
        let gate = gateway(now: { now })
        gate.failure(SharedBackendError.http(402), quota: .executions)
        #expect(throws: SharedBackendError.self) { try gate.permit(.executions) }
        try gate.permit(.reads)
        gate.failure(SharedBackendError.http(429), quota: .reads)
        #expect(throws: SharedBackendError.self) { try gate.permit(.reads) }
        now = now.addingTimeInterval(61)
        try gate.permit(.reads)
        #expect(throws: SharedBackendError.self) { try gate.permit(.executions) }
        now = now.addingTimeInterval(6 * 3600)
        try gate.permit(.executions)
    }
    @Test func executionGuardIsFinite() throws {
        let gate = gateway()
        for _ in 0..<12 { try gate.permit(.executions) }
        #expect(throws: SharedBackendError.self) { try gate.permit(.executions) }
    }
    @Test func rowTimestampAcceptsEpochAndISOAndMissingIsUnknown() {
        #expect(SharedMatchRepository.rowTime(.number(1_800_000_000_000)) == 1_800_000_000_000)
        #expect(SharedMatchRepository.rowTime(.number(1_800_000_000)) == 1_800_000_000_000)
        #expect(SharedMatchRepository.rowTime(.string("2026-01-01T00:00:00Z")) != nil)
        #expect(SharedMatchRepository.rowTime(.string("2026-10-31T01:23:45.678Z")) == 1_793_409_825_678)
        #expect(SharedMatchRepository.rowTime(nil) == nil)
    }
    @Test func visibleLifecycleCancelsPresenceAndIgnoresLateStreamEvents() async throws {
        let server = StubMatchService(envelope: try fixture())
        let match = try #require(server.envelope.summary)
        let model = SharedMatchScreenModel(match: match, repository: server)
        let task = Task { await model.runVisible() }
        await server.waitForPresence()
        #expect(server.presenceCount == 1)
        #expect(model.visibleSession != nil)
        #expect(server.commentaryRequests.first?.limit == 48)
        model.stop()
        task.cancel()
        await task.value
        #expect(model.visibleSession == nil)
        await server.waitForClose()
        #expect(server.closeCount == 1)
        server.onCommentary?(SharedCommentary(commentary: "Late delivery", sort_key: 10, match_id: match.id))
        #expect(model.commentary.isEmpty)
        #expect(server.presenceCount == 1)
        #expect(SharedMatchScreenModel.heartbeatNanoseconds == 90_000_000_000)
    }
    @Test func olderCommentaryIsExplicitAndUses150() async throws {
        let server = StubMatchService(envelope: try fixture())
        server.lines = [SharedCommentary(commentary: "Delivery", sort_key: 20)]
        let match = try #require(server.envelope.summary)
        let model = SharedMatchScreenModel(match: match, repository: server)
        let task = Task { await model.runVisible() }
        await server.waitForPresence()
        #expect(server.commentaryRequests.count == 1)
        await model.loadOlder()
        #expect(server.commentaryRequests.last?.older == 20)
        #expect(server.commentaryRequests.last?.limit == 150)
        task.cancel()
        await task.value
    }
}

private final class TestBundleMarker: NSObject { }

@MainActor
private final class StubMatchService: SharedMatchServing {
    let envelope: SharedMatchEnvelope
    var detailResponse: SharedMatchEnvelope
    private let closed = TestSignal()
    var presenceCount = 0
    var closeCount = 0
    var lines: [SharedCommentary] = []
    var commentaryRequests: [(older: Double?, limit: Int)] = []
    var onCommentary: ((SharedCommentary) -> Void)?
    private var presenceWaiter: CheckedContinuation<Void, Never>?
    init(envelope: SharedMatchEnvelope) { self.envelope = envelope; self.detailResponse = envelope }
    func savedDetail(_ id: String) -> SharedMatchEnvelope? { envelope }
    func save(_ envelope: SharedMatchEnvelope) { }
    func savedCommentary(_ id: String) -> [SharedCommentary] { [] }
    func saveCommentary(_ lines: [SharedCommentary], matchId: String) { }
    func detail(_ match: SharedMatch) async throws -> SharedMatchEnvelope { detailResponse }
    func scorecard(_ match: SharedMatch) async throws -> SharedScorecardSnapshot? { nil }
    func commentary(_ matchId: String, olderThan: Double?, newerThan: Double?) async throws -> [SharedCommentary] {
        commentaryRequests.append((olderThan, olderThan == nil && newerThan == nil ? 48 : 150))
        return lines
    }
    func presence(_ matchId: String) async throws {
        presenceCount += 1
        presenceWaiter?.resume()
        presenceWaiter = nil
    }
    func waitForClose() async { await closed.wait() }
    func waitForPresence() async {
        if presenceCount > 0 { return }
        await withCheckedContinuation { presenceWaiter = $0 }
    }
    func observe(_ matchId: String, onDetail: @escaping (SharedStoredRow) -> Void, onCommentary: @escaping (SharedCommentary) -> Void) async throws -> [SharedMatchStream] {
        self.onCommentary = onCommentary
        return [SharedMatchStream(close: { self.closeCount += 1; self.closed.fire() })]
    }
}

@MainActor
private final class TestSignal {
    private var fired = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        if fired { return }
        await withCheckedContinuation { waiters.append($0) }
    }
    func fire() {
        fired = true
        let pending = waiters
        waiters = []
        pending.forEach { $0.resume() }
    }
}

private final class FunctionURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    private static var response = Data()
    private static var recorded: [URLRequest] = []
    static func configure(_ data: Data) { lock.lock(); defer { lock.unlock() }; response = data; recorded = [] }
    static func requests() -> [URLRequest] { lock.lock(); defer { lock.unlock() }; return recorded }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        var captured = request
        if captured.httpBody == nil, let stream = captured.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var body = Data()
            var buffer = [UInt8](repeating: 0, count: 1024)
            while true {
                let count = stream.read(&buffer, maxLength: buffer.count)
                guard count > 0 else { break }
                body.append(contentsOf: buffer.prefix(count))
            }
            captured.httpBody = body
        }
        Self.lock.lock()
        Self.recorded.append(captured)
        let data = Self.response
        Self.lock.unlock()
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() { }
}
