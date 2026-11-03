import Foundation

/// Reads of a flight payload's loosely typed scalars, shared by the series parsers.
enum FlightField {
    /// Scalar to string; blank and React `$undefined`-style markers are treated as absent.
    static func string(_ any: Any?) -> String? {
        let s: String?
        if let v = any as? String { s = v }
        else if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { s = n.stringValue }
        else { s = nil }
        guard let s, !s.trimmingCharacters(in: .whitespaces).isEmpty, !s.hasPrefix("$") else { return nil }
        return s
    }

    static func int(_ any: Any?) -> Int? {
        if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { return n.intValue }
        return string(any).flatMap { Int($0) }
    }
}

/// A series' URL slug. Every series tab answers 200 with no data unless the slug is in the path
/// (`/cricket-series/{id}/{slug}/matches`), and it cannot be built from the series name ("SA v AUS
/// 2026" is `australia-tour-of-south-africa-2026`), so it is read off any of the series' own tab
/// links, as Android's `seriesSlugOf` does.
enum SeriesSlug {
    static func of(html: String, seriesId: String) -> String? {
        let pattern = "/cricket-series/\(NSRegularExpression.escapedPattern(for: seriesId))/[a-z0-9-]+/(matches|points-table|stats|squads|news|venues)"
        guard let range = html.range(of: pattern, options: .regularExpression) else { return nil }
        return html[range].split(separator: "/").dropFirst(2).first.map(String.init)
    }
}

/// The Schedule tab: `matchesData.matchDetails[].matchDetailsMap` holds one date group per heading, each
/// a list of the `{matchInfo, matchScore}` objects the match lists use. Port of Android's
/// `parseSeriesSchedule`. The site-wide ticker is on the page too as bare `matchInfo` objects, so the
/// walk keys on `matchDetailsMap` and never on `matchInfo` alone, or every series would list whatever
/// is live anywhere.
enum SeriesScheduleParser {
    static func parse(html: String) -> [SeriesMatchDay] {
        var days: [SeriesMatchDay] = []
        CricbuzzFlightParser.forEachPayload(in: html) { collect($0, into: &days) }
        return days
    }

    private static func collect(_ value: Any, into days: inout [SeriesMatchDay]) {
        if let dict = value as? [String: Any] {
            if let group = dict["matchDetailsMap"] as? [String: Any] {
                let matches = (group["match"] as? [Any] ?? []).compactMap { match($0 as? [String: Any]) }
                if !matches.isEmpty { days.append(SeriesMatchDay(label: FlightField.string(group["key"]) ?? "", matches: matches)) }
                return
            }
            // Dictionary order is unspecified; a payload holds its groups in one array, so order survives.
            for child in dict.values { collect(child, into: &days) }
        } else if let array = value as? [Any] {
            for child in array { collect(child, into: &days) }
        }
    }

    /// One `{matchInfo, matchScore}` object, or nil if it is not one. Port of Android's `parseMatchObject`.
    static func match(_ obj: [String: Any]?) -> MatchSummary? {
        guard let info = obj?["matchInfo"] as? [String: Any],
              let id = FlightField.string(info["matchId"]),
              let t1 = info["team1"] as? [String: Any], let name1 = FlightField.string(t1["teamName"]),
              let t2 = info["team2"] as? [String: Any], let name2 = FlightField.string(t2["teamName"]),
              let status = FlightField.string(info["status"]) else { return nil }
        let format = FlightField.string(info["matchFormat"]) ?? ""
        let score = obj?["matchScore"] as? [String: Any]
        let batting = FlightField.string(score?["battingTeamId"]) ?? FlightField.string(info["currBatTeamId"])
        func side(_ team: [String: Any], _ name: String, _ key: String) -> TeamScore {
            let s = innings(score?[key] as? [String: Any], multiDay: ScoreFormat.isMultiDay(format))
            let teamId = FlightField.string(team["teamId"]) ?? ""
            return TeamScore(teamId: teamId, name: name, score: s.score, overs: s.overs,
                             isBatting: batting != nil && batting == teamId)
        }
        return MatchSummary(
            id: id, title: "\(name1) vs \(name2)", status: status, state: FlightField.string(info["state"]) ?? "Live",
            matchFormat: format, seriesId: FlightField.string(info["seriesId"]) ?? "",
            seriesName: FlightField.string(info["seriesName"]) ?? "", matchDesc: FlightField.string(info["matchDesc"]) ?? "",
            startDate: FlightField.string(info["startDate"]) ?? "",
            teams: [side(t1, name1, "team1Score"), side(t2, name2, "team2Score")])
    }

    /// Every innings a side has batted ("266 & 83/2") and the overs of its newest.
    private static func innings(_ side: [String: Any]?, multiDay: Bool) -> (score: String, overs: String) {
        let list = ["inngs1", "inngs2"].compactMap { side?[$0] as? [String: Any] }
        let played = list.compactMap { inn in
            FlightField.int(inn["runs"]).map { (runs: $0, wickets: FlightField.int(inn["wickets"]) ?? 0) }
        }
        return (ScoreFormat.format(innings: played, multiDay: multiDay), FlightField.string(list.last?["overs"]) ?? "")
    }
}

/// Fetches a series' Schedule, Points table and Stats pages from Cricbuzz on demand (no Appwrite cost)
/// and keeps what it parsed in memory. Port of Android's `SeriesDetailScraper` / `PointsTableScraper`.
actor SeriesDetailScraper {
    static let shared = SeriesDetailScraper()
    static let ttl: TimeInterval = 300

    typealias HTMLFetcher = SquadsRepository.HTMLFetcher
    struct SlugNotFound: Error {}

    private let fetcher: HTMLFetcher
    private let clock: @Sendable () -> Date
    private var slugs: [String: String] = [:]
    private var schedules: [String: Cached<[SeriesMatchDay]>] = [:]
    private var tables: [String: Cached<[PointsGroup]>] = [:]
    private var stats: [String: Cached<SeriesStats>] = [:]

    init(fetcher: @escaping HTMLFetcher = { try await SquadsRepository.fetchHTML($0) }, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.fetcher = fetcher
        self.clock = clock
    }

    func schedule(seriesId: String, forceRefresh: Bool = false) async throws -> [SeriesMatchDay] {
        if !forceRefresh, let hit = schedules[seriesId], hit.isFresh(ttl: Self.ttl, now: clock()) { return hit.value }
        let days = SeriesScheduleParser.parse(html: try await page(seriesId, "matches"))
        schedules[seriesId] = Cached(value: days, loadedAt: clock())
        return days
    }

    /// Empty when the series has no table.
    func pointsTable(seriesId: String, forceRefresh: Bool = false) async throws -> [PointsGroup] {
        if !forceRefresh, let hit = tables[seriesId], hit.isFresh(ttl: Self.ttl, now: clock()) { return hit.value }
        let groups = PointsTableParser.parse(html: try await page(seriesId, "points-table"))
        tables[seriesId] = Cached(value: groups, loadedAt: clock())
        return groups
    }

    /// `statsType` nil asks for Cricbuzz's default (Most Runs). A series that has not started has no
    /// stats section at all, so a page that parses to nothing is `.empty`, not an error.
    func stats(seriesId: String, statsType: String?, forceRefresh: Bool = false) async throws -> SeriesStats {
        let key = "\(seriesId)|\(statsType ?? "")"
        if !forceRefresh, let hit = stats[key], hit.isFresh(ttl: Self.ttl, now: clock()) { return hit.value }
        let query = statsType.flatMap { $0.addingPercentEncoding(withAllowedCharacters: .alphanumerics) }
        let parsed = SeriesStatsParser.parse(html: try await page(seriesId, query.map { "stats?statsType=\($0)" } ?? "stats")) ?? .empty
        let entry = Cached(value: parsed, loadedAt: clock())
        stats[key] = entry
        if statsType == nil, !parsed.selectedType.isEmpty { stats["\(seriesId)|\(parsed.selectedType)"] = entry }
        return parsed
    }

    private func page(_ seriesId: String, _ tab: String) async throws -> String {
        let id = seriesId.filter { $0.isNumber }
        let slug = try await slug(for: id)
        guard let url = URL(string: "https://www.cricbuzz.com/cricket-series/\(id)/\(slug)/\(tab)") else { throw URLError(.badURL) }
        return try await fetcher(url)
    }

    /// One landing fetch per series. A landing page has answered 200 with no slugged link and carried
    /// them later (Android ANDROID-59), so a second unslugged tab is tried before giving up.
    private func slug(for id: String) async throws -> String {
        if let slug = slugs[id] { return slug }
        for tab in ["matches", "squads"] {
            guard let url = URL(string: "https://www.cricbuzz.com/cricket-series/\(id)/\(tab)") else { continue }
            if let slug = SeriesSlug.of(html: try await fetcher(url), seriesId: id) {
                slugs[id] = slug
                return slug
            }
        }
        throw SlugNotFound()
    }
}
