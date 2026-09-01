package com.tanush.bouncer.utils

import com.tanush.bouncer.analysis.CricketConstants

/**
 * Pure tokenisers behind [parseScore].
 *
 * Separate file so the branching lives away from `parseScore`, which carries a detekt baseline
 * entry keyed on its signature and complexity.
 */

/** Ten wickets ends an innings, so anything above it is a misparse, not a score. */
private const val MAX_WICKETS = 10

/** A score is at most three digits in any real format. */
// Both sides allow three digits: in "1-120" the wicket count comes first, so constraining the
// second group to two digits truncated 120 to 12.
private val RUNS_WICKETS = """^(\d{1,3})\s*[/-]\s*(\d{1,3})\b""".toRegex()
private val RUNS_ONLY = """^(\d{1,3})$""".toRegex()

/**
 * The separator Cricbuzz and Cricinfo both use between a side's innings: "266 & 83/2".
 *
 * A Test side bats twice, so its score is two innings joined, and the numeric consumers (run rate,
 * projections, the win-probability inputs) all want the live one — the last segment.
 */
private const val INNINGS_SEPARATOR = "&"

/** Innings of a score, oldest first: "266 & 83/2" is `["266", "83/2"]`, "199/7" is `["199/7"]`. */
internal fun inningsSegments(cleanedScore: String): List<String> =
    cleanedScore.split(INNINGS_SEPARATOR).map { it.trim() }.filter { it.isNotBlank() }

/** The innings currently being batted, which is the one every rate and projection is about. */
internal fun lastInningsSegment(cleanedScore: String): String =
    inningsSegments(cleanedScore).lastOrNull() ?: cleanedScore.trim()

/** Overs token inside parentheses ("(19.5 Ov)") or before a bare "ov". */
private val OVERS_IN_PARENS = """\(([^)]*)\)""".toRegex()
private val OVERS_TOKEN = """(\d{1,3}(?:\.\d)?)\s*(?:ov|over)""".toRegex(RegexOption.IGNORE_CASE)

/**
 * Extract the overs token from a score string, or "" when there is none.
 *
 * Replaces a `.replace("Ov", "").replace("ov", "")` chain that stripped those two letters wherever
 * they appeared: "(12.4 Overs)" came out as "12.4 ers".
 */
internal fun extractOvers(cleanedScore: String): String {
    OVERS_IN_PARENS.find(cleanedScore)?.groupValues?.get(1)?.let { inside ->
        OVERS_TOKEN.find(inside)?.let { return it.groupValues[1] }
        val bare = inside.trim()
        if (bare.toDoubleOrNull() != null) return bare
    }
    OVERS_TOKEN.find(cleanedScore)?.let { return it.groupValues[1] }
    return ""
}

/** Runs and wickets, or null when [scorePart] is not a score at all. */
internal fun parseRunsAndWickets(scorePart: String): Pair<Int, Int>? {
    val trimmed = scorePart.trim()
    RUNS_WICKETS.find(trimmed)?.let { match ->
        val first = match.groupValues[1].toInt()
        val second = match.groupValues[2].toInt()
        // Australian boards write "1-120" (wickets first). Only one ordering can be valid, since
        // wickets cannot exceed ten — "1-120" is 120 runs for 1, not 1 run for 120 wickets.
        return when {
            second <= MAX_WICKETS -> first to second
            first <= MAX_WICKETS -> second to first
            else -> null
        }
    }
    RUNS_ONLY.find(trimmed)?.let { return it.groupValues[1].toInt() to 0 }
    return null
}

/**
 * Strip the decorations Cricbuzz wraps around a score, leaving the "runs/wickets" part.
 */
internal fun scorePartOf(cleanedScore: String): String =
    cleanedScore.substringBefore("(")
        .let { OVERS_TOKEN.find(it)?.let { m -> it.substringBefore(m.value) } ?: it }
        .trim()

/** Balls implied by an overs token, for callers that need a ball count rather than the text. */
internal fun ballsFromOversToken(overs: String): Int? = CricketConstants.ballsFromOvers(overs)
