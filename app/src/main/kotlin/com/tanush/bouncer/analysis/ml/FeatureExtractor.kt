package com.tanush.bouncer.analysis.ml

import com.tanush.bouncer.analysis.MatchSituation
import com.tanush.bouncer.analysis.MatchupAnalysis
import com.tanush.bouncer.analysis.PlayerStatisticsEngine
import com.tanush.bouncer.analysis.TeamStatistics
import com.tanush.bouncer.analysis.VenueAnalysis
import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import kotlin.math.max
import kotlin.math.min
import io.sentry.Sentry

/**
 * Extracts numerical feature vectors from cricket match situations for ML model input.
 *
 * Features are normalized to [0, 1] range for stable gradient descent convergence.
 * The feature vector captures situational, historical, phase-based, player-level,
 * matchup, venue, and toss context.
 *
 * Feature indices (0-15: situational, 16-31: enriched):
 *  0: ballProgress        — fraction of innings completed
 *  1: wicketPressure      — fraction of wickets lost
 *  2: runRateRatio         — current RR / required RR, clamped
 *  3: runsNeededNorm       — runs still needed, normalized
 *  4: requiredRateNorm     — required run rate, normalized
 *  5: currentRateNorm      — current run rate, normalized
 *  6: recentMomentum       — last 5 overs runs (momentum indicator)
 *  7: powerPlayFlag        — 1.0 if in power play
 *  8: deathOversFlag       — 1.0 if in death overs
 *  9: middleOversFlag      — 1.0 if in middle overs
 * 10: battingStrengthNorm  — batting team's historical strength
 * 11: bowlingStrengthNorm  — bowling team's historical strength
 * 12: battingWinRateNorm   — batting team's win rate
 * 13: bowlingWinRateNorm   — bowling team's win rate
 * 14: pressureIndex        — composite pressure metric
 * 15: chaseEfficiency      — how well the chase is progressing
 * --- Player & context features ---
 * 16: batterAverage        — current batter's career batting average
 * 17: batterStrikeRate     — current batter's career strike rate
 * 18: batterRecentForm     — batter's recent form SR
 * 19: batterVenuePerf      — batter's venue-specific SR
 * 20: bowlerAverage        — current bowler's bowling average (inverted = better)
 * 21: bowlerEconomy        — current bowler's economy (inverted = better)
 * 22: bowlerRecentForm     — bowler's recent form economy (inverted)
 * 23: matchupStrikeRate    — batter vs bowler head-to-head SR
 * 24: tossWinnerBatting    — 1.0 if toss winner chose to bat
 * 25: venueAvgScore        — venue avg first innings score normalized
 * 26: venueChaseSuccess    — venue chase success rate
 * 27: partnershipRuns      — current partnership runs normalized
 * 28: partnershipBalls     — current partnership balls normalized
 * 29: matchTypeT20         — 1.0 if T20 format
 * 30: matchTypeODI         — 1.0 if ODI format
 * 31: matchTypeTest        — 1.0 if Test/multi-day format
 */
object FeatureExtractor {

    /** Total number of features in the extracted vector */
    const val FEATURE_COUNT = 32

    /** Maximum expected run rate for normalization */
    private const val MAX_RUN_RATE = 15.0

    /** Maximum expected target for normalization */
    private const val MAX_TARGET = 350.0

    /** Maximum balls in an innings (T20=120, ODI=300) */
    private const val MAX_BALLS_ODI = 300
    private const val MAX_BALLS_T20 = 120

    /**
     * Enriched context for player-level and venue-level feature extraction.
     * Pass this alongside the match situation for enhanced prediction accuracy.
     */
    data class EnrichedContext(
        val currentBatter: String = "",
        val currentBowler: String = "",
        val venue: String = "",
        val matchType: String = "",                    // "T20", "ODI", "Test", etc.
        val tossWinnerBatFirst: Boolean? = null,       // null = unknown
        val partnershipRuns: Int = 0,
        val partnershipBalls: Int = 0,
        val playerStats: PlayerStatisticsEngine? = null,
        val matchupAnalysis: MatchupAnalysis? = null,
        val venueAnalysis: VenueAnalysis? = null
    )

    /**
     * Extracts a normalized feature vector from a live match situation.
     */
    fun extractFeatures(
        situation: MatchSituation,
        battingTeamStats: TeamStatistics = TeamStatistics(),
        bowlingTeamStats: TeamStatistics = TeamStatistics(),
        runsNeeded: Int = 0,
        ballsBowled: Int = 0,
        wicketsLost: Int = 0,
        maxBalls: Int = MAX_BALLS_T20,
        enriched: EnrichedContext = EnrichedContext()
    ): DoubleArray {
        val features = DoubleArray(FEATURE_COUNT)

        // 0-15: Original situational features
        features[0] = clamp(ballsBowled.toDouble() / max(maxBalls, 1))
        features[1] = clamp(wicketsLost.toDouble() / 10.0)
        
        val rrRatio = if (situation.requiredRunRate > 0.0) {
            situation.currentRunRate / situation.requiredRunRate
        } else 1.0
        features[2] = clamp(rrRatio / 2.0)
        features[3] = clamp(runsNeeded.toDouble() / MAX_TARGET)
        features[4] = clamp(situation.requiredRunRate / MAX_RUN_RATE)
        features[5] = clamp(situation.currentRunRate / MAX_RUN_RATE)
        features[6] = clamp(situation.lastFiveOversRuns.toDouble() / 60.0)
        features[7] = if (situation.isPowerPlay) 1.0 else 0.0
        features[8] = if (situation.isDeathOvers) 1.0 else 0.0
        features[9] = if (!situation.isPowerPlay && !situation.isDeathOvers) 1.0 else 0.0
        features[10] = clamp(battingTeamStats.battingAverage / 50.0)
        features[11] = clamp(1.0 - (bowlingTeamStats.bowlingAverage / 50.0))
        features[12] = clamp(battingTeamStats.winRate)
        features[13] = clamp(bowlingTeamStats.winRate)
        
        val wicketPressure = wicketsLost.toDouble() / 10.0
        val ratePressure = clamp(situation.requiredRunRate / MAX_RUN_RATE)
        val timePressure = clamp(ballsBowled.toDouble() / max(maxBalls, 1))
        features[14] = clamp(wicketPressure * 0.4 + ratePressure * 0.35 + timePressure * 0.25)
        
        val expectedRunsAtThisPoint = if (maxBalls > 0) {
            (runsNeeded + (situation.currentRunRate * ballsBowled / 6.0)).let {
                if (it > 0) (situation.currentRunRate * ballsBowled / 6.0) / it else 0.5
            }
        } else 0.5
        features[15] = clamp(expectedRunsAtThisPoint)

        // 16-18: Current batter features
        val batterStats = enriched.playerStats?.getBatterStats(enriched.currentBatter)
        features[16] = clamp((batterStats?.average ?: 25.0) / 60.0)
        features[17] = clamp((batterStats?.strikeRate ?: 120.0) / 200.0)
        features[18] = clamp((batterStats?.recentFormStrikeRate ?: 120.0) / 200.0)

        // 19: Batter venue-specific performance
        val batterVenueSR = batterStats?.venueAverages?.get(enriched.venue)
        features[19] = clamp((batterVenueSR ?: batterStats?.strikeRate ?: 120.0) / 200.0)

        // 20-22: Current bowler features (inverted — lower is better for bowler)
        val bowlerStats = enriched.playerStats?.getBowlerStats(enriched.currentBowler)
        features[20] = clamp(1.0 - ((bowlerStats?.bowlingAverage ?: 30.0) / 60.0))
        features[21] = clamp(1.0 - ((bowlerStats?.economyRate ?: 8.0) / 15.0))
        features[22] = clamp(1.0 - ((bowlerStats?.recentFormEconomy ?: 8.0) / 15.0))

        // 23: Batter vs bowler matchup
        val matchup = enriched.matchupAnalysis?.getMatchup(enriched.currentBatter, enriched.currentBowler)
        features[23] = clamp((matchup?.strikeRate ?: 120.0) / 200.0)

        // 24: Toss impact
        features[24] = when (enriched.tossWinnerBatFirst) {
            true -> 1.0
            false -> 0.0
            null -> 0.5
        }

        // 25-26: Venue features
        val venueStats = enriched.venueAnalysis?.getVenueStats(enriched.venue)
            ?: enriched.venueAnalysis?.findVenue(enriched.venue)
        features[25] = clamp((venueStats?.avgFirstInningsScore ?: 160.0) / MAX_TARGET)
        features[26] = clamp(venueStats?.chaseSuccessRate ?: 0.5)

        // 27-28: Partnership features
        features[27] = clamp(enriched.partnershipRuns.toDouble() / 100.0)
        features[28] = clamp(enriched.partnershipBalls.toDouble() / 60.0)

        // 29-31: Match type one-hot
        val mt = enriched.matchType.uppercase()
        features[29] = if (mt.contains("T20") || mt.contains("IT20")) 1.0 else 0.0
        features[30] = if (mt.contains("ODI") || mt.contains("ODM")) 1.0 else 0.0
        features[31] = if (mt.contains("TEST") || mt.contains("MDM")) 1.0 else 0.0

        return features
    }

    /**
     * Extract features from a completed match at a specific point (for training data generation).
     */
    fun extractTrainingFeatures(
        innings: CricsheetInnings,
        atBall: Int,
        target: Int,
        battingTeamStats: TeamStatistics = TeamStatistics(),
        bowlingTeamStats: TeamStatistics = TeamStatistics(),
        maxBalls: Int = MAX_BALLS_T20,
        enriched: EnrichedContext = EnrichedContext()
    ): DoubleArray {
        var runsScored = 0
        var wicketsLost = 0
        var ballCount = 0
        var last30BallsRuns = 0
        var currentBatter = ""
        var currentBowler = ""
        var partnershipRuns = 0
        var partnershipBalls = 0

        for (over in innings.overs) {
            for (ball in over.balls) {
                if (ballCount >= atBall) break
                runsScored += ball.runs + ball.extras
                if (ball.isWicket) {
                    wicketsLost++
                    partnershipRuns = 0
                    partnershipBalls = 0
                } else {
                    partnershipRuns += ball.batterRuns
                    partnershipBalls += 1
                }
                ballCount++

                if (ballCount > atBall - 30) {
                    last30BallsRuns += ball.runs + ball.extras
                }
                
                // Track current batter/bowler at this point
                if (ball.batter.isNotBlank()) currentBatter = ball.batter
                if (ball.bowler.isNotBlank()) currentBowler = ball.bowler
            }
            if (ballCount >= atBall) break
        }

        val runsNeeded = max(target - runsScored, 0)
        val oversRemaining = max((maxBalls - atBall) / 6.0, 0.1)
        val oversBowled = max(atBall / 6.0, 0.1)
        val currentRunRate = runsScored / oversBowled
        val requiredRunRate = runsNeeded / oversRemaining
        val isPowerPlay = atBall < 36
        val isDeathOvers = atBall > (maxBalls - 30)

        val situation = MatchSituation(
            currentRunRate = currentRunRate,
            requiredRunRate = requiredRunRate,
            oversRemaining = oversRemaining,
            wicketsInHand = 10 - wicketsLost,
            lastFiveOversRuns = last30BallsRuns,
            isPowerPlay = isPowerPlay,
            isDeathOvers = isDeathOvers
        )

        // Build enriched context using the batter/bowler at this point
        val trainingEnriched = enriched.copy(
            currentBatter = if (enriched.currentBatter.isBlank()) currentBatter else enriched.currentBatter,
            currentBowler = if (enriched.currentBowler.isBlank()) currentBowler else enriched.currentBowler,
            partnershipRuns = partnershipRuns,
            partnershipBalls = partnershipBalls
        )

        return extractFeatures(
            situation = situation,
            battingTeamStats = battingTeamStats,
            bowlingTeamStats = bowlingTeamStats,
            runsNeeded = runsNeeded,
            ballsBowled = atBall,
            wicketsLost = wicketsLost,
            maxBalls = maxBalls,
            enriched = trainingEnriched
        )
    }

    /**
     * Generate multiple training samples from a single completed match.
     * Samples the match state at various ball counts to create diverse training data.
     */
    fun generateTrainingSamples(
        match: CricsheetMatch,
        teamStats: Map<String, TeamStatistics> = emptyMap(),
        samplesPerMatch: Int = 12,
        playerStats: PlayerStatisticsEngine? = null,
        matchupAnalysis: MatchupAnalysis? = null,
        venueAnalysis: VenueAnalysis? = null
    ): List<TrainingSample> {
        val samples = mutableListOf<TrainingSample>()

        if (match.innings.size < 2) return samples

        val firstInnings = match.innings[0]
        val secondInnings = match.innings[1]
        val target = firstInnings.total + 1

        val battingTeam = secondInnings.team
        val bowlingTeam = firstInnings.team

        val battingStats = teamStats[battingTeam] ?: TeamStatistics()
        val bowlingStats = teamStats[bowlingTeam] ?: TeamStatistics()

        val battingTeamWon = match.outcome?.winner == battingTeam

        val totalBalls = secondInnings.overs.sumOf { it.balls.size }
        val maxBalls = if (totalBalls > 120) MAX_BALLS_ODI else MAX_BALLS_T20

        // Build enriched context for this match
        val tossWinnerBatFirst = match.toss?.let { it.decision == "bat" }
        val enriched = EnrichedContext(
            venue = match.venue,
            matchType = match.matchType,
            tossWinnerBatFirst = tossWinnerBatFirst,
            playerStats = playerStats,
            matchupAnalysis = matchupAnalysis,
            venueAnalysis = venueAnalysis
        )

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
                maxBalls = maxBalls,
                enriched = enriched
            )

            val label = if (battingTeamWon) 1.0 else 0.0

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
