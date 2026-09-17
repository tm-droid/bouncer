package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Immutable
import com.tanush.bouncer.data.remote.CommentaryEntry

/** The over strip's placeholder for a delivery nothing can name: quiet, and never a scoring shot. */
internal const val UNKNOWN_BALL_GLYPH = "\u2013"

/** How far back the strip looks for the newest over. One over is a handful of rows; 40 is slack. */
private const val OVER_STRIP_SCAN = 40

/**
 * The chips above the feed: the over being bowled, and its real number.
 *
 * Two sources, and the order between them is the whole point. `match-sync` publishes Cricbuzz's own
 * `overSummary`, which is authoritative — it writes `W1` for a run-out that completed a run, and the
 * prose never says so — but Cricbuzz hangs that summary off the delivery that ENDS an over, so it
 * describes a COMPLETED over and never the one in progress. Rendering it under a header reading
 * "This Over" showed the previous over's six balls above a feed showing this over's first three.
 *
 * So the summary is used only when [summaryOver] says it describes the same over the feed is on;
 * otherwise the feed's own deliveries are. Either way the strip carries the over number, because a
 * strip that cannot say which over it means is what let the mismatch go unnoticed.
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
    if (live != null && summaryOver != null && summaryOver == live.overNumber && summary.isNotEmpty()) {
        return OverStrip(summaryOver, summary)
    }
    if (live != null) return OverStrip(live.overNumber, live.glyphs)
    if (summary.isNotEmpty()) return OverStrip(summaryOver, summary)
    return null
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
