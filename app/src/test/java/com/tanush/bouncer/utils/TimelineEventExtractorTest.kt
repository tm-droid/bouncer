package com.tanush.bouncer.utils

import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.*
import org.junit.Test

class TimelineEventExtractorTest {

    // ═══════════════════════════════════════════════════════════
    // Wicket Events
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `extracts WICKET from commentary containing WICKET keyword`() {
        val commentary = listOf("10.3 WICKET! Kohli is caught at slip")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }

    @Test
    fun `extracts WICKET from BOWLED text`() {
        val commentary = listOf("15.2 BOWLED! Clean bowled through the gate")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }

    @Test
    fun `extracts WICKET from LBW text`() {
        val commentary = listOf("8.4 LBW! Trapped in front of the stumps")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }

    @Test
    fun `extracts WICKET from RUN OUT text`() {
        val commentary = listOf("12.1 RUN OUT! Direct hit from mid-on")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }

    @Test
    fun `extracts WICKET from STUMPED text`() {
        val commentary = listOf("7.5 STUMPED! Quick work behind the stumps")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }

    @Test
    fun `extracts WICKET from OUT! text`() {
        val commentary = listOf("3.2 OUT! Edged and taken by the keeper")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }

    // ═══════════════════════════════════════════════════════════
    // Boundary Events
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `extracts FOUR from commentary`() {
        val commentary = listOf("4.3 FOUR! Driven through the covers")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.FOUR, events[0].type)
    }

    @Test
    fun `extracts SIX from commentary`() {
        val commentary = listOf("18.2 SIX! Massive hit over long-on")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.SIX, events[0].type)
    }

    @Test
    fun `SIX takes priority over FOUR when both present`() {
        // SIX is checked before FOUR in the extractor, so SIX wins
        val commentary = listOf("5.1 SIX! And it was nearly a FOUR but cleared the ropes")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.any { it.type == TimelineEventType.SIX })
    }

    // ═══════════════════════════════════════════════════════════
    // Milestone Events
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `extracts milestone for century`() {
        val commentary = listOf("That's his century! What an innings")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.any { it.type == TimelineEventType.MILESTONE })
    }

    @Test
    fun `extracts milestone for fifty`() {
        val commentary = listOf("Reaches his fifty off 32 balls")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.any { it.type == TimelineEventType.MILESTONE })
    }

    @Test
    fun `extracts milestone for half-century`() {
        val commentary = listOf("A well-deserved half-century for the opener")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.any { it.type == TimelineEventType.MILESTONE })
    }

    @Test
    fun `extracts milestone for hat-trick`() {
        val commentary = listOf("That's a hat-trick! Three in three!")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.any { it.type == TimelineEventType.MILESTONE })
    }

    @Test
    fun `extracts milestone for fifer`() {
        val commentary = listOf("He has a fifer! Five wickets in the innings")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.any { it.type == TimelineEventType.MILESTONE })
    }

    // ═══════════════════════════════════════════════════════════
    // Over End Events
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `extracts end of over event`() {
        val commentary = listOf("End of over 10, 65 for 2")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.OVER_END, events[0].type)
    }

    // ═══════════════════════════════════════════════════════════
    // Over Number Parsing
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `parses over number from commentary prefix`() {
        val commentary = listOf("14.5 FOUR! Slashed over point")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(14.5f, events[0].over, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `empty commentary returns empty events`() {
        val events = TimelineEventExtractor.extract(emptyList())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `blank lines are skipped`() {
        val commentary = listOf("", "   ", "\t")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `commentary without keywords returns empty events`() {
        val commentary = listOf("Good length delivery, defended back to the bowler")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `strips AI Fused prefix before parsing`() {
        val commentary = listOf("[AI Fused] 6.1 FOUR! Beautiful cover drive")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.FOUR, events[0].type)
        assertEquals(6.1f, events[0].over, 0.01f)
    }

    @Test
    fun `multiple events from multiple lines`() {
        val commentary = listOf(
            "10.2 FOUR! Driven past mid-off",
            "10.3 SIX! Over long-on",
            "10.4 WICKET! Caught at deep midwicket",
        )
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(3, events.size)
    }

    @Test
    fun `events sorted descending by over number`() {
        val commentary = listOf(
            "5.1 FOUR! Cut to the boundary",
            "10.3 SIX! Huge hit",
            "8.2 WICKET! Bowled him",
        )
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(3, events.size)
        // Should be sorted descending: 10.3, 8.2, 5.1
        assertTrue(events[0].over >= events[1].over)
        assertTrue(events[1].over >= events[2].over)
    }

    @Test
    fun `description is truncated to 120 characters`() {
        val longLine = "10.1 FOUR! " + "A".repeat(200)
        val events = TimelineEventExtractor.extract(listOf(longLine))
        assertEquals(1, events.size)
        assertTrue(events[0].description.length <= 120)
    }

    @Test
    fun `wicket takes priority over boundary`() {
        // If a line has both WICKET and FOUR, wicket should win
        val commentary = listOf("8.4 WICKET! Caught at the FOUR boundary rope")
        val events = TimelineEventExtractor.extract(commentary)
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.WICKET, events[0].type)
    }
}
