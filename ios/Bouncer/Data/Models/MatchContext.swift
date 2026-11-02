import Foundation

struct Venue: Decodable, Equatable, Hashable, Sendable {
    var id: String = ""
    var name: String = ""
    var city: String = ""
    var country: String = ""
    var timezone: String = ""
    var lat: Double?
    var lon: Double?
    var known: Bool = false
    var matches: Int?
    var scoring: Double?
    var chaseAdvantage: Double?
    var drawAdvantage: Double?
    var typicalFirstInnings: Int?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        id = w.string("id") ?? ""
        name = w.string("name") ?? ""
        city = w.string("city") ?? ""
        country = w.string("country") ?? ""
        timezone = w.string("timezone") ?? ""
        lat = w.double("lat")
        lon = w.double("lon")
        known = w.bool("known") ?? false
        matches = w.int("matches")
        scoring = w.double("scoring")
        chaseAdvantage = w.double("chaseAdvantage")
        drawAdvantage = w.double("drawAdvantage")
        typicalFirstInnings = w.int("typicalFirstInnings")
    }
}

struct TossInfo: Decodable, Equatable, Hashable, Sendable {
    var winnerId: String = ""
    var winnerName: String = ""
    /// "Bowling" / "Batting"
    var decision: String = ""

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        winnerId = w.string("winnerId") ?? ""
        winnerName = w.string("winnerName") ?? ""
        decision = w.string("decision") ?? ""
    }
}

struct PlayerRecord: Decodable, Equatable, Hashable, Sendable {
    var innings: Int?
    var runs: Int?
    var average: Double?
    var strikeRate: Double?
    var wickets: Int?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        innings = w.int("innings")
        runs = w.int("runs")
        average = w.double("average")
        strikeRate = w.double("strikeRate")
        wickets = w.int("wickets")
    }
}

struct ContextMember: Decodable, Equatable, Hashable, Sendable {
    var cbId: String = ""
    var name: String = ""
    var pid: String?
    var record: PlayerRecord?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        cbId = w.string("cbId") ?? ""
        name = w.string("name") ?? ""
        pid = w.string("pid")
        record = w.value("record")
    }
}

struct XIStrength: Decodable, Equatable, Hashable, Sendable {
    var batAvg: Double?
    var batStrike: Double?
    var batRecent: Double?
    var bowl: Double?
    var coverage: Double?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        batAvg = w.double("batAvg")
        batStrike = w.double("batStrike")
        batRecent = w.double("batRecent")
        bowl = w.double("bowl")
        coverage = w.double("coverage")
    }
}

struct ContextTeam: Decodable, Equatable, Hashable, Sendable {
    var id: String = ""
    var name: String = ""
    var shortName: String = ""
    var key: String?
    var known: Bool = false
    var elo: Double?
    var form: Double?
    var winRate: Double?
    var games: Int?
    var visits: Int?
    var xi: XIStrength?
    var joined: Int?
    var listed: Int?
    var members: [ContextMember] = []

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        id = w.string("id") ?? ""
        name = w.string("name") ?? ""
        shortName = w.string("shortName") ?? ""
        key = w.string("key")
        known = w.bool("known") ?? false
        elo = w.double("elo")
        form = w.double("form")
        winRate = w.double("winRate")
        games = w.int("games")
        visits = w.int("visits")
        xi = w.value("xi")
        joined = w.int("joined")
        listed = w.int("listed")
        members = w.array("members")
    }
}

struct HeadToHead: Decodable, Equatable, Hashable, Sendable {
    var games: Int = 0
    var firstWins: Int = 0
    var secondWins: Int = 0

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        games = w.int("games") ?? 0
        firstWins = w.int("firstWins") ?? 0
        secondWins = w.int("secondWins") ?? 0
    }
}

struct ForecastHour: Decodable, Equatable, Hashable, Sendable {
    /// Epoch ms.
    var t: Int64 = 0
    var tempC: Double?
    var humidity: Double?
    var dewC: Double?
    var rainMm: Double?
    var rainChance: Double?
    var windKph: Double?
    var cloud: Double?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        t = w.int64("t") ?? 0
        tempC = w.double("tempC")
        humidity = w.double("humidity")
        dewC = w.double("dewC")
        rainMm = w.double("rainMm")
        rainChance = w.double("rainChance")
        windKph = w.double("windKph")
        cloud = w.double("cloud")
    }

    var date: Date { Date(timeIntervalSince1970: TimeInterval(t) / 1000) }
}

struct Forecast: Decodable, Equatable, Hashable, Sendable {
    var source: String = ""
    var fetchedAt: Int64?
    var hours: [ForecastHour] = []

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        source = w.string("source") ?? ""
        fetchedAt = w.int64("fetchedAt")
        hours = w.array("hours")
    }
}

struct MatchPreview: Decodable, Equatable, Hashable, Sendable {
    var text: String = ""
    var provider: String?
    var at: Int64?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        text = w.string("text") ?? ""
        provider = w.string("provider")
        at = w.int64("at")
    }
}

/// The `mc-{id}` file: venue, toss, team model inputs, head-to-head, weather, AI preview.
struct MatchContext: Decodable, Equatable, Sendable {
    var v: Int = 0
    var matchId: String = ""
    var format: String = ""
    var gender: String = ""
    var modelAsOf: String = ""
    var venue: Venue?
    var toss: TossInfo?
    var dayNight: Bool = false
    var startMs: Int64?
    var teams: [ContextTeam] = []
    var h2h: HeadToHead?
    var forecast: Forecast?
    var rev: Int = 0
    var builtAt: Int64?
    var preview: MatchPreview?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        v = w.int("v") ?? 0
        matchId = w.string("matchId") ?? ""
        format = w.string("format") ?? ""
        gender = w.string("gender") ?? ""
        modelAsOf = w.string("modelAsOf") ?? ""
        venue = w.value("venue")
        toss = w.value("toss")
        dayNight = w.bool("dayNight") ?? false
        startMs = w.int64("startMs")
        teams = w.array("teams")
        h2h = w.value("h2h")
        forecast = w.value("forecast")
        rev = w.int("rev") ?? 0
        builtAt = w.int64("builtAt")
        preview = w.value("preview")
    }

    var isWomens: Bool { gender.lowercased() == "female" }
}
