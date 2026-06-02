package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.ml.FeatureExtractor
import com.tanush.bouncer.analysis.ml.ModelTrainer
import com.tanush.bouncer.analysis.ml.PredictionOutput
import com.tanush.bouncer.analysis.ml.ScorePredictionModel
import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import kotlin.math.max
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PitchCondition(val value: Double) {
    BATTING_FRIENDLY(1.2),
    BALANCED(1.0),
    BOWLING_FRIENDLY(0.8)
}

data class PlayerForm(
    val playerName: String,
    val average: Double,
    val strikeRate: Double,
    val recentForm: List<Double>
) {
    val name: String get() = playerName
}

data class MatchSituation(
    val currentRunRate: Double,
    val requiredRunRate: Double,
    val oversRemaining: Double,
    val wicketsInHand: Int,
    val lastFiveOversRuns: Int,
    val isPowerPlay: Boolean,
    val isDeathOvers: Boolean,
    val lastTwoOversRuns: Int = 0
)

data class TeamPerformance(
    val team1: String,
    val team2: String,
    val team1WinRate: Double,
    val team2WinRate: Double,
    val team1BattingStrength: Double,
    val team2BattingStrength: Double,
    val team1BowlingStrength: Double,
    val team2BowlingStrength: Double
)

data class TeamStatistics(
    val battingAverage: Double = 0.0,
    val bowlingAverage: Double = 0.0,
    val winRate: Double = 0.0,
    val powerPlayRunRate: Double = 0.0,
    val deathOversRunRate: Double = 0.0,
    val matchesPlayed: Int = 0
)

class WinProbabilityCalculator(
    private val requiredRunRate: Double = 6.0,
    private val battingTeamStrength: Double = 0.5,
    private val bowlingTeamStrength: Double = 0.5,
    private val pitchCondition: PitchCondition = PitchCondition.BALANCED,
    private val battingTeamForm: List<PlayerForm> = emptyList(),
    private val bowlingTeamForm: List<PlayerForm> = emptyList(),
    private val historicalData: Map<String, Double> = emptyMap(),
    /** Total balls in one innings: T20=120, ODI=300, Test=450. Defaults to 300 (ODI). */
    val totalBalls: Int = 300
) {
    // Constants for the game — use totalBalls so T20/ODI/Test are modelled correctly
    private val maxBalls = totalBalls
    private val maxWickets = 10

    // Tables to store run and wicket probabilities
    private var runTable: Array<DoubleArray>
    private var wicketTable: Array<DoubleArray>

    // Run probabilities for different shot types (simplified), pre-sorted by key to avoid
    // per-ball sort in the hot Monte Carlo loop.
    private val sortedRunProbabilities: List<Map.Entry<Int, Double>> = mapOf(
        0 to 0.3,  // Dot ball
        1 to 0.25, // Single
        2 to 0.15, // Double
        3 to 0.05, // Triple
        4 to 0.15, // Four
        6 to 0.1   // Six
    ).entries.sortedBy { it.key }

    // Cricsheet data
    private var historicalMatches: List<CricsheetMatch> = emptyList()
    private var venueStats: MutableMap<String, VenueStatistics> = mutableMapOf()
    var teamStats: MutableMap<String, TeamStatistics> = mutableMapOf()

    data class VenueStatistics(
        val averageFirstInningsScore: Double,
        val averageRunRate: Double,
        val averageWicketRate: Double,
        val pitchType: PitchCondition,
        val matchesPlayed: Int
    )

    init {
        // Initialize tables
        runTable = Array(maxBalls + 1) { DoubleArray(maxWickets + 1) }
        wicketTable = Array(maxBalls + 1) { DoubleArray(maxWickets + 1) }

        initializeProbabilityTables()
    }

    private fun initializeProbabilityTables() {
        try {
            for (ball in 0..maxBalls) {
                for (wicket in 0..maxWickets) {
                    // Calculate base probabilities with all factors
                    val baseRunProbability = calculateBaseRunProbability(ball, wicket)
                    val baseWicketProbability = calculateBaseWicketProbability(ball, wicket)

                    // Apply pitch condition modifier; clamp wicket probability to [0,1]
                    runTable[ball][wicket] = baseRunProbability * pitchCondition.value
                    wicketTable[ball][wicket] = (baseWicketProbability * (2 - pitchCondition.value)).coerceIn(0.0, 1.0)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun calculateBaseRunProbability(ball: Int, wicket: Int): Double {
        try {
            var probability = 1.0 + (requiredRunRate - 6.0) * 0.1

            // Add team strength factor
            probability += (battingTeamStrength - bowlingTeamStrength) * 0.2

            // Add historical data influence from Cricsheet
            val battingTeamName = battingTeamForm.firstOrNull()?.name ?: ""
            teamStats[battingTeamName]?.let { battingTeamStats ->
                probability += (battingTeamStats.battingAverage - 30.0) * 0.02
                probability += (battingTeamStats.powerPlayRunRate - 7.0) * 0.05
            }

            // Add player form influence
            val battingFormAverage = if (battingTeamForm.isNotEmpty()) {
                battingTeamForm.sumOf { it.average } / battingTeamForm.size
            } else {
                0.0
            }
            probability += (battingFormAverage - 0.5) * 0.1

            // Add match situation modifiers (death overs = last 20% of innings)
            val isPowerPlay = ball < 36
            val isDeathOvers = ball > (maxBalls * 0.8).toInt()

            if (isPowerPlay) {
                val battingTeamStats = teamStats[battingTeamName]
                if (battingTeamStats != null) {
                    probability *= (battingTeamStats.powerPlayRunRate / 7.0)
                } else {
                    probability *= 1.2
                }
            } else if (isDeathOvers) {
                val battingTeamStats = teamStats[battingTeamName]
                if (battingTeamStats != null) {
                    probability *= (battingTeamStats.deathOversRunRate / 8.0)
                } else {
                    probability *= 1.1
                }
            }

            return probability
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun calculateBaseWicketProbability(ball: Int, wicket: Int): Double {
        try {
            var probability = 0.05

            // Add pressure factor
            probability += wicket * 0.01

            // Add team strength factor
            probability += (bowlingTeamStrength - battingTeamStrength) * 0.02

            // Add historical data influence from Cricsheet
            val bowlingTeamName = bowlingTeamForm.firstOrNull()?.name ?: ""
            teamStats[bowlingTeamName]?.let { bowlingTeamStats ->
                probability += (bowlingTeamStats.bowlingAverage - 30.0) * 0.02
            }

            // Add player form influence
            val bowlingFormAverage = if (bowlingTeamForm.isNotEmpty()) {
                bowlingTeamForm.sumOf { it.average } / bowlingTeamForm.size
            } else {
                0.0
            }
            probability += (bowlingFormAverage - 0.5) * 0.02

            // Add match situation modifiers (death overs = last 20% of innings)
            val isPowerPlay = ball < 36
            val isDeathOvers = ball > (maxBalls * 0.8).toInt()

            if (isPowerPlay) {
                probability *= 1.1
            } else if (isDeathOvers) {
                probability *= 1.2
            }

            // Clamp wicket probability to valid range [0,1]
            return probability.coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun generateRuns(): Int {
        try {
            val random = Math.random()
            var cumulativeProbability = 0.0

            // sortedRunProbabilities is pre-sorted once at construction — no per-ball sort
            for ((runs, probability) in sortedRunProbabilities) {
                cumulativeProbability += probability
                if (random <= cumulativeProbability) {
                    return runs
                }
            }

            return 0
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun simulateOneMatch(startBall: Int, startWicket: Int, runsNeeded: Int): Boolean {
        try {
            var currentBall = startBall
            var currentWicket = startWicket
            var remainingRuns = runsNeeded

            while (currentBall < maxBalls && currentWicket < maxWickets) {
                val runs = generateRuns()
                val wicketProbability = wicketTable[currentBall][currentWicket]
                val isWicket = Math.random() < wicketProbability

                remainingRuns -= runs
                currentBall += 1
                if (isWicket) {
                    currentWicket += 1
                }

                if (remainingRuns <= 0) {
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Calculate win probability via Monte Carlo simulation.
     *
     * Callers MUST invoke this from a background coroutine (Dispatchers.Default or IO).
     * The function wraps its body in [withContext(Dispatchers.Default)] as a safety net,
     * so suspend-callers don't accidentally block the main thread.
     *
     * Returns a percentage in [0.0, 100.0].
     */
    suspend fun calculateWinProbability(
        runsNeeded: Int,
        ballsRemaining: Int,
        wicketsLost: Int,
        currentRunRate: Double,
        requiredRunRate: Double
    ): Double = withContext(Dispatchers.Default) {
        // Short-circuit: known terminal states
        if (wicketsLost >= maxWickets) return@withContext 0.0
        if (runsNeeded <= 0) return@withContext 100.0
        val safeBallsRemaining = ballsRemaining.coerceIn(0, maxBalls)
        if (safeBallsRemaining <= 0) return@withContext 0.0

        val ballsBowled = (maxBalls - safeBallsRemaining).coerceAtLeast(0)
        var wins = 0
        val simulations = (safeBallsRemaining * 150).coerceIn(500, 8_000)

        for (i in 0 until simulations) {
            if (simulateOneMatch(ballsBowled, wicketsLost, runsNeeded)) {
                wins += 1
            }
        }

        (wins.toDouble() / simulations * 100.0).coerceIn(0.0, 100.0)
    }

    suspend fun calculateSimpleWinProbability(
        runsNeeded: Int,
        ballsRemaining: Int,
        wicketsInHand: Int
    ): Double {
        val wicketsLost = 10 - wicketsInHand
        val oversRemaining = max(ballsRemaining / 6.0, 0.1)
        val reqRR = runsNeeded / oversRemaining

        return calculateWinProbability(
            runsNeeded = runsNeeded,
            ballsRemaining = ballsRemaining,
            wicketsLost = wicketsLost,
            currentRunRate = 6.0, // Default fallback
            requiredRunRate = reqRR
        )
    }

    fun analyzeMatchSituation(situation: MatchSituation): String {
        try {
            var analysis = ""

            // Run rate analysis
            if (situation.currentRunRate < situation.requiredRunRate) {
                analysis += "Current run rate is below required rate. "
            } else {
                analysis += "Current run rate is above required rate. "
            }

            // Wicket analysis
            val wicketsPerOver = situation.wicketsInHand.toDouble() / situation.oversRemaining
            if (wicketsPerOver < 0.5) {
                analysis += "Wicket preservation is crucial. "
            }

            // Recent form analysis
            if (situation.lastFiveOversRuns > (situation.requiredRunRate * 5).toInt()) {
                analysis += "Batting team is in good form. "
            } else {
                analysis += "Batting team needs to accelerate. "
            }

            // Phase analysis
            if (situation.isPowerPlay) {
                analysis += "Power play phase - aggressive batting expected. "
            } else if (situation.isDeathOvers) {
                analysis += "Death overs phase - high pressure situation. "
            }

            return analysis
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun loadCricsheetData(matches: List<CricsheetMatch>) {
        try {
            this.historicalMatches = matches
            processHistoricalData()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun processHistoricalData() {
        try {
            // Process venue statistics
            for (match in historicalMatches) {
                updateVenueStats(match)
                updateTeamStats(match)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun updateVenueStats(match: CricsheetMatch) {
        try {
            val venue = match.venue
            if (!venueStats.containsKey(venue)) {
                venueStats[venue] = VenueStatistics(
                    averageFirstInningsScore = 0.0,
                    averageRunRate = 0.0,
                    averageWicketRate = 0.0,
                    pitchType = PitchCondition.BALANCED,
                    matchesPlayed = 0
                )
            }

            // Update venue statistics
            match.innings.firstOrNull()?.let { firstInnings ->
                val currentStats = venueStats[venue] ?: return@let
                val newMatchesPlayed = currentStats.matchesPlayed + 1

                venueStats[venue] = VenueStatistics(
                    averageFirstInningsScore = (currentStats.averageFirstInningsScore * currentStats.matchesPlayed + firstInnings.total) / newMatchesPlayed,
                    averageRunRate = (currentStats.averageRunRate * currentStats.matchesPlayed + firstInnings.runRate) / newMatchesPlayed,
                    averageWicketRate = (currentStats.averageWicketRate * currentStats.matchesPlayed + firstInnings.wickets) / newMatchesPlayed,
                    pitchType = determinePitchType(match),
                    matchesPlayed = newMatchesPlayed
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun updateTeamStats(match: CricsheetMatch) {
        try {
            val battingTeam = match.teams.batting
            val bowlingTeam = match.teams.bowling

            // Initialize team stats if needed
            if (!teamStats.containsKey(battingTeam)) {
                teamStats[battingTeam] = TeamStatistics(
                    battingAverage = 0.0,
                    bowlingAverage = 0.0,
                    winRate = 0.0,
                    powerPlayRunRate = 0.0,
                    deathOversRunRate = 0.0,
                    matchesPlayed = 0
                )
            }
            if (!teamStats.containsKey(bowlingTeam)) {
                teamStats[bowlingTeam] = teamStats[battingTeam] ?: TeamStatistics(
                    battingAverage = 0.0,
                    bowlingAverage = 0.0,
                    winRate = 0.0,
                    powerPlayRunRate = 0.0,
                    deathOversRunRate = 0.0,
                    matchesPlayed = 0
                )
            }

            // Update team statistics
            updateTeamBattingStats(match)
            updateTeamBowlingStats(match)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun determinePitchType(match: CricsheetMatch): PitchCondition {
        try {
            // Analyze match data to determine pitch type
            val firstInnings = match.innings.firstOrNull() ?: return PitchCondition.BALANCED
            val secondInnings = match.innings.lastOrNull() ?: return PitchCondition.BALANCED

            val firstInningsRunRate = firstInnings.runRate
            val secondInningsRunRate = secondInnings.runRate

            // If second innings run rate is significantly higher, it's a batting-friendly pitch
            if (secondInningsRunRate > firstInningsRunRate * 1.2) {
                return PitchCondition.BATTING_FRIENDLY
            }
            // If second innings run rate is significantly lower, it's a bowling-friendly pitch
            else if (secondInningsRunRate < firstInningsRunRate * 0.8) {
                return PitchCondition.BOWLING_FRIENDLY
            }
            return PitchCondition.BALANCED
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun getTeamStats(team: String): TeamStatistics {
        try {
            return teamStats[team] ?: run {
                val newStats = TeamStatistics(
                    battingAverage = 0.0,
                    bowlingAverage = 0.0,
                    winRate = 0.0,
                    powerPlayRunRate = 0.0,
                    deathOversRunRate = 0.0,
                    matchesPlayed = 0
                )
                teamStats[team] = newStats
                newStats
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun updateTeamBattingStats(match: CricsheetMatch) {
        try {
            val battingTeam = match.teams.batting
            val bowlingTeam = match.teams.bowling

            var battingTeamStats = getTeamStats(battingTeam)
            var bowlingTeamStats = getTeamStats(bowlingTeam)

            // Update batting stats based on match data
            for (innings in match.innings) {
                if (innings.team == battingTeam) {
                    val runs = innings.overs.sumOf { it.runs }
                    val newMatchesPlayed = battingTeamStats.matchesPlayed + 1
                    battingTeamStats = battingTeamStats.copy(
                        battingAverage = (battingTeamStats.battingAverage * battingTeamStats.matchesPlayed + runs) / newMatchesPlayed,
                        matchesPlayed = newMatchesPlayed
                    )
                } else if (innings.team == bowlingTeam) {
                    val runs = innings.overs.sumOf { it.runs }
                    val newMatchesPlayed = bowlingTeamStats.matchesPlayed + 1
                    bowlingTeamStats = bowlingTeamStats.copy(
                        battingAverage = (bowlingTeamStats.battingAverage * bowlingTeamStats.matchesPlayed + runs) / newMatchesPlayed,
                        matchesPlayed = newMatchesPlayed
                    )
                }
            }

            teamStats[battingTeam] = battingTeamStats
            teamStats[bowlingTeam] = bowlingTeamStats
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun updateTeamBowlingStats(match: CricsheetMatch) {
        try {
            val battingTeam = match.teams.batting
            val bowlingTeam = match.teams.bowling

            var battingTeamStats = getTeamStats(battingTeam)
            var bowlingTeamStats = getTeamStats(bowlingTeam)

            // Update bowling stats based on match data
            for (innings in match.innings) {
                if (innings.team == battingTeam) {
                    val runs = innings.overs.sumOf { it.runs }
                    val newMatchesPlayed = bowlingTeamStats.matchesPlayed + 1
                    bowlingTeamStats = bowlingTeamStats.copy(
                        bowlingAverage = (bowlingTeamStats.bowlingAverage * bowlingTeamStats.matchesPlayed + runs) / newMatchesPlayed,
                        matchesPlayed = newMatchesPlayed
                    )
                } else if (innings.team == bowlingTeam) {
                    val runs = innings.overs.sumOf { it.runs }
                    val newMatchesPlayed = battingTeamStats.matchesPlayed + 1
                    battingTeamStats = battingTeamStats.copy(
                        bowlingAverage = (battingTeamStats.bowlingAverage * battingTeamStats.matchesPlayed + runs) / newMatchesPlayed,
                        matchesPlayed = newMatchesPlayed
                    )
                }
            }

            teamStats[battingTeam] = battingTeamStats
            teamStats[bowlingTeam] = bowlingTeamStats
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun calculateUpdatedWinRate(match: CricsheetMatch): Double {
        try {
            val battingTeam = match.teams.batting
            val bowlingTeam = match.teams.bowling

            // Get historical data for both teams
            val battingTeamStats = getTeamStats(battingTeam)
            val bowlingTeamStats = getTeamStats(bowlingTeam)

            // Calculate win probability based on historical performance
            val battingTeamWinRate = battingTeamStats.winRate
            val bowlingTeamWinRate = bowlingTeamStats.winRate

            // Normalize win rates to get probability
            val totalWinRate = battingTeamWinRate + bowlingTeamWinRate
            return if (totalWinRate > 0) battingTeamWinRate / totalWinRate else 0.5
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun analyzeTeamPerformance(match: CricsheetMatch): TeamPerformance {
        try {
            val battingTeam = match.teams.batting
            val bowlingTeam = match.teams.bowling

            // Get historical data for both teams
            val battingTeamStats = getTeamStats(battingTeam)
            val bowlingTeamStats = getTeamStats(bowlingTeam)

            return TeamPerformance(
                team1 = battingTeam,
                team2 = bowlingTeam,
                team1WinRate = battingTeamStats.winRate,
                team2WinRate = bowlingTeamStats.winRate,
                team1BattingStrength = battingTeamStats.battingAverage,
                team2BattingStrength = bowlingTeamStats.battingAverage,
                team1BowlingStrength = battingTeamStats.bowlingAverage,
                team2BowlingStrength = bowlingTeamStats.bowlingAverage
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ML-Enhanced Predictions
    // ═══════════════════════════════════════════════════════════════

    /** The ML model for neural network-based predictions (optional enhancement). */
    private var mlModel: ScorePredictionModel? = null
    private var mlModelTrained: Boolean = false

    /**
     * Set a trained ML model for enhanced predictions.
     * When set, [calculateMLWinProbability] uses the neural network instead
     * of Monte Carlo simulation.
     */
    fun setMLModel(model: ScorePredictionModel, trained: Boolean = true) {
        try {
            mlModel = model
            mlModelTrained = trained
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Calculate win probability using the trained ML model.
     *
     * Falls back to Monte Carlo simulation if no ML model is available.
     *
     * @param runsNeeded Runs still required
     * @param ballsRemaining Balls left in the innings
     * @param wicketsLost Wickets fallen
     * @param currentRunRate Current scoring rate
     * @param requiredRunRate Required scoring rate
     * @param battingTeam Name of the batting team (for historical lookup)
     * @param bowlingTeam Name of the bowling team (for historical lookup)
     * @return Win probability as percentage (0-100)
     */
    suspend fun calculateMLWinProbability(
        runsNeeded: Int,
        ballsRemaining: Int,
        wicketsLost: Int,
        currentRunRate: Double,
        requiredRunRate: Double,
        battingTeam: String = "",
        bowlingTeam: String = ""
    ): PredictionOutput {
        val mlModelLocal = mlModel
        if (mlModelLocal == null || !mlModelTrained) {
            // Fallback to simulation-based probability
            val simProb = calculateWinProbability(
                runsNeeded, ballsRemaining, wicketsLost, currentRunRate, requiredRunRate
            )
            return PredictionOutput(
                winProbability = simProb,
                predictedScore = 0.0,
                confidence = 0.3, // Low confidence for simulation-only
                features = DoubleArray(FeatureExtractor.FEATURE_COUNT),
                rawOutput = doubleArrayOf(simProb / 100.0, 0.0),
                isModelTrained = false
            )
        }

        val ballsBowled = max(0, maxBalls - ballsRemaining)
        val oversRemaining = max(ballsRemaining / 6.0, 0.1)
        // Power-play is first 6 overs (36 balls); death overs = last 20% of innings
        val deathOversThreshold = (maxBalls * 0.8).toInt()
        val situation = MatchSituation(
            currentRunRate = currentRunRate,
            requiredRunRate = requiredRunRate,
            oversRemaining = oversRemaining,
            wicketsInHand = 10 - wicketsLost,
            lastFiveOversRuns = (currentRunRate * 5).toInt(),
            isPowerPlay = ballsBowled < 36,
            isDeathOvers = ballsBowled > deathOversThreshold
        )

        val battingStats = getTeamStats(battingTeam)
        val bowlingStats = getTeamStats(bowlingTeam)

        val features = FeatureExtractor.extractFeatures(
            situation = situation,
            battingTeamStats = battingStats,
            bowlingTeamStats = bowlingStats,
            runsNeeded = runsNeeded,
            ballsBowled = ballsBowled,
            wicketsLost = wicketsLost,
            maxBalls = if (ballsRemaining > 120) 300 else 120,
            enriched = FeatureExtractor.EnrichedContext(
                pitchConditionValue = pitchCondition.value
            )
        )

        val output = mlModelLocal.forward(features)
        val winProb = output[0].coerceIn(0.0, 1.0) * 100.0
        val predictedScore = output[1] * ModelTrainer.MAX_SCORE

        return PredictionOutput(
            winProbability = winProb,
            predictedScore = predictedScore,
            confidence = kotlin.math.abs(output[0] - 0.5) * 2.0,
            features = features,
            rawOutput = output,
            isModelTrained = true
        )
    }

    /**
     * Train the ML model with loaded historical data and set it for use.
     * Call [loadCricsheetData] first to populate historicalMatches.
     */
    fun trainMLModel(): Boolean {
        try {
            if (historicalMatches.isEmpty()) return false

            val model = ScorePredictionModel.create()
            val samples = historicalMatches.flatMap { match ->
                FeatureExtractor.generateTrainingSamples(
                    match = match,
                    teamStats = teamStats,
                    samplesPerMatch = 12
                )
            }

            if (samples.isEmpty()) return false

            val pipeline = com.tanush.bouncer.analysis.ml.TrainingPipeline(
                com.tanush.bouncer.analysis.ml.TrainingConfig(
                    epochs = 80,
                    batchSize = 32,
                    learningRate = 0.001,
                    verbose = false
                )
            )

            val result = pipeline.train(
                model = model,
                data = samples,
                optimizer = com.tanush.bouncer.analysis.ml.Adam(),
                lossFunction = com.tanush.bouncer.analysis.ml.LossFunction.MultiTask()
            )

            setMLModel(result.bestModel, trained = true)
            return result.converged || result.totalEpochs > 10
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

