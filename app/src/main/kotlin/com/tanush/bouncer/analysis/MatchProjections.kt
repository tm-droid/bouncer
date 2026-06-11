package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.CricketConstants.BALLS_PER_OVER
import kotlin.math.roundToInt

/**
 * Statistically sound cricket match projection calculations.
 * All functions guard for edge cases; returns null where a result is meaningless.
 *
 * Use [CricketConstants] for format-specific total balls (T20_INNINGS_BALLS = 120,
 * ODI_INNINGS_BALLS = 300).
 */
object MatchProjections {

    /**
     * Current run rate in runs per over.
     *
     * @param runs total runs scored
     * @param ballsBowled total balls bowled (0 = no balls yet → returns null)
     */
    fun currentRunRate(runs: Int, ballsBowled: Int): Double? {
        if (ballsBowled <= 0) return null
        return runs.toDouble() * BALLS_PER_OVER / ballsBowled
    }

    /**
     * Required run rate in runs per over for the chasing team.
     *
     * Uses the format's total innings balls ([totalBalls]) — NOT the first innings'
     * actual overs bowled — so this is correct even when the first side was bowled out early.
     *
     * @param target runs the chasing team needs to exceed (first innings total + 1)
     * @param currentRuns chasing team's current score
     * @param ballsBowled balls already bowled in the chase
     * @param totalBalls total balls allowed in the innings (e.g. 120 for T20, 300 for ODI)
     * @return null if target already passed, no balls remaining, or invalid inputs
     */
    fun requiredRunRate(target: Int, currentRuns: Int, ballsBowled: Int, totalBalls: Int): Double? {
        if (totalBalls <= 0 || ballsBowled < 0) return null
        val ballsRemaining = totalBalls - ballsBowled
        if (ballsRemaining <= 0) return null
        val runsNeeded = target - currentRuns
        if (runsNeeded <= 0) return null   // target already passed
        return runsNeeded.toDouble() * BALLS_PER_OVER / ballsRemaining
    }

    /**
     * Projected final score based on current run rate extrapolated to end of innings.
     *
     * Formula: currentRuns * totalBalls / ballsBowled
     * (equivalent to currentRuns + crr_per_ball * ballsRemaining).
     *
     * @return null when no balls bowled yet (would be meaningless)
     */
    fun projectedScore(currentRuns: Int, ballsBowled: Int, totalBalls: Int): Int? {
        if (ballsBowled <= 0 || totalBalls <= 0) return null
        if (ballsBowled >= totalBalls) return currentRuns   // innings over: projection = actual
        return (currentRuns.toDouble() * totalBalls / ballsBowled).roundToInt()
    }

    /**
     * Over-by-over run rate at over index [overIndex] (0-based) from cumulative scores.
     *
     * Replaces the [RunRateComparisonCard] bug (index+1 assumes exactly one entry per over).
     * Each entry in [cumulativeScores] must correspond to exactly one completed over.
     * Returns 0f when no data.
     */
    fun overRunRates(cumulativeScores: List<Int>): List<Float> {
        if (cumulativeScores.isEmpty()) return emptyList()
        return cumulativeScores.mapIndexed { index, total ->
            val oversCompleted = (index + 1).toFloat()
            total.toFloat() / oversCompleted
        }
    }
}
