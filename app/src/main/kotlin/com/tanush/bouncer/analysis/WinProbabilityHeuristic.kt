package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.CricketConstants.BALLS_PER_OVER
import com.tanush.bouncer.analysis.CricketConstants.MatchFormat
import kotlin.math.exp
import kotlin.math.pow

/**
 * Win probability for the **batting** side, as a heuristic over the live match situation.
 *
 * This is not a trained model and does not pretend to be. It is a small, interpretable function
 * whose every constant is named and justified below. It replaces a hand-tuned formula that added
 * percentage points directly and clamped the total, which had three structural problems:
 *
 *  1. **It guessed the format.** Anything not matching "ODI" or "T20" got a 120-ball budget, so a
 *     Test past ball 120 computed `ballsRemaining = 1` and carried the full late-innings pressure
 *     penalty for the rest of the match. Here a format with no known ball budget returns null and
 *     the caller reports "undetermined" instead of a number.
 *  2. **It composed in probability space.** Terms were added as percentage points and the sum
 *     clamped, so an inner cap (±25) was needed to stop one term saturating the result, and the
 *     outer clamp was a formula artefact rather than a confidence floor. Composing in logit space
 *     and squashing once removes both.
 *  3. **It had a step function.** Pressure applied only above exactly 9.00 required run rate, so
 *     the probability jumped discontinuously as the rate crossed that value.
 *
 * The core idea is one quantity — [sustainableRunRate], the rate the batting side can realistically
 * maintain given its remaining wickets and how much of the innings is left — compared against what
 * the situation demands. Both the chase branch and the first-innings branch use it, so they cannot
 * drift apart.
 *
 * Calibration reference points (T20): a chase needing exactly the par rate with all ten wickets
 * reads 50%; chasing 200 from the start reads ~36%; 12 needed off the last over with five wickets
 * standing reads ~28%; 20 off the last over reads ~3%.
 *
 * Known limitation: the wicket term is a plain square-root resource factor, which over-penalises
 * a *late* ODI chase where only a handful of balls remain — with 30 balls left, four wickets in
 * hand is worth nearly as much as ten, because a side cannot realistically lose ten in 30 balls.
 * Modelling that properly needs a resources-remaining table of the DLS kind. Such positions read
 * conservatively (too low) rather than wrongly signed, and the constants here are calibrated to
 * the reference points above rather than fitted, because this repo has no accessible match
 * database to fit against.
 *
 * Not modelled, deliberately: DLS/rain-revised targets (no resource table or par-score source
 * exists in this codebase), super overs, and ties. Test matches return null — see [evaluateChase].
 */
object WinProbabilityHeuristic {

    // A rate advantage of roughly this fraction of the par rate moves the logit by 1
    // (~73% win probability). Scaling by par rate keeps the model's sensitivity proportional to
    // the format: a 1.5 rpo swing means far more in an ODI than in a T20.
    private const val RATE_SPREAD_RATIO = 0.36

    // Wickets convert to scoring capacity sub-linearly: losing the first two wickets barely dents
    // what a side can sustain, losing the eighth is close to terminal. The square root gives that
    // shape without a lookup table.
    private const val WICKET_EXPONENT = 0.5

    // Batting sides accelerate as the innings closes out. At the very last ball the sustainable
    // rate is ~1.6x its start-of-innings value, which is what makes a last-over chase live rather
    // than hopeless — the previous model had no such term and treated late overs as pure penalty.
    private const val DEATH_ACCELERATION = 0.6

    // Confidence floors. A live match is never certain: a 2% floor leaves room for the collapses
    // and heists that actually happen, while still reading as decided.
    private const val MIN_PROBABILITY = 2.0
    private const val MAX_PROBABILITY = 98.0

    // A first innings is genuinely less decided than a chase — there is a whole innings still to
    // come and no target to measure against — so its output is held to a narrower band.
    private const val FIRST_INNINGS_MIN_PROBABILITY = 15.0
    private const val FIRST_INNINGS_MAX_PROBABILITY = 85.0

    const val WICKETS_PER_INNINGS = 10

    /**
     * The rate the batting side can realistically sustain for the rest of the innings.
     *
     * @param parRate the format's full-innings par rate, in runs per over
     * @param wicketsInHand 0..10
     * @param ballsRemainingFraction balls left as a fraction of the innings, 1.0 at the start
     */
    fun sustainableRunRate(
        parRate: Double,
        wicketsInHand: Int,
        ballsRemainingFraction: Double,
    ): Double {
        val wicketResource = (wicketsInHand.toDouble() / WICKETS_PER_INNINGS).coerceIn(0.0, 1.0)
        val acceleration = 1.0 + DEATH_ACCELERATION * (1.0 - ballsRemainingFraction.coerceIn(0.0, 1.0))
        return parRate * acceleration * wicketResource.pow(WICKET_EXPONENT)
    }

    /**
     * Probability (0..100) that the **chasing** side wins, or null when the situation cannot be
     * evaluated — which includes any format without a fixed ball budget.
     *
     * Test matches return null rather than a number. A defensible Test model needs the innings
     * lead, sessions remaining, and follow-on state; none of that is carried by the live match
     * payload this reads from, and inventing a chase model over a guessed 120-ball budget is
     * exactly the bug being removed here.
     *
     * @param requiredRunRate runs per over the chasing side needs
     * @param ballsBowled balls bowled in the chase so far
     * @param wicketsLost wickets the chasing side has lost
     */
    fun evaluateChase(
        format: MatchFormat,
        requiredRunRate: Double,
        ballsBowled: Int,
        wicketsLost: Int,
    ): Double? {
        if (!requiredRunRate.isFinite() || requiredRunRate < 0) return null
        val innings = InningsBudget.of(format, ballsBowled) ?: return null

        val wicketsInHand = wicketsInHand(wicketsLost)
        // Out of wickets or out of balls: the chase is over either way.
        if (wicketsInHand == 0 || innings.ballsRemaining <= 0) return MIN_PROBABILITY
        // A required rate of zero means the target is already passed.
        if (requiredRunRate == 0.0) return MAX_PROBABILITY

        val sustainable =
            sustainableRunRate(innings.parRate, wicketsInHand, innings.ballsRemainingFraction)
        val logit = (sustainable - requiredRunRate) / (innings.parRate * RATE_SPREAD_RATIO)

        return (sigmoid(logit) * 100.0).coerceIn(MIN_PROBABILITY, MAX_PROBABILITY)
    }

    private fun wicketsInHand(wicketsLost: Int) =
        (WICKETS_PER_INNINGS - wicketsLost).coerceIn(0, WICKETS_PER_INNINGS)

    /** The format's ball budget resolved against the innings so far, or null if either is unusable. */
    private data class InningsBudget(
        val ballsBudget: Int,
        val parRate: Double,
        val ballsRemaining: Int,
    ) {
        val ballsRemainingFraction: Double get() = ballsRemaining.toDouble() / ballsBudget

        companion object {
            fun of(format: MatchFormat, ballsBowled: Int): InningsBudget? {
                val ballsBudget = CricketConstants.inningsBalls(format) ?: return null
                val parRate = CricketConstants.parRunRate(format) ?: return null
                if (ballsBowled < 0 || ballsBowled > ballsBudget) return null
                return InningsBudget(ballsBudget, parRate, ballsBudget - ballsBowled)
            }
        }
    }

    /**
     * Probability (0..100) that the side **batting first** ends up winning, or null when the
     * situation cannot be evaluated.
     *
     * Projects the innings forward at [sustainableRunRate] rather than at the current run rate,
     * so a side 60/0 after 10 overs (which will accelerate) and one 60/5 after 10 (which cannot)
     * are not projected to the same total. The previous first-innings term was
     * `(runs / 50.0).coerceAtMost(10.0)`, which reached its cap only at 500 runs and so
     * contributed at most ~4 points in any limited-overs game.
     */
    fun evaluateFirstInnings(
        format: MatchFormat,
        runs: Int,
        ballsBowled: Int,
        wicketsLost: Int,
    ): Double? {
        if (runs < 0) return null
        val innings = InningsBudget.of(format, ballsBowled) ?: return null

        val sustainable = sustainableRunRate(
            innings.parRate,
            wicketsInHand(wicketsLost),
            innings.ballsRemainingFraction,
        )
        val projected = runs + (innings.ballsRemaining.toDouble() / BALLS_PER_OVER) * sustainable
        val par = innings.parRate * innings.ballsBudget / BALLS_PER_OVER

        val logit = (projected - par) / (par * RATE_SPREAD_RATIO)
        return (sigmoid(logit) * 100.0)
            .coerceIn(FIRST_INNINGS_MIN_PROBABILITY, FIRST_INNINGS_MAX_PROBABILITY)
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}
