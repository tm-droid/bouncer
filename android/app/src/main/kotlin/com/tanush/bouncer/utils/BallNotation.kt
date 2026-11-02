package com.tanush.bouncer.utils

/**
 * The single anchored ball-number pattern for commentary lines.
 *
 * Seven sites carried their own variant of this — `^(\d{1,2}\.\d)\s`, `^\s*(\d{1,2}\.\d)`,
 * `^\s*(\d+\.\d+)`, `^\d+\.\d.*`, `^(\d{1,3}\.\d)`, `^\d{1,2}\.\d\s*[:,]?` and an unanchored
 * `\d{1,2}\.\d\s` — plus `BALL_RE = /(\d{1,3}\.\d)/` in match-sync. Two distinct defects ran through
 * them:
 *
 *  - **`{1,2}` breaks past over 99.** A Test innings routinely passes 100 overs, so "104.3 Bumrah to
 *    Root" matched as ball 4.3 where the pattern was unanchored, and not at all where it was
 *    anchored. `{1,3}` covers the longest first-class innings.
 *  - **Unanchored matching finds ball numbers that are not ball numbers.** The JS `BALL_RE` reads
 *    "45.2 runs per over" as ball 45.2, and `ballNumberLoosePattern` had the same problem. These
 *    sites all mean "this line *starts with* a ball number", which is an anchor.
 *
 * Deliberately NOT folded into [com.tanush.bouncer.analysis.CricketConstants.OVERS_NOTATION]. That
 * one is unanchored on purpose so it can find a figure anywhere inside "19.5 Ov"; conflating the two
 * is precisely how the JS bug arose.
 */

/** Matches a leading ball number, tolerating leading whitespace. Group 1 is the number. */
val BALL_PREFIX = """^\s*(\d{1,3}\.\d)""".toRegex()

/**
 * Cricbuzz's `ballMetric`, which does not mean the same thing in every format.
 *
 * Captured from live RSC payloads on 2026-07-30: over-based formats (Test, ODI, T20, List A) send
 * `over.ball` — `48.6`, `40.2`, `19.5`. **The Hundred sends a flat 1–100 ball counter** — `72`,
 * `89`, `100` — because it has no overs at all. [BALL_PREFIX] matches the first and rejects the
 * second, so the two cannot share a representation: coercing a Hundred counter into `over.ball`
 * invents an over that does not exist, and letting it fall through to "" is what put a fabricated
 * ball number on 33% of the commentary cache.
 *
 * Non-ball items (session notes, bowler changes, result lines) carry the literal string
 * `"$undefined"` where the number would sit — see [BALL_METRIC_UNDEFINED].
 */
sealed interface BallMetric {
    /** How this metric is written at the head of a commentary line. */
    val label: String

    /** Deliveries completed in the innings once this ball is bowled. */
    val ballsBowled: Int

    /** Over-based formats: `48.6` is over 48, ball 6. */
    data class OverBall(val over: Int, val ball: Int) : BallMetric {
        override val label: String get() = "$over.$ball"
        override val ballsBowled: Int get() = over * BALLS_PER_OVER + ball
    }

    /** The Hundred: ball [ball] of 100, with no over structure. */
    data class BallCount(val ball: Int) : BallMetric {
        override val label: String get() = "Ball $ball"
        override val ballsBowled: Int get() = ball
    }
}

/** Deliberately local: [BallMetric] must not depend on the analysis package. */
private const val BALLS_PER_OVER = 6

/** Matches the `Ball 72: ` head of a Hundred commentary line. Group 1 is the counter. */
val BALL_COUNT_PREFIX = """^\s*Ball\s+(\d{1,3})\b""".toRegex()

/**
 * The [BallMetric] a commentary line (or a bare label) begins with, or null when it begins with
 * neither form.
 *
 * The inverse of [BallMetric.label], and the reason The Hundred still reaches the ball path:
 * [ballNumberOf] only knows `over.ball`, so routing on it alone would send every Hundred delivery
 * to the event path.
 */
fun ballMetricOfLine(line: String): BallMetric? {
    ballNumberOf(line)?.let { return ballMetricOf(it) }
    BALL_COUNT_PREFIX.find(line)?.let { match ->
        return match.groupValues[1].toIntOrNull()?.let { BallMetric.BallCount(it) }
    }
    return null
}

/** Whether [line] already begins with either ball-label form. */
fun startsWithBallLabel(line: String): Boolean = ballMetricOfLine(line) != null

/** The RSC sentinel Cricbuzz puts in `ballMetric` on items that are not deliveries. */
const val BALL_METRIC_UNDEFINED = "\$undefined"

private val OVER_BALL_METRIC = """^(\d{1,3})\.(\d)$""".toRegex()
private val BALL_COUNT_METRIC = """^(\d{1,3})$""".toRegex()

/**
 * Parses a raw `ballMetric` value, or null when the item is not a delivery.
 *
 * Null covers [BALL_METRIC_UNDEFINED], blanks, and anything non-numeric. Null means "no ball
 * number" and must stay that way — the caller routes those to the event path rather than
 * substituting a neighbouring ball's number.
 */
fun ballMetricOf(raw: String?): BallMetric? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value == BALL_METRIC_UNDEFINED) return null
    OVER_BALL_METRIC.find(value)?.let { match ->
        val over = match.groupValues[1].toIntOrNull() ?: return null
        val ball = match.groupValues[2].toIntOrNull() ?: return null
        return BallMetric.OverBall(over, ball)
    }
    BALL_COUNT_METRIC.find(value)?.let { match ->
        val ball = match.groupValues[1].toIntOrNull() ?: return null
        return BallMetric.BallCount(ball)
    }
    return null
}

/** Ball number at the start of [line], or null when the line does not begin with one. */
fun ballNumberOf(line: String): String? = BALL_PREFIX.find(line)?.groupValues?.get(1)

/** Whether [line] begins with a ball number. */
fun startsWithBallNumber(line: String): Boolean = BALL_PREFIX.containsMatchIn(line)

/**
 * Ball digit within the over, i.e. the `5` of `18.5`.
 *
 * Takes the [Double] form used by the prompt-phase selector.
 *
 * The audit this came from claimed `((ballNumber * 10).toInt() % 10)` yields 4 for 18.5 on IEEE-754
 * grounds. That is **not true** — 18.5 is exactly representable, `18.5 * 10` is exactly 185.0, and
 * truncation is exact for every one of the 1500 ball numbers up to over 249. Nothing was broken.
 *
 * This still rounds rather than truncates, and exists as a named helper, for two reasons that do
 * hold: rounding is correct for a [Double] that has picked up drift from arithmetic rather than
 * being parsed straight from "18.5" (truncation turns 18.499999 into 4, rounding keeps 5), and the
 * ball digit belongs beside [BALL_PREFIX] rather than inline in a prompt selector.
 */
fun ballDigitOf(ballNumber: Double): Int {
    val digit = Math.round(ballNumber * 10) % 10
    return digit.toInt()
}
