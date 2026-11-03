import Foundation

/// One hit of the `player-search` function.
struct PlayerSearchResult: Equatable, Hashable, Sendable, Identifiable, Decodable {
    var id: String
    var name: String
    var country: String
    var imageURL: URL?

    init(id: String, name: String, country: String, imageURL: URL? = nil) {
        self.id = id; self.name = name; self.country = country; self.imageURL = imageURL
    }

    /// Rows without an id or a name are dropped by the caller (`Lossy`).
    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        guard let id = w.string("id"), !id.isEmpty, let name = w.string("name"), !name.isEmpty else {
            throw AppwriteError.decoding("player without id or name")
        }
        self.init(id: id, name: name, country: w.string("country") ?? "",
                  imageURL: w.string("imageUrl").flatMap { $0.isEmpty ? nil : URL(string: $0) })
    }
}

/// `{players:[…]}` from `player-search`.
struct PlayerSearchResponse: Decodable {
    var players: [PlayerSearchResult]
    init(from decoder: Decoder) throws { players = try Wire(decoder).array("players") }
}

/// One format's batting and bowling figures. Missing numbers are 0 and missing text "-", as on Android.
struct FormatStats: Equatable, Sendable, Decodable {
    var matches = 0, innings = 0, runs = 0
    var highScore = "-"
    var average = 0.0, strikeRate = 0.0
    var hundreds = 0, fifties = 0, fours = 0, sixes = 0
    var wickets = 0
    var bowlingAverage = 0.0, economy = 0.0
    var bestBowling = "-"
    var fiveWickets = 0

    init() {}

    /// Reads the `stats_json` shape the Android app writes (camelCase keys, tolerant of absent ones).
    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        matches = w.int("matches") ?? 0; innings = w.int("innings") ?? 0; runs = w.int("runs") ?? 0
        highScore = w.string("highScore") ?? "-"
        average = w.double("average") ?? 0; strikeRate = w.double("strikeRate") ?? 0
        hundreds = w.int("hundreds") ?? 0; fifties = w.int("fifties") ?? 0
        fours = w.int("fours") ?? 0; sixes = w.int("sixes") ?? 0
        wickets = w.int("wickets") ?? 0
        bowlingAverage = w.double("bowlingAverage") ?? 0; economy = w.double("economy") ?? 0
        bestBowling = w.string("bestBowling") ?? "-"
        fiveWickets = w.int("fiveWickets") ?? 0
    }

    var hasBatting: Bool { innings > 0 || runs > 0 }
    var hasBowling: Bool { wickets > 0 || bowlingAverage > 0 }
}

struct RecentInnings: Equatable, Sendable {
    var runs: Int
    var balls = 0
    var opponent = ""
    var format = ""
    var date = ""
}

struct PlayerProfile: Equatable, Sendable {
    /// Cricbuzz profile id.
    var id: String
    var name: String
    var country = ""
    var role = ""
    var dateOfBirth = ""
    var battingStyle = ""
    var bowlingStyle = ""
    var imageURL: URL?
    var bio = ""
    var teams: [String] = []
    /// Keyed "Test", "ODI", "T20I", "IPL".
    var stats: [String: FormatStats] = [:]
    /// Newest first.
    var recentForm: [RecentInnings] = []

    static let formats = ["Test", "ODI", "T20I", "IPL"]

    /// Cricbuzz's head-shot for a `faceImageId`.
    static func imageURL(faceImageId: String) -> URL? {
        URL(string: "https://www.cricbuzz.com/a/img/v1/152x152/i1/c\(faceImageId)/player.jpg")
    }

    /// The formats this player has figures for, in the usual order.
    var formatsWithStats: [String] { Self.formats.filter { stats[$0] != nil } }

    /// A `player-profiles` row (Android's cache table). Nil when it has no name, no figures, or is
    /// older than `maxAge`: a name with no figures is worse than a scrape.
    init?(row: Data, id: String, now: Date = Date(), maxAge: TimeInterval = 7 * 86_400) {
        guard let wire = try? WireJSON.decode(Row.self, from: row) else { return nil }
        if let updated = wire.updatedAt, now.timeIntervalSince(updated) > maxAge { return nil }
        guard let name = wire.name, !wire.stats.isEmpty else { return nil }
        self.init(id: id, name: name, country: wire.country, role: wire.role,
                  teams: wire.team.isEmpty ? [] : [wire.team], stats: wire.stats)
    }

    init(id: String, name: String, country: String = "", role: String = "", dateOfBirth: String = "",
         battingStyle: String = "", bowlingStyle: String = "", imageURL: URL? = nil, bio: String = "",
         teams: [String] = [], stats: [String: FormatStats] = [:], recentForm: [RecentInnings] = []) {
        self.id = id; self.name = name; self.country = country; self.role = role; self.dateOfBirth = dateOfBirth
        self.battingStyle = battingStyle; self.bowlingStyle = bowlingStyle; self.imageURL = imageURL
        self.bio = bio; self.teams = teams; self.stats = stats; self.recentForm = recentForm
    }

    private struct Row: Decodable {
        var name: String?
        var country = "", role = "", team = ""
        var stats: [String: FormatStats] = [:]
        var updatedAt: Date?

        init(from decoder: Decoder) throws {
            let w = try Wire(decoder)
            name = w.string("name")
            country = w.string("country") ?? ""; role = w.string("role") ?? ""; team = w.string("team_name") ?? ""
            if let json = w.string("stats_json") {
                stats = (try? WireJSON.decode([String: FormatStats].self, from: json)) ?? [:]
            }
            updatedAt = w.string("updated_at").flatMap { Row.iso.date(from: $0) ?? Row.isoFractional.date(from: $0) }
        }

        private static let iso = ISO8601DateFormatter()
        private static let isoFractional: ISO8601DateFormatter = {
            let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
        }()
    }
}

/// Batting-form maths (PlayerFormMath.kt): career runs per innings pooled across formats, and the
/// direction of the last few innings against the whole recent run.
enum PlayerForm {
    static let risingRatio = 1.15
    static let decliningRatio = 0.85
    static let trendWindow = 3

    static func trend(_ scores: [Int]) -> String {
        guard !scores.isEmpty else { return "Stable" }
        let overall = Double(scores.reduce(0, +)) / Double(scores.count)
        let recentScores = scores.prefix(trendWindow)
        let recent = Double(recentScores.reduce(0, +)) / Double(recentScores.count)
        if recent > overall * risingRatio { return "Rising" }
        if recent < overall * decliningRatio { return "Declining" }
        return "Stable"
    }

    /// "5 Nov 1988 · 37" from Cricbuzz's "November 05, 1988" (age worked out here, not trusted from the page). Unknown shapes are shown as they came.
    static func bornLine(_ raw: String, today: Date = Date(), calendar: Calendar = Calendar(identifier: .gregorian)) -> String {
        let text = raw.split(separator: "(", maxSplits: 1, omittingEmptySubsequences: false).first
            .map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
        guard !text.isEmpty else { return "" }
        let parse = DateFormatter()
        parse.locale = Locale(identifier: "en_US_POSIX"); parse.timeZone = calendar.timeZone
        var date: Date?
        for pattern in ["MMMM dd, yyyy", "MMMM d, yyyy", "MMM d, yyyy", "d MMM yyyy", "yyyy-MM-dd"] {
            parse.dateFormat = pattern
            if let d = parse.date(from: text) { date = d; break }
        }
        guard let date else { return text }
        let age = calendar.dateComponents([.year], from: date, to: today).year ?? 0
        parse.dateFormat = "d MMM yyyy"
        return "\(parse.string(from: date)) · \(age)"
    }
}
