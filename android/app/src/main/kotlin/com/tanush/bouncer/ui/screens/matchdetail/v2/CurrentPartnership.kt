package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.Innings

/**
 * The unbroken stand in [innings], as runs and legal balls since the last wicket fell.
 *
 * Read off the innings' own total and fall of wickets, so extras count toward it as they do in a
 * scorecard's partnership column. Null when the innings' score or overs cannot be read, or the
 * last wicket's figures are past them — a card a moment behind its own total.
 */
internal fun currentPartnership(innings: Innings): Pair<Int, Int>? {
    val now = runsAndBalls(innings.score, innings.overs) ?: return null
    // The latest wicket is the one at the highest score: the wicket numbers are often not stored.
    val last = innings.fallOfWickets.maxByOrNull { leadingRuns(it.score) ?: -1 }
    val atWicket = if (last == null) 0 to 0 else runsAndBalls(last.score, last.overs) ?: return null
    val runs = now.first - atWicket.first
    val balls = now.second - atWicket.second
    return (runs to balls).takeIf { runs >= 0 && balls >= 0 }
}

/**
 * "141/3" at "16.3" as 141 runs off 99 balls; null when either does not read. The leading run
 * count only: a scorecard innings reads "156-4 f/o", the live list "156/4".
 */
private fun runsAndBalls(score: String, overs: String): Pair<Int, Int>? {
    val runs = leadingRuns(score) ?: return null
    val balls = ballsIn(overs) ?: return null
    return runs to balls
}

/** "16.3" (or the card's "16.3 Ov") as 99 legal balls. Null unless it reads as overs.balls, balls under six. */
private fun ballsIn(overs: String): Int? {
    val m = LEADING_OVERS.find(overs) ?: return null
    val whole = m.groupValues[1].toIntOrNull() ?: return null
    val part = m.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
    if (part !in 0 until BALLS_IN_OVER) return null
    return whole * BALLS_IN_OVER + part
}

private const val BALLS_IN_OVER = 6
private val LEADING_RUNS = Regex("""^\s*(\d+)""")
private val LEADING_OVERS = Regex("""^\s*(\d+)(?:\.(\d+))?""")

private fun leadingRuns(score: String): Int? = LEADING_RUNS.find(score)?.groupValues?.get(1)?.toIntOrNull()
