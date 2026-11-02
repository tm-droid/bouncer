import Foundation

class CommentaryScorer {
    // Example keywords for excitement/sentiment
    private let excitementWords = ["six", "four", "wicket", "out!", "amazing", "unbelievable", "incredible", "massive", "huge", "crucial", "fifty", "hundred", "milestone", "record"]
    
    /// Scores a commentary snippet based on length, sentiment, key names, and repetition
    func score(snippet: String, keyNames: [String], recentHistory: [String]) -> Double {
        var score = 0.0
        // Length: prefer 40-180 chars
        let length = snippet.count
        if length > 40 && length < 180 { score += 1.0 } else if length >= 180 { score += 0.5 } else { score -= 0.5 }
        // Sentiment/excitement
        let lower = snippet.lowercased()
        if excitementWords.contains(where: { lower.contains($0) }) { score += 1.0 }
        if snippet.contains("!") { score += 0.5 }
        // Key player/team names
        for name in keyNames { if lower.contains(name.lowercased()) { score += 0.5 } }
        // Repetition: penalize if similar to recent
        for recent in recentHistory.suffix(3) {
            if recent.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == lower.trimmingCharacters(in: .whitespacesAndNewlines) {
                score -= 1.0
            }
        }
        return score
    }
    
    /// Compares two snippets and decides which to use, or if both should be fused by AI
    enum ComparisonResult {
        case useFirst
        case useSecond
        case fuseBoth
    }
    
    func compare(snippet1: String, snippet2: String, keyNames: [String], recentHistory: [String], threshold: Double = 0.5) -> ComparisonResult {
        let score1 = score(snippet: snippet1, keyNames: keyNames, recentHistory: recentHistory)
        let score2 = score(snippet: snippet2, keyNames: keyNames, recentHistory: recentHistory)
        if abs(score1 - score2) < threshold {
            return .fuseBoth
        } else if score1 > score2 {
            return .useFirst
        } else {
            return .useSecond
        }
    }
} 