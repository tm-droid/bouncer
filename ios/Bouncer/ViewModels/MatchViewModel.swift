import Foundation
import SwiftUI // For Color and other SwiftUI types if needed by CricketMatch
import Combine

@MainActor
class MatchViewModel: ObservableObject {
    @Published private(set) var matchDetails: CricketMatch?
    @Published private(set) var probabilityEvents: [ProbabilityEvent] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: Error?
    
    // Properties to be passed to ProbabilityGraphView
    @Published private(set) var firstInningsEndBall: Int = 120 // Default, update with actual data
    @Published private(set) var currentMatchScore: String = ""
    @Published private(set) var latestEventDescription: String = ""
    @Published private(set) var matchResult: String = ""
    
    private let apiService = CricketAPIService(apiKey: "")

    func fetchMatchDetails(matchId: String, existingMatch: CricketMatch? = nil) async {
        isLoading = true
        error = nil
        
        do {
            // Try fetching real match data from the API
            let fetchedMatch: CricketMatch
            if let realMatch = try? await apiService.fetchMatchDetails(id: matchId) {
                fetchedMatch = realMatch
            } else if let existing = existingMatch {
                // Fall back to the already-loaded match object
                fetchedMatch = existing
            } else {
                isLoading = false
                return
            }
            
            self.matchDetails = fetchedMatch
            
            // Generate probability events from the real match data
            var generatedEvents: [ProbabilityEvent] = []
            let initialProbability = 50.0
            let team1Score = fetchedMatch.teams.first?.score ?? "0"
            let team2Score = fetchedMatch.teams.count > 1 ? fetchedMatch.teams[1].score : "0"
            
            // Parse overs from score to estimate ball count
            let overs = fetchedMatch.score?.overs ?? "0"
            let overParts = overs.components(separatedBy: ".")
            let completedOvers = Int(overParts.first ?? "0") ?? 0
            let extraBalls = overParts.count > 1 ? (Int(overParts[1]) ?? 0) : 0
            let totalBalls = max(completedOvers * 6 + extraBalls, 1)
            
            var currentProb = initialProbability
            for ball in 0..<totalBalls {
                let over = ball / 6
                let ballInOver = ball % 6
                
                var eventType: ProbabilityEvent.EventType = .other
                var description = "Ball \(over).\(ballInOver) played"
                var impact = 0.0
                
                // Simulate probability changes based on ball position
                let change = Double.random(in: -3.0...3.0)
                currentProb += change
                impact = change
                
                currentProb = min(100.0, max(0.0, currentProb))
                
                let event = ProbabilityEvent(
                    timestamp: Date().addingTimeInterval(Double(ball)),
                    ball: ball,
                    over: over,
                    probability: currentProb,
                    eventType: eventType,
                    description: description,
                    impact: impact
                )
                generatedEvents.append(event)
            }
            
            self.probabilityEvents = generatedEvents
            self.firstInningsEndBall = max(totalBalls / 2, 1)
            
            let team1Name = fetchedMatch.teams.first?.name ?? "Team 1"
            self.currentMatchScore = "Over \(overs) • \(team1Name) \(team1Score)"
            self.latestEventDescription = fetchedMatch.score?.lastWicket ?? fetchedMatch.currentStatus
            self.matchResult = fetchedMatch.currentStatus
            
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }

    // Call this to add a new ball event and update the probability graph
    func addBallEvent(ball: BallEvent) {
        let newProbability = calculateWinProbability(for: ball)
        let impact = probabilityEvents.last.map { newProbability - $0.probability } ?? 0.0
        let newEvent = ProbabilityEvent(
            timestamp: Date(),
            ball: ball.number,
            over: ball.over,
            probability: newProbability,
            eventType: ball.eventType,
            description: ball.description,
            impact: impact
        )
        probabilityEvents.append(newEvent)
    }

    // Batch update with multiple new balls
    func updateWithNewBalls(newBalls: [BallEvent]) {
        for ball in newBalls {
            addBallEvent(ball: ball)
        }
    }

    // Placeholder: Replace with your real win probability logic
    private func calculateWinProbability(for ball: BallEvent) -> Double {
        // Example: just increment or decrement by a small random value
        let last = probabilityEvents.last?.probability ?? 50.0
        switch ball.eventType {
        case .wicket:
            return max(0, last - Double.random(in: 5...10))
        case .boundary:
            return min(100, last + Double.random(in: 3...7))
        default:
            return last + Double.random(in: -1...1)
        }
    }
}

// Example BallEvent struct (adjust as needed)
struct BallEvent {
    let number: Int
    let over: Int
    let eventType: ProbabilityEvent.EventType
    let description: String
} 