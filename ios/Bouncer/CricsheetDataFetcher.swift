import Foundation

// MARK: - Cricsheet Data Fetcher
internal class CricsheetDataFetcher {
    private let baseURL = "https://cricsheet.org/downloads/"
    private let fileManager = FileManager.default
    private let documentsPath: String
    
    internal init() {
        documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
    }
    
    internal func downloadMatchData(for tournament: String, year: Int) async throws -> [CricsheetMatch] {
        let urlString = "\(baseURL)\(tournament)_\(year).json"
        guard let url = URL(string: urlString) else {
            throw CricsheetError.invalidURL
        }
        
        let (data, _) = try await URLSession.shared.data(from: url)
        return try parseMatchData(data)
    }
    
    private func parseMatchData(_ data: Data) throws -> [CricsheetMatch] {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        // Cricsheet JSON structure
        struct CricsheetResponse: Codable {
            let meta: MatchMeta
            let info: MatchInfo
            let innings: [Innings]
        }
        
        struct MatchMeta: Codable {
            let data_version: String
            let created: Date
            let revision: Int
        }
        
        struct MatchInfo: Codable {
            let dates: [Date]
            let match_type: String
            let venue: String
            let teams: [String]
            let outcome: Outcome
            let gender: String
            let overs: [Over]
            let player_of_match: String
            let umpires: [String]
        }
        
        struct Outcome: Codable {
            let winner: String?
            let by: By?
            let result: MatchResult?
        }
        
        struct By: Codable {
            let runs: Int?
            let wickets: Int?
        }
        
        struct Innings: Codable {
            let team: String
            let overs: [Over]
        }
        
        struct Over: Codable {
            let over: Int
            let deliveries: [Delivery]
        }
        
        struct Delivery: Codable {
            let runs: Runs
            let wickets: [Wicket]?
        }
        
        struct Runs: Codable {
            let total: Int
            let extras: Int
        }
        
        struct Wicket: Codable {
            let kind: String
            let player_out: String
        }
        
        let response = try decoder.decode(CricsheetResponse.self, from: data)
        
        // Convert to our model
        return [CricsheetMatch(
            matchId: response.meta.data_version,
            date: response.info.dates[0],
            venue: response.info.venue,
            teams: CricsheetMatch.Teams(
                batting: response.info.teams[0],
                bowling: response.info.teams[1]
            ),
            innings: response.innings.map { innings in
                CricsheetInnings(
                    team: innings.team,
                    overs: innings.overs.map { over in
                        CricsheetOver(
                            overNumber: over.over,
                            balls: over.deliveries.map { delivery in
                                CricsheetBall(
                                    runs: delivery.runs.total,
                                    isWicket: delivery.wickets != nil,
                                    wicketType: delivery.wickets?.first?.kind,
                                    extras: delivery.runs.extras,
                                    extraType: nil
                                )
                            },
                            runs: over.deliveries.reduce(0) { $0 + $1.runs.total },
                            wickets: over.deliveries.filter { $0.wickets != nil }.count
                        )
                    },
                    total: innings.overs.flatMap { $0.deliveries }.reduce(0) { $0 + $1.runs.total },
                    wickets: innings.overs.flatMap { $0.deliveries }.filter { $0.wickets != nil }.count,
                    runRate: Double(innings.overs.flatMap { $0.deliveries }.reduce(0) { $0 + $1.runs.total }) / Double(innings.overs.count)
                )
            },
            result: MatchResult(
                winner: response.info.outcome.winner,
                margin: response.info.outcome.by?.runs ?? response.info.outcome.by?.wickets ?? 0,
                marginType: response.info.outcome.by?.runs != nil ? "runs" : "wickets"
            )
        )]
    }
    
    /// Downloads and parses all historical matches from Cricsheet (all_json.zip)
    internal func downloadAllHistoricalMatches() async throws -> [CricsheetMatch] {
        let urlString = "https://cricsheet.org/downloads/all_json.zip"
        guard let url = URL(string: urlString) else {
            throw CricsheetError.invalidURL
        }
        let (data, _) = try await URLSession.shared.data(from: url)
        // Save zip to disk
        let zipPath = (documentsPath as NSString).appendingPathComponent("all_json.zip")
        try data.write(to: URL(fileURLWithPath: zipPath))
        // Unzip and parse all JSON files
        let matches = try unzipAndParseAllMatches(from: zipPath)
        return matches
    }

    /// Unzips the downloaded zip and parses all JSON files into CricsheetMatch
    private func unzipAndParseAllMatches(from zipPath: String) throws -> [CricsheetMatch] {
        // NOTE: This is a placeholder. You need to use a zip library (e.g., SSZipArchive) to unzip and parse all files.
        // For now, return an empty array or implement actual logic if the library is available.
        // Example: SSZipArchive.unzipFile(atPath:zipPath, toDestination:documentsPath)
        // Then iterate over all JSON files and parse them using parseMatchData(_:)
        return []
    }

    /// Filters matches by series name, team, or event
    internal func filterMatches(_ matches: [CricsheetMatch], seriesName: String? = nil, team: String? = nil, event: String? = nil) -> [CricsheetMatch] {
        return matches.filter { match in
            let matchesSeries = seriesName == nil || match.venue.localizedCaseInsensitiveContains(seriesName!)
            let matchesTeam = team == nil || match.teams.batting == team || match.teams.bowling == team
            // You can add more sophisticated event/series filtering if metadata is available
            return matchesSeries && matchesTeam
        }
    }
}

// MARK: - Cricsheet Error
public enum CricsheetError: Error {
    case invalidURL
    case downloadFailed
    case parsingFailed
} 