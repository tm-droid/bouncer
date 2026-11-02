import Foundation

/// News through the `bouncer-api` function. Each call is a billed execution, so the list is cached
/// for 5 minutes and articles for the life of the process (bounded), both single-flight.
actor NewsRepository {
    static let shared = NewsRepository()
    static let listTTL: TimeInterval = 300
    static let articleLimit = 40

    private let api: any AppwriteAPI
    private let clock: @Sendable () -> Date
    private var listCache: Cached<NewsList>?
    private var articles: [Int: NewsArticle] = [:]
    private var articleOrder: [Int] = []
    private let listFlight = SingleFlight<String, NewsList>()
    private let articleFlight = SingleFlight<Int, NewsArticle>()

    init(api: any AppwriteAPI = AppwriteClient.shared, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.api = api
        self.clock = clock
    }

    func stories(forceRefresh: Bool = false) async throws -> NewsList {
        if !forceRefresh, let hit = listCache, hit.isFresh(ttl: Self.listTTL, now: clock()) { return hit.value }
        do {
            let fresh = try await listFlight.run("list") { [api] in
                let data = try await api.execute(path: "/news", body: "")
                do { return try WireJSON.decode(NewsList.self, from: data) }
                catch { throw AppwriteError.decoding(error.localizedDescription) }
            }
            listCache = Cached(value: fresh, loadedAt: clock())
            return fresh
        } catch {
            if error is CancellationError { throw error }
            if let old = listCache { return old.value }
            throw error
        }
    }

    func article(id: Int) async throws -> NewsArticle {
        // A stale article (server fell back to an older cache) is not remembered, so a retry can heal.
        if let hit = articles[id], !hit.isStale { return hit }
        let article = try await articleFlight.run(id) { [api] in
            let data = try await api.execute(path: "/news", body: "{\"storyId\":\(id)}")
            do { return try WireJSON.decode(NewsArticle.self, from: data) }
            catch { throw AppwriteError.decoding(error.localizedDescription) }
        }
        if !article.isStale {
            articles[id] = article
            articleOrder.append(id)
            if articleOrder.count > Self.articleLimit { articles[articleOrder.removeFirst()] = nil }
        }
        return article
    }
}
