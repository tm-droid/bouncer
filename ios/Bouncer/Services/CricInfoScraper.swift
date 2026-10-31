import Foundation
import SwiftSoup

class CricInfoScraper {
    private let matchIdMapKey = "CricbuzzCricinfoMatchIdMap"
    private var matchIdMap: [String: String] = [:] // cricbuzzId: cricinfoId
    private var commentaryAvailabilityCache: [String: Bool] = [:]
    private let commentaryAvailabilityKey = "CricinfoCommentaryAvailabilityCache"
    
    init() {
        loadMapping()
    }
    
    /// Fetches live ball-by-ball commentary for a given match from Cricinfo
    func fetchLiveCommentary(matchUrl: String) async throws -> [String] {
        guard let url = URL(string: matchUrl) else {
            let msg = "CricInfoScraper: Invalid URL: \(matchUrl)"
            print(msg)
            throw NSError(domain: "CricInfoScraper", code: -1, userInfo: [NSLocalizedDescriptionKey: msg])
        }
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15", forHTTPHeaderField: "User-Agent")
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let html = String(data: data, encoding: .utf8) else {
                let msg = "CricInfoScraper: Invalid data encoding for URL: \(matchUrl)"
                print(msg)
                throw NSError(domain: "CricInfoScraper", code: -3, userInfo: [NSLocalizedDescriptionKey: msg])
            }
            let doc = try SwiftSoup.parse(html)
            // Select the first div with the Cricinfo commentary class
            if let latestBallDiv = try doc.select("div.ds-text-tight-l.ds-flex.ds-px-3.ds-py-2").first() {
                let latestBallText = try latestBallDiv.text()
                print("CricInfoScraper: Latest ball commentary: \(latestBallText)")
                return [latestBallText]
            } else {
                print("CricInfoScraper: No latest ball commentary found for URL: \(matchUrl)")
                return []
            }
        } catch {
            print("CricInfoScraper: Error fetching commentary for URL: \(matchUrl): \(error)")
            throw error
        }
    }

    /// Helper to normalize match titles for better fuzzy matching
    private func normalizeTitle(_ title: String) -> String {
        let lower = title.lowercased()
        let noPunct = lower.replacingOccurrences(of: "[^a-z0-9 ]", with: "", options: .regularExpression)
        let collapsed = noPunct.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        return collapsed.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Helper to extract team names from a match title like "Worcestershire vs Hampshire"
    private func extractTeamNames(_ title: String) -> [String] {
        let lower = title.lowercased()
        let vsSplit = lower.components(separatedBy: " vs ")
        if vsSplit.count == 2 {
            return vsSplit.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        // Try splitting by " v " as well
        let vSplit = lower.components(separatedBy: " v ")
        if vSplit.count == 2 {
            return vSplit.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        return []
    }

    /// Finds the closest live match on Cricinfo by match name using Levenshtein distance, returns (url, matchId)
    func findClosestLiveMatchUrlAndId(matchName: String) async throws -> (url: String, matchId: String)? {
        let liveUrl = "https://www.espncricinfo.com/live-cricket-score"
        guard let url = URL(string: liveUrl) else { return nil }
        let (data, _) = try await URLSession.shared.data(from: url)
        guard let html = String(data: data, encoding: .utf8) else { return nil }
        let doc = try SwiftSoup.parse(html)
        // Each match card: 'a.ds-no-tap-higlight'
        let matchLinks = try doc.select("a.ds-no-tap-higlight")
        var bestScore = Int.max
        var bestUrl: String? = nil
        var bestTitle: String? = nil
        let fallbackThreshold = 15 // Only accept matches with score below this for fallback
        let teamMatchThreshold = 200 // Much higher threshold for team-name matches
        let normalizedBuzz = normalizeTitle(matchName)
        let teamNames = extractTeamNames(matchName)
        print("CricInfoScraper: Extracted team names from buzz title: \(teamNames)")
        var filteredCandidates: [(title: String, normalized: String, href: String, score: Int)] = []
        for link in matchLinks {
            let title = try link.text()
            let normalizedInfo = normalizeTitle(title)
            let href = try link.attr("href")
            // Only consider candidates that contain both team names
            if teamNames.count == 2 && normalizedInfo.contains(teamNames[0]) && normalizedInfo.contains(teamNames[1]) {
                let score = Self.levenshtein(aStr: normalizedBuzz, bStr: normalizedInfo)
                print("CricInfoScraper: [TEAM MATCH] Candidate title=\(title), normalized=\(normalizedInfo), score=\(score)")
                filteredCandidates.append((title, normalizedInfo, href, score))
            } else {
                let score = Self.levenshtein(aStr: normalizedBuzz, bStr: normalizedInfo)
                print("CricInfoScraper: Candidate title=\(title), normalized=\(normalizedInfo), score=\(score)")
            }
        }
        if !filteredCandidates.isEmpty {
            // Pick the candidate with the lowest score
            let best = filteredCandidates.min(by: { $0.score < $1.score })!
            print("CricInfoScraper: [TEAM MATCH] Using threshold \(teamMatchThreshold)")
            if best.score <= teamMatchThreshold {
                print("CricInfoScraper: [TEAM MATCH] Selected title=\(best.title), score=\(best.score)")
                let urlStr = best.href.hasPrefix("http") ? best.href : ("https://www.espncricinfo.com" + best.href)
                if let matchId = Self.extractCricinfoMatchId(from: urlStr) {
                    return (urlStr, matchId)
                }
            } else {
                print("CricInfoScraper: [TEAM MATCH] No candidate below threshold. Best was \(best.title) with score \(best.score)")
            }
        }
        // Fallback to old logic if no team match found
        for link in matchLinks {
            let title = try link.text()
            let normalizedInfo = normalizeTitle(title)
            let href = try link.attr("href")
            let score = Self.levenshtein(aStr: normalizedBuzz, bStr: normalizedInfo)
            if score < bestScore {
                bestScore = score
                bestUrl = href.hasPrefix("http") ? href : ("https://www.espncricinfo.com" + href)
                bestTitle = title
            }
        }
        if bestScore > fallbackThreshold {
            print("CricInfoScraper: No close enough match found for \(matchName). Best was \(bestTitle ?? "none") with score \(bestScore)")
            return nil
        }
        guard let urlStr = bestUrl, let matchId = Self.extractCricinfoMatchId(from: urlStr) else { return nil }
        return (urlStr, matchId)
    }

    /// Extracts the Cricinfo match ID from a match URL (4th last path component)
    static func extractCricinfoMatchId(from url: String) -> String? {
        // Try to extract the last numeric segment before /live-cricket-score
        let pattern = "(\\d+)(?=/live-cricket-score)"
        if let range = url.range(of: pattern, options: .regularExpression) {
            return String(url[range])
        }
        // Fallback: get the last numeric segment in the path
        let comps = url.split(separator: "/")
        for comp in comps.reversed() {
            if let _ = Int(comp) {
                return String(comp)
            }
        }
        print("CricInfoScraper: extractCricinfoMatchId could not find numeric match ID in URL: \(url)")
        return nil
    }

    /// Maps a Cricbuzz match ID to a Cricinfo match ID
    func mapMatchIds(cricbuzzId: String, cricinfoId: String) {
        matchIdMap["cricbuzz_\(cricbuzzId)"] = "cricinfo_\(cricinfoId)"
        saveMapping()
        print("CricInfoScraper: Mapped cricbuzz_\(cricbuzzId) to cricinfo_\(cricinfoId)")
    }
    
    /// Retrieves the mapped Cricinfo match ID for a Cricbuzz match ID
    func getMappedCricinfoId(for cricbuzzId: String) -> String? {
        return matchIdMap["cricbuzz_\(cricbuzzId)"]
    }
    
    /// Saves the mapping to UserDefaults
    private func saveMapping() {
        UserDefaults.standard.set(matchIdMap, forKey: matchIdMapKey)
    }
    
    /// Loads the mapping from UserDefaults
    private func loadMapping() {
        if let saved = UserDefaults.standard.dictionary(forKey: matchIdMapKey) as? [String: String] {
            matchIdMap = saved
        }
    }
    
    /// Clears the mapping (for debugging or reset)
    func clearMapping() {
        matchIdMap.removeAll()
        UserDefaults.standard.removeObject(forKey: matchIdMapKey)
        print("CricInfoScraper: Cleared all match ID mappings.")
    }

    /// Levenshtein distance (Swift equivalent to fuzzywuzzy/rapidfuzz)
    static func levenshtein(aStr: String, bStr: String) -> Int {
        let a = Array(aStr)
        let b = Array(bStr)
        var dist = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in 0...a.count { dist[i][0] = i }
        for j in 0...b.count { dist[0][j] = j }
        for i in 1..<(a.count + 1) {
            for j in 1..<(b.count + 1) {
                if a[i-1] == b[j-1] {
                    dist[i][j] = dist[i-1][j-1]
                } else {
                    dist[i][j] = min(
                        dist[i-1][j] + 1,
                        dist[i][j-1] + 1,
                        dist[i-1][j-1] + 1
                    )
                }
            }
        }
        return dist[a.count][b.count]
    }

    /// Checks and caches whether Cricinfo commentary is available for a given matchId
    func isCommentaryAvailable(matchId: String, url: String) async -> Bool {
        if let cached = commentaryAvailabilityCache[matchId] {
            return cached
        }
        // Optionally, load from UserDefaults
        if let saved = UserDefaults.standard.dictionary(forKey: commentaryAvailabilityKey) as? [String: Bool],
           let persisted = saved[matchId] {
            commentaryAvailabilityCache[matchId] = persisted
            return persisted
        }
        // Fetch the page and check for commentary section
        guard let urlObj = URL(string: url) else { return false }
        do {
            let (data, _) = try await URLSession.shared.data(from: urlObj)
            guard let html = String(data: data, encoding: .utf8) else { return false }
            let doc = try SwiftSoup.parse(html)
            // Check for commentary section (e.g., div with class containing 'commentary')
            let commentaryDiv = try doc.select("div.ds-text-tight-l.ds-flex.ds-px-3.ds-py-2").first()
            let available = commentaryDiv != nil
            commentaryAvailabilityCache[matchId] = available
            // Save to UserDefaults
            var saved = UserDefaults.standard.dictionary(forKey: commentaryAvailabilityKey) as? [String: Bool] ?? [:]
            saved[matchId] = available
            UserDefaults.standard.set(saved, forKey: commentaryAvailabilityKey)
            return available
        } catch {
            print("CricInfoScraper: Error checking commentary availability for matchId=\(matchId): \(error)")
            return false
        }
    }

    /// Clears the commentary availability cache (for debugging)
    func clearCommentaryAvailabilityCache() {
        commentaryAvailabilityCache.removeAll()
        UserDefaults.standard.removeObject(forKey: commentaryAvailabilityKey)
        print("CricInfoScraper: Cleared commentary availability cache.")
    }
} 