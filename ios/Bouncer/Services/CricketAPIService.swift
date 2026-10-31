import Foundation

class CricketAPIService {
    private let apiKey: String
    // Deployed Vercel API
    private let baseURL = "https://api-iota-jade.vercel.app/"
    private var lastRequestTime: Date?
    private let minimumRequestInterval: TimeInterval = 1.0
    
    init(apiKey: String) {
        self.apiKey = apiKey
    }
    
    func fetchMatches(for statusType: String) async throws -> [CricketMatch] {
        await handleRateLimiting()

        let apiType: String
        switch statusType {
        case "Live": apiType = "live"
        case "Recent": apiType = "recent"
        case "Upcoming": apiType = "upcoming"
        default: apiType = "live"
        }

        return try await fetchSanwebMatches(type: apiType)
    }

    private func fetchSanwebMatches(type: String) async throws -> [CricketMatch] {
        guard let url = URL(string: baseURL + "matches?type=" + type) else { throw APIError.invalidResponse }
        let (data, _) = try await URLSession.shared.data(from: url)
        let listResponse = try JSONDecoder().decode(SanwebMatchesListResponse.self, from: data)
        
        var matches: [CricketMatch] = []
        
        if type == "live" {
            // For live matches, we need details (scores)
            await withTaskGroup(of: CricketMatch?.self) { group in
                for matchSummary in listResponse.matches {
                    group.addTask {
                        return try? await self.fetchSanwebMatchDetails(id: matchSummary.id, category: "Live")
                    }
                }
                
                for await match in group {
                    if let match = match {
                        matches.append(match)
                    }
                }
            }
        } else {
            // For Recent and Upcoming, construct from summary
            for matchSummary in listResponse.matches {
                matches.append(createMatchFromSummary(matchSummary, category: type.capitalized))
            }
        }
        
        return matches
    }

    private func createMatchFromSummary(_ summary: SanwebMatchSummary, category: String) -> CricketMatch {
        let titleParts = summary.title.components(separatedBy: ", ")
        let teamsStr = titleParts.first ?? "Unknown vs Unknown"
        let teams = teamsStr.components(separatedBy: " vs ")
        let team1Name = teams.first ?? "Team 1"
        let team2Name = teams.count > 1 ? teams[1] : "Team 2"
        
        let team1 = CricketMatch.Team(
            name: team1Name,
            score: "",
            status: "",
            isBatting: false,
            logoUrl: TeamDirectory.shared.teams[team1Name]?.logoUrl
        )
        
        let team2 = CricketMatch.Team(
            name: team2Name,
            score: "",
            status: "",
            isBatting: false,
            logoUrl: TeamDirectory.shared.teams[team2Name]?.logoUrl
        )
        
        return CricketMatch(
            id: summary.id,
            seriesId: "",
            matchTitle: summary.title,
            matchType: "",
            status: summary.status,
            teams: [team1, team2],
            venue: "",
            date: "",
            matchTime: "",
            score: nil,
            currentStatus: summary.status,
            info: summary.title,
            recentBalls: nil,
            currentBowler: nil,
            scrapedBatsmen: nil,
            category: category
        )
    }

    func fetchMatchDetails(id: String, category: String = "Live") async throws -> CricketMatch {
        return try await fetchSanwebMatchDetails(id: id, category: category)
    }

    private func fetchSanwebMatchDetails(id: String, category: String) async throws -> CricketMatch {
        guard let url = URL(string: baseURL + "score?id=" + id) else { throw APIError.invalidResponse }
        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(SanwebMatchResponse.self, from: data)
        
        // Map SanwebResponse to CricketMatch
        // Parse title "Australia vs Pakistan, 2nd Test - Live Cricket Score"
        let titleParts = response.title.components(separatedBy: ", ")
        let teamsStr = titleParts.first?.replacingOccurrences(of: " - Live Cricket Score", with: "") ?? "Unknown vs Unknown"
        let teams = teamsStr.components(separatedBy: " vs ")
        let team1Name = teams.first ?? "Team 1"
        let team2Name = teams.count > 1 ? teams[1] : "Team 2"
        
        // Parse livescore: "AUS 168/3 (60)" - extract abbreviation + score
        let scoreParts = response.livescore.components(separatedBy: " ")
        let battingAbbr = scoreParts.first ?? ""
        let battingScore = scoreParts.count > 1 ? scoreParts[1] : ""
        
        // Determine which team is actually batting by matching the abbreviation
        let team1Abbr = String(team1Name.prefix(3)).uppercased()
        let team2Abbr = String(team2Name.prefix(3)).uppercased()
        let isTeam1Batting = battingAbbr.uppercased().hasPrefix(team1Abbr) || team1Name.uppercased().hasPrefix(battingAbbr.uppercased())
        
        let team1 = CricketMatch.Team(
            name: team1Name,
            score: isTeam1Batting ? battingScore : "",
            status: isTeam1Batting ? "bat" : "bowl",
            isBatting: isTeam1Batting,
            logoUrl: TeamDirectory.shared.teams[team1Name]?.logoUrl
        )
        
        let team2 = CricketMatch.Team(
            name: team2Name,
            score: isTeam1Batting ? "" : battingScore,
            status: isTeam1Batting ? "bowl" : "bat",
            isBatting: !isTeam1Batting,
            logoUrl: TeamDirectory.shared.teams[team2Name]?.logoUrl
        )
        
        // Parse batsmen
        let batsman1 = CricketMatch.BatsmanScore(
            name: response.batterone,
            runs: Int(response.batsmanonerun) ?? 0,
            balls: Int(response.batsmanoneball.replacingOccurrences(of: "(", with: "").replacingOccurrences(of: ")", with: "")) ?? 0,
            fours: 0, // Not provided
            sixes: 0, // Not provided
            strikeRate: Double(response.batsmanonesr) ?? 0.0
        )
        
        let batsman2 = CricketMatch.BatsmanScore(
            name: response.battertwo,
            runs: Int(response.batsmantworun) ?? 0,
            balls: Int(response.batsmantwoball.replacingOccurrences(of: "(", with: "").replacingOccurrences(of: ")", with: "")) ?? 0,
            fours: 0,
            sixes: 0,
            strikeRate: Double(response.batsmantwosr) ?? 0.0
        )
        
        // Parse bowlers
        let bowler1 = CricketMatch.BowlerScore(
            name: response.bowlerone,
            overs: Double(response.bowleroneover) ?? 0.0,
            maidens: 0, // Not provided
            runs: Int(response.bowleronerun) ?? 0,
            wickets: Int(response.bowleronewickers) ?? 0,
            economy: Double(response.bowleroneeconomy) ?? 0.0
        )
        
        let matchScore = CricketMatch.MatchScore(
            currentInnings: response.update,
            batsmen: [batsman1, batsman2],
            bowlers: [bowler1],
            overs: nil,
            currentRunRate: response.runrate,
            requiredRunRate: nil,
            lastWicket: nil,
            recentBalls: nil
        )
        
        return CricketMatch(
            id: id,
            seriesId: "",
            matchTitle: response.title,
            matchType: "Test", // Simplified, should parse from title
            status: response.update,
            teams: [team1, team2],
            venue: "", // Not provided
            date: "", // Not provided
            matchTime: "",
            score: matchScore,
            currentStatus: response.update,
            info: response.title,
            recentBalls: nil,
            currentBowler: nil,
            scrapedBatsmen: nil,
            category: category
        )
    }
    
    private func handleRateLimiting() async {
        guard let lastRequestTime = lastRequestTime else {
            self.lastRequestTime = Date()
            return
        }
        
        let timeSinceLastRequest = Date().timeIntervalSince(lastRequestTime)
        if timeSinceLastRequest < minimumRequestInterval {
            let sleepTime = minimumRequestInterval - timeSinceLastRequest
            print("Rate limit: Sleeping for \(sleepTime) seconds")
            try? await Task.sleep(nanoseconds: UInt64(sleepTime * 1_000_000_000))
        }
        self.lastRequestTime = Date()
    }
    
    enum APIError: Error {
        case invalidResponse
        case invalidData
        case decodingError(Error)
    }
}

// MARK: - Sanweb API Models
struct SanwebMatchResponse: Codable {
    let title: String
    let update: String
    let livescore: String
    let runrate: String
    let batterone: String
    let batsmanonerun: String
    let batsmanoneball: String
    let batsmanonesr: String
    let battertwo: String
    let batsmantworun: String
    let batsmantwoball: String
    let batsmantwosr: String
    let bowlerone: String
    let bowleroneover: String
    let bowleronerun: String
    let bowleronewickers: String
    let bowleroneeconomy: String
    let bowlertwo: String
    let bowlertwoover: String
    let bowlertworun: String
    let bowlertwowickers: String
    let bowlertwoeconomy: String
}

struct SanwebMatchesListResponse: Codable {
    let matches: [SanwebMatchSummary]
    let count: Int
}

struct SanwebMatchSummary: Codable {
    let id: String
    let title: String
    let status: String
}
