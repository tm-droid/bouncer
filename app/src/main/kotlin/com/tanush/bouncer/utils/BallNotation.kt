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
