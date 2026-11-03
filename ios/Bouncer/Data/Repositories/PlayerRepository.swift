import Foundation

/// Player profiles. Search goes through the `bouncer-api` function (a billed execution, so answers
/// are cached and single-flight). A profile is the shared `player-profiles` row when one exists
/// (one billed row), else a Cricbuzz scrape (free). Profiles are kept in memory for the process.
actor PlayerRepository {
    static let shared = PlayerRepository()
    static let table = "player-profiles"
    static let searchLimit = 10
    static let cacheLimit = 40

    private let api: any AppwriteAPI
    private let fetcher: SquadsRepository.HTMLFetcher
    private var searches: [String: [PlayerSearchResult]] = [:]
    private var profiles: [String: PlayerProfile] = [:]
    private var profileOrder: [String] = []
    private let searchFlight = SingleFlight<String, [PlayerSearchResult]>()
    private let profileFlight = SingleFlight<String, PlayerProfile>()

    init(api: any AppwriteAPI = AppwriteClient.shared,
         fetcher: @escaping SquadsRepository.HTMLFetcher = { try await SquadsRepository.fetchHTML($0) }) {
        self.api = api
        self.fetcher = fetcher
    }

    /// Players matching `q`, best first, at most 10. Empty for a blank query without a request.
    func search(_ q: String) async throws -> [PlayerSearchResult] {
        let key = q.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { return [] }
        if let hit = searches[key] { return hit }
        let results = try await searchFlight.run(key) { [api] in
            let data = try await api.execute(path: "/player-search", body: "{\"q\":\(WireJSON.quote(key))}")
            do { return Array(try WireJSON.decode(PlayerSearchResponse.self, from: data).players.prefix(Self.searchLimit)) }
            catch { throw AppwriteError.decoding(error.localizedDescription) }
        }
        searches[key] = results
        return results
    }

    /// The hit whose name equals `name` (ignoring case and accents), else the first, as Android does.
    nonisolated static func bestMatch(for name: String, in results: [PlayerSearchResult]) -> PlayerSearchResult? {
        let wanted = name.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespaces)
        return results.first { $0.name.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil) == wanted }
            ?? results.first
    }

    /// `id` is Cricbuzz's profile id when the caller has it; otherwise the name is searched.
    func profile(id: String?, name: String) async throws -> PlayerProfile {
        var playerId = id.flatMap { $0.isEmpty ? nil : $0 }
        var resolvedName = name
        if playerId == nil {
            guard let hit = Self.bestMatch(for: name, in: try await search(name)) else { throw AppwriteError.notFound }
            playerId = hit.id
            resolvedName = hit.name
        }
        let pid = playerId!
        if let hit = profiles[pid] { return hit }
        let loaded = try await profileFlight.run(pid) { [self] in try await load(id: pid, name: resolvedName) }
        profiles[pid] = loaded
        profileOrder.append(pid)
        if profileOrder.count > Self.cacheLimit { profiles[profileOrder.removeFirst()] = nil }
        return loaded
    }

    /// Table first; any failure there (404, quota, bad row) falls through to the scrape.
    private func load(id: String, name: String) async throws -> PlayerProfile {
        if let data = try? await api.getRow(table: Self.table, id: "cb_\(id)"),
           let row = PlayerProfile(row: data, id: id) {
            return row
        }
        try Task.checkCancellation()
        let html = try await fetcher(Self.profileURL(id: id, name: name))
        guard let scraped = PlayerProfileParser.parse(html: html, id: id, fallbackName: name) else { throw AppwriteError.notFound }
        return scraped
    }

    nonisolated static func profileURL(id: String, name: String) -> URL {
        let slug = name.lowercased().replacing(#/[^a-z0-9]+/#, with: "-").trimmingCharacters(in: CharacterSet(charactersIn: "-"))
        return URL(string: "https://www.cricbuzz.com/profiles/\(id.filter { $0.isNumber })/\(slug)")!
    }
}
