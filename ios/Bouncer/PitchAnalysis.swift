import Foundation

// Rename to avoid conflicts
struct PitchAnalysisResult {
    let condition: String
    let behavior: String
    let averageScore: Double
    let averageRunRate: Double
    let averageWicketRate: Double
}

enum MatchPhase {
    case powerPlay
    case middle
    case death
}

extension WinProbabilityCalculator {
    func analyzePitchConditions(match: CricsheetMatch) -> PitchAnalysisResult {
        // Calculate run rates for different phases
        let firstInningsRates = calculatePhaseRunRates(innings: match.innings[0])
        let secondInningsRates = calculatePhaseRunRates(innings: match.innings[1])
        
        // Analyze pitch behavior
        let behavior = analyzePitchBehavior(
            firstInningsRates: firstInningsRates,
            secondInningsRates: secondInningsRates
        )
        
        // Calculate averages
        let averageScore = Double(match.innings.reduce(0) { $0 + $1.total }) / Double(match.innings.count)
        let averageRunRate = match.innings.reduce(0.0) { $0 + $1.runRate } / Double(match.innings.count)
        let averageWicketRate = Double(match.innings.reduce(0) { $0 + $1.wickets }) / Double(match.innings.count * 20)
        
        return PitchAnalysisResult(
            condition: determinePitchCondition(runRate: averageRunRate, wicketRate: averageWicketRate),
            behavior: behavior,
            averageScore: averageScore,
            averageRunRate: averageRunRate,
            averageWicketRate: averageWicketRate
        )
    }
    
    private func calculatePhaseRunRates(innings: CricsheetInnings) -> [MatchPhase: Double] {
        var rates: [MatchPhase: Double] = [:]
        
        // Power Play (first 6 overs)
        let powerPlayOvers = innings.overs.isEmpty ? [] : Array(innings.overs.prefix(6))
        rates[.powerPlay] = calculatePhaseRunRate(overs: powerPlayOvers)
        
        // Middle Overs (7-15)
        let middleOvers = innings.overs.count > 6 ? Array(innings.overs.dropFirst(6).prefix(9)) : []
        rates[.middle] = calculatePhaseRunRate(overs: middleOvers)
        
        // Death Overs (last 5)
        let deathOvers = innings.overs.isEmpty ? [] : Array(innings.overs.suffix(5))
        rates[.death] = calculatePhaseRunRate(overs: deathOvers)
        
        return rates
    }
    
    private func calculatePhaseRunRate(overs: [CricsheetOver]) -> Double {
        let totalRuns = overs.reduce(0) { $0 + $1.runs }
        return Double(totalRuns) / Double(overs.count)
    }
    
    private func analyzePitchBehavior(
        firstInningsRates: [MatchPhase: Double],
        secondInningsRates: [MatchPhase: Double]
    ) -> String {
        var behavior = ""
        
        // Compare power play rates
        if let firstPP = firstInningsRates[.powerPlay],
           let secondPP = secondInningsRates[.powerPlay] {
            let ppDiff = secondPP - firstPP
            if abs(ppDiff) > 1.0 {
                behavior += ppDiff > 0 ? "Pitch eased for batting in power play. " : "Pitch became more difficult in power play. "
            }
        }
        
        // Compare middle overs rates
        if let firstMiddle = firstInningsRates[.middle],
           let secondMiddle = secondInningsRates[.middle] {
            let middleDiff = secondMiddle - firstMiddle
            if abs(middleDiff) > 0.8 {
                behavior += middleDiff > 0 ? "Middle overs became easier. " : "Middle overs became tougher. "
            }
        }
        
        // Compare death overs rates
        if let firstDeath = firstInningsRates[.death],
           let secondDeath = secondInningsRates[.death] {
            let deathDiff = secondDeath - firstDeath
            if abs(deathDiff) > 1.5 {
                behavior += deathDiff > 0 ? "Death overs favored batting. " : "Death overs favored bowling. "
            }
        }
        
        return behavior.isEmpty ? "Pitch remained consistent throughout the match." : behavior
    }
    
    private func determinePitchCondition(runRate: Double, wicketRate: Double) -> String {
        if runRate > 8.0 && wicketRate < 0.2 {
            return "Batting Paradise"
        } else if runRate < 6.0 && wicketRate > 0.4 {
            return "Bowling Friendly"
        } else if runRate > 7.0 {
            return "Good Batting Track"
        } else if wicketRate > 0.3 {
            return "Good Bowling Track"
        } else {
            return "Balanced Pitch"
        }
    }
} 