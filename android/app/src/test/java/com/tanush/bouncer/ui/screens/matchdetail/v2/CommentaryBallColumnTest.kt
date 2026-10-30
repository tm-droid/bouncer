package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The over and ball number come from the row's `ball` column, not from the prose.
 *
 * `bouncer-match-sync` prefixes the line with the ball only on the AI path; when the provider chain
 * produces nothing it writes the raw Cricbuzz text unprefixed (`ai_generated = false`), and that
 * passthrough is the dominant path in production — 1103 of them in six days when this was written.
 * Every one of those rows parsed to `overNumber = 0f`, which hides the pill and drops the delivery
 * out of the over grouping entirely. That is the "over and ball number doesn't show up" report.
 */
class CommentaryBallColumnTest {

    @Test
    fun `passthrough line with no prefix still gets its over from the ball column`() {
        val entry = CommentaryEntry(
            text = "Bumrah to Root, no run, angled across off stump.",
            event = TimelineEventType.OTHER,
            ball = "18.5",
        )
        val parsed = entry.parseBall()
        assertEquals(18.5f, parsed.overNumber, 0.001f)
        assertEquals("18.5", parsed.ballLabel)
        // The whole line is the delivery text — nothing was stripped as a prefix.
        assertEquals("Bumrah to Root, no run, angled across off stump.", parsed.ballText)
    }

    @Test
    fun `the ball column wins over a prefix in the text`() {
        val parsed = CommentaryEntry("18.5: Bumrah to Root, no run.", null, "18.5").parseBall()
        assertEquals(18.5f, parsed.overNumber, 0.001f)
        assertEquals("18.5", parsed.ballLabel)
        assertEquals("Bumrah to Root, no run.", parsed.ballText)
    }

    @Test
    fun `a Hundred delivery is labelled but has no over`() {
        // The Hundred counts 1..100 and has no overs. The label must survive; the over must not be
        // invented, because grouping on a fabricated over is worse than not grouping at all.
        val parsed = CommentaryEntry("Jansen to Banton, FOUR.", TimelineEventType.FOUR, "Ball 72").parseBall()
        assertEquals("72", parsed.ballLabel)
        assertEquals(0f, parsed.overNumber, 0.001f)
    }

    @Test
    fun `a row with no ball column still parses a prefixed line`() {
        val parsed = CommentaryEntry("40.5: Albert drives through cover.", null, null).parseBall()
        assertEquals(40.5f, parsed.overNumber, 0.001f)
        assertEquals("40.5", parsed.ballLabel)
    }

    @Test
    fun `a non-delivery line has no label and stays out of the ball path`() {
        val parsed = CommentaryEntry("Rain has stopped play.", null, null).parseBall()
        assertNull(parsed.ballLabel)
        assertEquals(0f, parsed.overNumber, 0.001f)
    }

    @Test
    fun `a blank ball column is treated as absent`() {
        val parsed = CommentaryEntry("12.3: Starc bangs it in short.", null, "").parseBall()
        assertEquals(12.3f, parsed.overNumber, 0.001f)
    }
}
