package com.tanush.bouncer.ui.screens.matchdetail.v2

// ── What a delivery was worth ────────────────────────────────────────────────
//
// Split from CommentaryParsing.kt, which owns the delivery's label and text. Same parsing
// pass, addressed at the runs: the number is the one fact about a ball that does not survive
// `bouncer-match-sync`'s rewrite, so it is read from Cricbuzz's raw clause and nowhere else.

/**
 * Cricbuzz's outcome clause: `"<Bowler> to <Batter>, <outcome>[, description]"`.
 *
 * Anchored to that second segment on purpose. The unanchored `\b(\d+) runs?\b` this replaced
 * scanned the whole line, so a dot ball whose prose read "India need 42 runs from 30" rendered a
 * `42` pill — the inverse of the bug below, from the same regex.
 */
private val BALL_OUTCOME = Regex("^[^,]+,\\s*([^,]+)")
private val TRAILING_PUNCTUATION = Regex("[.!]+$")
private val RUNS_OFF_BAT = Regex("^(\\d{1,3}) runs?$", RegexOption.IGNORE_CASE)
private val NO_RUN = Regex("^no run$", RegexOption.IGNORE_CASE)
private val OUTCOME_FOUR = Regex("^four$", RegexOption.IGNORE_CASE)
private val OUTCOME_SIX = Regex("^six$", RegexOption.IGNORE_CASE)
private val OUTCOME_WICKET = Regex("^out\\b", RegexOption.IGNORE_CASE)
private val EXTRA_RUNS =
    Regex("^(?:(\\d{1,3})\\s*)?(?:wd|wides?|nb|no.?balls?|lb|leg ?byes?|byes?|b)$", RegexOption.IGNORE_CASE)
// Cricbuzz writes the bare-word extras with their runs in the NEXT segment — "leg byes, 4 runs" —
// where the shorthand carries them in the same one ("4 lb"). Without this, every such delivery
// scored 1: right for the common case and silently wrong for the rest.
private val EXTRA_FOLLOW_RUNS =
    Regex("^[^,]+,\\s*[^,]+,\\s*(\\d{1,3}) runs?\\b", RegexOption.IGNORE_CASE)

/**
 * Total runs off the delivery, extras included, read from Cricbuzz's own outcome clause — or null
 * when the clause is not one this recognises.
 *
 * Port of `parseBallRuns` in `appwrite/functions/match-sync/src/cricbuzz.js`, which is the copy
 * that actually writes the `runs` column; `CommentaryRunsParityTest` fails the build on a one-sided
 * edit. This one runs only where that column is absent: a row written before it existed, or the
 * on-device scrape fallback, both of which still carry the raw text.
 *
 * Null, never 0, for an unrecognised clause. Defaulting to 0 is what put a dot ball on every
 * single, and a MAIDEN badge on an over of six of them.
 */
internal fun parseBallRuns(text: String): Int? {
    // Trailing punctuation is trimmed because the clause is matched whole: a "1 run." reaching an
    // anchored `^(\d+) runs?$` would score null and lose a delivery the raw text names outright.
    val clause = BALL_OUTCOME.find(text)?.groupValues?.get(1)?.trim()
        ?.replace(TRAILING_PUNCTUATION, "").orEmpty()
    if (clause.isEmpty()) return null
    if (NO_RUN.matches(clause)) return 0
    RUNS_OFF_BAT.find(clause)?.let { return it.groupValues[1].toIntOrNull() }
    if (OUTCOME_FOUR.matches(clause)) return 4
    if (OUTCOME_SIX.matches(clause)) return 6
    // A dismissal's clause carries no runs, and a run-out's completed runs are not in it either.
    if (OUTCOME_WICKET.containsMatchIn(clause)) return 0
    return extraRunsOf(clause, text)
}

/**
 * Runs off an extra, or null when the clause is not an extra at all.
 *
 * Cricbuzz's shorthand carries the number in the clause ("4 lb"); its bare words carry it one
 * segment later ("leg byes, 4 runs"). One is worth the default of 1 only when neither is there.
 */
private fun extraRunsOf(clause: String, text: String): Int? {
    val m = EXTRA_RUNS.find(clause) ?: return null
    m.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { return it }
    return EXTRA_FOLLOW_RUNS.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
}

/**
 * The over's total, or null when any ball in it has an unknown run value.
 *
 * All-or-nothing on purpose: one unknown ball makes the sum a guess, and a guessed total reading 0
 * is what put a MAIDEN badge on an over of six singles.
 */
internal fun overTotalRuns(balls: List<ParsedBall>): Int? =
    if (balls.all { it.runs != null }) balls.sumOf { it.runs ?: 0 } else null

/** A maiden needs a known zero total and no extras — an unknown total is never a maiden. */
internal fun isMaidenOver(balls: List<ParsedBall>): Boolean =
    overTotalRuns(balls) == 0 && balls.isNotEmpty() && balls.none { it.isWide || it.isNoBall }
