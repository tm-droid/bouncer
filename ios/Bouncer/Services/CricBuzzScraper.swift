import Foundation
import SwiftSoup

/// Live score data extracted from the CricBuzz live scores page
struct LiveScoreData: Equatable {
    struct TeamScore: Equatable {
        let name: String
        let score: String  // e.g. "245/6"
        let overs: String  // e.g. "(78.2)"
        let isBatting: Bool
    }
    let teams: [TeamScore]
    let status: String           // e.g. "Day 2 - Session 1" or "Australia won by 5 wickets"
    let currentRunRate: String?  // e.g. "4.52"
    let requiredRunRate: String? // e.g. "6.10"
}

class CricBuzzScraper {
    func fetchLiveMatches(from url: String) async throws -> [CricketMatch] {
        guard let url = URL(string: url) else {
            throw NSError(domain: "CricBuzzScraper", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15", forHTTPHeaderField: "User-Agent")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
            (200...299).contains(httpResponse.statusCode) else {
            throw NSError(domain: "CricBuzzScraper", code: -2, userInfo: [NSLocalizedDescriptionKey: "Invalid server response"])
        }
        
        guard let html = String(data: data, encoding: .utf8) else {
            print("Failed to decode HTML data")
            throw NSError(domain: "CricBuzzScraper", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid data encoding"])
        }
        
        // <<< DEBUG PRINT: HTML snippet >>>
        print("HTML snippet (first 500 chars):\n\(html.prefix(500))\n")
        
        print("Successfully retrieved HTML content of length: \(html.count)")
        
        let doc = try SwiftSoup.parse(html)
        var matches: [CricketMatch] = []
        
        let matchElements = try doc.select("div.cb-mtch-lst.cb-col.cb-col-100.cb-tms-itm")
        print("Found \(matchElements.count) match elements")
        
        for element in matchElements {
            do {
                let matchId = UUID().uuidString
                let matchInfo = try element.select("h3.cb-lv-scr-mtch-hdr, span.text-bold").text()
                
                let statusElement = try element.select("div.cb-text-live, div.cb-text-complete, div.cb-text-inprogress, div.cb-text-upcoming").first()
                let rawStatus = try statusElement?.text() ?? "Unknown"
                print("Match status: \(rawStatus)")
                
                let cleanedStatus = rawStatus
                    .components(separatedBy: .whitespacesAndNewlines)
                    .filter { !$0.isEmpty }
                    .joined(separator: " ")
                
                print("Processing match from URL: \(url.absoluteString)")
                
                let matchType = determineMatchType(from: matchInfo)
                // Determine category for filtering
                let category: String
                switch matchType.lowercased() {
                case "test", "odi", "t20", "t20i", "world cup":
                    category = "International"
                case let type where type.contains("women"):
                    category = "Women"
                case let type where type.contains("league"):
                    category = "League"
                case let type where type.contains("domestic"):
                    category = "Domestic"
                default:
                    category = "Other"
                }
                
                let battingTeamElement = try element.select("div.cb-hmscg-bat-txt").first()
                let bowlingTeamElement = try element.select("div.cb-hmscg-bwl-txt").first()
                var teams: [CricketMatch.Team] = []
                
                if let battingTeam = battingTeamElement {
                    let name = try battingTeam.select("div.cb-hmscg-tm-nm").text()
                    let scoreDiv = try battingTeam.select("div.cb-ovr-flo").first { element in
                        try element.attr("style").contains("width:140px")
                    }
                    let scoreText = try scoreDiv?.text() ?? ""
                    let (score, _, _) = parseScoreText(scoreText)
                    
                    teams.append(CricketMatch.Team(name: name, score: score, status: "bat", isBatting: true, logoUrl: nil))
                }
                
                if let bowlingTeam = bowlingTeamElement {
                    let name = try bowlingTeam.select("div.cb-hmscg-tm-nm").text()
                    let scoreDiv = try bowlingTeam.select("div.cb-ovr-flo").first { element in
                        try element.attr("style").contains("width:140px")
                    }
                    let scoreText = try scoreDiv?.text() ?? ""
                    let (score, _, _) = parseScoreText(scoreText)
                    
                    teams.append(CricketMatch.Team(name: name, score: score, status: "bowl", isBatting: false, logoUrl: nil))
                }
                
                let venue = try element.select("div.cb-hmscg-vnue-tx").text()
                let date = try element.select("div.cb-hmscg-tm-sta").text()
                _ = try getMatchStage(from: element)
                let seriesId = try element.select("div.cb-series-id").text()
                let matchTitle = try element.select("div.cb-match-title").text()
                let matchTime = try element.select("div.cb-match-time").text()
                let scoreText = try element.select("div.cb-scr-wll-chvrn").text()
                let info = try element.select("div.cb-text-inprogress").text()
                
                let matchScore = CricketMatch.MatchScore(
                    currentInnings: scoreText,
                    batsmen: [],
                    bowlers: [],
                    overs: nil,
                    currentRunRate: nil,
                    requiredRunRate: nil,
                    lastWicket: nil,
                    recentBalls: nil
                )
                
                let match = CricketMatch(
                    id: matchId,
                    seriesId: seriesId,
                    matchTitle: matchTitle,
                    matchType: matchType,
                    status: cleanedStatus,
                    teams: teams,
                    venue: venue.ifEmpty(defaultValue: "Unknown Venue"),
                    date: date.ifEmpty(defaultValue: "Unknown Date"),
                    matchTime: matchTime,
                    score: matchScore,
                    currentStatus: cleanedStatus,
                    info: info,
                    recentBalls: nil,
                    currentBowler: nil,
                    scrapedBatsmen: nil,
                    category: category
                )
                
                matches.append(match)
                print("Added match: \(match.id) - \(match.teams.map { $0.name }.joined(separator: " vs "))")
            } catch {
                print("Error parsing match: \(error)")
                continue
            }
        }
        
        return matches
    }
    
    private func determineMatchType(from matchInfo: String) -> String {
        if matchInfo.contains("Test") { return "Test" }
        if matchInfo.contains("ODI") { return "ODI" }
        if matchInfo.contains("T20") || matchInfo.contains("T20I") { return "T20" }
        if matchInfo.contains("World Cup") { return "World Cup" }
        return "Limited Overs"
    }
    
    private func getMatchStage(from element: Element) throws -> String {
        let stageSelectors = [
            "div.cb-col.cb-col-100.cb-ltst-wgt-hdr",
            "div.cb-mtch-info",
            "div.cb-mtch-crd-state"
        ]
        
        for selector in stageSelectors {
            if let stageText = try element.select(selector).first()?.text(),
               !stageText.isEmpty {
                return stageText
            }
        }
        return "League Stage"
    }
    
    func fetchBallByBallAndBatsmen(matchId: String) async throws -> (recentBalls: [String], overNumbers: [String], batsmen: [ScrapedBatsman], bowler: ScrapedBowler?, recentBallsSummary: String?, liveScore: LiveScoreData?) {
        let url = "https://www.cricbuzz.com/live-cricket-scores/\(matchId)"
        guard let url = URL(string: url) else {
            throw NSError(domain: "CricBuzzScraper", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "CricBuzzScraper", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid data encoding"])
        }
        let doc = try SwiftSoup.parse(html)
        
        // === Extract live team scores from the page ===
        var liveScore: LiveScoreData? = nil
        do {
            var teamScores: [LiveScoreData.TeamScore] = []
            var liveStatus = ""
            var currentRunRate: String? = nil
            var requiredRunRate: String? = nil
            
            // Extract team scores from the batting/bowling sections on the live score page
            // CricBuzz uses cb-hmscg-bat-txt for batting team and cb-hmscg-bwl-txt for bowling team
            let battingTeamEl = try doc.select("div.cb-hmscg-bat-txt").first()
            let bowlingTeamEl = try doc.select("div.cb-hmscg-bwl-txt").first()
            
            if let battingEl = battingTeamEl {
                let name = try battingEl.select("div.cb-hmscg-tm-nm").text()
                let scoreDiv = try battingEl.select("div.cb-ovr-flo").first { el in
                    try el.attr("style").contains("width:140px")
                }
                let scoreText = try scoreDiv?.text() ?? ""
                let parsed = Self.extractScoreAndOvers(scoreText)
                teamScores.append(LiveScoreData.TeamScore(name: name, score: parsed.score, overs: parsed.overs, isBatting: true))
            }
            
            if let bowlingEl = bowlingTeamEl {
                let name = try bowlingEl.select("div.cb-hmscg-tm-nm").text()
                let scoreDiv = try bowlingEl.select("div.cb-ovr-flo").first { el in
                    try el.attr("style").contains("width:140px")
                }
                let scoreText = try scoreDiv?.text() ?? ""
                let parsed = Self.extractScoreAndOvers(scoreText)
                teamScores.append(LiveScoreData.TeamScore(name: name, score: parsed.score, overs: parsed.overs, isBatting: false))
            }
            
            // If the above selectors didn't work, try the header score section
            if teamScores.isEmpty {
                // Try cb-font-20 (large score text on live pages)
                let scoreHeaders = try doc.select("div.cb-scrd-hdr-rw, div.cb-col.cb-col-100.cb-scr-wll-chvrn")
                for header in scoreHeaders {
                    let text = try header.text()
                    if !text.isEmpty {
                        // Format: "IND 245/6 (78.2 Ovs)" or similar
                        let parsed = Self.extractScoreAndOvers(text)
                        if !parsed.score.isEmpty {
                            let namePart = text.components(separatedBy: CharacterSet.decimalDigits).first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                            teamScores.append(LiveScoreData.TeamScore(name: namePart, score: parsed.score, overs: parsed.overs, isBatting: teamScores.isEmpty))
                        }
                    }
                }
            }
            
            // Extract match status
            let statusSelectors = ["div.cb-text-live", "div.cb-text-complete", "div.cb-text-inprogress", "div.cb-scr-wll-chvrn"]
            for selector in statusSelectors {
                if let statusEl = try doc.select(selector).first() {
                    liveStatus = try statusEl.text()
                    if !liveStatus.isEmpty { break }
                }
            }
            
            // Extract run rates from the mini-info section
            let infoElements = try doc.select("div.cb-min-inf span.ng-binding, div.cb-min-inf span")
            for el in infoElements {
                let text = try el.text()
                if text.lowercased().contains("crr") {
                    currentRunRate = text.replacingOccurrences(of: "CRR:", with: "").replacingOccurrences(of: "CRR", with: "").trimmingCharacters(in: .whitespaces)
                }
                if text.lowercased().contains("rrr") {
                    requiredRunRate = text.replacingOccurrences(of: "RRR:", with: "").replacingOccurrences(of: "RRR", with: "").trimmingCharacters(in: .whitespaces)
                }
            }
            
            if !teamScores.isEmpty {
                liveScore = LiveScoreData(teams: teamScores, status: liveStatus, currentRunRate: currentRunRate, requiredRunRate: requiredRunRate)
                print("CricBuzzScraper: Extracted live scores: \(teamScores.map { "\($0.name): \($0.score) \($0.overs)" })")
            }
        } catch {
            print("CricBuzzScraper: Error extracting live scores: \(error)")
        }
        
        // Extract ball-by-ball data from recent balls section
        var recentBalls: [String] = []
        var overNumbers: [String] = []
        
        // Try multiple selectors for recent balls
        let recentBallSelectors = [
            "div.cb-min-rcnt span.ng-binding",
            "div.cb-min-rcnt span",
            "div.cb-recent-balls span",
            "div.cb-ball-by-ball span"
        ]
        
        for selector in recentBallSelectors {
            let ballElements = try doc.select(selector)
            if !ballElements.isEmpty {
                for element in ballElements {
                    let ballText = try element.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    if !ballText.isEmpty && isValidBallResult(ballText) {
                        recentBalls.append(ballText)
                    }
                }
                if !recentBalls.isEmpty {
                    print("CricBuzzScraper: Found recent balls using selector '\(selector)': \(recentBalls)")
                    break
                }
            }
        }
        
        // If no recent balls found, try to extract from commentary
        if recentBalls.isEmpty {
            let ballDivs = try doc.select("div.cb-col.cb-col-100.ng-scope")
            for ballDiv in ballDivs {
                let overNumElements = try ballDiv.select("div.cb-mat-mnu-wrp.cb-ovr-num.ng-binding.ng-scope")
                let commElements = try ballDiv.select("p.cb-com-ln")
                if let overNumElement = overNumElements.first, let commElement = commElements.first {
                    let overNum = try overNumElement.text()
                    let comm = try commElement.text()
                    if !overNum.isEmpty, !comm.isEmpty {
                        overNumbers.append(overNum)
                        // Extract ball result from commentary
                        let ballResult = extractBallResultFromCommentary(comm)
                        if !ballResult.isEmpty {
                            recentBalls.append(ballResult)
                        }
                    }
                }
            }
        }
        
        // Extract over numbers from the scorecard
        if overNumbers.isEmpty {
            let overSelectors = [
                "div.cb-min-hdr-rw:contains(Over)",
                "div.cb-ovr-num",
                "span.cb-ovr-num"
            ]
            
            for selector in overSelectors {
                let overElements = try doc.select(selector)
                for element in overElements {
                    let overText = try element.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    if !overText.isEmpty && overText.contains(".") {
                        overNumbers.append(overText)
                    }
                }
                if !overNumbers.isEmpty {
                    break
                }
            }
        }
        
        print("CricBuzzScraper: Fetched overNumbers: \(overNumbers)")
        print("CricBuzzScraper: Fetched recentBalls: \(recentBalls)")
        
        // Batsmen extraction (sectioned)
        var batsmen: [ScrapedBatsman] = []
        if let batsmanHeader = try? doc.select("div.cb-min-hdr-rw:contains(Batter)").first {
            var next = try batsmanHeader.nextElementSibling()
            while let row = next, row.hasClass("cb-min-itm-rw") {
                let nameLinks = try row.select("div.cb-col-50 > a")
                let statDivs = try row.select("div.ab, div.text-right")
                if let nameLink = nameLinks.first, statDivs.count >= 5 {
                    let name = try nameLink.text()
                    let isStriker = try row.select("span[ng-if=\"$first\"]").first != nil
                    let runs = try statDivs[0].text()
                    let balls = try statDivs[1].text()
                    let fours = try statDivs[2].text()
                    let sixes = try statDivs[3].text()
                    let strikeRate = try statDivs[4].text()
                    batsmen.append(ScrapedBatsman(name: name, runs: runs, balls: balls, fours: fours, sixes: sixes, strikeRate: strikeRate, isStriker: isStriker))
                }
                next = try row.nextElementSibling()
            }
        }
        print("CricBuzzScraper: Total batsmen parsed: \(batsmen.count)")
        
        // Bowler extraction (sectioned)
        var bowler: ScrapedBowler? = nil
        // Try to extract bowler from latest ball-by-ball commentary
        if let latestBall = recentBalls.last {
            // Example: "7.6 Ankit Sharma to Sidak Gurvinder Singh, 1 run, ..."
            let pattern = #"^\d+\.\d+\s+([^\s]+(?:\s+[^\s]+)*)\s+to\s"#
            if let regex = try? NSRegularExpression(pattern: pattern, options: []),
               let match = regex.firstMatch(in: latestBall, options: [], range: NSRange(location: 0, length: latestBall.utf16.count)),
               let range = Range(match.range(at: 1), in: latestBall) {
                let bowlerName = String(latestBall[range])
                // Try to find this bowler in the parsed bowler table (for stats)
                if let found = bowler == nil ? nil : bowler, found.name == bowlerName {
                    // Already set
                } else {
                    // Search in summary table below
                    if let bowlerHeader = try? doc.select("div.cb-min-hdr-rw:contains(Bowler)").first {
                        var next = try bowlerHeader.nextElementSibling()
                        while let row = next, row.hasClass("cb-min-itm-rw") {
                            let nameLinks = try row.select("div.cb-col-50 > a")
                            let statDivs = try row.select("div.text-right, div.ab")
                            if let nameLink = nameLinks.first, statDivs.count >= 5 {
                                let name = try nameLink.text()
                                if name == bowlerName {
                                    let overs = try statDivs[0].text()
                                    let maidens = try statDivs[1].text()
                                    let runs = try statDivs[2].text()
                                    let wickets = try statDivs[3].text()
                                    let economy = try statDivs[4].text()
                                    bowler = ScrapedBowler(name: name, overs: overs, maidens: maidens, runs: runs, wickets: wickets, economy: economy)
                                    break
                                }
                            }
                            next = try row.nextElementSibling()
                        }
                    }
                    // If not found, fallback to just the name
                    if bowler == nil {
                        bowler = ScrapedBowler(name: bowlerName, overs: "-", maidens: "-", runs: "-", wickets: "-", economy: "-")
                    }
                }
            }
        }
        // If still not found, fallback to summary table's first bowler
        if bowler == nil, let bowlerHeader = try? doc.select("div.cb-min-hdr-rw:contains(Bowler)").first {
            var next = try bowlerHeader.nextElementSibling()
            while let row = next, row.hasClass("cb-min-itm-rw") {
                let nameLinks = try row.select("div.cb-col-50 > a")
                let statDivs = try row.select("div.text-right, div.ab")
                if let nameLink = nameLinks.first, statDivs.count >= 5 {
                    let name = try nameLink.text()
                    let overs = try statDivs[0].text()
                    let maidens = try statDivs[1].text()
                    let runs = try statDivs[2].text()
                    let wickets = try statDivs[3].text()
                    let economy = try statDivs[4].text()
                    bowler = ScrapedBowler(name: name, overs: overs, maidens: maidens, runs: runs, wickets: wickets, economy: economy)
                    break
                }
                next = try row.nextElementSibling()
            }
        }
        print("CricBuzzScraper: Current bowler: \(bowler?.name ?? "None")")
        
        // Scrape recent balls summary
        var recentBallsSummary: String? = nil
        if let recentDiv = try? doc.select("div.cb-min-rcnt").first() {
            recentBallsSummary = try? recentDiv.select("span.ng-binding").last()?.text()
        }
        
        return (recentBalls, overNumbers, batsmen, bowler, recentBallsSummary, liveScore)
    }
    
    /// Extract score (e.g. "245/6") and overs (e.g. "(78.2)") from a text like "245/6 (78.2 Ovs)" or "245-6 78.2"
    static func extractScoreAndOvers(_ text: String) -> (score: String, overs: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        
        // Pattern: "SCORE (OVERS Ovs)" or "SCORE (OVERS)"
        let pattern = #"(\d+[/-]\d+)\s*\(([0-9.]+)\s*(?:Ovs|ov)?\)"#
        if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) {
            let score = match.range(at: 1).location != NSNotFound ? (trimmed as NSString).substring(with: match.range(at: 1)) : ""
            let overs = match.range(at: 2).location != NSNotFound ? "(\((trimmed as NSString).substring(with: match.range(at: 2))))" : ""
            return (score.replacingOccurrences(of: "-", with: "/"), overs)
        }
        
        // Fallback: just try to find a score pattern
        let scorePattern = #"(\d+[/-]\d+)"#
        if let regex = try? NSRegularExpression(pattern: scorePattern),
           let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) {
            let score = (trimmed as NSString).substring(with: match.range(at: 1))
            return (score.replacingOccurrences(of: "-", with: "/"), "")
        }
        
        return (trimmed, "")
    }
    
    // Helper function to validate if a string represents a valid ball result
    private func isValidBallResult(_ text: String) -> Bool {
        let validResults = ["0", "1", "2", "3", "4", "5", "6", "7", "W", "WD", "NB", "LB", "B"]
        return validResults.contains(text) || text.matches(of: /^[0-9]+$/).count > 0
    }
    
    // Helper function to extract ball result from commentary text
    private func extractBallResultFromCommentary(_ commentary: String) -> String {
        let lowercased = commentary.lowercased()
        
        // Check for wickets
        if lowercased.contains("wicket") || lowercased.contains("out") || lowercased.contains("caught") || lowercased.contains("bowled") || lowercased.contains("lbw") || lowercased.contains("stumped") || lowercased.contains("run out") {
            return "W"
        }
        
        // Check for boundaries
        if lowercased.contains("six") || lowercased.contains("6 runs") {
            return "6"
        }
        if lowercased.contains("four") || lowercased.contains("4 runs") {
            return "4"
        }
        
        // Check for other runs
        if lowercased.contains("3 runs") || lowercased.contains("three runs") {
            return "3"
        }
        if lowercased.contains("2 runs") || lowercased.contains("two runs") {
            return "2"
        }
        if lowercased.contains("1 run") || lowercased.contains("single") {
            return "1"
        }
        
        // Check for extras
        if lowercased.contains("wide") {
            return "WD"
        }
        if lowercased.contains("no ball") {
            return "NB"
        }
        if lowercased.contains("leg bye") {
            return "LB"
        }
        if lowercased.contains("bye") {
            return "B"
        }
        
        // Default to dot ball
        return "0"
    }
    
    /// Fetch recent balls from the scorecard section specifically
    func fetchRecentBallsFromScorecard(matchId: String) async throws -> [String] {
        let url = "https://www.cricbuzz.com/live-cricket-scores/\(matchId)"
        guard let url = URL(string: url) else {
            throw NSError(domain: "CricBuzzScraper", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15", forHTTPHeaderField: "User-Agent")
        
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "CricBuzzScraper", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid data encoding"])
        }
        
        let doc = try SwiftSoup.parse(html)
        var recentBalls: [String] = []
        
        // Try multiple selectors for recent balls in the scorecard
        let scorecardSelectors = [
            "div.cb-min-rcnt span.ng-binding",
            "div.cb-min-rcnt span",
            "div.cb-recent-balls span",
            "div.cb-ball-by-ball span",
            "div.cb-min-rcnt div span",
            "div.cb-recent-balls div span"
        ]
        
        for selector in scorecardSelectors {
            let ballElements = try doc.select(selector)
            if !ballElements.isEmpty {
                for element in ballElements {
                    let ballText = try element.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    if !ballText.isEmpty && isValidBallResult(ballText) {
                        recentBalls.append(ballText)
                    }
                }
                if !recentBalls.isEmpty {
                    print("CricBuzzScraper: Found recent balls from scorecard using selector '\(selector)': \(recentBalls)")
                    break
                }
            }
        }
        
        // If still no balls found, try to extract from the commentary section
        if recentBalls.isEmpty {
            let commentarySelectors = [
                "div.cb-col.cb-col-100.ng-scope p.cb-com-ln",
                "div.cb-com-ln",
                "p.cb-com-ln"
            ]
            
            for selector in commentarySelectors {
                let commElements = try doc.select(selector)
                for element in commElements {
                    let commText = try element.text()
                    let ballResult = extractBallResultFromCommentary(commText)
                    if !ballResult.isEmpty && ballResult != "0" {
                        recentBalls.append(ballResult)
                    }
                }
                if !recentBalls.isEmpty {
                    print("CricBuzzScraper: Found recent balls from commentary using selector '\(selector)': \(recentBalls)")
                    break
                }
            }
        }
        
        // Limit to last 6 balls (1 over)
        if recentBalls.count > 6 {
            recentBalls = Array(recentBalls.suffix(6))
        }
        
        print("CricBuzzScraper: Final recent balls: \(recentBalls)")
        return recentBalls
    }
    
    func fetchLiveCommentary(matchUrl: String) async throws -> [String] {
        guard let url = URL(string: matchUrl) else {
            let msg = "CricBuzzScraper: Invalid URL: \(matchUrl)"
            print(msg)
            throw NSError(domain: "CricBuzzScraper", code: -1, userInfo: [NSLocalizedDescriptionKey: msg])
        }
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15", forHTTPHeaderField: "User-Agent")
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let html = String(data: data, encoding: .utf8) else {
                let msg = "CricBuzzScraper: Invalid data encoding for URL: \(matchUrl)"
                print(msg)
                throw NSError(domain: "CricBuzzScraper", code: -3, userInfo: [NSLocalizedDescriptionKey: msg])
            }
            let doc = try SwiftSoup.parse(html)
            // Select the first div with ng-show="!isCommentaryRendered"
            if let commentaryContainer = try doc.select("div[ng-show='!isCommentaryRendered']").first(),
               let latestBallDiv = try commentaryContainer.select("div").first() {
                let latestBallText = try latestBallDiv.text()
                print("CricBuzzScraper: Latest ball commentary: \(latestBallText)")
                return [latestBallText]
            } else {
                print("CricBuzzScraper: No latest ball commentary found for URL: \(matchUrl)")
                return []
            }
        } catch {
            print("CricBuzzScraper: Error fetching commentary for URL: \(matchUrl): \(error)")
            throw error
        }
    }
}

extension String {
    func ifEmpty(defaultValue: String) -> String {
        return self.isEmpty ? defaultValue : self
    }
}

private func parseScoreText(_ text: String) -> (score: String, wickets: Int, overs: Double) {
    let components = text.components(separatedBy: " ")
    var score = "0"
    var wickets = 0
    var overs = 0.0
    
    if let scoreComponent = components.first {
        let scoreParts = scoreComponent.components(separatedBy: "-")
        if scoreParts.count > 0 {
            score = scoreParts[0]
            if scoreParts.count > 1 {
                wickets = Int(scoreParts[1]) ?? 0
            }
        }
    }
    
    if components.count > 1 {
        let oversText = components[1].replacingOccurrences(of: "(", with: "")
            .replacingOccurrences(of: ")", with: "")
            .replacingOccurrences(of: "Ovs", with: "")
            .trimmingCharacters(in: .whitespaces)
        overs = Double(oversText) ?? 0.0
    }
    
    return (score, wickets, overs)
}
