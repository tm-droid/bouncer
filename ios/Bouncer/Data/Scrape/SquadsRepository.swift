import Foundation

/// Fetches team sheets from Cricbuzz on demand (no Appwrite cost) and keeps them for the process.
/// A playing XI never changes once announced; a pre-toss squad is kept `preTossTTL` so the XI
/// appears once it is published.
actor SquadsRepository {
    static let shared = SquadsRepository()
    static let preTossTTL: TimeInterval = 600
    static let desktopUserAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    typealias HTMLFetcher = @Sendable (URL) async throws -> String

    private let fetcher: HTMLFetcher
    private let clock: @Sendable () -> Date
    private var cache: [String: (at: Date, squads: Squads)] = [:]
    private var inFlight: [String: Task<Squads?, Never>] = [:]

    init(fetcher: @escaping HTMLFetcher = { try await SquadsRepository.fetchHTML($0) }, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.fetcher = fetcher
        self.clock = clock
    }

    static func url(matchId: String) -> URL {
        URL(string: "https://www.cricbuzz.com/cricket-match-squads/\(matchId.filter { $0.isNumber })")!
    }

    func squads(matchId: String) async -> Squads? {
        let now = clock()
        if let hit = cache[matchId], hit.squads.hasPlayingXI || now.timeIntervalSince(hit.at) < Self.preTossTTL {
            return hit.squads
        }
        if let task = inFlight[matchId] { return await task.value ?? cache[matchId]?.squads }
        let fetcher = self.fetcher
        let url = Self.url(matchId: matchId)
        let task = Task<Squads?, Never> {
            guard let html = try? await fetcher(url) else { return nil }
            return SquadsParser.parse(html: html)
        }
        inFlight[matchId] = task
        let result = await task.value
        inFlight[matchId] = nil
        if let result { cache[matchId] = (clock(), result) }
        return result ?? cache[matchId]?.squads
    }

    static func fetchHTML(_ url: URL) async throws -> String {
        var request = URLRequest(url: url)
        request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return String(decoding: data, as: UTF8.self)
    }
}
