package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.analysis.CricketConstants.MatchFormat
import com.tanush.bouncer.ui.components.MomentumShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase windows and momentum thresholds used to be T20 literals (powerplay 1-6, middle 7-15, death
 * 16-20, thresholds 60/30, 80/40, 70/30), so an ODI rendered T20 phases. These assert the windows
 * now follow the format, and that T20 output is unchanged.
 */
class OverviewMatchPhasesTest {

    /** Cumulative (over, runs) milestones at a flat [runRate] runs per over up to [throughOver]. */
    private fun milestones(throughOver: Int, runRate: Double): List<Pair<Int, Int>> =
        (1..throughOver).map { over -> over to (over * runRate).toInt() }

    private fun phasesFor(
        format: MatchFormat,
        throughOver: Int,
        runRate: Double = 6.0,
    ) = buildMatchPhases(
        overMilestones = milestones(throughOver, runRate),
        powerplays = emptyList(),
        scorecard = null,
        isLive = false,
        format = format,
    )

    // --- Window boundaries -------------------------------------------------

    @Test
    fun `T20 powerplay covers overs 1-6`() {
        val pp = phasesFor(MatchFormat.T20, throughOver = 20).first()
        assertEquals("Powerplay", pp.phaseName)
        assertEquals("1-6", pp.overs)
    }

    @Test
    fun `ODI powerplay covers overs 1-10, not 1-6`() {
        val pp = phasesFor(MatchFormat.ODI, throughOver = 50).first()
        assertEquals("Powerplay", pp.phaseName)
        assertEquals("1-10", pp.overs)
    }

    @Test
    fun `T20 middle and death windows are unchanged`() {
        val phases = phasesFor(MatchFormat.T20, throughOver = 20)
        assertEquals("7-15", phases.single { it.phaseName == "Middle Overs" }.overs)
        assertEquals("16-20", phases.single { it.phaseName == "Death Overs" }.overs)
    }

    @Test
    fun `ODI middle and death windows follow the 50-over structure`() {
        val phases = phasesFor(MatchFormat.ODI, throughOver = 50)
        assertEquals("11-39", phases.single { it.phaseName == "Middle Overs" }.overs)
        assertEquals("40-50", phases.single { it.phaseName == "Death Overs" }.overs)
    }

    @Test
    fun `an ODI at over 20 has not reached its death phase`() {
        // The old code emitted a "Death Overs" phase for any innings past over 16, so a 50-over
        // match 20 overs in reported death overs while 30 overs remained.
        val phases = phasesFor(MatchFormat.ODI, throughOver = 20)
        assertNull(phases.firstOrNull { it.phaseName == "Death Overs" })
        assertNotNull(phases.firstOrNull { it.phaseName == "Powerplay" })
    }

    @Test
    fun `an ODI 8 overs in has no phases yet`() {
        assertTrue(phasesFor(MatchFormat.ODI, throughOver = 8).isEmpty())
    }

    // --- Momentum thresholds ---------------------------------------------

    @Test
    fun `T20 powerplay momentum still turns over at the old 60-30 run marks`() {
        // Old literals: >= 60 strong, <= 30 weak, over a 6-over powerplay.
        val strong = phasesFor(MatchFormat.T20, throughOver = 6, runRate = 11.0)
        assertEquals(MomentumShift.TEAM1_STRONG, strong.first().momentumShift)

        val weak = phasesFor(MatchFormat.T20, throughOver = 6, runRate = 4.0)
        assertEquals(MomentumShift.TEAM2_STRONG, weak.first().momentumShift)

        val balanced = phasesFor(MatchFormat.T20, throughOver = 6, runRate = 7.0)
        assertEquals(MomentumShift.BALANCED, balanced.first().momentumShift)
    }

    @Test
    fun `ODI powerplay momentum scales to the ODI par rate`() {
        // 60 runs off 10 ODI overs is a par-ish start, not a strong one — the old code compared it
        // against the T20 threshold of 60 and called it TEAM1_STRONG.
        val parish = phasesFor(MatchFormat.ODI, throughOver = 10, runRate = 6.0)
        assertEquals(MomentumShift.BALANCED, parish.first().momentumShift)

        val strong = phasesFor(MatchFormat.ODI, throughOver = 10, runRate = 9.0)
        assertEquals(MomentumShift.TEAM1_STRONG, strong.first().momentumShift)
    }

    @Test
    fun `a format with no par rate reports balanced rather than inventing a comparison`() {
        val phases = phasesFor(MatchFormat.TEST, throughOver = 20, runRate = 11.0)
        assertTrue(phases.all { it.momentumShift == MomentumShift.BALANCED })
    }

    // --- Death-phase wicket window ---------------------------------------

    @Test
    fun `death phase is bounded by the actual last over, not a hardcoded 20`() {
        // A rain-shortened T20 ending at over 18 previously scanned wickets in 16..20.
        val phases = phasesFor(MatchFormat.T20, throughOver = 18)
        assertEquals("16-18", phases.single { it.phaseName == "Death Overs" }.overs)
    }
}
