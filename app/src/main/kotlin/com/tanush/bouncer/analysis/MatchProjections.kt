package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.CricketConstants.BALLS_PER_OVER
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.utils.parseScore
import kotlin.math.roundToInt

/**
 * Statistically sound cricket match projection calculations.
 * All functions guard for edge cases; returns null where a result is meaningless.
 *
 * Use [CricketConstants] for format-specific total balls (T20_INNINGS_BALLS = 120,
 * ODI_INNINGS_BALLS = 300).
 */
object MatchProjections {

    /** A full complement of wickets. */
    private const val MAX_WICKETS = 10

    // Wicket damping for [projectedScore], mirroring winprob.js: at 10 wickets in hand the
    // remaining overs are worth the full current run rate, at 0 they are worth 55% of it.
    private const val WICKET_DAMPING_FLOOR = 0.55
    private const val WICKET_DAMPING_RANGE = 0.45

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
     * Projected final score, damped by wickets in hand.
     *
     * `runs + crr * oversRemaining * (0.55 + 0.45 * wicketsInHand / 10)`
     *
     * This used to be straight linear extrapolation — `currentRuns * totalBalls / ballsBowled` —
     * which ignored wickets entirely, so a side 6 down after 8 overs of a T20 projected exactly the
     * same total as one 0 down. That is wrong in the direction that matters: a team with two
     * wickets left does not keep scoring at its current rate, and the projection was most
     * confident precisely when a collapse had made it least reliable.
     *
     * The damping factor is the one this project's own trained win-probability model already uses
     * server-side (`appwrite/functions/win-probability/src/winprob.js`), so the device and the
     * server now answer the same question the same way. At all ten wickets in hand it is the old
     * linear projection; at one wicket left it discounts the remaining overs to ~60%.
     *
     * @param wicketsInHand 0..10; coerced into range
     * @return null when no balls bowled yet (would be meaningless)
     */
    fun projectedScore(currentRuns: Int, ballsBowled: Int, totalBalls: Int, wicketsInHand: Int): Int? {
        if (ballsBowled <= 0 || totalBalls <= 0) return null
        if (ballsBowled >= totalBalls) return currentRuns   // innings over: projection = actual
        val runRatePerBall = currentRuns.toDouble() / ballsBowled
        val ballsRemaining = totalBalls - ballsBowled
        val wicketFraction = wicketsInHand.coerceIn(0, MAX_WICKETS).toDouble() / MAX_WICKETS
        val damping = WICKET_DAMPING_FLOOR + WICKET_DAMPING_RANGE * wicketFraction
        return (currentRuns + runRatePerBall * ballsRemaining * damping).roundToInt()
    }

    /**
     * The batting side's projected final total for [match], or its current total when a projection
     * would be meaningless.
     *
     * Exists because the "Projected: N" label on the live scoring card was being fed the score the
     * team was already on, so it announced a projection while displaying the present — while
     * [projectedScore] sat here with no callers in main source at all.
     *
     * Returns the current total rather than null for the cases a projection cannot cover: a format
     * with no fixed innings length (Test), an unparseable score, or no balls bowled yet.
     */
    fun projectedTotalFor(match: CricketMatch, cumulativeScores: List<Int>): Int {
        val current = cumulativeScores.lastOrNull() ?: 0
        val batting = match.teams.firstOrNull { it.isBatting } ?: return current
        val parsed = parseScore(batting.score).takeIf { it.isValid } ?: return current
        val ballsBowled = CricketConstants.ballsFromOvers(batting.overs) ?: return current
        val totalBalls = CricketConstants.inningsBalls(
            CricketConstants.formatFromMatchType(match.matchType)
        ) ?: return current
        return projectedScore(
            currentRuns = parsed.runs,
            ballsBowled = ballsBowled,
            totalBalls = totalBalls,
            wicketsInHand = MAX_WICKETS - parsed.wickets,
        ) ?: current
    }
}
