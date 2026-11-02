import Foundation

/// Which billed quota a request draws from. Quotas latch independently.
enum Quota: String, Sendable, CaseIterable {
    case dbRead, bandwidth, executions

    /// Maps Appwrite's 402 `type` (e.g. `limit_databases_reads_exceeded`) onto a quota.
    static func classify(errorType: String?, message: String?, fallback: Quota) -> Quota {
        let hay = ((errorType ?? "") + " " + (message ?? "")).lowercased()
        if hay.contains("bandwidth") { return .bandwidth }
        if hay.contains("execution") { return .executions }
        if hay.contains("database") || hay.contains("read") { return .dbRead }
        return fallback
    }
}

enum AppwriteError: Error, Equatable, Sendable {
    /// 402: a plan quota is spent. The breaker is latched; retrying does nothing until it resets.
    case quotaExceeded(Quota)
    /// The breaker is latched and no probe is due, so no request was sent.
    case breakerOpen(Quota)
    /// 429: short pause, nothing latched.
    case rateLimited(retryAfter: TimeInterval)
    case notFound
    case http(status: Int, type: String?, message: String?)
    case decoding(String)
    case transport(String)
    case invalidResponse

    /// True for failures worth retrying later without any user action.
    var isTransient: Bool {
        switch self {
        case .rateLimited, .transport: return true
        case let .http(status, _, _): return status >= 500
        default: return false
        }
    }

    /// True when the failure means "the backend is not currently readable" (serve cache).
    var isQuota: Bool {
        switch self {
        case .quotaExceeded, .breakerOpen: return true
        default: return false
        }
    }
}
