import Foundation

struct TeamStatistics {
    var battingAverage: Double
    var bowlingAverage: Double
    var winRate: Double
    var powerPlayRunRate: Double
    var deathOversRunRate: Double
    var matchesPlayed: Int
}

extension WinProbabilityCalculator {
    func updateTeamBattingStats(_ team: String, _ match: CricsheetMatch) {
        guard let battingInnings = match.innings.first(where: { $0.team == team }) else { return }
        
        let currentStats = teamStats[team] ?? TeamStatistics(
            battingAverage: 0,
            bowlingAverage: 0,
            winRate: 0,
            powerPlayRunRate: 0,
            deathOversRunRate: 0,
            matchesPlayed: 0
        )
        let newMatchesPlayed = currentStats.matchesPlayed + 1
        
        // Calculate power play and death overs stats
        let powerPlayOvers = battingInnings.overs.isEmpty ? [] : Array(battingInnings.overs.prefix(6))
        let deathOvers = battingInnings.overs.isEmpty ? [] : Array(battingInnings.overs.suffix(4))
        
        let powerPlayRuns = powerPlayOvers.reduce(0) { $0 + $1.runs }
        let deathOversRuns = deathOvers.reduce(0) { $0 + $1.runs }
        
        let powerPlayRunRate = Double(powerPlayRuns) / 6.0
        let deathOversRunRate = Double(deathOversRuns) / 4.0
        
        // Update team statistics
        let newBattingAverage = (currentStats.battingAverage * Double(currentStats.matchesPlayed) + Double(battingInnings.total)) / Double(newMatchesPlayed)
        let newPowerPlayRunRate = (currentStats.powerPlayRunRate * Double(currentStats.matchesPlayed) + powerPlayRunRate) / Double(newMatchesPlayed)
        let newDeathOversRunRate = (currentStats.deathOversRunRate * Double(currentStats.matchesPlayed) + deathOversRunRate) / Double(newMatchesPlayed)
        
        teamStats[team] = TeamStatistics(
            battingAverage: newBattingAverage,
            bowlingAverage: currentStats.bowlingAverage, // Updated in updateTeamBowlingStats
            winRate: calculateUpdatedWinRate(team: team, match: match),
            powerPlayRunRate: newPowerPlayRunRate,
            deathOversRunRate: newDeathOversRunRate,
            matchesPlayed: newMatchesPlayed
        )
    }
    
    func updateTeamBowlingStats(_ team: String, _ match: CricsheetMatch) {
        guard let bowlingInnings = match.innings.first(where: { $0.team != team }) else { return }
        
        let currentStats = teamStats[team] ?? TeamStatistics(
            battingAverage: 0,
            bowlingAverage: 0,
            winRate: 0,
            powerPlayRunRate: 0,
            deathOversRunRate: 0,
            matchesPlayed: 0
        )
        let newMatchesPlayed = currentStats.matchesPlayed + 1
        
        // Calculate bowling economy and wicket-taking rate
        let totalRuns = bowlingInnings.total
        _ = bowlingInnings.wickets // This value is not used in current calculations
        let oversBowled = Double(bowlingInnings.overs.count)
        
        let economyRate = Double(totalRuns) / oversBowled
        let newBowlingAverage = (currentStats.bowlingAverage * Double(currentStats.matchesPlayed) + economyRate) / Double(newMatchesPlayed)
        
        // Update team statistics
        teamStats[team] = TeamStatistics(
            battingAverage: currentStats.battingAverage, // Updated in updateTeamBattingStats
            bowlingAverage: newBowlingAverage,
            winRate: currentStats.winRate, // Updated in updateTeamBattingStats
            powerPlayRunRate: currentStats.powerPlayRunRate,
            deathOversRunRate: currentStats.deathOversRunRate,
            matchesPlayed: newMatchesPlayed
        )
    }
    
    private func calculateUpdatedWinRate(team: String, match: CricsheetMatch) -> Double {
        let currentStats = teamStats[team] ?? TeamStatistics(
            battingAverage: 0,
            bowlingAverage: 0,
            winRate: 0,
            powerPlayRunRate: 0,
            deathOversRunRate: 0,
            matchesPlayed: 0
        )
        let isWinner = match.result.winner == team
        let newMatchesPlayed = currentStats.matchesPlayed + 1
        
        return (currentStats.winRate * Double(currentStats.matchesPlayed) + (isWinner ? 1.0 : 0.0)) / Double(newMatchesPlayed)
    }
    
    func analyzeTeamPerformance(_ team: String) -> String {
        guard let stats = teamStats[team] else { return "No data available for team" }
        
        var analysis = ""
        
        // Overall performance
        analysis += "Overall Performance:\n"
        analysis += "• Win Rate: \(Int(stats.winRate * 100))%\n"
        analysis += "• Matches Played: \(stats.matchesPlayed)\n\n"
        
        // Batting analysis
        analysis += "Batting Performance:\n"
        analysis += "• Average Score: \(Int(stats.battingAverage))\n"
        analysis += "• Power Play Run Rate: \(String(format: "%.2f", stats.powerPlayRunRate))\n"
        analysis += "• Death Overs Run Rate: \(String(format: "%.2f", stats.deathOversRunRate))\n\n"
        
        // Bowling analysis
        analysis += "Bowling Performance:\n"
        analysis += "• Economy Rate: \(String(format: "%.2f", stats.bowlingAverage))\n"
        
        // Add phase-specific analysis
        if stats.powerPlayRunRate > 8.0 {
            analysis += "• Strong power play batting\n"
        } else if stats.powerPlayRunRate < 6.0 {
            analysis += "• Struggles in power play\n"
        }
        
        if stats.deathOversRunRate > 9.0 {
            analysis += "• Excellent death overs batting\n"
        } else if stats.deathOversRunRate < 7.0 {
            analysis += "• Struggles in death overs\n"
        }
        
        return analysis
    }
} 