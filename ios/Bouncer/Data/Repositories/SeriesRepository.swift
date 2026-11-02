import Foundation

/// The `active-series` storage file, cached for 10 minutes with single-flight.
actor SeriesRepository {
    static let shared = SeriesRepository()
    static let ttl: TimeInterval = 600

    private let api: any AppwriteAPI
    private let clock: @Sendable () -> Date
    private var cache: Cached<ActiveSeriesFile>?
    private let flight = SingleFlight<String, ActiveSeriesFile>()

    init(api: any AppwriteAPI = AppwriteClient.shared, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.api = api
        self.clock = clock
    }

    func activeSeries(forceRefresh: Bool = false) async throws -> ActiveSeriesFile {
        if !forceRefresh, let hit = cache, hit.isFresh(ttl: Self.ttl, now: clock()) { return hit.value }
        do {
            let fresh = try await flight.run("active-series") { [api] in
                let data = try await api.download(file: "active-series")
                do { return try WireJSON.decode(ActiveSeriesFile.self, from: data) }
                catch { throw AppwriteError.decoding(error.localizedDescription) }
            }
            cache = Cached(value: fresh, loadedAt: clock())
            return fresh
        } catch {
            if error is CancellationError { throw error }
            if let old = cache { return old.value }
            throw error
        }
    }
}
