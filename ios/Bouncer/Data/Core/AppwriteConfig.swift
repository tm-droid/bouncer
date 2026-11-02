import Foundation

enum AppwriteConfig {
    static let endpoint = URL(string: "https://sgp.cloud.appwrite.io/v1")!
    static let realtimeEndpoint = "wss://sgp.cloud.appwrite.io/v1/realtime"
    static let projectId = "6a02f7f3000e6e100b19"
    static let databaseId = "bouncer-db"
    static let bucketId = "cricket-stats"
    static let functionId = "bouncer-api"

    enum Table {
        static let liveDetail = "match-live-detail"
        static let scorecard = "match-scorecard"
        static let commentary = "match-commentary"
    }

    /// Row-scoped Realtime channel. Appwrite has no query-filtered channels, hence the per-row id.
    static func rowChannel(table: String, rowId: String) -> String {
        "databases.\(databaseId).tables.\(table).rows.\(rowId)"
    }

    /// Whole-table channel (commentary row ids are unguessable, so it is filtered client-side).
    static func tableChannel(table: String) -> String {
        "databases.\(databaseId).collections.\(table).documents"
    }
}

/// Stable per-install id sent as `x-bouncer-device`. Not a credential; a rate-limit bucket key.
enum DeviceIdentity {
    static let defaultsKey = "appwrite_device_id"
    private static let lock = NSLock()
    nonisolated(unsafe) private static var cached: String?

    static var id: String { id(defaults: .standard) }

    static func id(defaults: UserDefaults) -> String {
        lock.lock(); defer { lock.unlock() }
        if let cached { return cached }
        if let stored = defaults.string(forKey: defaultsKey), !stored.isEmpty {
            cached = stored
            return stored
        }
        let fresh = UUID().uuidString
        defaults.set(fresh, forKey: defaultsKey)
        cached = fresh
        return fresh
    }
}
