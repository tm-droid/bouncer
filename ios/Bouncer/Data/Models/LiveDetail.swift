import Foundation

struct Batter: Decodable, Equatable, Hashable, Sendable {
    var name: String = ""
    var runs: String = ""
    var balls: String = ""
    var fours: String = ""
    var sixes: String = ""
    var strikeRate: String = ""
    var isStriker: Bool = false

    init(name: String = "", runs: String = "", balls: String = "", fours: String = "", sixes: String = "",
         strikeRate: String = "", isStriker: Bool = false) {
        self.name = name; self.runs = runs; self.balls = balls; self.fours = fours
        self.sixes = sixes; self.strikeRate = strikeRate; self.isStriker = isStriker
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        name = w.string("name") ?? ""
        runs = w.string("runs") ?? ""
        balls = w.string("balls") ?? ""
        fours = w.string("fours") ?? ""
        sixes = w.string("sixes") ?? ""
        strikeRate = w.string("strikeRate") ?? ""
        isStriker = w.bool("isStriker") ?? false
    }
}

struct Bowler: Decodable, Equatable, Hashable, Sendable {
    var name: String = ""
    var overs: String = ""
    var maidens: String = ""
    var runs: String = ""
    var wickets: String = ""
    var economy: String = ""

    init(name: String = "", overs: String = "", maidens: String = "", runs: String = "", wickets: String = "",
         economy: String = "") {
        self.name = name; self.overs = overs; self.maidens = maidens; self.runs = runs
        self.wickets = wickets; self.economy = economy
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        name = w.string("name") ?? ""
        overs = w.string("overs") ?? ""
        maidens = w.string("maidens") ?? ""
        runs = w.string("runs") ?? ""
        wickets = w.string("wickets") ?? ""
        economy = w.string("economy") ?? ""
    }
}

/// Test-only extras on `Situation.long`.
struct LongFormSituation: Decodable, Equatable, Hashable, Sendable {
    var inningsIndex: Int?
    var lead: Int?
    var ballsBowled: Int?
    var ownInningsUsed: Int?
    var oppInningsUsed: Int?
    var followedOn: Bool = false
    var declared: Bool = false
    var scheduledDays: Int?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        inningsIndex = w.int("inningsIndex")
        lead = w.int("lead")
        ballsBowled = w.int("ballsBowled")
        ownInningsUsed = w.int("ownInningsUsed")
        oppInningsUsed = w.int("oppInningsUsed")
        followedOn = w.bool("followedOn") ?? false
        declared = w.bool("declared") ?? false
        scheduledDays = w.int("scheduledDays")
    }
}

/// Typed match state from the backend (numbers here are real numbers, unlike the string stats).
struct Situation: Decodable, Equatable, Hashable, Sendable {
    var inningsId: Int?
    var matchFormat: String = ""
    var battingTeamName: String?
    var bowlingTeamName: String?
    var battingTeamIsTeam1: Bool?
    var firstInningsTeamId: String?
    var runsScored: Int?
    var wicketsLost: Int?
    var ballsBowled: Int?
    var target: Int?
    var runsNeeded: Int?
    var oversRemaining: Double?
    var currentRunRate: Double?
    var requiredRunRate: Double?
    var long: LongFormSituation?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        inningsId = w.int("inningsId")
        matchFormat = w.string("matchFormat") ?? ""
        battingTeamName = w.string("battingTeamName")
        bowlingTeamName = w.string("bowlingTeamName")
        battingTeamIsTeam1 = w.bool("battingTeamIsTeam1")
        firstInningsTeamId = w.string("firstInningsTeamId")
        runsScored = w.int("runsScored")
        wicketsLost = w.int("wicketsLost")
        ballsBowled = w.int("ballsBowled")
        target = w.int("target")
        runsNeeded = w.int("runsNeeded")
        oversRemaining = w.double("oversRemaining")
        currentRunRate = w.double("currentRunRate")
        requiredRunRate = w.double("requiredRunRate")
        long = w.value("long")
    }

    var isChasing: Bool { target != nil }
}

struct WinProbabilityFactor: Decodable, Equatable, Hashable, Sendable {
    var key: String = ""
    var delta: Double = 0

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        key = w.string("k") ?? ""
        delta = w.double("d") ?? 0
    }
}

/// The `payload` JSON string of a `match-live-detail` row. Every field optional on the wire.
struct LiveDetailPayload: Decodable, Equatable, Sendable {
    var batsmen: [Batter] = []
    var bowler: Bowler?
    var recentBalls: [String] = []
    var recentBallsOver: Int?
    /// "32/0 (11)" — the batting side's current innings.
    var score: String = ""
    /// Team1 score in list format ("346 & 32/0"); `team1Name` here is a short code ("INDA").
    var team1Score: String?
    var team1Overs: String?
    var team1Name: String?
    var team1Id: String?
    var team2Score: String?
    var team2Overs: String?
    var team2Name: String?
    var team2Id: String?
    var currentRunRate: String = ""
    var requiredRunRate: String = ""
    var status: String = ""
    var situation: Situation?
    var contextRev: Int?
    var winProbabilityTeam1: Double?
    var winProbabilityConfidence: Double?
    var winProbabilityDraw: Double?
    var winProbabilityFactors: [WinProbabilityFactor] = []

    init() {}

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        batsmen = w.array("batsmen")
        bowler = w.value("bowler")
        recentBalls = w.array("recentBalls", of: LossyString.self).map(\.value)
        recentBallsOver = w.int("recentBallsOver")
        score = w.string("score") ?? ""
        team1Score = w.string("team1Score")
        team1Overs = w.string("team1Overs")
        team1Name = w.string("team1Name")
        team1Id = w.string("team1Id")
        team2Score = w.string("team2Score")
        team2Overs = w.string("team2Overs")
        team2Name = w.string("team2Name")
        team2Id = w.string("team2Id")
        currentRunRate = w.string("currentRunRate") ?? ""
        requiredRunRate = w.string("requiredRunRate") ?? ""
        status = w.string("status") ?? ""
        situation = w.value("situation")
        contextRev = w.int("contextRev")
        winProbabilityTeam1 = w.double("winProbabilityTeam1")
        winProbabilityConfidence = w.double("winProbabilityConfidence")
        winProbabilityDraw = w.double("winProbabilityDraw")
        winProbabilityFactors = w.array("winProbabilityFactors")
    }

    var striker: Batter? { batsmen.first(where: \.isStriker) ?? batsmen.first }
    var phase: MatchPhase { MatchPhase.from(statusText: status) }

    /// Team1's win probability from team2's side, nil when the model has no number.
    var winProbabilityTeam2: Double? {
        guard let p1 = winProbabilityTeam1 else { return nil }
        return max(0, 1 - p1 - (winProbabilityDraw ?? 0))
    }
}

/// A string-valued array element that tolerates numbers.
struct LossyString: Decodable, Equatable, Sendable {
    let value: String
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let s = try? c.decode(String.self) { value = s; return }
        if let i = try? c.decode(Int64.self) { value = String(i); return }
        if let d = try? c.decode(Double.self) { value = Wire.format(d); return }
        throw DecodingError.typeMismatch(String.self, .init(codingPath: decoder.codingPath, debugDescription: "not a scalar"))
    }
}

/// A `match-live-detail` row (REST or Realtime payload). `payload` is itself a JSON string.
struct LiveDetailRow: Decodable, Equatable, Sendable {
    var matchId: String = ""
    var payloadJSON: String = ""
    var updatedAt: String?
    var lastCommentarySortKey: Int64?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        matchId = w.string("match_id") ?? ""
        payloadJSON = w.string("payload") ?? ""
        updatedAt = w.string("updated_at")
        lastCommentarySortKey = w.int64("last_commentary_sort_key")
    }

    /// Decodes the embedded payload string; nil when absent or malformed.
    func decodePayload() -> LiveDetailPayload? {
        guard !payloadJSON.isEmpty else { return nil }
        return try? WireJSON.decode(LiveDetailPayload.self, from: payloadJSON)
    }
}
