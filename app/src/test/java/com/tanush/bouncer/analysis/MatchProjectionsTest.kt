package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.CricketConstants.ODI_INNINGS_BALLS
import com.tanush.bouncer.analysis.CricketConstants.T20_INNINGS_BALLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchProjectionsTest {

    // -------------------------------------------------------------------------
    // currentRunRate
    // -------------------------------------------------------------------------

    @Test
    fun `currentRunRate ballsBowled 0 returns null`() {
        assertNull(MatchProjections.currentRunRate(runs = 50, ballsBowled = 0))
    }

    @Test
    fun `currentRunRate negative ballsBowled returns null`() {
        assertNull(MatchProjections.currentRunRate(runs = 50, ballsBowled = -1))
    }

    @Test
    fun `currentRunRate 120 runs in 120 balls T20 innings done returns 6_0`() {
        val crr = MatchProjections.currentRunRate(runs = 120, ballsBowled = T20_INNINGS_BALLS)
        assertEquals(6.0, crr!!, 0.01)
    }

    @Test
    fun `currentRunRate 48 runs in 30 balls returns 9_6`() {
        // 48 * 6 / 30 = 9.6
        val crr = MatchProjections.currentRunRate(runs = 48, ballsBowled = 30)
        assertEquals(9.6, crr!!, 0.01)
    }

    @Test
    fun `currentRunRate 0 runs 6 balls returns 0_0`() {
        val crr = MatchProjections.currentRunRate(runs = 0, ballsBowled = 6)
        assertEquals(0.0, crr!!, 0.01)
    }

    // -------------------------------------------------------------------------
    // requiredRunRate
    // -------------------------------------------------------------------------

    @Test
    fun `requiredRunRate totalBalls 0 returns null`() {
        assertNull(MatchProjections.requiredRunRate(target = 150, currentRuns = 0, ballsBowled = 0, totalBalls = 0))
    }

    @Test
    fun `requiredRunRate target already passed returns null`() {
        // currentRuns >= target
        assertNull(MatchProjections.requiredRunRate(target = 100, currentRuns = 100, ballsBowled = 60, totalBalls = T20_INNINGS_BALLS))
        assertNull(MatchProjections.requiredRunRate(target = 100, currentRuns = 101, ballsBowled = 60, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `requiredRunRate no balls remaining returns null`() {
        assertNull(MatchProjections.requiredRunRate(target = 150, currentRuns = 80, ballsBowled = T20_INNINGS_BALLS, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `requiredRunRate standard T20 case`() {
        // target 150, current 80, 60 balls bowled, 120 total
        // need 70 in 60 balls = 70 * 6 / 60 = 7.0 rpo
        val rrr = MatchProjections.requiredRunRate(target = 150, currentRuns = 80, ballsBowled = 60, totalBalls = T20_INNINGS_BALLS)
        assertEquals(7.0, rrr!!, 0.01)
    }

    @Test
    fun `requiredRunRate bowled out early ODI uses totalBalls not first innings overs`() {
        // Team 1 scored 164 and was bowled out in 38 overs (228 balls) of a 50-over ODI.
        // target = 165, chasing team 0/0, 0 balls bowled, totalBalls = ODI_INNINGS_BALLS (300)
        // Correct RRR: 165 * 6 / 300 = 3.3 rpo
        // Old (buggy) calculation: 165 / 38 = 4.34 rpo
        val rrr = MatchProjections.requiredRunRate(
            target = 165,
            currentRuns = 0,
            ballsBowled = 0,
            totalBalls = ODI_INNINGS_BALLS
        )
        assertEquals(3.3, rrr!!, 0.01)
    }

    @Test
    fun `requiredRunRate negative ballsBowled returns null`() {
        assertNull(MatchProjections.requiredRunRate(target = 150, currentRuns = 0, ballsBowled = -1, totalBalls = T20_INNINGS_BALLS))
    }

    // -------------------------------------------------------------------------
    // projectedScore
    //
    // Damped by wickets in hand since 2026-08. The previous linear extrapolation gave a side 6
    // down after 8 overs the same projection as one 0 down; these tests pin the difference.
    // -------------------------------------------------------------------------

    @Test
    fun `projectedScore ballsBowled 0 returns null`() {
        assertNull(
            MatchProjections.projectedScore(
                currentRuns = 60, ballsBowled = 0, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 10,
            )
        )
    }

    @Test
    fun `projectedScore totalBalls 0 returns null`() {
        assertNull(
            MatchProjections.projectedScore(
                currentRuns = 60, ballsBowled = 60, totalBalls = 0, wicketsInHand = 10,
            )
        )
    }

    @Test
    fun `projectedScore ballsBowled equals totalBalls returns currentRuns`() {
        // innings over: projection = actual, whatever the wickets
        assertEquals(
            143,
            MatchProjections.projectedScore(
                currentRuns = 143, ballsBowled = T20_INNINGS_BALLS, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 4,
            )
        )
    }

    @Test
    fun `projectedScore ballsBowled greater than totalBalls returns currentRuns`() {
        // defensive: also treat > as innings-over
        assertEquals(
            143,
            MatchProjections.projectedScore(
                currentRuns = 143, ballsBowled = 121, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 10,
            )
        )
    }

    @Test
    fun `projectedScore all wickets in hand matches linear extrapolation`() {
        // damping is 0.55 + 0.45 * 1.0 = 1.0, so this is the old formula exactly: 60 * 120 / 60
        assertEquals(
            120,
            MatchProjections.projectedScore(
                currentRuns = 60, ballsBowled = 60, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 10,
            )
        )
    }

    @Test
    fun `projectedScore 0 runs projects 0 regardless of wickets`() {
        assertEquals(
            0,
            MatchProjections.projectedScore(
                currentRuns = 0, ballsBowled = 12, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 10,
            )
        )
    }

    @Test
    fun `projectedScore falls as wickets fall on identical scores`() {
        // The bug this replaces: these three were all 150.
        val batting = List(11) { wickets ->
            MatchProjections.projectedScore(
                currentRuns = 60, ballsBowled = 48, totalBalls = T20_INNINGS_BALLS, wicketsInHand = wickets,
            )!!
        }
        // Strictly increasing in wickets in hand.
        for (i in 1 until batting.size) {
            assertTrue(
                "projection should rise with wickets in hand: $batting",
                batting[i] > batting[i - 1],
            )
        }
        // 60 off 48 with all ten in hand is the linear 150; nine down is well short of it.
        assertEquals(150, batting[10])
        assertTrue("nine down must project below linear", batting[1] < 130)
    }

    @Test
    fun `projectedScore clamps wickets in hand into range`() {
        val ten = MatchProjections.projectedScore(
            currentRuns = 60, ballsBowled = 48, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 10,
        )
        val absurd = MatchProjections.projectedScore(
            currentRuns = 60, ballsBowled = 48, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 99,
        )
        val negative = MatchProjections.projectedScore(
            currentRuns = 60, ballsBowled = 48, totalBalls = T20_INNINGS_BALLS, wicketsInHand = -3,
        )
        val none = MatchProjections.projectedScore(
            currentRuns = 60, ballsBowled = 48, totalBalls = T20_INNINGS_BALLS, wicketsInHand = 0,
        )
        assertEquals(ten, absurd)
        assertEquals(none, negative)
    }

    // -------------------------------------------------------------------------
    // cumulativeRunRates
    // -------------------------------------------------------------------------

    @Test
    fun `cumulativeRunRates empty list returns empty list`() {
        assertEquals(emptyList<Float>(), MatchProjections.cumulativeRunRates(emptyList()))
    }

    @Test
    fun `cumulativeRunRates single over 45 runs returns 45_0f`() {
        val result = MatchProjections.cumulativeRunRates(listOf(45))
        assertEquals(1, result.size)
        assertEquals(45.0f, result[0], 0.01f)
    }

    @Test
    fun `cumulativeRunRates two overs cumulative 6 and 14 returns correct run rates`() {
        // over 0: 6/1 = 6.0, over 1: 14/2 = 7.0
        val result = MatchProjections.cumulativeRunRates(listOf(6, 14))
        assertEquals(2, result.size)
        assertEquals(6.0f, result[0], 0.01f)
        assertEquals(7.0f, result[1], 0.01f)
    }

    @Test
    fun `cumulativeRunRates large list last element is cumulative total divided by overs`() {
        // 20-over cumulative scores: each over adds 8 runs
        val cumulative = (1..20).map { it * 8 }   // 8, 16, 24, ... 160
        val result = MatchProjections.cumulativeRunRates(cumulative)
        assertEquals(20, result.size)
        // Last element: 160 runs / 20 overs = 8.0f
        assertEquals(8.0f, result.last(), 0.01f)
        // First element: 8 / 1 = 8.0f
        assertEquals(8.0f, result.first(), 0.01f)
    }
}
