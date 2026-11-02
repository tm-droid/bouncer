import Foundation

struct BatsmanStats: Decodable, Equatable, Hashable, Sendable {
    var name: String = ""
    var dismissal: String = ""
    var runs: String = ""
    var balls: String = ""
    var fours: String = ""
    var sixes: String = ""
    var strikeRate: String = ""
    var profileUrl: String?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        name = w.string("name") ?? ""
        dismissal = w.string("dismissal") ?? ""
        runs = w.string("runs") ?? ""
        balls = w.string("balls") ?? ""
        fours = w.string("fours") ?? ""
        sixes = w.string("sixes") ?? ""
        strikeRate = w.string("strikeRate") ?? ""
        profileUrl = w.string("profileUrl")
    }

    var isNotOut: Bool { dismissal.isEmpty || dismissal.lowercased().contains("not out") || dismissal.lowercased() == "batting" }
}

struct BowlerStats: Decodable, Equatable, Hashable, Sendable {
    var name: String = ""
    var overs: String = ""
    var maidens: String = ""
    var runs: String = ""
    var wickets: String = ""
    var noBalls: String = ""
    var wides: String = ""
    var economy: String = ""
    var profileUrl: String?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        name = w.string("name") ?? ""
        overs = w.string("overs") ?? ""
        maidens = w.string("maidens") ?? ""
        runs = w.string("runs") ?? ""
        wickets = w.string("wickets") ?? ""
        noBalls = w.string("noBalls") ?? ""
        wides = w.string("wides") ?? ""
        economy = w.string("economy") ?? ""
        profileUrl = w.string("profileUrl")
    }
}

struct FallOfWicket: Decodable, Equatable, Hashable, Sendable {
    var score: String = ""
    var overs: String = ""
    var batsman: String = ""
    var number: Int = 0

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        score = w.string("score") ?? ""
        overs = w.string("overs") ?? ""
        batsman = w.string("batsman") ?? ""
        number = w.int("number") ?? 0
    }
}

struct Partnership: Decodable, Equatable, Hashable, Sendable {
    var player1: String = ""
    var player2: String = ""
    var runs: String = ""
    var balls: String = ""
    var contribution1: String = ""
    var contribution2: String = ""

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        player1 = w.string("player1") ?? ""
        player2 = w.string("player2") ?? ""
        runs = w.string("runs") ?? ""
        balls = w.string("balls") ?? ""
        contribution1 = w.string("contribution1") ?? ""
        contribution2 = w.string("contribution2") ?? ""
    }
}

struct Innings: Decodable, Equatable, Hashable, Sendable {
    /// Short code on the wire ("PRSW", "INDA").
    var teamName: String = ""
    /// "121-8" (dash form).
    var score: String = ""
    /// "19 Ov"
    var overs: String = ""
    var batsmen: [BatsmanStats] = []
    var bowlers: [BowlerStats] = []
    var fallOfWickets: [FallOfWicket] = []
    var partnerships: [Partnership] = []
    var extras: String = ""
    var total: String = ""

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        teamName = w.string("teamName") ?? ""
        score = w.string("score") ?? ""
        overs = w.string("overs") ?? ""
        batsmen = w.array("batsmen")
        bowlers = w.array("bowlers")
        fallOfWickets = w.array("fallOfWickets")
        partnerships = w.array("partnerships")
        extras = w.string("extras") ?? ""
        total = w.string("total") ?? ""
    }

    var scoreSlashed: String { ScoreFormat.slashed(score) }
}

/// One over of the run-progression chart (`overs[]`); `p` is the win probability after it, when known.
struct OverSummary: Decodable, Equatable, Hashable, Sendable {
    var innings: Int = 0
    var over: Int = 0
    var runs: Int = 0
    var score: Int = 0
    var wickets: Int = 0
    var team: String = ""
    var probability: Double?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        innings = w.int("innings") ?? 0
        over = w.int("over") ?? 0
        runs = w.int("runs") ?? 0
        score = w.int("score") ?? 0
        wickets = w.int("wickets") ?? 0
        team = w.string("team") ?? ""
        probability = w.double("p")
    }
}

/// Per-batter shot zones (`wagon[]`).
struct WagonEntry: Decodable, Equatable, Hashable, Sendable {
    var innings: Int = 0
    var name: String = ""
    var runs: Int = 0
    var balls: Int = 0
    var zones: [Int] = []

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        innings = w.int("innings") ?? 0
        name = w.string("name") ?? ""
        runs = w.int("runs") ?? 0
        balls = w.int("balls") ?? 0
        zones = w.array("zones", of: LossyInt.self).map(\.value)
    }
}

struct LossyInt: Decodable, Equatable, Sendable {
    let value: Int
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let i = try? c.decode(Int.self) { value = i; return }
        if let d = try? c.decode(Double.self), let i = Wire.safeInt64(d).flatMap({ Int(exactly: $0) }) { value = i; return }
        if let s = try? c.decode(String.self), let i = Int(s) { value = i; return }
        throw DecodingError.typeMismatch(Int.self, .init(codingPath: decoder.codingPath, debugDescription: "not an int"))
    }
}

/// The `scorecard` JSON string of a `match-scorecard` row.
struct Scorecard: Decodable, Equatable, Sendable {
    var innings: [Innings] = []
    var overs: [OverSummary] = []
    var wagon: [WagonEntry] = []
    var probabilityTeamId: String?

    init() {}

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        innings = w.array("innings")
        overs = w.array("overs")
        wagon = w.array("wagon")
        probabilityTeamId = w.string("probabilityTeamId")
    }
}

/// A `match-scorecard` row (REST or Realtime payload).
struct ScorecardRow: Decodable, Equatable, Sendable {
    var matchId: String = ""
    var scorecardJSON: String = ""
    var updatedAt: String?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        matchId = w.string("match_id") ?? ""
        scorecardJSON = w.string("scorecard") ?? ""
        updatedAt = w.string("updated_at")
    }

    func decodeScorecard() -> Scorecard? {
        guard !scorecardJSON.isEmpty else { return nil }
        return try? WireJSON.decode(Scorecard.self, from: scorecardJSON)
    }
}
