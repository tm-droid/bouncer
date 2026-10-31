import Foundation
import Appwrite

@MainActor
struct SharedMatchStream {
    let close: () async -> Void
}

@MainActor
protocol SharedMatchServing: AnyObject {
    func savedDetail(_ id: String) -> SharedMatchEnvelope?
    func save(_ envelope: SharedMatchEnvelope)
    func savedCommentary(_ id: String) -> [SharedCommentary]
    func saveCommentary(_ lines: [SharedCommentary], matchId: String)
    func detail(_ match: SharedMatch) async throws -> SharedMatchEnvelope
    func scorecard(_ match: SharedMatch) async throws -> SharedScorecardSnapshot?
    func commentary(_ matchId: String, olderThan: Double?, newerThan: Double?) async throws -> [SharedCommentary]
    func presence(_ matchId: String) async throws
    func observe(_ matchId: String, onDetail: @escaping (SharedStoredRow) -> Void, onCommentary: @escaping (SharedCommentary) -> Void) async throws -> [SharedMatchStream]
}

@MainActor
final class SharedMatchRepository: SharedMatchServing {
    let gateway: SharedGateway
    let configuration: SharedBackendConfiguration
    private let storage: Storage
    private let tables: TablesDB
    private let functions: Functions
    private let realtime: Realtime
    private let session: URLSession

    init(configuration: SharedBackendConfiguration, gateway: SharedGateway? = nil, session: URLSession = .shared) {
        self.configuration = configuration
        self.gateway = gateway ?? SharedGateway()
        self.session = session
        let client = Client().setEndpoint(configuration.endpoint).setProject(configuration.projectId)
        storage = Storage(client)
        tables = TablesDB(client)
        functions = Functions(client)
        realtime = Realtime(client)
    }
    func savedList(_ kind: MatchListKind) -> SharedMatchList? {
        gateway.saved("list.\(kind.rawValue)").flatMap { try? JSONDecoder().decode(SharedMatchList.self, from: $0.data) }
    }
    func list(_ kind: MatchListKind, force: Bool = false) async throws -> SharedMatchList {
        let bytes = try await gateway.load("list.\(kind.rawValue)", quota: .bandwidth, ttl: kind.ttl, force: force) { [self] in
            let buffer = try await storage.getFileDownload(bucketId: configuration.matchesBucketId, fileId: "\(kind.rawValue)-matches")
            let data = Data(buffer.readableBytesView)
            _ = try JSONDecoder().decode(SharedMatchList.self, from: data)
            return data
        }
        return try JSONDecoder().decode(SharedMatchList.self, from: bytes)
    }
    func savedDetail(_ id: String) -> SharedMatchEnvelope? {
        gateway.saved("screen.\(id)").flatMap { try? JSONDecoder().decode(SharedMatchEnvelope.self, from: $0.data) }
    }
    func save(_ envelope: SharedMatchEnvelope) {
        if let bytes = try? JSONEncoder().encode(envelope) { gateway.remember(bytes, key: "screen.\(envelope.matchId)") }
    }
    func savedCommentary(_ id: String) -> [SharedCommentary] {
        guard let data = gateway.saved("commentary.\(id).latest.")?.data else { return [] }
        return (try? JSONDecoder().decode([SharedCommentary].self, from: data)) ?? []
    }
    func saveCommentary(_ lines: [SharedCommentary], matchId: String) {
        if let data = try? JSONEncoder().encode(Array(lines.prefix(48))) {
            gateway.remember(data, key: "commentary.\(matchId).latest.")
        }
    }
    private func row(_ match: SharedMatch, scorecard: Bool = false) async throws -> SharedStoredRow? {
        let table = configuration.tables[scorecard ? "matchScorecard" : "matchLiveDetail"]!
        do {
            let data = try await gateway.load("row.\(table).\(match.id)", quota: .reads, ttl: match.isLive ? 60 : 600) { [self] in
                let row = try await tables.getRow(databaseId: configuration.databaseId, tableId: table, rowId: match.id, nestedType: SharedStoredRow.self)
                return try JSONEncoder().encode(row.data)
            }
            return try JSONDecoder().decode(SharedStoredRow.self, from: data)
        } catch let error as AppwriteError where error.code == 404 { return nil }
    }
    static func rowTime(_ value: SharedJSON?) -> Double? {
        if let number = value?.number { return number < 1e12 ? number * 1000 : number }
        if let text = value?.text {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: text) { return date.timeIntervalSince1970 * 1000 }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: text) { return date.timeIntervalSince1970 * 1000 }
        }
        return nil
    }
    func detail(_ match: SharedMatch) async throws -> SharedMatchEnvelope {
        var saved = savedDetail(match.id)
        if let saved, (!match.isTerminal || saved.summary?.isTerminal == true),
           let section = saved.availability["detail"], section.available, !section.stale,
           let stamp = section.fetchedAt, Date().timeIntervalSince1970 - stamp / 1000 < (match.isLive ? 60 : 600) {
            return saved
        }
        var rowError: Error?
        do {
            if let row = try await row(match), let raw = row.payload {
                let payload = try SharedJSON.parse(raw)
                let timestamp = Self.rowTime(row.updated_at)
                let stale = timestamp.map { Date().timeIntervalSince1970 - $0 / 1000 > (match.isLive ? 60 : 600) } ?? true
                // Keep a result-bearing completed summary from the list over legacy frozen rows.
                var base = saved ?? SharedMatchEnvelope(schemaVersion: 1, matchId: match.id, state: match.state, summary: match,
                    fetchedAt: nil, stale: true, retryAfterSeconds: 0, availability: [:])
                if base.summary?.isTerminal != true { base.summary = match; base.state = match.state }
                let envelope = base.applyingDetail(payload, fetchedAt: timestamp, ttl: match.isLive ? 60 : 600)
                saved = envelope
                save(envelope)
                if !stale { return envelope }
            }
        } catch { rowError = error }
        do { return try await matchDetail(match) }
        catch {
            if let saved { return saved }
            throw rowError ?? error
        }
    }
    func scorecard(_ match: SharedMatch) async throws -> SharedScorecardSnapshot? {
        if let saved = savedDetail(match.id), let value = saved.scorecard,
           let section = saved.availability["scorecard"], section.available, !section.stale,
           let stamp = section.fetchedAt, Date().timeIntervalSince1970 - stamp / 1000 < (match.isLive ? 60 : 600) {
            return SharedScorecardSnapshot(value: value, fetchedAt: stamp, stale: false)
        }
        var fallback: SharedScorecardSnapshot?
        do {
            if let row = try await row(match, scorecard: true), let raw = row.scorecard {
                let value = try SharedJSON.parse(raw)
                let timestamp = Self.rowTime(row.updated_at)
                let stale = timestamp.map { Date().timeIntervalSince1970 - $0 / 1000 > (match.isLive ? 60 : 600) } ?? true
                let snapshot = SharedScorecardSnapshot(value: value, fetchedAt: timestamp, stale: stale)
                if !stale { return snapshot }
                fallback = snapshot
            }
        } catch is CancellationError { throw CancellationError() }
        catch { /* The shared route can still supply a missing or unavailable row. */ }
        do {
            let result = try await matchDetail(match)
            guard let value = result.scorecard else { return fallback }
            return SharedScorecardSnapshot(value: value, fetchedAt: result.availability["scorecard"]?.fetchedAt,
                stale: result.availability["scorecard"]?.stale ?? true)
        } catch is CancellationError { throw CancellationError() }
        catch { if let fallback { return fallback }; throw error }
    }
    private struct ExecutionResponse: Decodable {
        let status: String
        let responseStatusCode: Int
        let responseBody: String
    }
    func matchDetail(_ match: SharedMatch) async throws -> SharedMatchEnvelope {
        let key = "detail.\(match.id)"
        let cached = gateway.saved(key).flatMap { try? JSONDecoder().decode(SharedMatchEnvelope.self, from: $0.data) }
        let ttl = max(match.isLive ? 60.0 : 600.0, Double(cached?.retryAfterSeconds ?? 0))
        let bytes = try await gateway.load(key, quota: .executions, ttl: ttl) { [self] in
            // Only this route uses the shared 20-second deadline; SDK service timeouts stay untouched.
            var request = URLRequest(url: URL(string: configuration.endpoint + "/functions/\(configuration.apiFunctionId)/executions")!, timeoutInterval: 20)
            request.httpMethod = "POST"
            request.setValue(configuration.projectId, forHTTPHeaderField: "X-Appwrite-Project")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            let body = String(decoding: try JSONEncoder().encode(["matchId": match.id]), as: UTF8.self)
            request.httpBody = try JSONSerialization.data(withJSONObject: ["body": body, "async": false, "path": "/match-detail", "method": "POST"])
            let (data, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse else { throw SharedBackendError.invalidResponse }
            guard (200..<300).contains(response.statusCode) else { throw SharedBackendError.http(response.statusCode) }
            let execution = try JSONDecoder().decode(ExecutionResponse.self, from: data)
            guard execution.status == "completed", (200..<300).contains(execution.responseStatusCode) else { throw SharedBackendError.http(execution.responseStatusCode) }
            let result = Data(execution.responseBody.utf8)
            let envelope = try JSONDecoder().decode(SharedMatchEnvelope.self, from: result)
            guard envelope.schemaVersion == 1, envelope.matchId == match.id else { throw SharedBackendError.invalidResponse }
            return result
        }
        let result = try JSONDecoder().decode(SharedMatchEnvelope.self, from: bytes).preservingLastGood(savedDetail(match.id))
        save(result)
        return result
    }
    func commentary(_ matchId: String, olderThan: Double? = nil, newerThan: Double? = nil) async throws -> [SharedCommentary] {
        let limit = olderThan == nil && newerThan == nil ? 48 : 150
        let key = "commentary.\(matchId).\(olderThan.map { String($0) } ?? "latest").\(newerThan.map { String($0) } ?? "")"
        let data = try await gateway.load(key, quota: .reads, cost: limit, ttl: 30) { [self] in
            var queries = [Query.equal("match_id", value: matchId), Query.orderDesc("sort_key"), Query.limit(limit)]
            if let olderThan { queries.append(Query.lessThan("sort_key", value: olderThan)) }
            if let newerThan { queries.append(Query.greaterThan("sort_key", value: newerThan)) }
            let rows = try await tables.listRows(databaseId: configuration.databaseId, tableId: configuration.tables["matchCommentary"]!, queries: queries, total: false, nestedType: SharedCommentary.self)
            return try JSONEncoder().encode(rows.rows.map(\.data))
        }
        return try JSONDecoder().decode([SharedCommentary].self, from: data)
    }
    func presence(_ matchId: String) async throws {
        _ = try await gateway.load("presence.\(matchId)", quota: .executions, ttl: 60) { [self] in
            let body = String(decoding: try JSONEncoder().encode(["matchId": matchId]), as: UTF8.self)
            let result = try await functions.createExecution(functionId: configuration.apiFunctionId, body: body, async: false, path: "/presence", method: .pOST)
            guard result.status == .completed, (200..<300).contains(result.responseStatusCode) else { throw SharedBackendError.http(result.responseStatusCode) }
            return Data(result.responseBody.utf8)
        }
    }
    func observe(_ matchId: String, onDetail: @escaping (SharedStoredRow) -> Void, onCommentary: @escaping (SharedCommentary) -> Void) async throws -> [SharedMatchStream] {
        try gateway.permit(.realtime, cost: 2)
        let root = "databases.\(configuration.databaseId).tables"
        var subscriptions: [RealtimeSubscription] = []
        do {
            let detail = try await realtime.subscribe(channels: Set(["\(root).\(configuration.tables["matchLiveDetail"]!).rows.\(matchId)"]), payloadType: SharedStoredRow.self) { event in
                guard let payload = event.payload, payload["match_id"] as? String == matchId,
                      let bytes = try? JSONSerialization.data(withJSONObject: payload),
                      let row = try? JSONDecoder().decode(SharedStoredRow.self, from: bytes) else { return }
                Task { @MainActor in onDetail(row) }
            }
            subscriptions.append(detail)
            let commentary = try await realtime.subscribe(channels: Set(["\(root).\(configuration.tables["matchCommentary"]!).rows"]), payloadType: SharedCommentary.self, queries: [Query.equal("match_id", value: matchId)]) { event in
                guard let payload = event.payload, payload["match_id"] as? String == matchId,
                      let bytes = try? JSONSerialization.data(withJSONObject: payload),
                      let line = try? JSONDecoder().decode(SharedCommentary.self, from: bytes) else { return }
                Task { @MainActor in onCommentary(line) }
            }
            subscriptions.append(commentary)
            return subscriptions.map { subscription in SharedMatchStream(close: { try? await subscription.close() }) }
        } catch {
            for subscription in subscriptions { try? await subscription.close() }
            gateway.failure(error, quota: .realtime)
            throw error
        }
    }
}
