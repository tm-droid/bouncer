package com.tanush.bouncer.utils

/**
 * Overs the way a scoreboard writes them.
 *
 * Cricbuzz reports a completed over as `19.6`; a person reads that as a typo for `20`. A sixth-ball
 * figure is rolled into the next whole over, and anything that is not `<overs>.<ball>` — blank, the
 * scraper's `0.0` not-started sentinel stays as it is for its callers to hide — passes through.
 */
fun displayOvers(overs: String): String {
    val trimmed = overs.trim()
    val match = OVERS_PATTERN.matchEntire(trimmed) ?: return overs
    val (whole, ball) = match.destructured
    return if (ball == LAST_BALL) (whole.toInt() + 1).toString() else trimmed
}

private val OVERS_PATTERN = Regex("""(\d{1,4})\.(\d)""")
private const val LAST_BALL = "6"
