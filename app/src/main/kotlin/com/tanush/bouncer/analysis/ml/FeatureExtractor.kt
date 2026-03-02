package com.tanush.bouncer.analysis.ml

import com.tanush.bouncer.analysis.MatchSituation
import com.tanush.bouncer.analysis.TeamStatistics
import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import kotlin.math.max
import kotlin.math.min
import io.sentry.Sentry

/**
 * Extracts numerical feature vectors from cricket match situations for ML model input.
 *
 * Features are normalized to [0, 1] range for stable gradient descent convergence.
 * The feature vector captures situational, historical, and phase-based match context.
 *
 * Feature indices:
 *  0: ballProgress        — fraction of innings completed (ball / maxBalls)
 *  1: wicketPressure      — fraction of wickets lost (wicketsLost / 10)
 *  2: runRateRatio         — current RR / required RR, clamped [0, 2] → [0, 1]
 *  3: runsNeededNorm       — runs still needed, normalized to typical max target
 *  4: requiredRateNorm     — required run rate, normalized
 *  5: currentRateNorm      — current run rate, normalized
 *  6: recentMomentum       — last 5 overs runs normalized (momentum indicator)
 *  7: powerPlayFlag        — 1.0 if in power play, else 0.0
 *  8: deathOversFlag       — 1.0 if in death overs, else 0.0
 *  9: middleOversFlag      — 1.0 if in middle overs, else 0.0
 * 10: battingStrengthNorm  — batting team's historical strength [0, 1]
 * 11: bowlingStrengthNorm  — bowling team's historical strength [0, 1]
 * 12: battingWinRateNorm   — batting team's historical win rate [0, 1]
 * 13: bowlingWinRateNorm   — bowling team's historical win rate [0, 1]
 * 14: pressureIndex        — composite pressure metric [0, 1]
 * 15: chaseEfficiency      — how well the chase is progressing [0, 1]
 */
object FeatureExtractor {

    /** Total number of features in the extracted vector */
    const val FEATURE_COUNT = 16

    /** Maximum expected run rate for normalization */
    private const val MAX_RUN_RATE = 15.0

    /** Maximum expected target for normalization */
    private const val MAX_TARGET = 350.0

    /** Maximum balls in an innings (T20=120, ODI=300) */
    private const val MAX_BALLS_ODI = 300
    private const val MAX_BALLS_T20 = 120

    /**
     * Extracts a normalized feature vector from a live match situation.
     *
     * @param situation Current match parameters
     * @param battingTeamStats Historical statistics for the batting side
     * @param bowlingTeamStats Historical statistics for the bowling side
     * @param runsNeeded Runs still required to win
     * @param ballsBowled Number of balls already bowled in the innings
     * @param wicketsLost Number of wickets fallen
     * @param maxBalls Maximum balls in this format (120 for T20, 300 for ODI)
     * @return DoubleArray of length [FEATURE_COUNT] with values in [0, 1]
     */
    fun extractFeatures(
        situation: MatchSituation,
        battingTeamStats: TeamStatistics = TeamStatistics(),
        bowlingTeamStats: TeamStatistics = TeamStatistics(),
        runsNeeded: Int = 0,
        ballsBowled: Int = 0,
        wicketsLost: Int = 0,
        maxBalls: Int = MAX_BALLS_T20
    ): DoubleArray {
        val features = DoubleArray(FEATURE_COUNT)

        // 0: Ball progress — fraction of innings completed
        features[0] = clamp(ballsBowled.toDouble() / max(maxBalls, 1))

        // 1: Wicket pressure — fraction of wickets lost
        features[1] = clamp(wicketsLost.toDouble() / 10.0)

        // 2: Run rate ratio (current / required), clamped and normalized
        val rrRatio = if (situation.requiredRunRate > 0.0) {
            situation.currentRunRate / situation.requiredRunRate
        } else {
            1.0
        }
        features[2] = clamp(rrRatio / 2.0) // ratio of 2.0 → 1.0

        // 3: Runs needed, normalized to max target
        features[3] = clamp(runsNeeded.toDouble() / MAX_TARGET)

        // 4: Required run rate, normalized
        features[4] = clamp(situation.requiredRunRate / MAX_RUN_RATE)

        // 5: Current run rate, normalized
        features[5] = clamp(situation.currentRunRate / MAX_RUN_RATE)

        // 6: Recent momentum (last 5 overs runs, ~40 max realistic → normalize)
        features[6] = clamp(situation.lastFiveOversRuns.toDouble() / 60.0)

        // 7-9: Phase flags (one-hot encoding of match phase)
        features[7] = if (situation.isPowerPlay) 1.0 else 0.0
        features[8] = if (situation.isDeathOvers) 1.0 else 0.0
        features[9] = if (!situation.isPowerPlay && !situation.isDeathOvers) 1.0 else 0.0

        // 10: Batting team strength (historical batting average, normalized)
        features[10] = clamp(battingTeamStats.battingAverage / 50.0)

        // 11: Bowling team strength (lower bowling avg = stronger, invert)
        features[11] = clamp(1.0 - (bowlingTeamStats.bowlingAverage / 50.0))

        // 12: Batting team win rate
        features[12] = clamp(battingTeamStats.winRate)

        // 13: Bowling team win rate
        features[13] = clamp(bowlingTeamStats.winRate)

        // 14: Composite pressure index
        // High when: many wickets lost + high required rate + few balls left
        val wicketPressure = wicketsLost.toDouble() / 10.0
        val ratePressure = clamp(situation.requiredRunRate / MAX_RUN_RATE)
        val timePressure = clamp(ballsBowled.toDouble() / max(maxBalls, 1))
        features[14] = clamp((wicketPressure * 0.4 + ratePressure * 0.35 + timePressure * 0.25))

        // 15: Chase efficiency — how efficiently the batting team is tracking the target
        val expectedRunsAtThisPoint = if (maxBalls > 0) {
            (runsNeeded + (situation.currentRunRate * ballsBowled / 6.0)).let {
                if (it > 0) (situation.currentRunRate * ballsBowled / 6.0) / it else 0.5
            }
        } else {
            0.5
        }
        features[15] = clamp(expectedRunsAtThisPoint)

        return features
    }

    /**
     * Extract features from a completed match at a specific point (for training data generation).
     *
     * @param innings The innings data
     * @param atBall Extract situation at this ball number
     * @param target The target score (for 2nd innings)
     * @param battingTeamStats Historical stats
     * @param bowlingTeamStats Historical stats
     * @param maxBalls Format's max balls
     * @return Feature vector and the label (1.0 = batting team won, 0.0 = lost)
     */
    fun extractTrainingFeatures(
        innings: CricsheetInnings,
        atBall: Int,
        target: Int,
        battingTeamStats: TeamStatistics = TeamStatistics(),
        bowlingTeamStats: TeamStatistics = TeamStatistics(),
        maxBalls: Int = MAX_BALLS_T20
    ): DoubleArray {
        // Reconstruct match state at the given ball
        var runsScored = 0
        var wicketsLost = 0
        var ballCount = 0
        var last30BallsRuns = 0

        for (over in innings.overs) {
            for (ball in over.balls) {
                if (ballCount >= atBall) break
                runsScored += ball.runs + ball.extras
                if (ball.isWicket) wicketsLost++
                ballCount++

                // Track last 30 balls (5 overs) for momentum
                if (ballCount > atBall - 30) {
                    last30BallsRuns += ball.runs + ball.extras
                }
            }
            if (ballCount >= atBall) break
        }

        val runsNeeded = max(target - runsScored, 0)
        val oversRemaining = max((maxBalls - atBall) / 6.0, 0.1)
        val oversBowled = max(atBall / 6.0, 0.1)
        val currentRunRate = runsScored / oversBowled
        val requiredRunRate = runsNeeded / oversRemaining
        val isPowerPlay = atBall < 36  // first 6 overs
        val isDeathOvers = atBall > (maxBalls - 30)  // last 5 overs

        val situation = MatchSituation(
            currentRunRate = currentRunRate,
            requiredRunRate = requiredRunRate,
            oversRemaining = oversRemaining,
            wicketsInHand = 10 - wicketsLost,
            lastFiveOversRuns = last30BallsRuns,
            isPowerPlay = isPowerPlay,
            isDeathOvers = isDeathOvers
        )

        return extractFeatures(
            situation = situation,
            battingTeamStats = battingTeamStats,
            bowlingTeamStats = bowlingTeamStats,
            runsNeeded = runsNeeded,
            ballsBowled = atBall,
            wicketsLost = wicketsLost,
            maxBalls = maxBalls
        )
    }

    /**
     * Generate multiple training samples from a single completed match.
     * Samples the match state at various ball counts to create diverse training data.
     *
     * @param match The completed match
     * @param teamStats Map of team name → statistics
     * @param samplesPerMatch How many samples to extract per match
     * @return List of (features, label) pairs
     */
    fun generateTrainingSamples(
        match: CricsheetMatch,
        teamStats: Map<String, TeamStatistics> = emptyMap(),
        samplesPerMatch: Int = 12
    ): List<TrainingSample> {
        val samples = mutableListOf<TrainingSample>()

        // We focus on 2nd innings where we have a target
        if (match.innings.size < 2) return samples

        val firstInnings = match.innings[0]
        val secondInnings = match.innings[1]
        val target = firstInnings.total + 1 // Target is first innings total + 1

        val battingTeam = secondInnings.team
        val bowlingTeam = firstInnings.team

        val battingStats = teamStats[battingTeam] ?: TeamStatistics()
        val bowlingStats = teamStats[bowlingTeam] ?: TeamStatistics()

        // Determine the label: did batting team (chasing team) win?
        val battingTeamWon = match.outcome?.winner == battingTeam

        // Determine max balls based on total overs in the innings
        val totalBalls = secondInnings.overs.sumOf { it.balls.size }
        val maxBalls = if (totalBalls > 120) MAX_BALLS_ODI else MAX_BALLS_T20

        // Sample at evenly-spaced ball positions throughout the innings
        val sampleInterval = max(1, totalBalls / samplesPerMatch)
        for (i in 0 until samplesPerMatch) {
            val atBall = min(i * sampleInterval + 1, totalBalls - 1)
            if (atBall <= 0) continue

            val features = extractTrainingFeatures(
                innings = secondInnings,
                atBall = atBall,
                target = target,
                battingTeamStats = battingStats,
                bowlingTeamStats = bowlingStats,
                maxBalls = maxBalls
            )

            // Label: 1.0 if batting team won, 0.0 if they lost
            val label = if (battingTeamWon) 1.0 else 0.0

            // Also generate score prediction label (runs scored at this point normalized)
            var runsAtPoint = 0
            var ballCount = 0
            for (over in secondInnings.overs) {
                for (ball in over.balls) {
                    if (ballCount >= atBall) break
                    runsAtPoint += ball.runs + ball.extras
                    ballCount++
                }
                if (ballCount >= atBall) break
            }
            val scorePredictionLabel = runsAtPoint.toDouble() / MAX_TARGET

            samples.add(
                TrainingSample(
                    features = features,
                    winProbabilityLabel = label,
                    scorePredictionLabel = scorePredictionLabel,
                    matchId = match.matchId,
                    ballNumber = atBall
                )
            )
        }

        return samples
    }

    /** Clamp a value to [0.0, 1.0] */
    private fun clamp(value: Double): Double = max(0.0, min(1.0, value))
}

/**
 * A single training sample with features and multiple prediction targets.
 */
data class TrainingSample(
    val features: DoubleArray,
    val winProbabilityLabel: Double,   // 0.0 or 1.0 (classification target)
    val scorePredictionLabel: Double,  // Normalized score (regression target)
    val matchId: String,
    val ballNumber: Int
) {
    override fun equals(other: Any?): Boolean {
        try {
            if (this === other) return true
            if (other !is TrainingSample) return false
            return features.contentEquals(other.features) &&
                    winProbabilityLabel == other.winProbabilityLabel &&
                    scorePredictionLabel == other.scorePredictionLabel &&
                    matchId == other.matchId &&
                    ballNumber == other.ballNumber
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    override fun hashCode(): Int {
        try {
            var result = features.contentHashCode()
            result = 31 * result + winProbabilityLabel.hashCode()
            result = 31 * result + scorePredictionLabel.hashCode()
            result = 31 * result + matchId.hashCode()
            result = 31 * result + ballNumber
            return result
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
