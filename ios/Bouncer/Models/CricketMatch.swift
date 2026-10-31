import Foundation

struct CricketMatch: Identifiable, Equatable, Hashable, Decodable {
    let id: String
    let seriesId: String
    let matchTitle: String
    let matchType: String
    let status: String
    let teams: [Team]
    let venue: String
    let date: String
    let matchTime: String
    let score: MatchScore?
    let currentStatus: String
    let info: String
    // Scraped live data
    var recentBalls: [String]? = nil
    var currentBowler: ScrapedBowler? = nil
    var scrapedBatsmen: [ScrapedBatsman]? = nil
    // Add category for filtering
    var category: String? = nil
    
    struct Team: Codable, Equatable, Hashable {
        let name: String
        let score: String
        let status: String
        let isBatting: Bool
        let logoUrl: String?
    }

    static func ==(lhs: CricketMatch, rhs: CricketMatch) -> Bool {
        return lhs.id == rhs.id &&
               lhs.matchType == rhs.matchType &&
               lhs.status == rhs.status &&
               lhs.teams == rhs.teams &&
               lhs.venue == rhs.venue &&
               lhs.date == rhs.date &&
               lhs.score == rhs.score &&
               lhs.recentBalls == rhs.recentBalls &&
               lhs.currentBowler == rhs.currentBowler &&
               lhs.scrapedBatsmen == rhs.scrapedBatsmen &&
               lhs.category == rhs.category
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
        hasher.combine(seriesId)
        hasher.combine(matchTitle)
        hasher.combine(matchType)
        hasher.combine(status)
        hasher.combine(teams) // Team must be Hashable
        hasher.combine(venue)
        hasher.combine(date)
        hasher.combine(matchTime)
        hasher.combine(score)
        hasher.combine(currentStatus)
        hasher.combine(info)
        hasher.combine(recentBalls)
        hasher.combine(currentBowler)
        hasher.combine(scrapedBatsmen)
        hasher.combine(category)
    }

    struct MatchScore: Equatable, Hashable, Decodable {
        let currentInnings: String
        var batsmen: [BatsmanScore]
        let bowlers: [BowlerScore]
        // Additional properties for live activity content state
        let overs: String?
        let currentRunRate: String?
        let requiredRunRate: String?
        let lastWicket: String?
        let recentBalls: String?


        func hash(into hasher: inout Hasher) {
            hasher.combine(currentInnings)
            hasher.combine(batsmen)
            hasher.combine(bowlers)
        }

        enum CodingKeys: String, CodingKey {
            case currentInnings = "inningsBreak"
            case batsmen = "batsmanScores"
            case bowlers = "bowlerScores"
            case overs, currentRunRate, requiredRunRate, lastWicket, recentBalls
        }
    }

    struct BatsmanScore: Equatable, Hashable, Decodable {
        let name: String
        let runs: Int
        let balls: Int
        let fours: Int
        let sixes: Int
        let strikeRate: Double

        func hash(into hasher: inout Hasher) {
            hasher.combine(name)
            hasher.combine(runs)
            hasher.combine(balls)
            hasher.combine(fours)
            hasher.combine(sixes)
            hasher.combine(strikeRate)
        }
    }

    struct BowlerScore: Equatable, Hashable, Decodable {
        let name: String
        let overs: Double
        let maidens: Int
        let runs: Int
        let wickets: Int
        let economy: Double

        func hash(into hasher: inout Hasher) {
            hasher.combine(name)
            hasher.combine(overs)
            hasher.combine(maidens)
            hasher.combine(runs)
            hasher.combine(wickets)
            hasher.combine(economy)
        }
    }
}
