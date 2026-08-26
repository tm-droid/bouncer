package com.tanush.bouncer.ui.screens.matchdetail.v2

// ── What a delivery was worth, and what kind of delivery it was ──────────────
//
// Split from CommentaryParsing.kt, which owns the delivery's label and text. Same parsing
// pass, addressed at the outcome: neither the number nor the kind survives
// `bouncer-match-sync`'s rewrite, so both are read from Cricbuzz's raw clause and nowhere else.

/**
 * The kind of delivery, where it is not an ordinary one.
 *
 * Carried as data for the same reason [ParsedBall.runs] is: the rewrite turns "wide" into "strays
 * down leg", so deciding this by searching the rendered prose for the word "wide" was the same
 * guess-from-prose failure that put a dot ball on every single.
 */
internal enum class BallExtra { WIDE, NOBALL, BYE, LEGBYE }

/** [runs] is the delivery's TOTAL, extras included; [extra] is null for an ordinary delivery. */
internal data class BallOutcome(val runs: Int?, val extra: BallExtra?)

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

/**
 * Both wordings Cricbuzz uses for a dismissal.
 *
 * It pushes a short flash node ("THATS OUT!! Caught!!") and then the detailed one ("out Caught by
 * Steven Smith!! …"). Measured over 124 deliveries across 8 live matches, the flash form was 11 of
 * the 12 clauses the `^out\b`-only version could not read.
 */
private val OUTCOME_WICKET = Regex("^(?:out\\b|that'?s out\\b)", RegexOption.IGNORE_CASE)

/**
 * Extras, in Cricbuzz's own shorthand ("1 wd", "2 nb", "1 lb", "4 b") plus the bare word it uses
 * when the extra is worth one. Group 2 is the word, which decides the [BallExtra].
 */
private val EXTRA_KIND =
    Regex("^(?:(\\d{1,3})\\s*)?(wd|wides?|nb|no.?balls?|lb|leg ?byes?|byes?|b)$", RegexOption.IGNORE_CASE)

/**
 * The runs a bare-word extra carries one segment later — "leg byes, 1 run".
 *
 * A boundary bye puts the word FOUR there rather than a numeral ("byes, FOUR" — `B4` in Cricbuzz's
 * over summary), which the numeric-only version of this scored as 1.
 */
private val EXTRA_FOLLOW =
    Regex("^[^,]+,\\s*[^,]+,\\s*(?:(\\d{1,3}) runs?|(four|six))\\b", RegexOption.IGNORE_CASE)

/**
 * What the delivery was worth and what kind it was, from Cricbuzz's own outcome clause — or
 * `BallOutcome(null, null)` when the clause is not one this recognises.
 *
 * Port of `parseBallOutcome` in `appwrite/functions/match-sync/src/cricbuzz.js`, which is the copy
 * that actually writes the `runs` and `extra` columns; `CommentaryRunsParityTest` fails the build
 * on a one-sided edit. This one runs only where those columns are absent: a row written before they
 * existed, or the on-device scrape fallback, both of which still carry the raw text.
 *
 * Null runs, never 0, for an unrecognised clause. Defaulting to 0 is what put a dot ball on every
 * single, and a MAIDEN badge on an over of six of them.
 */
internal fun parseBallOutcome(text: String): BallOutcome {
    // Trailing punctuation is trimmed because the clause is matched whole: a "1 run." reaching an
    // anchored `^(\d+) runs?$` would score null and lose a delivery the raw text names outright.
    val clause = BALL_OUTCOME.find(text)?.groupValues?.get(1)?.trim()
        ?.replace(TRAILING_PUNCTUATION, "").orEmpty()
    if (clause.isEmpty()) return BallOutcome(null, null)
    if (NO_RUN.matches(clause)) return BallOutcome(0, null)
    RUNS_OFF_BAT.find(clause)?.let { return BallOutcome(it.groupValues[1].toIntOrNull(), null) }
    if (OUTCOME_FOUR.matches(clause)) return BallOutcome(4, null)
    if (OUTCOME_SIX.matches(clause)) return BallOutcome(6, null)
    // A dismissal's clause carries no runs. A run-out that completed one is `W1` in the over
    // summary, but the clause does not say so, and inventing the 1 is the failure this file exists
    // to prevent.
    if (OUTCOME_WICKET.containsMatchIn(clause)) return BallOutcome(0, null)
    return extraOutcomeOf(clause, text)
}

/**
 * The `extra` column's value as a [BallExtra], or null for an absent or unrecognised one.
 *
 * Unknown values are null rather than a throw, for the same reason `parseCommentaryEvent` handles
 * them that way: a server-side vocabulary change must degrade to "the client will read this one
 * from the text", never to a failed write that loses the commentary line.
 */
internal fun parseBallExtra(raw: String?): BallExtra? = when (raw) {
    "WIDE" -> BallExtra.WIDE
    "NOBALL" -> BallExtra.NOBALL
    "BYE" -> BallExtra.BYE
    "LEGBYE" -> BallExtra.LEGBYE
    else -> null
}

/** Total runs off the delivery, or null — [parseBallOutcome] without the kind. */
internal fun parseBallRuns(text: String): Int? = parseBallOutcome(text).runs

/**
 * The extra's kind and total, or `BallOutcome(null, null)` when the clause is not an extra.
 *
 * Two numbers with two meanings, and Cricbuzz's own over summary is what settles them. An INLINE
 * number is the delivery's total, penalty included: `1 Wd3 0 0 4 W W` totals `overRuns = 8`, so
 * `Wd3` is 3 and not 4. A number in the FOLLOWING segment is the runs scored, to which the
 * penalty is added: match 169561's over 5 reads `B1 0 1 0 1 N2 0` for `overRuns = 6`, so its
 * "no ball, 2 runs" is 3; match 154452's `6 Wd 6 6 1 4 N1 1` totals 27, so "no ball, 1 run" is 2.
 *
 * Only wides and no-balls carry a penalty. Byes and leg byes are the batting side's, so
 * "byes, 1 run" is worth exactly the one run — `B1`.
 */
private fun extraOutcomeOf(clause: String, text: String): BallOutcome {
    val m = EXTRA_KIND.find(clause) ?: return BallOutcome(null, null)
    val kind = extraKindOf(m.groupValues[2]) ?: return BallOutcome(null, null)
    val inline = m.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()
    if (inline != null) return BallOutcome(inline, kind)
    val penalty = if (kind == BallExtra.WIDE || kind == BallExtra.NOBALL) 1 else 0
    // A bare "wide" or "no ball" scored nothing beyond its penalty; a bare "byes" is worth at least
    // the one run Cricbuzz writes as `B1`.
    val follow = followRunsOf(text) ?: return BallOutcome(if (penalty > 0) penalty else 1, kind)
    return BallOutcome(penalty + follow, kind)
}

// Named, not inline, so CommentaryRunsParityTest can compare them against the JS copies. An
// unnamed literal here is a pattern the guard cannot see, which is how a one-sided edit gets in.
private val KIND_WIDE = Regex("^(?:wd|wides?)$", RegexOption.IGNORE_CASE)
private val KIND_NOBALL = Regex("^(?:nb|no.?balls?)$", RegexOption.IGNORE_CASE)
private val KIND_LEGBYE = Regex("^(?:lb|leg ?byes?)$", RegexOption.IGNORE_CASE)
private val KIND_BYE = Regex("^(?:byes?|b)$", RegexOption.IGNORE_CASE)

private fun extraKindOf(word: String): BallExtra? = when {
    KIND_WIDE.matches(word) -> BallExtra.WIDE
    KIND_NOBALL.matches(word) -> BallExtra.NOBALL
    KIND_LEGBYE.matches(word) -> BallExtra.LEGBYE
    KIND_BYE.matches(word) -> BallExtra.BYE
    else -> null
}

private fun followRunsOf(text: String): Int? {
    val m = EXTRA_FOLLOW.find(text) ?: return null
    m.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { return it }
    return if (m.groupValues[2].equals("four", ignoreCase = true)) 4 else 6
}

/**
 * The over's total, or null when any ball in it has an unknown run value.
 *
 * All-or-nothing on purpose: one unknown ball makes the sum a guess, and a guessed total reading 0
 * is what put a MAIDEN badge on an over of six singles.
 */
internal fun overTotalRuns(balls: List<ParsedBall>): Int? =
    if (balls.all { it.runs != null }) balls.sumOf { it.runs ?: 0 } else null

/**
 * A maiden is an over with no runs charged to the BOWLER — which is not the same as an over worth
 * nothing.
 *
 * Byes and leg byes are not the bowler's, so `0 0 0 0 0 L1` is still a maiden despite Cricbuzz
 * scoring the over 1; wides and no-balls are, so an over containing either never is. Summing the
 * raw total and testing it against zero — what this did before the `extra` column existed — got the
 * bye case wrong in the strictest possible way, by denying a bowler a maiden they had bowled.
 */
internal fun isMaidenOver(balls: List<ParsedBall>): Boolean {
    if (balls.isEmpty() || balls.any { it.runs == null }) return false
    if (balls.any { it.extra == BallExtra.WIDE || it.extra == BallExtra.NOBALL }) return false
    return balls.filter { it.extra != BallExtra.BYE && it.extra != BallExtra.LEGBYE }
        .sumOf { it.runs ?: 0 } == 0
}
