package com.tanush.bouncer.utils

import org.junit.Assert.*
import org.junit.Test

class MatchUtilsTest {

    // ═══════════════════════════════════════════════════════════
    // parseScore
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `parseScore with standard format`() {
        val score = parseScore("199/7")
        assertTrue(score.isValid)
        assertEquals(199, score.runs)
        assertEquals(7, score.wickets)
    }

    @Test
    fun `parseScore with overs in parens`() {
        val score = parseScore("199/7 (20.0)")
        assertTrue(score.isValid)
        assertEquals(199, score.runs)
        assertEquals(7, score.wickets)
        assertEquals("20.0", score.overs)
    }

    @Test
    fun `parseScore with Ov suffix`() {
        val score = parseScore("250/4 (50.0 Ov)")
        assertTrue(score.isValid)
        assertEquals(250, score.runs)
        assertEquals(4, score.wickets)
        assertEquals("50.0", score.overs)
    }

    @Test
    fun `parseScore with dash separator`() {
        val score = parseScore("150-5")
        assertTrue(score.isValid)
        assertEquals(150, score.runs)
        assertEquals(5, score.wickets)
    }

    @Test
    fun `parseScore null returns invalid`() {
        val score = parseScore(null)
        assertFalse(score.isValid)
        assertEquals(0, score.runs)
    }

    @Test
    fun `parseScore blank returns invalid`() {
        val score = parseScore("")
        assertFalse(score.isValid)
    }

    @Test
    fun `parseScore Data Not Found returns invalid`() {
        val score = parseScore("Data Not Found")
        assertFalse(score.isValid)
    }

    @Test
    fun `parseScore TBD returns invalid`() {
        val score = parseScore("TBD")
        assertFalse(score.isValid)
    }

    @Test
    fun `parseScore dash placeholder returns invalid`() {
        val score = parseScore("-")
        assertFalse(score.isValid)
    }

    @Test
    fun `parseScore with amp entity for second innings`() {
        val score = parseScore("&amp; 123/4")
        assertTrue(score.isValid)
        assertEquals(123, score.runs)
        assertEquals(4, score.wickets)
    }

    @Test
    fun `parseScore runs only no wickets`() {
        val score = parseScore("350")
        assertEquals(350, score.runs)
        assertEquals(0, score.wickets)
    }

    @Test
    fun `parseScore zero wickets still valid`() {
        val score = parseScore("50/0")
        assertTrue(score.isValid)
        assertEquals(50, score.runs)
        assertEquals(0, score.wickets)
    }

    // ═══════════════════════════════════════════════════════════
    // toDisplayString
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `toDisplayString valid score shows runs slash wickets`() {
        val score = parseScore("199/7")
        assertEquals("199/7", score.toDisplayString())
    }

    @Test
    fun `toDisplayString invalid score shows raw fallback`() {
        val score = ScoreInfo(0, 0, "", "some weird text", false)
        assertEquals("some weird text", score.toDisplayString())
    }

    @Test
    fun `toDisplayString blank raw returns dash`() {
        val score = ScoreInfo(0, 0, "", "", false)
        assertEquals("-", score.toDisplayString())
    }

    // ═══════════════════════════════════════════════════════════
    // toFullDisplayString
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `toFullDisplayString includes overs`() {
        val score = parseScore("199/7 (20.0)")
        assertEquals("199/7 (20.0)", score.toFullDisplayString())
    }

    @Test
    fun `toFullDisplayString without overs omits parens`() {
        val score = parseScore("199/7")
        assertEquals("199/7", score.toFullDisplayString())
    }

    // ═══════════════════════════════════════════════════════════
    // getMatchPhase
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `phase for won status is RESULT`() {
        assertEquals(MatchPhase.RESULT, getMatchPhase("India won by 6 wickets"))
    }

    @Test
    fun `phase for drawn status is RESULT`() {
        assertEquals(MatchPhase.RESULT, getMatchPhase("Match drawn"))
    }

    @Test
    fun `phase for abandoned is ABANDONED`() {
        assertEquals(MatchPhase.ABANDONED, getMatchPhase("Match abandoned"))
    }

    @Test
    fun `phase for no result is NO_RESULT`() {
        assertEquals(MatchPhase.NO_RESULT, getMatchPhase("No result"))
    }

    @Test
    fun `phase for rain delay`() {
        assertEquals(MatchPhase.RAIN_DELAY, getMatchPhase("Rain stopped play"))
    }

    @Test
    fun `phase for toss`() {
        assertEquals(MatchPhase.TOSS, getMatchPhase("Toss - India opted to bat"))
    }

    @Test
    fun `phase for chase detection`() {
        assertEquals(MatchPhase.CHASING, getMatchPhase("Need 120 runs to win"))
    }

    @Test
    fun `phase for target status`() {
        assertEquals(MatchPhase.CHASING, getMatchPhase("Target: 250"))
    }

    @Test
    fun `phase for upcoming`() {
        assertEquals(MatchPhase.UPCOMING, getMatchPhase("Upcoming"))
    }

    @Test
    fun `phase for null status is UPCOMING`() {
        assertEquals(MatchPhase.UPCOMING, getMatchPhase(null))
    }

    @Test
    fun `phase for blank status is UPCOMING`() {
        assertEquals(MatchPhase.UPCOMING, getMatchPhase(""))
    }

    @Test
    fun `phase for innings break`() {
        assertEquals(MatchPhase.INNINGS_BREAK, getMatchPhase("Innings break"))
    }

    @Test
    fun `phase for strategic timeout`() {
        assertEquals(MatchPhase.STRATEGIC_TIMEOUT, getMatchPhase("Strategic timeout"))
    }

    @Test
    fun `phase for lunch`() {
        assertEquals(MatchPhase.LUNCH, getMatchPhase("Lunch"))
    }

    @Test
    fun `phase for stumps`() {
        assertEquals(MatchPhase.STUMPS, getMatchPhase("Stumps"))
    }

    @Test
    fun `phase for bad light`() {
        assertEquals(MatchPhase.BAD_LIGHT, getMatchPhase("Bad light"))
    }
}
