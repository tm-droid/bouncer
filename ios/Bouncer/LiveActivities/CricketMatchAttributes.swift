import Foundation
import ActivityKit

public struct CricketMatchAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var matchId: String
        public var team1Score: String
        public var team2Score: String
        public var currentInnings: String
        public var lastUpdated: Date
        public var alertType: String?  // For wicket/six alerts
        // Widget-friendly live data
        public var batsmen: [BatsmanWidget]?
        public var bowler: BowlerWidget?
        public var recentBalls: [String]?
        public init(matchId: String, team1Score: String, team2Score: String, currentInnings: String, lastUpdated: Date, alertType: String? = nil, batsmen: [BatsmanWidget]? = nil, bowler: BowlerWidget? = nil, recentBalls: [String]? = nil) {
            self.matchId = matchId
            self.team1Score = team1Score
            self.team2Score = team2Score
            self.currentInnings = currentInnings
            self.lastUpdated = lastUpdated
            self.alertType = alertType
            self.batsmen = batsmen
            self.bowler = bowler
            self.recentBalls = recentBalls
        }
    }
    
    public var matchId: String
    public var team1Name: String
    public var team2Name: String
    public var matchType: String
    
    public init(matchId: String, team1Name: String, team2Name: String, matchType: String) {
        self.matchId = matchId
        self.team1Name = team1Name
        self.team2Name = team2Name
        self.matchType = matchType
    }
}

public struct BatsmanWidget: Codable, Hashable {
    public var name: String
    public var runs: String
    public var balls: String
    public var isStriker: Bool
    public init(name: String, runs: String, balls: String, isStriker: Bool) {
        self.name = name
        self.runs = runs
        self.balls = balls
        self.isStriker = isStriker
    }
}

public struct BowlerWidget: Codable, Hashable {
    public var name: String
    public var overs: String
    public var runs: String
    public var wickets: String
    public var economy: String
    public init(name: String, overs: String, runs: String, wickets: String, economy: String) {
        self.name = name
        self.overs = overs
        self.runs = runs
        self.wickets = wickets
        self.economy = economy
    }
}