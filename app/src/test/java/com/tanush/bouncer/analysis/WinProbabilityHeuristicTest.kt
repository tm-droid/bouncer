package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.CricketConstants.MatchFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinProbabilityHeuristicTest {

    private fun chase(
        format: MatchFormat = MatchFormat.T20,
        rrr: Double,
        ballsBowled: Int,
        wicketsLost: Int,
    ) = WinProbabilityHeuristic.evaluateChase(format, rrr, ballsBowled, wicketsLost)

    // ── Format handling: the root of the old Test bug ──

    @Test
    fun `formats without a fixed ball budget are not scored`() {
        // REGRESSION: these all previously fell back to a 120-ball T20 budget. A Test past ball
        // 120 then computed ballsRemaining = 1 and carried a permanent late-innings penalty.
        assertNull(chase(MatchFormat.TEST, rrr = 4.0, ballsBowled = 300, wicketsLost = 3))
        assertNull(chase(MatchFormat.UNKNOWN, rrr = 8.0, ballsBowled = 60, wicketsLost = 3))
    }

    @Test
    fun `known formats are scored on their own ball budget`() {
        assertNotNull(chase(MatchFormat.T20, rrr = 8.0, ballsBowled = 60, wicketsLost = 3))
        assertNotNull(chase(MatchFormat.ODI, rrr = 5.6, ballsBowled = 150, wicketsLost = 3))
        assertNotNull(chase(MatchFormat.HUNDRED, rrr = 8.6, ballsBowled = 50, wicketsLost = 3))
        assertNotNull(chase(MatchFormat.T10, rrr = 10.5, ballsBowled = 30, wicketsLost = 3))
    }

    @Test
    fun `ODI overs beyond a T20 innings are handled, not extrapolated`() {
        // 180 balls into an ODI chase is a normal position; under a 120-ball budget it was
        // off the end of the innings entirely.
        val p = chase(MatchFormat.ODI, rrr = 6.0, ballsBowled = 180, wicketsLost = 4)
        assertNotNull(p)
        assertTrue("expected a live-looking probability, was $p", p!! in 15.0..85.0)
    }

    // ── Calibration: par chase is a coin flip ──

    @Test
    fun `a chase at exactly the par rate with all wickets is near even`() {
        listOf(
            MatchFormat.T20 to CricketConstants.T20_PAR_RUN_RATE,
            MatchFormat.ODI to CricketConstants.ODI_PAR_RUN_RATE,
            MatchFormat.T10 to CricketConstants.T10_PAR_RUN_RATE,
        ).forEach { (format, par) ->
            val p = chase(format, rrr = par, ballsBowled = 0, wicketsLost = 0)!!
            assertEquals("format=$format", 50.0, p, 0.001)
        }
    }

    // ── Monotonicity: the properties any sane model must have ──

    @Test
    fun `more wickets in hand never lowers the probability`() {
        val probabilities = (0..9).map { chase(rrr = 9.0, ballsBowled = 60, wicketsLost = it)!! }
        probabilities.zipWithNext { moreWickets, fewerWickets ->
            assertTrue(
                "losing a wicket must not help: $probabilities",
                moreWickets >= fewerWickets,
            )
        }
    }

    @Test
    fun `a higher required rate never raises the probability`() {
        val probabilities = listOf(4.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0)
            .map { chase(rrr = it, ballsBowled = 60, wicketsLost = 3)!! }
        probabilities.zipWithNext { easier, harder ->
            assertTrue("needing more runs must not help: $probabilities", easier >= harder)
        }
    }

    @Test
    fun `probability is continuous across the old nine-run-rate step`() {
        // REGRESSION: pressure applied only when reqRR > 9.0, so the output jumped at exactly 9.
        val below = chase(rrr = 8.99, ballsBowled = 90, wicketsLost = 5)!!
        val above = chase(rrr = 9.01, ballsBowled = 90, wicketsLost = 5)!!
        assertTrue("discontinuity at rrr=9: $below -> $above", kotlin.math.abs(below - above) < 0.5)
    }

    // ── Golden scenarios ──

    @Test
    fun `comfortable chase reads strongly favourable`() {
        // 30 needed off 60 balls (rrr 3.0) with 8 wickets standing.
        val p = chase(rrr = 3.0, ballsBowled = 60, wicketsLost = 2)!!
        assertTrue("expected > 80, was $p", p > 80.0)
    }

    @Test
    fun `hopeless chase reads strongly unfavourable`() {
        // 30 needed off the last over with 5 down.
        val p = chase(rrr = 30.0, ballsBowled = 114, wicketsLost = 5)!!
        assertTrue("expected < 10, was $p", p < 10.0)
    }

    @Test
    fun `last-over thriller stays live rather than collapsing to nothing`() {
        // 12 off 6 with 5 wickets in hand is hard but happens. The old model's late-innings
        // penalty had no counterbalancing acceleration term and buried situations like this.
        val p = chase(rrr = 12.0, ballsBowled = 114, wicketsLost = 5)!!
        assertTrue("expected a live 10-50%, was $p", p in 10.0..50.0)
    }

    @Test
    fun `all out is a loss`() {
        assertEquals(2.0, chase(rrr = 6.0, ballsBowled = 90, wicketsLost = 10)!!, 0.001)
    }

    @Test
    fun `no balls remaining resolves`() {
        assertEquals(2.0, chase(rrr = 6.0, ballsBowled = 120, wicketsLost = 4)!!, 0.001)
    }

    // ── Input validation ──

    @Test
    fun `invalid inputs return null rather than a number`() {
        assertNull(chase(rrr = Double.NaN, ballsBowled = 60, wicketsLost = 3))
        assertNull(chase(rrr = Double.POSITIVE_INFINITY, ballsBowled = 60, wicketsLost = 3))
        assertNull(chase(rrr = -1.0, ballsBowled = 60, wicketsLost = 3))
        assertNull(chase(rrr = 6.0, ballsBowled = -1, wicketsLost = 3))
        assertNull(chase(rrr = 6.0, ballsBowled = 121, wicketsLost = 3))
    }

    // ── First innings ──

    @Test
    fun `first innings distinguishes a platform from a collapse`() {
        // REGRESSION: the old term was (runs / 50.0).coerceAtMost(10.0) minus 2 per wicket, so
        // 60/0 and 60/5 after ten overs differed by only 10 points and neither moved far from 50.
        val platform = WinProbabilityHeuristic
            .evaluateFirstInnings(MatchFormat.T20, runs = 60, ballsBowled = 60, wicketsLost = 0)!!
        val collapse = WinProbabilityHeuristic
            .evaluateFirstInnings(MatchFormat.T20, runs = 60, ballsBowled = 60, wicketsLost = 5)!!

        assertTrue("60/0 should read at least par, was $platform", platform >= 48.0)
        assertTrue("60/5 should read below par, was $collapse", collapse < 40.0)
        assertTrue("the two must be clearly separated", platform - collapse > 10.0)
    }

    @Test
    fun `first innings is held to a narrower band than a chase`() {
        val huge = WinProbabilityHeuristic
            .evaluateFirstInnings(MatchFormat.T20, runs = 250, ballsBowled = 110, wicketsLost = 1)!!
        val awful = WinProbabilityHeuristic
            .evaluateFirstInnings(MatchFormat.T20, runs = 20, ballsBowled = 110, wicketsLost = 9)!!
        assertTrue("was $huge", huge <= 85.0)
        assertTrue("was $awful", awful >= 15.0)
    }

    @Test
    fun `first innings is not scored without a ball budget`() {
        assertNull(
            WinProbabilityHeuristic
                .evaluateFirstInnings(MatchFormat.TEST, runs = 300, ballsBowled = 480, wicketsLost = 4),
        )
    }

    // ── sustainableRunRate, the shared core ──

    @Test
    fun `sustainable rate rises toward the death and falls with wickets`() {
        val par = CricketConstants.T20_PAR_RUN_RATE
        val start = WinProbabilityHeuristic.sustainableRunRate(par, 10, 1.0)
        val death = WinProbabilityHeuristic.sustainableRunRate(par, 10, 0.05)
        assertTrue("acceleration should raise the rate at the death", death > start)
        assertEquals("full resources at the start = par", par, start, 0.001)

        val fiveDown = WinProbabilityHeuristic.sustainableRunRate(par, 5, 1.0)
        assertTrue("wickets lost must reduce the sustainable rate", fiveDown < start)
    }
}
