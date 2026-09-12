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

    // ═══════════════════════════════════════════════════════════
    // Wicket exclusion phrases
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `MID-WICKET does not trigger wicket event`() {
        val commentary = listOf("4.2 FOUR! Swept to the MID-WICKET boundary")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue("Should have a FOUR event", events.any { it.type == TimelineEventType.FOUR })
        assertTrue("Should NOT have a WICKET event", events.none { it.type == TimelineEventType.WICKET })
    }

    @Test
    fun `OVER THE WICKET does not trigger wicket event`() {
        val commentary = listOf("7.1 Coming over the wicket to the right-hander")
        val events = TimelineEventExtractor.extract(commentary)
        assertTrue("Should NOT have a WICKET event", events.none { it.type == TimelineEventType.WICKET })
    }

    // ── deduplicateByBall (G22: perf only, output must not move) ─────────────

    /** Progressive updates for the same ball, plus a near-duplicate pair and a non-ball line. */
    private val dedupFixture = listOf(
        "18.1 Bumrah to Maxwell, no run",
        "18.1 Bumrah to Maxwell, no run, beaten outside off stump",
        "18.2 Bumrah to Maxwell, FOUR, driven through the covers",
        "[AI Fused] 18.3 Bumrah to Maxwell, SIX over long on",
        "18.4  Bumrah   to   Maxwell,  1 run   ",
        "Drinks break on the field",
        "18.5 Bumrah to Green, WICKET! caught behind",
        "104.3 Ashwin to Root, defended solidly back down the ground",
    )

    @Test
    fun `deduplicateByBall keeps the longest progressive update per ball`() {
        val result = TimelineEventExtractor.deduplicateByBall(dedupFixture)
        // Only 18.1 collapses, and only because its two entries are the same delivery: the longer
        // is a strict extension of the shorter. Every other ball survives.
        //
        // 18.3 and 18.4 used to be absent here, and the assertion said so as though it were the
        // contract. It was not — it was the bug. Pass 2 compared lines across ball numbers, and with
        // the old 0.4 ratio the effective prefix was 15 characters, the width of
        // "<bowler> to <bat". So "bumrah to maxwell, six over long on" read as a duplicate of
        // "bumrah to maxwell, four, driven through the covers" and was deleted. In production that
        // rendered a six-ball over as one or two deliveries, always the longest lines in it — the
        // wickets and the boundaries.
        assertEquals(
            listOf(
                "18.1 Bumrah to Maxwell, no run, beaten outside off stump",
                "18.2 Bumrah to Maxwell, FOUR, driven through the covers",
                "18.3 Bumrah to Maxwell, SIX over long on",
                "18.4 Bumrah to Maxwell, 1 run",
                "Drinks break on the field",
                "18.5 Bumrah to Green, WICKET! caught behind",
                "104.3 Ashwin to Root, defended solidly back down the ground",
            ),
            result,
        )
    }

    @Test
    fun `deduplicateByBall handles degenerate blocks`() {
        assertEquals(emptyList<String>(), TimelineEventExtractor.deduplicateByBall(emptyList()))
        assertEquals(emptyList<String>(), TimelineEventExtractor.deduplicateByBall(listOf("", "   ")))
        assertEquals(
            listOf("Drinks break on the field"),
            TimelineEventExtractor.deduplicateByBall(
                listOf("Drinks break on the field", "Drinks break on the field"),
            ),
        )
        // Under DEDUP_MIN_LENGTH, so pass 2 cannot touch them; the identical pair still collapses in
        // pass 1 on the shared ball number.
        assertEquals(
            listOf("18.1 short", "104.3 Ashwin to Root"),
            TimelineEventExtractor.deduplicateByBall(
                listOf("18.1 short", "18.1 short", "  ", "104.3 Ashwin to Root"),
            ),
        )
    }

    @Test
    fun `deduplicateByBall is idempotent`() {
        val once = TimelineEventExtractor.deduplicateByBall(dedupFixture)
        assertEquals(once, TimelineEventExtractor.deduplicateByBall(once))
    }

    @Test
    fun `deduplicateByBall drops a near-duplicate prefix pair for one ball`() {
        val lines = listOf(
            "1.1 Starc to Rohit, defended back down the ground with soft hands",
            "1.1 Starc to Rohit, defended back down the ground",
        )
        // One delivery, published twice — the shorter is a truncated version of the fuller line.
        assertEquals(
            listOf("1.1 Starc to Rohit, defended back down the ground with soft hands"),
            TimelineEventExtractor.deduplicateByBall(lines),
        )
    }

    @Test
    fun `deduplicateByBall keeps near-identical lines that are different deliveries`() {
        // 1.1 and 2.1 read almost the same because the same bowler beat the same batter twice.
        // Different balls are different deliveries; nothing about shared prose says otherwise.
        val lines = listOf(
            "1.1 Starc to Rohit, defended back down the ground with soft hands",
            "2.1 Starc to Rohit, defended back down the ground",
        )
        assertEquals(2, TimelineEventExtractor.deduplicateByBall(lines).size)
    }

    @Test
    fun `deduplicateByBall collapses whitespace and strips the AI Fused prefix`() {
        val result = TimelineEventExtractor.deduplicateByBall(
            listOf("[AI Fused] 18.4  Bumrah   to   Maxwell,  1 run   "),
        )
        assertEquals(listOf("18.4 Bumrah to Maxwell, 1 run"), result)
    }

    @Test
    fun `deduplicateByBall handles a full commentary block`() {
        // 40 lines is roughly one render, and the size at which the old inner-loop Regex
        // construction cost ~1500 compilations. Every line shares the "Bowler to Batter, " opening
        // and every line is a different delivery, so all 40 must survive. This used to assert a
        // single survivor — 40 deliveries collapsing to one on a shared 15-character opening is the
        // clearest statement of the bug that emptied the commentary tab.
        val many = (1..40).map { "${it / 6}.${it % 6 + 1} Bowler to Batter, delivery number $it" }
        assertEquals(many, TimelineEventExtractor.deduplicateByBall(many))
    }

    @Test
    fun `deduplicateByBall keeps every delivery of an over of raw passthrough rows`() {
        // The real over 18 of ETPL match 169380, as bouncer-match-sync stored it: raw Cricbuzz
        // text with no ball prefix in the line, because the LLM rewrite budget did not reach it.
        // Three deliveries are byte-identical, and all six survive only because the ball column —
        // not the prose — is the dedup key. The screenshot that opened this bug showed 18.4 and
        // 18.1 and nothing else.
        val over = listOf(
            "18.1" to "Tymal Mills to Ashwin, out Lbw!! Ashwin lbw b Tymal Mills 1(2)",
            "18.2" to "Tymal Mills to Matthew Hollard, no run",
            "18.3" to "Tymal Mills to Matthew Hollard, no run",
            "18.4" to "Tymal Mills to Matthew Hollard, 1 run, to short mid-wicket",
            "18.5" to "Tymal Mills to Shankar, 1 run, to point",
            "18.6" to "Tymal Mills to Matthew Hollard, no run",
        )
        val result = TimelineEventExtractor.deduplicateByBall(
            over,
            textOf = { it.second },
            withText = { entry, text -> entry.first to text },
            ballOf = { it.first },
        )
        assertEquals(over, result)
    }

    @Test
    fun `deduplicateByBall still collapses a repeated ball in a passthrough over`() {
        // A wide and then the legal delivery share a ball number but are separate events, while the
        // progressive republish of one of them is not. Longest per ball wins.
        val rows = listOf(
            "18.1" to "Tymal Mills to Ashwin, wide",
            "18.1" to "Tymal Mills to Ashwin, wide down the leg side, called immediately",
        )
        val result = TimelineEventExtractor.deduplicateByBall(
            rows,
            textOf = { it.second },
            withText = { entry, text -> entry.first to text },
            ballOf = { it.first },
        )
        assertEquals(
            listOf("18.1" to "Tymal Mills to Ashwin, wide down the leg side, called immediately"),
            result,
        )
    }
}
