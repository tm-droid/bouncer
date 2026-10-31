import Foundation

struct ScrapedBowler: Equatable, Codable, Hashable {
    let name: String
    let overs: String
    let maidens: String
    let runs: String
    let wickets: String
    let economy: String
    var hatTrick: Bool { false }
}

struct ScrapedBatsman: Equatable, Codable, Hashable {
    let name: String
    let runs: String
    let balls: String
    let fours: String
    let sixes: String
    let strikeRate: String
    let isStriker: Bool
} 