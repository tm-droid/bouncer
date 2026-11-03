import Foundation

/// A side's results by format from the static history site (Cricsheet), falling back to Bouncer's own
/// archive for a side Cricsheet withholds (Afghanistan). Not Appwrite, so no quota. Every failure is
/// nil: the page just shows no records. Kept for the process; the sites are rebuilt weekly.
actor TeamRecordsRepository {
    static let shared = TeamRecordsRepository()
    static let historyBase = URL(string: "https://tm-droid.github.io/bouncer-history/")!
    static let archiveBase = URL(string: "https://tm-droid.github.io/bouncer-archive/")!

    /// Body of a 200, nil for 404, offline or anything else.
    typealias Fetcher = @Sendable (URL) async -> Data?

    private let fetcher: Fetcher
    private var teams: [HistoryTeam]?
    private var cache: [String: [FormatRecord]] = [:]

    init(fetcher: @escaping Fetcher = { await TeamRecordsRepository.get($0) }) {
        self.fetcher = fetcher
    }

    /// `name` is the app's team name, with any "Women" suffix.
    func records(for name: String) async -> [FormatRecord]? {
        let women = TeamRecords.isWomens(name)
        let side = TeamRecords.archiveSideName(name)
        let cacheKey = "\(side)|\(women)"
        if let hit = cache[cacheKey] { return hit }
        guard let teams = await loadTeams() else { return nil }
        let result: [FormatRecord]?
        if let key = TeamRecords.key(in: teams, name: side, women: women) {
            result = await fetchTeam(Self.historyBase, key: key)
        } else if !women {
            // Only once the Cricsheet list loaded: offline, a missing key says nothing about the side.
            result = await fetchTeam(Self.archiveBase, key: TeamRecords.archiveKey(side))?.map { r in
                var r = r; r.fromArchive = true; return r
            }
        } else {
            result = nil
        }
        if let result { cache[cacheKey] = result }
        return result
    }

    private func loadTeams() async -> [HistoryTeam]? {
        if let teams { return teams }
        guard let data = await fetcher(Self.historyBase.appending(path: "idx/teams.json")),
              let parsed = TeamRecords.parseTeams(data) else { return nil }
        teams = parsed
        return parsed
    }

    private func fetchTeam(_ base: URL, key: String) async -> [FormatRecord]? {
        guard let data = await fetcher(base.appending(path: "s/team/\(key).json")) else { return nil }
        return TeamRecords.parseTeam(data)
    }

    static func get(_ url: URL) async -> Data? {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return data
    }
}
