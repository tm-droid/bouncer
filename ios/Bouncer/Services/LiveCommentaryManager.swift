import Foundation

class LiveCommentaryManager {
    private let cricbuzz = CricBuzzScraper()
    private let cricinfo = CricInfoScraper()
    private let scorer = CommentaryScorer()
    private let fusion = CommentaryFusionService()
    private var commentaryHistory: [String] = []
    private let historyLimit = 5
    private var aiCommentaryHistory: [String] = []
    
    /// Helper: Generate a summary string for the last 3 overs (18 balls) using full commentary lines
    func generateOversSummary(from commentaryLines: [String]) -> String {
        guard !commentaryLines.isEmpty else { return "No recent commentary available." }
        let totalBalls = min(max(commentaryLines.count, 0), 18)
        let lines: [String]
        if totalBalls > 0 {
            lines = Array(commentaryLines.prefix(totalBalls))
        } else {
            lines = []
        }
        let numbered = lines.enumerated().map { "\($0 + 1). \($1)" }
        return numbered.joined(separator: "\n")
    }

    /// Fetches and returns the best commentary for a new ball, fusing with AI if needed
    func getBestCommentary(cricbuzzUrl: String, cricinfoUrl: String, keyNames: [String], recentBalls: [String]? = nil) async -> String {
        // Fetch ball-by-ball, over numbers, batsmen, and bowler from CricBuzzScraper
        let matchId: String = URL(string: cricbuzzUrl)?.lastPathComponent ?? "unknown"
        let result = try? await cricbuzz.fetchBallByBallAndBatsmen(matchId: matchId)
        let (recentBalls, overNumbers, batsmen, bowler, recentBallsSummary, _) = result ?? ([], [], [], nil, nil, nil)
        let ballNumber = overNumbers.last ?? ""
        let bowlerName = bowler?.name ?? ""
        let batsmanName = batsmen.first(where: { $0.isStriker })?.name ?? batsmen.first?.name ?? ""
        // Use recent balls summary from CricBuzz scorecard when available as score context
        let scoreContext = recentBallsSummary ?? ""

        async let buzzSnippets = cricbuzz.fetchLiveCommentary(matchUrl: cricbuzzUrl)
        async let infoSnippets = cricinfo.fetchLiveCommentary(matchUrl: cricinfoUrl)
        let (buzz, info) = await (try? buzzSnippets, try? infoSnippets)
        let buzzLine = buzz?.first ?? ""
        let infoLine = info?.first ?? ""
        // Use last 18 lines of AI commentary history for the overs summary
        let last18Count = min(max(aiCommentaryHistory.count, 0), 18)
        let last18AI: [String] = last18Count > 0 ? Array(aiCommentaryHistory.suffix(last18Count)) : []
        let oversSummary: String
        if !last18AI.isEmpty {
            oversSummary = await fusion.summarizeOversWithAI(commentaryLines: last18AI, matchId: matchId, over: 0)
        } else {
            oversSummary = "No recent commentary available."
        }
        let resultValue: String
        switch scorer.compare(snippet1: buzzLine, snippet2: infoLine, keyNames: keyNames, recentHistory: commentaryHistory) {
        case .useFirst:
            resultValue = buzzLine
        case .useSecond:
            resultValue = infoLine
        case .fuseBoth:
            resultValue = await fusion.fuse(
                snippet1: buzzLine,
                snippet2: infoLine,
                oversSummary: oversSummary,
                matchId: matchId,
                over: 0,
                ballNumber: ballNumber,
                bowler: bowlerName,
                batsman: batsmanName,
                scoreContext: scoreContext
            )
        }
        // Update rolling history
        commentaryHistory.append(resultValue)
        if commentaryHistory.count > historyLimit {
            commentaryHistory.removeFirst(commentaryHistory.count - historyLimit)
        }
        // Update AI commentary history for rolling overs summary
        aiCommentaryHistory.append(resultValue)
        if aiCommentaryHistory.count > 18 {
            aiCommentaryHistory.removeFirst(aiCommentaryHistory.count - 18)
        }
        return resultValue
    }
    
    /// Fetches and returns the best commentary for a new ball, automatically finding the closest Cricinfo match and mapping IDs
    func getBestCommentaryForMatch(matchTitle: String, cricbuzzId: String, cricbuzzUrl: String, keyNames: [String]) async -> String {
        print("LiveCommentaryManager: getBestCommentaryForMatch called with matchTitle=\(matchTitle), cricbuzzId=\(cricbuzzId)")
        // Check if we already have a mapped Cricinfo ID
        if let mappedCricinfoId = cricinfo.getMappedCricinfoId(for: cricbuzzId) {
            let cricinfoUrl = "https://www.espncricinfo.com/series/\(mappedCricinfoId)/commentary"
            print("LiveCommentaryManager: Found mapped Cricinfo ID=\(mappedCricinfoId), using URL=\(cricinfoUrl)")
            let commentaryAvailable = await cricinfo.isCommentaryAvailable(matchId: mappedCricinfoId, url: cricinfoUrl)
            if commentaryAvailable {
                print("LiveCommentaryManager: Cricinfo commentary is available, fusing both sources.")
                return await getBestCommentary(cricbuzzUrl: cricbuzzUrl, cricinfoUrl: cricinfoUrl, keyNames: keyNames)
            } else {
                print("LiveCommentaryManager: Cricinfo commentary NOT available, using only Cricbuzz but passing through AI.")
                // Fetch Cricbuzz commentary and pass through AI fusion with empty infoLine
                let buzz = try? await cricbuzz.fetchLiveCommentary(matchUrl: cricbuzzUrl)
                let buzzLine = buzz?.first ?? ""
                // Use the fusion service with only Cricbuzz line
                let oversSummary = buzzLine.isEmpty ? "No recent commentary available." : buzzLine
                // Fetch ball-by-ball, over numbers, batsmen, and bowler from CricBuzzScraper
                let result = try? await cricbuzz.fetchBallByBallAndBatsmen(matchId: cricbuzzId)
                let (recentBalls, overNumbers, batsmen, bowler, recentBallsSummary, _) = result ?? ([], [], [], nil, nil, nil)
                let ballNumber = overNumbers.last ?? ""
                let bowlerName = bowler?.name ?? ""
                let batsmanName = batsmen.first(where: { $0.isStriker })?.name ?? batsmen.first?.name ?? ""
                let scoreContext = recentBallsSummary ?? "" // Use scorecard summary when available
                return await fusion.fuse(snippet1: buzzLine, snippet2: "", oversSummary: oversSummary, matchId: cricbuzzId, over: 0, ballNumber: ballNumber, bowler: bowlerName, batsman: batsmanName, scoreContext: scoreContext)
            }
        }
        // Find the closest Cricinfo match URL and ID
        if let (cricinfoUrl, cricinfoId) = try? await cricinfo.findClosestLiveMatchUrlAndId(matchName: matchTitle) {
            print("LiveCommentaryManager: Levenshtein found Cricinfo URL=\(cricinfoUrl), ID=\(cricinfoId)")
            cricinfo.mapMatchIds(cricbuzzId: cricbuzzId, cricinfoId: cricinfoId)
            let commentaryAvailable = await cricinfo.isCommentaryAvailable(matchId: cricinfoId, url: cricinfoUrl)
            if commentaryAvailable {
                print("LiveCommentaryManager: Cricinfo commentary is available, fusing both sources.")
                return await getBestCommentary(cricbuzzUrl: cricbuzzUrl, cricinfoUrl: cricinfoUrl, keyNames: keyNames)
            } else {
                print("LiveCommentaryManager: Cricinfo commentary NOT available, using only Cricbuzz but passing through AI.")
                let buzz = try? await cricbuzz.fetchLiveCommentary(matchUrl: cricbuzzUrl)
                let buzzLine = buzz?.first ?? ""
                let oversSummary = buzzLine.isEmpty ? "No recent commentary available." : buzzLine
                // Fetch ball-by-ball, over numbers, batsmen, and bowler from CricBuzzScraper
                let result = try? await cricbuzz.fetchBallByBallAndBatsmen(matchId: cricbuzzId)
                let (recentBalls, overNumbers, batsmen, bowler, recentBallsSummary, _) = result ?? ([], [], [], nil, nil, nil)
                let ballNumber = overNumbers.last ?? ""
                let bowlerName = bowler?.name ?? ""
                let batsmanName = batsmen.first(where: { $0.isStriker })?.name ?? batsmen.first?.name ?? ""
                let scoreContext = recentBallsSummary ?? "" // Use scorecard summary when available
                return await fusion.fuse(snippet1: buzzLine, snippet2: "", oversSummary: oversSummary, matchId: cricbuzzId, over: 0, ballNumber: ballNumber, bowler: bowlerName, batsman: batsmanName, scoreContext: scoreContext)
            }
        } else {
            print("LiveCommentaryManager: Could not find Cricinfo match for title=\(matchTitle)")
            // Fallback: use only Cricbuzz, pass through AI
            let buzz = try? await cricbuzz.fetchLiveCommentary(matchUrl: cricbuzzUrl)
            let buzzLine = buzz?.first ?? ""
            let oversSummary = buzzLine.isEmpty ? "No recent commentary available." : buzzLine
            // Fetch ball-by-ball, over numbers, batsmen, and bowler from CricBuzzScraper
            let result = try? await cricbuzz.fetchBallByBallAndBatsmen(matchId: cricbuzzId)
            let (recentBalls, overNumbers, batsmen, bowler, recentBallsSummary, _) = result ?? ([], [], [], nil, nil, nil)
            let ballNumber = overNumbers.last ?? ""
            let bowlerName = bowler?.name ?? ""
            let batsmanName = batsmen.first(where: { $0.isStriker })?.name ?? batsmen.first?.name ?? ""
            let scoreContext = recentBallsSummary ?? "" // Use scorecard summary when available
            return await fusion.fuse(snippet1: buzzLine, snippet2: "", oversSummary: oversSummary, matchId: cricbuzzId, over: 0, ballNumber: ballNumber, bowler: bowlerName, batsman: batsmanName, scoreContext: scoreContext)
        }
    }
    
    /// Returns the mapped Cricinfo match ID for a given Cricbuzz match ID
    func getMappedCricinfoId(for cricbuzzId: String) -> String? {
        return cricinfo.getMappedCricinfoId(for: cricbuzzId)
    }
    
    /// Returns the current rolling commentary history
    func getHistory() -> [String] {
        return commentaryHistory
    }
} 