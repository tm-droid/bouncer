import Foundation
import Charts

struct ProbabilityEvent: Codable, Equatable {
    let timestamp: Date
    let ball: Int
    let over: Int
    let probability: Double
    let eventType: EventType
    let description: String
    let impact: Double  // How much this event changed the probability
    
    enum EventType: String, Codable, Equatable {
        case boundary
        case wicket
        case dotBall
        case runOut
        case powerPlay
        case deathOvers
        case milestone
        case other
    }
    
    static func == (lhs: ProbabilityEvent, rhs: ProbabilityEvent) -> Bool {
        return lhs.timestamp == rhs.timestamp &&
               lhs.ball == rhs.ball &&
               lhs.over == rhs.over &&
               lhs.probability == rhs.probability &&
               lhs.eventType == rhs.eventType &&
               lhs.description == rhs.description &&
               lhs.impact == rhs.impact
    }
}

class ProbabilityTracker {
    private var events: [ProbabilityEvent] = []
    private var currentProbability: Double = 50.0
    private let matchId: String
    private let calculator: WinProbabilityCalculator
    
    init(matchId: String, calculator: WinProbabilityCalculator) {
        self.matchId = matchId
        self.calculator = calculator
    }
    
    func trackEvent(
        ball: Int,
        over: Int,
        runs: Int,
        isWicket: Bool,
        wicketType: String?,
        extras: Int,
        extraType: String?,
        matchSituation: MatchSituation
    ) {
        let oldProbability = currentProbability
        
        // Calculate new probability
        currentProbability = calculator.calculateWinProbability(
            runsNeeded: Int(matchSituation.requiredRunRate * Double(matchSituation.oversRemaining)),
            ballsBowled: ball,
            wicketsLost: 10 - matchSituation.wicketsInHand,
            matchSituation: matchSituation
        )
        
        // Determine event type and description
        let (eventType, description) = determineEventType(
            runs: runs,
            isWicket: isWicket,
            wicketType: wicketType,
            extras: extras,
            extraType: extraType,
            ball: ball
        )
        
        // Calculate impact
        let impact = currentProbability - oldProbability
        
        // Create and store event
        let event = ProbabilityEvent(
            timestamp: Date(),
            ball: ball,
            over: over,
            probability: currentProbability,
            eventType: eventType,
            description: description,
            impact: impact
        )
        
        events.append(event)
    }
    
    private func determineEventType(
        runs: Int,
        isWicket: Bool,
        wicketType: String?,
        extras: Int,
        extraType: String?,
        ball: Int
    ) -> (ProbabilityEvent.EventType, String) {
        if isWicket {
            return (.wicket, "Wicket: \(wicketType ?? "Unknown")")
        } else if runs >= 4 {
            return (.boundary, "\(runs) runs")
        } else if runs == 0 {
            return (.dotBall, "Dot ball")
        } else if ball <= 36 {
            return (.powerPlay, "Power play ball")
        } else if ball >= 240 {
            return (.deathOvers, "Death overs ball")
        } else if runs == 50 || runs == 100 {
            return (.milestone, "\(runs) runs milestone")
        }
        return (.other, "\(runs) runs")
    }
    
    // MARK: - Analysis Methods
    
    func getProbabilityGraph() -> [ProbabilityEvent] {
        return events
    }
    
    func getKeyMoments() -> [ProbabilityEvent] {
        // Return events that had significant impact on the probability
        return events.filter { abs($0.impact) > 5.0 }
            .sorted { abs($0.impact) > abs($1.impact) }
    }
    
    func getPhaseAnalysis() -> String {
        var analysis = ""
        
        // Power Play Analysis
        let powerPlayEvents = events.filter { $0.ball <= 36 }
        let powerPlayImpact = powerPlayEvents.reduce(0.0) { $0 + $1.impact }
        analysis += "Power Play Impact: \(String(format: "%.1f", powerPlayImpact))%\n"
        
        // Middle Overs Analysis
        let middleOversEvents = events.filter { $0.ball > 36 && $0.ball < 240 }
        let middleOversImpact = middleOversEvents.reduce(0.0) { $0 + $1.impact }
        analysis += "Middle Overs Impact: \(String(format: "%.1f", middleOversImpact))%\n"
        
        // Death Overs Analysis
        let deathOversEvents = events.filter { $0.ball >= 240 }
        let deathOversImpact = deathOversEvents.reduce(0.0) { $0 + $1.impact }
        analysis += "Death Overs Impact: \(String(format: "%.1f", deathOversImpact))%\n"
        
        return analysis
    }
    
    func getEventAnalysis() -> String {
        var analysis = ""
        
        // Boundary Analysis
        let boundaryEvents = events.filter { $0.eventType == .boundary }
        let boundaryImpact = boundaryEvents.reduce(0.0) { $0 + $1.impact }
        analysis += "Boundaries Impact: \(String(format: "%.1f", boundaryImpact))%\n"
        
        // Wicket Analysis
        let wicketEvents = events.filter { $0.eventType == .wicket }
        let wicketImpact = wicketEvents.reduce(0.0) { $0 + $1.impact }
        analysis += "Wickets Impact: \(String(format: "%.1f", wicketImpact))%\n"
        
        // Dot Ball Analysis
        let dotBallEvents = events.filter { $0.eventType == .dotBall }
        let dotBallImpact = dotBallEvents.reduce(0.0) { $0 + $1.impact }
        analysis += "Dot Balls Impact: \(String(format: "%.1f", dotBallImpact))%\n"
        
        return analysis
    }
    
    func getMatchSummary() -> String {
        var summary = "Match Probability Analysis\n\n"
        
        // Overall probability change
        if let firstEvent = events.first, let lastEvent = events.last {
            let totalChange = lastEvent.probability - firstEvent.probability
            summary += "Overall Probability Change: \(String(format: "%.1f", totalChange))%\n\n"
        }
        
        // Add phase analysis
        summary += "Phase Analysis:\n"
        summary += getPhaseAnalysis()
        summary += "\n"
        
        // Add event analysis
        summary += "Event Analysis:\n"
        summary += getEventAnalysis()
        summary += "\n"
        
        // Add key moments
        summary += "Key Moments:\n"
        let keyMoments = getKeyMoments()
        let safePrefix = keyMoments.isEmpty ? [] : Array(keyMoments.prefix(5))
        for event in safePrefix {
            summary += "• \(event.description) (Impact: \(String(format: "%.1f", event.impact))%)\n"
        }
        
        return summary
    }
}

// MARK: - Data Caching
extension ProbabilityTracker {
    func saveToCache() {
        let cache = ProbabilityCache(
            events: events,
            timestamp: Date(),
            currentProbability: currentProbability
        )
        
        // Save to UserDefaults for now (could be replaced with CoreData or other storage)
        if let encoded = try? JSONEncoder().encode(cache) {
            UserDefaults.standard.set(encoded, forKey: "probability_cache_\(matchId)")
        }
    }
    
    func loadFromCache() {
        if let data = UserDefaults.standard.data(forKey: "probability_cache_\(matchId)"),
           let cache = try? JSONDecoder().decode(ProbabilityCache.self, from: data) {
            self.events = cache.events
            self.currentProbability = cache.currentProbability
        }
    }
}

struct ProbabilityCache: Codable {
    let events: [ProbabilityEvent]
    let timestamp: Date
    let currentProbability: Double
    
    enum CodingKeys: String, CodingKey {
        case events
        case timestamp
        case currentProbability
    }
    
    init(events: [ProbabilityEvent], timestamp: Date, currentProbability: Double) {
        self.events = events
        self.timestamp = timestamp
        self.currentProbability = currentProbability
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        events = try container.decode([ProbabilityEvent].self, forKey: .events)
        timestamp = try container.decode(Date.self, forKey: .timestamp)
        currentProbability = try container.decode(Double.self, forKey: .currentProbability)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(events, forKey: .events)
        try container.encode(timestamp, forKey: .timestamp)
        try container.encode(currentProbability, forKey: .currentProbability)
    }
}

// Fix the Double to Int conversion error
private func calculateBallNumber(over: Int, ball: Int) -> Int {
    return (over * 6) + ball
} 