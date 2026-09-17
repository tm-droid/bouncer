package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Immutable
import com.tanush.bouncer.data.remote.CommentaryEntry

/** The over strip's placeholder for a delivery nothing can name: quiet, and never a scoring shot. */
internal const val UNKNOWN_BALL_GLYPH = "\u2013"

/** How far back the strip looks for the newest over. One over is a handful of rows; 40 is slack. */
private const val OVER_STRIP_SCAN = 40

/**
 * The chips for the over being bowled, and its real number.
 *
 * Two sources. `match-sync` publishes Cricbuzz's miniscore `recentOvsStats` — the string behind
 * Cricbuzz's own "this over" bar, live ball by ball, and authoritative: it writes `W1` for a run-out
 * that completed a run, which the prose never states. The commentary feed is the other: it arrives
 * per ball over Realtime, where the payload is rewritten about once a minute.
 *
 * So the payload wins unless the feed has already moved on to a later over, and the strip always
 * carries the over's number. The first version of this strip showed the last COMPLETED over's
 * `overSummary` under a header reading "This Over", above a feed showing the next over — the number
 * is what makes that kind of disagreement visible, and comparable.
 *
 * @param summary the payload's `recentBalls`.
 * @param summaryOver the payload's `recentBallsOver`; null on a row written before that field, and
 *   on a ball-count format with no over to number.
 */
internal fun buildOverStrip(
    commentary: List<CommentaryEntry>,
    summary: List<String>,
    summaryOver: Int?,
): OverStrip? {
    val live = newestOver(commentary)
    val payloadCurrent = summaryOver != null && (live == null || summaryOver >= live.overNumber)
    return when {
        summary.isNotEmpty() && payloadCurrent -> OverStrip(summaryOver, summary)
        live != null -> OverStrip(live.overNumber, live.glyphs)
        summary.isNotEmpty() -> OverStrip(summaryOver, summary)
        else -> null
    }
}

/** The strip's contents: the over's number, and its deliveries oldest-first. */
@Immutable
data class OverStrip(
    /**
     * Cricbuzz's over number — ball `48.4` sits in over **49** — or null when nothing names one: a
     * ball-count format like The Hundred, or a payload written before `recentBallsOver` existed.
     */
    val overNumber: Int?,
    val balls: List<String>,
)

private class LiveOver(val overNumber: Int, val glyphs: List<String>)

/**
 * The newest over in the feed, read the way [buildCommentaryBlocks] groups it.
 *
 * The feed is newest-first, so the run at its head is the latest over. [ParsedBall.overNumber]
 * counts COMPLETED overs (ball `48.4` gives 48), so the over's own number is one more.
 */
private fun newestOver(commentary: List<CommentaryEntry>): LiveOver? {
    val balls = commentary.take(OVER_STRIP_SCAN)
        .map { it.parseBall() }
        // Session notes and weather lines sit above the deliveries; walk past them.
        .dropWhile { it.ballLabel == null || it.overNumber <= 0f }
    val head = balls.firstOrNull() ?: return null
    val run = balls.takeWhile { sameBlock(head, it) }
    return LiveOver(
        overNumber = head.overNumber.toInt() + 1,
        glyphs = run.asReversed().map { it.toBallString() ?: UNKNOWN_BALL_GLYPH },
    )
}
