import Foundation

/// The four billed operations the app performs. Every repository depends on this protocol, never on
/// the concrete client, so tests substitute fakes and no call can bypass the quota breaker.
protocol AppwriteAPI: Sendable {
    /// Storage file bytes (`cricket-stats` bucket).
    func download(file: String) async throws -> Data
    /// One row of a table, raw JSON. Billed per row.
    func getRow(table: String, id: String) async throws -> Data
    /// A page of rows, raw JSON `{total, rows}`. Billed per row returned.
    func listRows(table: String, queries: [Query]) async throws -> Data
    /// Executes `bouncer-api` synchronously and returns the function's `responseBody` bytes.
    func execute(path: String, body: String) async throws -> Data
}

actor AppwriteClient: AppwriteAPI {
    static let shared = AppwriteClient()

    private let session: URLSession
    private let breaker: QuotaBreaker
    private let deviceId: @Sendable () -> String
    private var etags: [String: (etag: String, data: Data)] = [:]
    private static let etagCacheLimit = 24

    init(session: URLSession = AppwriteClient.makeSession(),
         breaker: QuotaBreaker = .shared,
         deviceId: @escaping @Sendable () -> String = { DeviceIdentity.id }) {
        self.session = session
        self.breaker = breaker
        self.deviceId = deviceId
    }

    /// No URLCache: the storage endpoint sends `max-age` of 45 days and no validators, so the system
    /// cache would serve month-old match lists. Freshness is the repositories' TTL; ETags are handled
    /// by hand in `download` in case the server starts sending them.
    static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 60
        config.waitsForConnectivity = false
        return URLSession(configuration: config)
    }

    // MARK: AppwriteAPI

    func download(file: String) async throws -> Data {
        let path = "/storage/buckets/\(AppwriteConfig.bucketId)/files/\(Self.pathEscape(file))/download"
        var request = makeRequest(path: path, query: nil)
        let cached = etags[file]
        if let cached { request.setValue(cached.etag, forHTTPHeaderField: "If-None-Match") }
        let (data, response) = try await perform(request, quota: .bandwidth)
        if response.statusCode == 304, let cached { return cached.data }
        if let etag = response.value(forHTTPHeaderField: "ETag") {
            if etags.count >= Self.etagCacheLimit, etags[file] == nil, let drop = etags.keys.first { etags[drop] = nil }
            etags[file] = (etag, data)
        }
        return data
    }

    func getRow(table: String, id: String) async throws -> Data {
        let path = "/tablesdb/\(AppwriteConfig.databaseId)/tables/\(Self.pathEscape(table))/rows/\(Self.pathEscape(id))"
        let (data, _) = try await perform(makeRequest(path: path, query: nil), quota: .dbRead)
        return data
    }

    func listRows(table: String, queries: [Query]) async throws -> Data {
        let path = "/tablesdb/\(AppwriteConfig.databaseId)/tables/\(Self.pathEscape(table))/rows"
        let (data, _) = try await perform(makeRequest(path: path, query: queries), quota: .dbRead)
        return data
    }

    func execute(path: String, body: String) async throws -> Data {
        var request = makeRequest(path: "/functions/\(AppwriteConfig.functionId)/executions", query: nil)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let envelope: [String: Any] = [
            "async": false,
            "path": path,
            "body": body,
            "headers": ["x-bouncer-device": deviceId()],
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: envelope)
        let (data, _) = try await perform(request, quota: .executions)
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AppwriteError.invalidResponse
        }
        let status = (obj["responseStatusCode"] as? Int) ?? 0
        let responseBody = (obj["responseBody"] as? String) ?? ""
        guard (200..<300).contains(status) else {
            throw AppwriteError.http(status: status == 0 ? 502 : status, type: "function_error", message: String(responseBody.prefix(200)))
        }
        return Data(responseBody.utf8)
    }

    // MARK: Plumbing

    private func makeRequest(path: String, query: [Query]?) -> URLRequest {
        var string = AppwriteConfig.endpoint.absoluteString + path
        if let query, !query.isEmpty {
            string += "?" + query.map { "queries%5B%5D=" + Self.queryEscape($0.json) }.joined(separator: "&")
        }
        var request = URLRequest(url: URL(string: string)!)
        request.setValue(AppwriteConfig.projectId, forHTTPHeaderField: "X-Appwrite-Project")
        request.setValue(deviceId(), forHTTPHeaderField: "x-bouncer-device")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func perform(_ request: URLRequest, quota: Quota) async throws -> (Data, HTTPURLResponse) {
        if let blocked = breaker.check(quota) { throw blocked }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw AppwriteError.transport(error.localizedDescription)
        }
        guard let http = response as? HTTPURLResponse else { throw AppwriteError.invalidResponse }
        switch http.statusCode {
        case 200..<300, 304:
            breaker.recordSuccess(quota)
            return (data, http)
        case 402:
            let info = Self.errorInfo(data)
            let hit = Quota.classify(errorType: info.type, message: info.message, fallback: quota)
            breaker.recordQuotaExceeded(hit)
            throw AppwriteError.quotaExceeded(hit)
        case 429:
            let after = http.value(forHTTPHeaderField: "Retry-After").flatMap(TimeInterval.init)
            breaker.recordRateLimited(retryAfter: after)
            throw AppwriteError.rateLimited(retryAfter: after ?? QuotaBreaker.defaultRatePause)
        case 404:
            throw AppwriteError.notFound
        default:
            let info = Self.errorInfo(data)
            throw AppwriteError.http(status: http.statusCode, type: info.type, message: info.message)
        }
    }

    static func errorInfo(_ data: Data) -> (type: String?, message: String?) {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return (nil, nil) }
        return (obj["type"] as? String, obj["message"] as? String)
    }

    private static let unreserved = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

    static func queryEscape(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: unreserved) ?? s
    }

    static func pathEscape(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: unreserved) ?? s
    }
}
