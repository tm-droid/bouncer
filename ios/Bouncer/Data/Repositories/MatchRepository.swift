import Foundation

enum MatchListKind: String, Sendable, CaseIterable {
    case live, recent, upcoming

    var fileName: String { "\(rawValue)-matches" }

    /// Client TTL: live changes every ~30s server-side; the others a few times a day.
    var ttl: TimeInterval { self == .live ? 30 : 600 }
}

struct MatchList: Equatable, Sendable {
    var kind: MatchListKind
    var matches: [MatchSummary]
    /// Server's `fetchedAt` for the file, when present.
    var fetchedAt: Date?
    /// When this device loaded it.
    var loadedAt: Date
    /// True when a refresh failed and this is the previous copy (quota breaker, offline).
    var isStale: Bool
}

/// Match lists from the public storage files. Cache-first with per-kind TTLs and single-flight, so
/// any number of callers inside the TTL cost zero downloads and concurrent misses cost one.
actor MatchRepository {
    static let shared = MatchRepository()

    private let api: any AppwriteAPI
    private let clock: @Sendable () -> Date
    private var cache: [MatchListKind: Cached<MatchList>] = [:]
    private let flight = SingleFlight<MatchListKind, MatchList>()

    init(api: any AppwriteAPI = AppwriteClient.shared, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.api = api
        self.clock = clock
    }

    /// Fresh-enough list, from cache when inside the TTL. Falls back to a stale copy when the refresh
    /// fails; throws only when there is nothing cached at all.
    func matches(_ kind: MatchListKind, forceRefresh: Bool = false) async throws -> MatchList {
        let now = clock()
        if !forceRefresh, let hit = cache[kind], hit.isFresh(ttl: kind.ttl, now: now) { return hit.value }
        do {
            let fresh = try await flight.run(kind) { [api, clock] in
                let data = try await api.download(file: kind.fileName)
                let file: MatchListFile
                do { file = try WireJSON.decode(MatchListFile.self, from: data) }
                catch { throw AppwriteError.decoding(error.localizedDescription) }
                return MatchList(kind: kind, matches: file.matches, fetchedAt: file.fetchedAt, loadedAt: clock(), isStale: false)
            }
            cache[kind] = Cached(value: fresh, loadedAt: fresh.loadedAt)
            return fresh
        } catch {
            if error is CancellationError { throw error }
            if var old = cache[kind]?.value {
                old.isStale = true
                return old
            }
            throw error
        }
    }

    /// Whatever is in memory, regardless of age. For instant first paint.
    func cached(_ kind: MatchListKind) -> MatchList? { cache[kind]?.value }

    /// A match by id from any cached list.
    func cachedMatch(id: String) -> MatchSummary? {
        for kind in MatchListKind.allCases {
            if let hit = cache[kind]?.value.matches.first(where: { $0.id == id }) { return hit }
        }
        return nil
    }

    /// All three lists, refreshed in parallel; individual failures fall back to stale copies.
    func refreshAll(forceRefresh: Bool = false) async -> [MatchListKind: MatchList] {
        await withTaskGroup(of: (MatchListKind, MatchList?).self) { group in
            for kind in MatchListKind.allCases {
                group.addTask { (kind, try? await self.matches(kind, forceRefresh: forceRefresh)) }
            }
            var out: [MatchListKind: MatchList] = [:]
            for await (kind, list) in group { if let list { out[kind] = list } }
            return out
        }
    }
}
