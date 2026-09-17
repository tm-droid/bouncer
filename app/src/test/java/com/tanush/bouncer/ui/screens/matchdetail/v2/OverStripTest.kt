package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The strip above the feed shows the over being bowled, and says which over that is.
 *
 * Cricbuzz publishes an `overSummary` only once an over ENDS — it hangs off the delivery that closes
 * it, so ball `48.6` carries `overNumber: 49`. `bouncer-match-sync` published that summary as
 * `recentBalls` with no number attached, and the device rendered it under a header reading "This
 * Over". While over 49 was being bowled the strip therefore showed over 48's six balls, directly
 * above a feed showing 48.1, 48.2, 48.3 — two different overs, nothing saying so.
 *
 * `recentBallsOver` is what settles it: the summary is used only when it describes the same over the
 * feed is on.
 */
class OverStripTest {

    private fun ball(
        label: String,
        text: String,
        runs: Int? = null,
        extra: String? = null,
        event: TimelineEventType = TimelineEventType.OTHER,
        innings: Int? = 2,
    ) = CommentaryEntry(text = text, event = event, ball = label, runs = runs, extra = extra, innings = innings)

    /** Newest first, as the feed arrives. */
    private fun overInProgress() = listOf(
        ball("48.4", "Starc to Smith, 1 run", runs = 1),
        ball("48.3", "Starc to Root, out Bowled!", runs = 0, event = TimelineEventType.WICKET),
        ball("48.2", "Starc to Root, FOUR", runs = 4, event = TimelineEventType.FOUR),
        ball("48.1", "Starc to Root, no run", runs = 0),
    )

    @Test
    fun `an over in progress comes from the feed, not from the previous over's summary`() {
        val strip = buildOverStrip(
            commentary = overInProgress(),
            summary = listOf("1", "0", "0", "4", "0", "1"),
            summaryOver = 48,
        )

        // Balls 48.1-48.4 are the first four deliveries of over 49. A feed-derived dot is written
        // "." where Cricbuzz's own summary writes "0"; both classify as a dot chip.
        assertEquals(49, strip?.overNumber)
        assertEquals(listOf(".", "4", "W", "1"), strip?.balls)
    }

    @Test
    fun `the summary wins once it describes the over the feed is on`() {
        val summary = listOf("1", "Wd3", "0", "0", "4", "W", "W")
        val strip = buildOverStrip(
            commentary = overInProgress(),
            // Over 49 has now ended, so Cricbuzz has published its summary.
            summary = summary,
            summaryOver = 49,
        )

        assertEquals(49, strip?.overNumber)
        // All seven glyphs, extras included. `takeLast(6)` used to drop the opening delivery of any
        // over that ran past six, and `Wd3` — a wide worth three — used to render as `Wd`.
        assertEquals(summary, strip?.balls)
    }

    @Test
    fun `a summary one over behind never overwrites the live over`() {
        val strip = buildOverStrip(
            commentary = listOf(ball("48.1", "Starc to Root, no run", runs = 0)),
            summary = listOf("6", "4", "1", "0", "3", "1"),
            summaryOver = 48,
        )

        assertEquals(49, strip?.overNumber)
        assertEquals(listOf("."), strip?.balls)
    }

    @Test
    fun `the strip stops at the over boundary`() {
        val commentary = overInProgress() + listOf(
            ball("47.6", "Cummins to Smith, 2 runs", runs = 2),
            ball("47.5", "Cummins to Smith, no run", runs = 0),
        )

        assertEquals(listOf(".", "4", "W", "1"), buildOverStrip(commentary, emptyList(), null)?.balls)
    }

    @Test
    fun `the strip stops at the innings boundary`() {
        val commentary = listOf(
            ball("48.2", "Cummins to Smith, 1 run", runs = 1, innings = 3),
            // Same over number, previous innings: a Test's overs restart, so this is a different over.
            ball("48.1", "Starc to Root, no run", runs = 0, innings = 2),
        )

        assertEquals(listOf("1"), buildOverStrip(commentary, emptyList(), null)?.balls)
    }

    @Test
    fun `with no commentary the summary is all there is`() {
        val strip = buildOverStrip(emptyList(), listOf("6", "4", "1", "0", "3", "1"), 49)

        assertEquals(49, strip?.overNumber)
        assertEquals(listOf("6", "4", "1", "0", "3", "1"), strip?.balls)
    }

    @Test
    fun `an older payload carrying no over number still renders, unnumbered`() {
        val strip = buildOverStrip(emptyList(), listOf("0", "1"), null)

        assertNull(strip?.overNumber)
        assertEquals(listOf("0", "1"), strip?.balls)
    }

    @Test
    fun `a ball-count format invents no over`() {
        // The Hundred counts 1..100 and has no over to number; the label is a bare counter.
        val strip = buildOverStrip(
            commentary = listOf(ball("72", "Mills to Hollard, no run", runs = 0)),
            summary = listOf("0", "1"),
            summaryOver = null,
        )

        assertNull(strip?.overNumber)
        assertEquals(listOf("0", "1"), strip?.balls)
    }

    @Test
    fun `nothing at all is no strip, not an empty one`() {
        assertNull(buildOverStrip(emptyList(), emptyList(), null))
    }

    @Test
    fun `a delivery nothing can name holds its place`() {
        // No runs column, no event, and prose that states no outcome: the ball happened, and the
        // strip must not silently close up over it.
        val strip = buildOverStrip(
            commentary = listOf(
                ball("48.2", "Starc to Smith, 1 run", runs = 1),
                ball("48.1", "Starc to Root,", runs = null),
            ),
            summary = emptyList(),
            summaryOver = null,
        )

        assertEquals(listOf(UNKNOWN_BALL_GLYPH, "1"), strip?.balls)
    }

    @Test
    fun `session notes above the deliveries are walked past`() {
        val commentary = listOf(
            CommentaryEntry(text = "Drinks break.", event = null, ball = null, innings = 2),
            ball("48.1", "Starc to Root, no run", runs = 0),
        )

        assertEquals(49, buildOverStrip(commentary, emptyList(), null)?.overNumber)
    }
}
