import Foundation

// MARK: - Cricsheet Data Structures
public struct CricsheetMatch: Codable {
    public let matchId: String
    public let date: Date
    public let venue: String
    public let teams: Teams
    public let innings: [CricsheetInnings]
    public let result: MatchResult
    
    public struct Teams: Codable {
        public let batting: String
        public let bowling: String
        
        public init(batting: String, bowling: String) {
            self.batting = batting
            self.bowling = bowling
        }
    }
    
    public init(matchId: String, date: Date, venue: String, teams: Teams, innings: [CricsheetInnings], result: MatchResult) {
        self.matchId = matchId
        self.date = date
        self.venue = venue
        self.teams = teams
        self.innings = innings
        self.result = result
    }
    
    enum CodingKeys: String, CodingKey {
        case matchId, date, venue, teams, innings, result
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        matchId = try container.decode(String.self, forKey: .matchId)
        date = try container.decode(Date.self, forKey: .date)
        venue = try container.decode(String.self, forKey: .venue)
        teams = try container.decode(Teams.self, forKey: .teams)
        innings = try container.decode([CricsheetInnings].self, forKey: .innings)
        result = try container.decode(MatchResult.self, forKey: .result)
    }
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(matchId, forKey: .matchId)
        try container.encode(date, forKey: .date)
        try container.encode(venue, forKey: .venue)
        try container.encode(teams, forKey: .teams)
        try container.encode(innings, forKey: .innings)
        try container.encode(result, forKey: .result)
    }
}

public struct CricsheetInnings: Codable {
    public let team: String
    public let overs: [CricsheetOver]
    public let total: Int
    public let wickets: Int
    public let runRate: Double
    
    public init(team: String, overs: [CricsheetOver], total: Int, wickets: Int, runRate: Double) {
        self.team = team
        self.overs = overs
        self.total = total
        self.wickets = wickets
        self.runRate = runRate
    }
}

public struct CricsheetOver: Codable {
    public let overNumber: Int
    public let balls: [CricsheetBall]
    public let runs: Int
    public let wickets: Int
    
    public init(overNumber: Int, balls: [CricsheetBall], runs: Int, wickets: Int) {
        self.overNumber = overNumber
        self.balls = balls
        self.runs = runs
        self.wickets = wickets
    }
}

public struct CricsheetBall: Codable {
    public let runs: Int
    public let isWicket: Bool
    public let wicketType: String?
    public let extras: Int
    public let extraType: String?
    
    public init(runs: Int, isWicket: Bool, wicketType: String?, extras: Int, extraType: String?) {
        self.runs = runs
        self.isWicket = isWicket
        self.wicketType = wicketType
        self.extras = extras
        self.extraType = extraType
    }
}

public struct MatchResult: Codable {
    public let winner: String?
    public let margin: Int
    public let marginType: String  // "runs" or "wickets"
    
    public init(winner: String?, margin: Int, marginType: String) {
        self.winner = winner
        self.margin = margin
        self.marginType = marginType
    }
} 