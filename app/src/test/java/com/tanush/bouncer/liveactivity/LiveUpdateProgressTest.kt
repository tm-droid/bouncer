package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.CricketConstants.MatchFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Live Update's progress bar is the one graphic a promoted notification gets, so what it
 * claims has to be true. These pin the two things that were easy to get wrong: a Test being given
 * a ball budget it does not have, and an unreadable over figure rendering as "nothing has
 * happened" rather than "nothing is known".
 */
class LiveUpdateProgressTest {

    private fun determinate(progress: LiveUpdateProgress): LiveUpdateProgress.Determinate {
        assertTrue("expected a determinate bar, got $progress", progress is LiveUpdateProgress.Determinate)
        return progress as LiveUpdateProgress.Determinate
    }

    // ── Limited overs: balls out of the format's budget ──────────────────────

    @Test
    fun `T20 counts balls bowled against the format budget`() {
        val bar = determinate(progressFor(MatchFormat.T20, overs = "12.3", wickets = 2))
        assertEquals(ProgressUnit.BALLS, bar.unit)
        assertEquals(12 * CricketConstants.BALLS_PER_OVER + 3, bar.progress)
        assertEquals(CricketConstants.T20_INNINGS_BALLS, bar.max)
    }

    @Test
    fun `ODI segments are the powerplay, middle and death windows`() {
        val bar = determinate(progressFor(MatchFormat.ODI, overs = "20.0", wickets = 1))
        assertEquals(
            listOf(SegmentTint.POWERPLAY, SegmentTint.MIDDLE, SegmentTint.DEATH),
            bar.segments.map { it.tint },
        )
        assertEquals(CricketConstants.ODI_POWERPLAY_END_BALL, bar.segments[0].length)
        assertEquals(
            CricketConstants.ODI_DEATH_START_BALL - CricketConstants.ODI_POWERPLAY_END_BALL,
            bar.segments[1].length,
        )
        assertEquals(
            CricketConstants.ODI_INNINGS_BALLS - CricketConstants.ODI_DEATH_START_BALL,
            bar.segments[2].length,
        )
    }

    /** ProgressStyle takes its maximum from the segment lengths, so they must sum to the budget. */
    @Test
    fun `segment lengths sum to the innings budget`() {
        listOf(MatchFormat.T20, MatchFormat.ODI, MatchFormat.T10, MatchFormat.HUNDRED).forEach { format ->
            val bar = determinate(progressFor(format, overs = "1.0", wickets = 0))
            assertEquals("$format", bar.max, bar.segments.sumOf { it.length })
        }
    }

    /** T10 and The Hundred have no phase windows in CricketConstants, so they are not invented. */
    @Test
    fun `formats without defined phases get one neutral segment`() {
        val bar = determinate(progressFor(MatchFormat.HUNDRED, overs = "8.0", wickets = 1))
        assertEquals(listOf(SegmentTint.NEUTRAL), bar.segments.map { it.tint })
        assertEquals(CricketConstants.HUNDRED_INNINGS_BALLS, bar.max)
    }

    @Test
    fun `wickets become points at their ball positions`() {
        val bar = determinate(
            progressFor(MatchFormat.T20, overs = "12.3", wickets = 2, fallOfWicketOvers = listOf("4.2", "11.5")),
        )
        assertEquals(listOf(26, 71), bar.points)
    }

    @Test
    fun `fall-of-wicket entries past the budget are dropped rather than clamped onto the end`() {
        val bar = determinate(
            progressFor(MatchFormat.T20, overs = "19.4", wickets = 3, fallOfWicketOvers = listOf("2.0", "25.0", "")),
        )
        assertEquals(listOf(12), bar.points)
    }

    @Test
    fun `overs beyond the budget clamp instead of overflowing the bar`() {
        val bar = determinate(progressFor(MatchFormat.T20, overs = "24.0", wickets = 5))
        assertEquals(CricketConstants.T20_INNINGS_BALLS, bar.progress)
    }

    // ── Multi-day: wickets out of ten, and no invented denominator ───────────

    @Test
    fun `a Test uses wickets out of ten because it has no ball budget`() {
        val bar = determinate(progressFor(MatchFormat.TEST, overs = "156.4", wickets = 4))
        assertEquals(ProgressUnit.WICKETS, bar.unit)
        assertEquals(4, bar.progress)
        assertEquals(10, bar.max)
        assertTrue("a Test bar must not carry ball-derived points", bar.points.isEmpty())
    }

    @Test
    fun `a Test never borrows a limited-overs ball budget`() {
        val bar = determinate(progressFor(MatchFormat.TEST, overs = "50.0", wickets = 0))
        listOf(
            CricketConstants.T20_INNINGS_BALLS,
            CricketConstants.ODI_INNINGS_BALLS,
            CricketConstants.T10_INNINGS_BALLS,
            CricketConstants.HUNDRED_INNINGS_BALLS,
        ).forEach { budget -> assertTrue("Test max must not be $budget", bar.max != budget) }
    }

    @Test
    fun `an unknown format falls back to the wicket model, not to T20`() {
        val bar = determinate(progressFor(MatchFormat.UNKNOWN, overs = "10.0", wickets = 3))
        assertEquals(ProgressUnit.WICKETS, bar.unit)
        assertEquals(3, bar.progress)
    }

    // ── "Unknown" must not render as "nothing yet" ───────────────────────────

    @Test
    fun `unreadable overs give an indeterminate bar rather than a zeroed one`() {
        listOf(null, "", "Ov", "12.9").forEach { overs ->
            assertEquals(
                "overs=$overs",
                LiveUpdateProgress.Indeterminate,
                progressFor(MatchFormat.T20, overs = overs, wickets = 0),
            )
        }
    }

    @Test
    fun `unknown wickets in a Test give an indeterminate bar, not zero wickets down`() {
        assertEquals(
            LiveUpdateProgress.Indeterminate,
            progressFor(MatchFormat.TEST, overs = "90.0", wickets = null),
        )
    }
}
