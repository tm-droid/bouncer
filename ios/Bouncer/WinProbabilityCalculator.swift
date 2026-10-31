import Foundation

// MARK: - Supporting Types
internal enum PitchCondition: Double {
    case battingFriendly = 1.2
    case balanced = 1.0
    case bowlingFriendly = 0.8
}

internal struct PlayerForm: Codable {
    internal let playerName: String
    internal let average: Double
    internal let strikeRate: Double
    internal let recentForm: [Double]
    
    internal var name: String { playerName }
}

internal struct MatchSituation {
    internal let currentRunRate: Double
    internal let requiredRunRate: Double
    internal let oversRemaining: Double
    internal let wicketsInHand: Int
    internal let lastFiveOversRuns: Int
    internal let isPowerPlay: Bool
    internal let isDeathOvers: Bool
}

struct TeamPerformance {
    let team1: String
    let team2: String
    let team1WinRate: Double
    let team2WinRate: Double
    let team1BattingStrength: Double
    let team2BattingStrength: Double
    let team1BowlingStrength: Double
    let team2BowlingStrength: Double
}

class WinProbabilityCalculator {
    // Constants for the game
    private let maxBalls = 300  // For 50 overs
    private let maxWickets = 10
    
    // Match context
    private let requiredRunRate: Double
    private let battingTeamStrength: Double
    private let bowlingTeamStrength: Double
    private let pitchCondition: PitchCondition
    private let battingTeamForm: [PlayerForm]
    private let bowlingTeamForm: [PlayerForm]
    private let historicalData: [String: Double]  // Key-value pairs for historical stats
    
    // Tables to store run and wicket probabilities
    private var runTable: [[Double]]
    private var wicketTable: [[Double]]
    
    // Run probabilities for different shot types (simplified)
    private let runProbabilities: [Int: Double] = [
        0: 0.3,  // Dot ball
        1: 0.25, // Single
        2: 0.15, // Double
        3: 0.05, // Triple
        4: 0.15, // Four
        6: 0.1   // Six
    ]
    
    // Historical data keys
    private enum HistoricalKey: String {
        case averageRunRate = "avg_run_rate"
        case averageWicketRate = "avg_wicket_rate"
        case powerPlayRunRate = "powerplay_run_rate"
        case deathOversRunRate = "death_overs_run_rate"
        case teamVsTeamWinRate = "team_vs_team_win_rate"
    }
    
    // Cricsheet data
    private var historicalMatches: [CricsheetMatch] = []
    private var venueStats: [String: VenueStatistics] = [:]
    public var teamStats: [String: TeamStatistics] = [:]
    private var matchHistory: [CricsheetMatch] = []
    
    // MARK: - Cricsheet Integration
    
    public struct VenueStatistics {
        var averageFirstInningsScore: Double
        var averageRunRate: Double
        var averageWicketRate: Double
        var pitchType: PitchCondition
        var matchesPlayed: Int
    }
    
    public struct TeamStatistics {
        var battingAverage: Double
        var bowlingAverage: Double
        var winRate: Double
        var powerPlayRunRate: Double
        var deathOversRunRate: Double
        var matchesPlayed: Int
    }
    
    init(
        requiredRunRate: Double = 6.0,
        battingTeamStrength: Double = 0.5,
        bowlingTeamStrength: Double = 0.5,
        pitchCondition: PitchCondition = .balanced,
        battingTeamForm: [PlayerForm] = [],
        bowlingTeamForm: [PlayerForm] = [],
        historicalData: [String: Double] = [:]
    ) {
        self.requiredRunRate = requiredRunRate
        self.battingTeamStrength = battingTeamStrength
        self.bowlingTeamStrength = bowlingTeamStrength
        self.pitchCondition = pitchCondition
        self.battingTeamForm = battingTeamForm
        self.bowlingTeamForm = bowlingTeamForm
        self.historicalData = historicalData
        
        // Initialize tables
        runTable = Array(repeating: Array(repeating: 0.0, count: maxWickets + 1), count: maxBalls + 1)
        wicketTable = Array(repeating: Array(repeating: 0.0, count: maxWickets + 1), count: maxBalls + 1)
        
        initializeProbabilityTables()
    }
    
    private func initializeProbabilityTables() {
        for ball in 0...maxBalls {
            for wicket in 0...maxWickets {
                // Calculate base probabilities with all factors
                let baseRunProbability = calculateBaseRunProbability(ball: ball, wicket: wicket)
                let baseWicketProbability = calculateBaseWicketProbability(ball: ball, wicket: wicket)
                
                // Apply pitch condition modifier
                runTable[ball][wicket] = baseRunProbability * pitchCondition.rawValue
                wicketTable[ball][wicket] = baseWicketProbability * (2 - pitchCondition.rawValue)
            }
        }
    }
    
    private func calculateBaseRunProbability(ball: Int, wicket: Int) -> Double {
        var probability = 1.0 + (requiredRunRate - 6.0) * 0.1
        
        // Add team strength factor
        probability += (battingTeamStrength - bowlingTeamStrength) * 0.2
        
        // Add historical data influence from Cricsheet
        if let battingTeamStats = teamStats[battingTeamForm.first?.name ?? ""] {
            probability += (battingTeamStats.battingAverage - 30.0) * 0.02
            probability += (battingTeamStats.powerPlayRunRate - 7.0) * 0.05
        }
        
        // Add player form influence
        let battingFormAverage = battingTeamForm.map { $0.average }.reduce(0, +) / Double(max(1, battingTeamForm.count))
        probability += (battingFormAverage - 0.5) * 0.1
        
        // Add match situation modifiers
        let isPowerPlay = ball < 36
        let isDeathOvers = ball > 240
        
        if isPowerPlay {
            if let battingTeamStats = teamStats[battingTeamForm.first?.name ?? ""] {
                probability *= (battingTeamStats.powerPlayRunRate / 7.0)
            } else {
                probability *= 1.2
            }
        } else if isDeathOvers {
            if let battingTeamStats = teamStats[battingTeamForm.first?.name ?? ""] {
                probability *= (battingTeamStats.deathOversRunRate / 8.0)
            } else {
                probability *= 1.1
            }
        }
        
        return probability
    }
    
    private func calculateBaseWicketProbability(ball: Int, wicket: Int) -> Double {
        var probability = 0.05
        
        // Add pressure factor
        probability += Double(wicket) * 0.01
        
        // Add team strength factor
        probability += (bowlingTeamStrength - battingTeamStrength) * 0.02
        
        // Add historical data influence from Cricsheet
        if let bowlingTeamStats = teamStats[bowlingTeamForm.first?.name ?? ""] {
            probability += (bowlingTeamStats.bowlingAverage - 30.0) * 0.02
        }
        
        // Add player form influence
        let bowlingFormAverage = bowlingTeamForm.map { $0.average }.reduce(0, +) / Double(max(1, bowlingTeamForm.count))
        probability += (bowlingFormAverage - 0.5) * 0.02
        
        // Add match situation modifiers
        let isPowerPlay = ball < 36
        let isDeathOvers = ball > 240
        
        if isPowerPlay {
            probability *= 1.1
        } else if isDeathOvers {
            probability *= 1.2
        }
        
        return probability
    }
    
    private func generateRuns() -> Int {
        let random = Double.random(in: 0...1)
        var cumulativeProbability = 0.0
        
        for (runs, probability) in runProbabilities.sorted(by: { $0.key < $1.key }) {
            cumulativeProbability += probability
            if random <= cumulativeProbability {
                return runs
            }
        }
        
        return 0
    }
    
    private func simulateOneMatch(startBall: Int, startWicket: Int, runsNeeded: Int) -> Bool {
        var currentBall = startBall
        var currentWicket = startWicket
        var remainingRuns = runsNeeded
        
        while currentBall < maxBalls && currentWicket < maxWickets {
            let runs = generateRuns()
            let wicketProbability = wicketTable[currentBall][currentWicket]
            let isWicket = Double.random(in: 0...1) < wicketProbability
            
            remainingRuns -= runs
            currentBall += 1
            if isWicket {
                currentWicket += 1
            }
            
            if remainingRuns <= 0 {
                return true
            }
        }
        
        return false
    }
    
    func calculateWinProbability(
        runsNeeded: Int,
        ballsBowled: Int,
        wicketsLost: Int,
        matchSituation: MatchSituation? = nil,
        simulations: Int = 5000
    ) -> Double {
        var wins = 0
        
        for _ in 0..<simulations {
            if simulateOneMatch(startBall: ballsBowled, startWicket: wicketsLost, runsNeeded: runsNeeded) {
                wins += 1
            }
        }
        
        return Double(wins) / Double(simulations) * 100.0
    }
    
    // Helper method to analyze match situation
    func analyzeMatchSituation(_ situation: MatchSituation) -> String {
        var analysis = ""
        
        // Run rate analysis
        if situation.currentRunRate < situation.requiredRunRate {
            analysis += "Current run rate is below required rate. "
        } else {
            analysis += "Current run rate is above required rate. "
        }
        
        // Wicket analysis
        let wicketsPerOver = Double(situation.wicketsInHand) / situation.oversRemaining
        if wicketsPerOver < 0.5 {
            analysis += "Wicket preservation is crucial. "
        }
        
        // Recent form analysis
        if situation.lastFiveOversRuns > Int(situation.requiredRunRate * 5) {
            analysis += "Batting team is in good form. "
        } else {
            analysis += "Batting team needs to accelerate. "
        }
        
        // Phase analysis
        if situation.isPowerPlay {
            analysis += "Power play phase - aggressive batting expected. "
        } else if situation.isDeathOvers {
            analysis += "Death overs phase - high pressure situation. "
        }
        
        return analysis
    }
    
    func loadCricsheetData(matches: [CricsheetMatch]) {
        self.historicalMatches = matches
        processHistoricalData()
    }
    
    private func processHistoricalData() {
        // Process venue statistics
        for match in historicalMatches {
            updateVenueStats(match)
            updateTeamStats(match)
        }
    }
    
    private func updateVenueStats(_ match: CricsheetMatch) {
        let venue = match.venue
        if venueStats[venue] == nil {
            venueStats[venue] = VenueStatistics(
                averageFirstInningsScore: 0,
                averageRunRate: 0,
                averageWicketRate: 0,
                pitchType: .balanced,
                matchesPlayed: 0
            )
        }
        
        // Update venue statistics
        if let firstInnings = match.innings.first {
            let currentStats = venueStats[venue]!
            let newMatchesPlayed = currentStats.matchesPlayed + 1
            
            venueStats[venue] = VenueStatistics(
                averageFirstInningsScore: (currentStats.averageFirstInningsScore * Double(currentStats.matchesPlayed) + Double(firstInnings.total)) / Double(newMatchesPlayed),
                averageRunRate: (currentStats.averageRunRate * Double(currentStats.matchesPlayed) + firstInnings.runRate) / Double(newMatchesPlayed),
                averageWicketRate: (currentStats.averageWicketRate * Double(currentStats.matchesPlayed) + Double(firstInnings.wickets)) / Double(newMatchesPlayed),
                pitchType: determinePitchType(match),
                matchesPlayed: newMatchesPlayed
            )
        }
    }
    
    private func updateTeamStats(_ match: CricsheetMatch) {
        let battingTeam = match.teams.batting
        let bowlingTeam = match.teams.bowling
        
        // Initialize team stats if needed
        if teamStats[battingTeam] == nil {
            teamStats[battingTeam] = TeamStatistics(
                battingAverage: 0,
                bowlingAverage: 0,
                winRate: 0,
                powerPlayRunRate: 0,
                deathOversRunRate: 0,
                matchesPlayed: 0
            )
        }
        if teamStats[bowlingTeam] == nil {
            teamStats[bowlingTeam] = teamStats[battingTeam]!
        }
        
        // Update team statistics
        updateTeamBattingStats(match: match)
        updateTeamBowlingStats(match: match)
    }
    
    private func determinePitchType(_ match: CricsheetMatch) -> PitchCondition {
        // Analyze match data to determine pitch type
        let firstInnings = match.innings.first!
        let secondInnings = match.innings.last!
        
        let firstInningsRunRate = firstInnings.runRate
        let secondInningsRunRate = secondInnings.runRate
        
        // If second innings run rate is significantly higher, it's a batting-friendly pitch
        if secondInningsRunRate > firstInningsRunRate * 1.2 {
            return .battingFriendly
        }
        // If second innings run rate is significantly lower, it's a bowling-friendly pitch
        else if secondInningsRunRate < firstInningsRunRate * 0.8 {
            return .bowlingFriendly
        }
        return .balanced
    }
    
    private func getTeamStats(_ team: String) -> TeamStatistics {
        if let stats = teamStats[team] {
            return stats
        }
        let newStats = TeamStatistics(
            battingAverage: 0,
            bowlingAverage: 0,
            winRate: 0,
            powerPlayRunRate: 0,
            deathOversRunRate: 0,
            matchesPlayed: 0
        )
        teamStats[team] = newStats
        return newStats
    }
    
    private func calculateUpdatedWinRate(match: CricsheetMatch) -> Double {
        let battingTeam = match.teams.batting
        let bowlingTeam = match.teams.bowling
        
        // Get historical data for both teams
        let battingTeamStats = getTeamStats(battingTeam)
        let bowlingTeamStats = getTeamStats(bowlingTeam)
        
        // Calculate win probability based on historical performance
        let battingTeamWinRate = battingTeamStats.winRate
        let bowlingTeamWinRate = bowlingTeamStats.winRate
        
        // Normalize win rates to get probability
        let totalWinRate = battingTeamWinRate + bowlingTeamWinRate
        return totalWinRate > 0 ? battingTeamWinRate / totalWinRate : 0.5
    }
    
    private func analyzeTeamPerformance(match: CricsheetMatch) -> TeamPerformance {
        let battingTeam = match.teams.batting
        let bowlingTeam = match.teams.bowling
        
        // Get historical data for both teams
        let battingTeamStats = getTeamStats(battingTeam)
        let bowlingTeamStats = getTeamStats(bowlingTeam)
        
        return TeamPerformance(
            team1: battingTeam,
            team2: bowlingTeam,
            team1WinRate: battingTeamStats.winRate,
            team2WinRate: bowlingTeamStats.winRate,
            team1BattingStrength: battingTeamStats.battingAverage,
            team2BattingStrength: bowlingTeamStats.battingAverage,
            team1BowlingStrength: battingTeamStats.bowlingAverage,
            team2BowlingStrength: bowlingTeamStats.bowlingAverage
        )
    }
    
    private func updateTeamBattingStats(match: CricsheetMatch) {
        let battingTeam = match.teams.batting
        let bowlingTeam = match.teams.bowling
        
        var battingTeamStats = getTeamStats(battingTeam)
        var bowlingTeamStats = getTeamStats(bowlingTeam)
        
        // Update batting stats based on match data
        for innings in match.innings {
            if innings.team == battingTeam {
                let runs = innings.overs.reduce(0) { $0 + $1.runs }
                let _ = innings.overs.reduce(0) { $0 + $1.wickets }
                let newMatchesPlayed = battingTeamStats.matchesPlayed + 1
                battingTeamStats = TeamStatistics(
                    battingAverage: (battingTeamStats.battingAverage * Double(battingTeamStats.matchesPlayed) + Double(runs)) / Double(newMatchesPlayed),
                    bowlingAverage: battingTeamStats.bowlingAverage,
                    winRate: battingTeamStats.winRate,
                    powerPlayRunRate: battingTeamStats.powerPlayRunRate,
                    deathOversRunRate: battingTeamStats.deathOversRunRate,
                    matchesPlayed: newMatchesPlayed
                )
            } else if innings.team == bowlingTeam {
                let runs = innings.overs.reduce(0) { $0 + $1.runs }
                let _ = innings.overs.reduce(0) { $0 + $1.wickets }
                let newMatchesPlayed = bowlingTeamStats.matchesPlayed + 1
                bowlingTeamStats = TeamStatistics(
                    battingAverage: (bowlingTeamStats.battingAverage * Double(bowlingTeamStats.matchesPlayed) + Double(runs)) / Double(newMatchesPlayed),
                    bowlingAverage: bowlingTeamStats.bowlingAverage,
                    winRate: bowlingTeamStats.winRate,
                    powerPlayRunRate: bowlingTeamStats.powerPlayRunRate,
                    deathOversRunRate: bowlingTeamStats.deathOversRunRate,
                    matchesPlayed: newMatchesPlayed
                )
            }
        }
        
        teamStats[battingTeam] = battingTeamStats
        teamStats[bowlingTeam] = bowlingTeamStats
    }
    
    private func updateTeamBowlingStats(match: CricsheetMatch) {
        let battingTeam = match.teams.batting
        let bowlingTeam = match.teams.bowling
        
        var battingTeamStats = getTeamStats(battingTeam)
        var bowlingTeamStats = getTeamStats(bowlingTeam)
        
        // Update bowling stats based on match data
        for innings in match.innings {
            if innings.team == battingTeam {
                let runs = innings.overs.reduce(0) { $0 + $1.runs }
                let _ = innings.overs.reduce(0) { $0 + $1.wickets }
                let newMatchesPlayed = bowlingTeamStats.matchesPlayed + 1
                bowlingTeamStats = TeamStatistics(
                    battingAverage: bowlingTeamStats.battingAverage,
                    bowlingAverage: (bowlingTeamStats.bowlingAverage * Double(bowlingTeamStats.matchesPlayed) + Double(runs)) / Double(newMatchesPlayed),
                    winRate: bowlingTeamStats.winRate,
                    powerPlayRunRate: bowlingTeamStats.powerPlayRunRate,
                    deathOversRunRate: bowlingTeamStats.deathOversRunRate,
                    matchesPlayed: newMatchesPlayed
                )
            } else if innings.team == bowlingTeam {
                let runs = innings.overs.reduce(0) { $0 + $1.runs }
                let _ = innings.overs.reduce(0) { $0 + $1.wickets }
                let newMatchesPlayed = battingTeamStats.matchesPlayed + 1
                battingTeamStats = TeamStatistics(
                    battingAverage: battingTeamStats.battingAverage,
                    bowlingAverage: (battingTeamStats.bowlingAverage * Double(battingTeamStats.matchesPlayed) + Double(runs)) / Double(newMatchesPlayed),
                    winRate: battingTeamStats.winRate,
                    powerPlayRunRate: battingTeamStats.powerPlayRunRate,
                    deathOversRunRate: battingTeamStats.deathOversRunRate,
                    matchesPlayed: newMatchesPlayed
                )
            }
        }
        
        teamStats[battingTeam] = battingTeamStats
        teamStats[bowlingTeam] = bowlingTeamStats
    }
    
    func updateMatchStats(match: CricsheetMatch) {
        updateTeamBattingStats(match: match)
        updateTeamBowlingStats(match: match)
    }
    
    private func calculateTimeRemaining(match: CricsheetMatch) -> Double {
        let currentTime = Date()
        let matchEndTime = match.date.addingTimeInterval(8 * 3600) // Assuming 8 hours for a match
        let timeRemaining = matchEndTime.timeIntervalSince(currentTime)
        return max(0, timeRemaining)
    }
}

class TeamStats {
    var matchesPlayed: Int = 0
    var wins: Int = 0
    var totalRuns: Int = 0
    var totalWickets: Int = 0
    var totalOvers: Double = 0
    var totalRunsConceded: Int = 0
    var totalWicketsTaken: Int = 0
    var battingAverage: Double = 0.0
    var bowlingAverage: Double = 0.0
    var powerPlayRunRate: Double = 0.0
    var deathOversRunRate: Double = 0.0
    
    var winRate: Double {
        return matchesPlayed > 0 ? Double(wins) / Double(matchesPlayed) : 0.5
    }
    
    var battingStrength: Double {
        return matchesPlayed > 0 ? Double(totalRuns) / Double(matchesPlayed) : 0.0
    }
    
    var bowlingStrength: Double {
        return matchesPlayed > 0 ? Double(totalWicketsTaken) / Double(matchesPlayed) : 0.0
    }
    
    func updateBattingStats(runs: Int, wickets: Int) {
        totalRuns += runs
        totalWickets += wickets
        matchesPlayed += 1
        battingAverage = Double(totalRuns) / Double(matchesPlayed)
    }
    
    func updateBowlingStats(runs: Int, wickets: Int) {
        totalRunsConceded += runs
        totalWicketsTaken += wickets
        matchesPlayed += 1
        bowlingAverage = Double(totalRunsConceded) / Double(matchesPlayed)
    }
}

// MARK: - Enhanced Analysis
extension WinProbabilityCalculator {
    func loadCricsheetMatches() async throws {
        let fetcher = CricsheetDataFetcher()
        
        // Download data for major tournaments
        let tournaments = ["ipl", "bbl", "cpl", "psl"]
        let currentYear = Calendar.current.component(.year, from: Date())
        
        for tournament in tournaments {
            for year in (currentYear-5)...currentYear {
                do {
                    let matches = try await fetcher.downloadMatchData(for: tournament, year: year)
                    loadCricsheetData(matches: matches)
                } catch {
                    print("Failed to load \(tournament) \(year): \(error)")
                }
            }
        }
    }
    
    // Enhanced pitch analysis
    private func analyzePitchConditions(_ match: CricsheetMatch) -> PitchAnalysis {
        let firstInnings = match.innings[0]
        let secondInnings = match.innings[1]
        
        // Calculate run rates for different phases
        let firstInningsPowerPlay = calculatePhaseRunRate(firstInnings, phase: .powerPlay)
        let firstInningsMiddle = calculatePhaseRunRate(firstInnings, phase: .middle)
        let firstInningsDeath = calculatePhaseRunRate(firstInnings, phase: .death)
        
        let secondInningsPowerPlay = calculatePhaseRunRate(secondInnings, phase: .powerPlay)
        let secondInningsMiddle = calculatePhaseRunRate(secondInnings, phase: .middle)
        let secondInningsDeath = calculatePhaseRunRate(secondInnings, phase: .death)
        
        // Analyze pitch behavior
        let pitchBehavior = analyzePitchBehavior(
            firstInnings: (powerPlay: firstInningsPowerPlay, middle: firstInningsMiddle, death: firstInningsDeath),
            secondInnings: (powerPlay: secondInningsPowerPlay, middle: secondInningsMiddle, death: secondInningsDeath)
        )
        
        return PitchAnalysis(
            condition: determinePitchType(match),
            behavior: pitchBehavior,
            averageScore: Double(firstInnings.total + secondInnings.total) / 2.0,
            averageRunRate: (firstInnings.runRate + secondInnings.runRate) / 2.0,
            averageWicketRate: Double(firstInnings.wickets + secondInnings.wickets) / 2.0
        )
    }
    
    private enum MatchPhase {
        case powerPlay
        case middle
        case death
    }
    
    private func calculatePhaseRunRate(_ innings: CricsheetInnings, phase: MatchPhase) -> Double {
        let overs: [CricsheetOver]
        switch phase {
        case .powerPlay:
            overs = innings.overs.isEmpty ? [] : Array(innings.overs.prefix(6))
        case .middle:
            overs = Array(innings.overs.dropFirst(6).dropLast(4))
        case .death:
            overs = innings.overs.isEmpty ? [] : Array(innings.overs.suffix(4))
        }
        
        let runs = overs.reduce(0) { $0 + $1.runs }
        return Double(runs) / Double(overs.count)
    }
    
    private func analyzePitchBehavior(
        firstInnings: (powerPlay: Double, middle: Double, death: Double),
        secondInnings: (powerPlay: Double, middle: Double, death: Double)
    ) -> String {
        var behavior = ""
        
        // Analyze power play
        if secondInnings.powerPlay > firstInnings.powerPlay * 1.2 {
            behavior += "Pitch eases up during power play. "
        } else if secondInnings.powerPlay < firstInnings.powerPlay * 0.8 {
            behavior += "Pitch becomes difficult during power play. "
        }
        
        // Analyze middle overs
        if secondInnings.middle > firstInnings.middle * 1.2 {
            behavior += "Middle overs become easier to bat. "
        } else if secondInnings.middle < firstInnings.middle * 0.8 {
            behavior += "Middle overs become more challenging. "
        }
        
        // Analyze death overs
        if secondInnings.death > firstInnings.death * 1.2 {
            behavior += "Death overs favor batting. "
        } else if secondInnings.death < firstInnings.death * 0.8 {
            behavior += "Death overs favor bowling. "
        }
        
        return behavior
    }
}

struct PitchAnalysis {
    let condition: PitchCondition
    let behavior: String
    let averageScore: Double
    let averageRunRate: Double
    let averageWicketRate: Double
} 