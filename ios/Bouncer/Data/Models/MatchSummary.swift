import Foundation

enum MatchPhase: String, Sendable, Equatable {
    case upcoming, live, finished

    /// Phase from Cricbuzz's `state` ("Preview", "In Progress", "Stumps", "Complete", ...). Anything
    /// that is not clearly before the start or after the end is live: breaks, rain delays and
    /// "Badlight" are live matches that are not currently being played.
    static func from(state: String, status: String = "") -> MatchPhase {
        let s = state.trimmingCharacters(in: .whitespaces).lowercased()
        switch s {
        case "preview", "upcoming", "scheduled", "not started", "fixture":
            return .upcoming
        case "complete", "completed", "result", "abandon", "abandoned", "no result", "cancelled",
             "canceled", "drawn", "draw", "tied", "forfeited", "postponed":
            return .finished
        case "":
            return from(statusText: status)
        default:
            return .live
        }
    }

    /// Fallback for rows with no `state`: classify the free-text status, terminal markers first
    /// ("Match drawn, England 2nd innings 340/6" is finished even though it mentions an innings).
    static func from(statusText: String) -> MatchPhase {
        let s = statusText.lowercased()
        if s.isEmpty { return .upcoming }
        let terminal = ["won by", "won the match", "won the series", "match drawn", "match tied", "no result",
                        "abandon", "match drawn", " beat ", "wins by", "win by", "forfeit"]
        if terminal.contains(where: s.contains) { return .finished }
        if s.hasPrefix("match starts") || s.contains("starts at") || s.contains("yet to begin") { return .upcoming }
        return .live
    }
}

struct TeamScore: Decodable, Equatable, Hashable, Sendable {
    var teamId: String = ""
    var name: String = ""
    /// Wire string, already Test-formatted for multi-innings sides: "346 & 32/0", "180/6", "".
    var score: String = ""
    var overs: String = ""
    var isBatting: Bool = false

    init(teamId: String = "", name: String = "", score: String = "", overs: String = "", isBatting: Bool = false) {
        self.teamId = teamId; self.name = name; self.score = score; self.overs = overs; self.isBatting = isBatting
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        teamId = w.string("teamId") ?? ""
        name = w.string("name") ?? ""
        score = w.string("score") ?? ""
        overs = w.string("overs") ?? ""
        isBatting = w.bool("isBatting") ?? false
    }

    /// Short code: initials of the significant words ("Royal Challengers Bengaluru" -> "RCB").
    var acronym: String { TeamAcronym.make(name) }
    var hasScore: Bool { !score.isEmpty }
    /// Each innings of this side, oldest first ("346 & 32/0" -> ["346", "32/0"]).
    var inningsScores: [String] { ScoreFormat.splitInnings(score) }
}

struct MatchSummary: Decodable, Equatable, Hashable, Sendable, Identifiable {
    var id: String = ""
    var title: String = ""
    var status: String = ""
    var state: String = ""
    var matchFormat: String = ""
    var seriesId: String = ""
    var seriesName: String = ""
    var matchDesc: String = ""
    /// Epoch milliseconds as the wire string.
    var startDate: String = ""
    var teams: [TeamScore] = []

    init(id: String = "", title: String = "", status: String = "", state: String = "", matchFormat: String = "",
         seriesId: String = "", seriesName: String = "", matchDesc: String = "", startDate: String = "",
         teams: [TeamScore] = []) {
        self.id = id; self.title = title; self.status = status; self.state = state
        self.matchFormat = matchFormat; self.seriesId = seriesId; self.seriesName = seriesName
        self.matchDesc = matchDesc; self.startDate = startDate; self.teams = teams
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        id = w.string("id") ?? ""
        title = w.string("title") ?? ""
        status = w.string("status") ?? ""
        state = w.string("state") ?? ""
        matchFormat = w.string("matchFormat") ?? ""
        seriesId = w.string("seriesId") ?? ""
        seriesName = w.string("seriesName") ?? ""
        matchDesc = w.string("matchDesc") ?? ""
        startDate = w.string("startDate") ?? ""
        teams = w.array("teams")
    }

    var phase: MatchPhase { MatchPhase.from(state: state, status: status) }
    var isLive: Bool { phase == .live }
    var isMultiDay: Bool { ScoreFormat.isMultiDay(matchFormat) }

    /// Kick-off time from the epoch-ms wire string.
    var startsAt: Date? {
        guard let ms = Int64(startDate), ms > 0 else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
    }

    /// The side currently at the crease (first team flagged `isBatting`).
    var battingTeam: TeamScore? { teams.first(where: \.isBatting) }
    var bowlingTeam: TeamScore? { teams.first(where: { !$0.isBatting }) }
    var team1: TeamScore? { teams.first }
    var team2: TeamScore? { teams.count > 1 ? teams[1] : nil }
}

/// Envelope of `live|recent|upcoming-matches` files. `fetchedAt` is epoch ms (a number on the wire).
struct MatchListFile: Decodable, Equatable, Sendable {
    var matches: [MatchSummary] = []
    var count: Int = 0
    var fetchedAt: Date?

    init(matches: [MatchSummary] = [], count: Int = 0, fetchedAt: Date? = nil) {
        self.matches = matches; self.count = count; self.fetchedAt = fetchedAt
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        if let ms = w.int64("fetchedAt"), ms > 0 { fetchedAt = Date(timeIntervalSince1970: TimeInterval(ms) / 1000) }
        if let dataDecoder = try? w.container.superDecoder(forKey: WireKey("data")), let d = try? Wire(dataDecoder) {
            matches = d.array("matches")
            count = d.int("count") ?? matches.count
        }
    }
}

enum TeamAcronym {
    private static let noise: Set<String> = ["cricket", "team", "national", "the", "of", "and", "xi", "club", "county"]

    static func normalize(_ name: String) -> String {
        let parts = name.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        return parts.joined(separator: " ").lowercased()
    }

    static func make(_ name: String, maxLength: Int = 3) -> String {
        let words = normalize(name).split(separator: " ").map(String.init).filter { !noise.contains($0) }
        if words.isEmpty { return String(name.trimmingCharacters(in: .whitespaces).uppercased().prefix(maxLength)) }
        if words.count == 1 { return String(words[0].uppercased().prefix(maxLength)) }
        return String(words.compactMap { $0.first.map { String($0).uppercased() } }.joined().prefix(maxLength))
    }
}

enum ScoreFormat {
    static let multiDayFormats: Set<String> = ["TEST", "FC"]
    static func isMultiDay(_ format: String?) -> Bool { multiDayFormats.contains((format ?? "").uppercased()) }

    /// "346 & 32/0" -> ["346", "32/0"]
    static func splitInnings(_ score: String) -> [String] {
        score.components(separatedBy: "&").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    /// Test-score text from innings results, oldest first. A completed innings (10 wickets) drops its
    /// wicket count in the multi-day formats only, the way Cricbuzz writes a Test score:
    /// `[(346,10),(32,0)]` -> "346 & 32/0"; limited overs keep "runs/wickets".
    static func format(innings: [(runs: Int, wickets: Int)], multiDay: Bool) -> String {
        innings.map { multiDay && $0.wickets >= 10 ? String($0.runs) : "\($0.runs)/\($0.wickets)" }.joined(separator: " & ")
    }

    /// Scorecard text uses "121-8"; the list uses "121/8". Normalises the first form to the second.
    static func slashed(_ score: String) -> String {
        guard let r = score.range(of: #"^(\d+)-(\d+)$"#, options: .regularExpression) else { return score }
        return score[r].replacingOccurrences(of: "-", with: "/")
    }

    /// (runs, wickets) from "121/8", "121-8" or "346"; wickets nil when absent (all out in a Test).
    static func parse(_ score: String) -> (runs: Int, wickets: Int?)? {
        let s = score.trimmingCharacters(in: .whitespaces)
        let parts = s.split(whereSeparator: { $0 == "/" || $0 == "-" }).map(String.init)
        guard let first = parts.first, let runs = Int(first) else { return nil }
        return (runs, parts.count > 1 ? Int(parts[1]) : nil)
    }
}
