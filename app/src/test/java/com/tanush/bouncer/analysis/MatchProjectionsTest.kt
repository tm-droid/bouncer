package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.CricketConstants.ODI_INNINGS_BALLS
import com.tanush.bouncer.analysis.CricketConstants.T20_INNINGS_BALLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    // -------------------------------------------------------------------------

    @Test
    fun `projectedScore ballsBowled 0 returns null`() {
        assertNull(MatchProjections.projectedScore(currentRuns = 60, ballsBowled = 0, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `projectedScore totalBalls 0 returns null`() {
        assertNull(MatchProjections.projectedScore(currentRuns = 60, ballsBowled = 60, totalBalls = 0))
    }

    @Test
    fun `projectedScore ballsBowled equals totalBalls returns currentRuns`() {
        // innings over: projection = actual
        assertEquals(143, MatchProjections.projectedScore(currentRuns = 143, ballsBowled = T20_INNINGS_BALLS, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `projectedScore ballsBowled greater than totalBalls returns currentRuns`() {
        // defensive: also treat > as innings-over
        assertEquals(143, MatchProjections.projectedScore(currentRuns = 143, ballsBowled = 121, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `projectedScore 60 runs in 60 balls T20 projects 120`() {
        // 60 * 120 / 60 = 120
        assertEquals(120, MatchProjections.projectedScore(currentRuns = 60, ballsBowled = 60, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `projectedScore 0 runs 12 balls projects 0`() {
        assertEquals(0, MatchProjections.projectedScore(currentRuns = 0, ballsBowled = 12, totalBalls = T20_INNINGS_BALLS))
    }

    @Test
    fun `projectedScore 100 runs in 90 balls 120 total projects 133`() {
        // 100 * 120 / 90 = 133.33... → rounded to 133
        assertEquals(133, MatchProjections.projectedScore(currentRuns = 100, ballsBowled = 90, totalBalls = T20_INNINGS_BALLS))
    }

    // -------------------------------------------------------------------------
    // overRunRates
    // -------------------------------------------------------------------------

    @Test
    fun `overRunRates empty list returns empty list`() {
        assertEquals(emptyList<Float>(), MatchProjections.overRunRates(emptyList()))
    }

    @Test
    fun `overRunRates single over 45 runs returns 45_0f`() {
        val result = MatchProjections.overRunRates(listOf(45))
        assertEquals(1, result.size)
        assertEquals(45.0f, result[0], 0.01f)
    }

    @Test
    fun `overRunRates two overs cumulative 6 and 14 returns correct run rates`() {
        // over 0: 6/1 = 6.0, over 1: 14/2 = 7.0
        val result = MatchProjections.overRunRates(listOf(6, 14))
        assertEquals(2, result.size)
        assertEquals(6.0f, result[0], 0.01f)
        assertEquals(7.0f, result[1], 0.01f)
    }

    @Test
    fun `overRunRates large list last element is cumulative total divided by overs`() {
        // 20-over cumulative scores: each over adds 8 runs
        val cumulative = (1..20).map { it * 8 }   // 8, 16, 24, ... 160
        val result = MatchProjections.overRunRates(cumulative)
        assertEquals(20, result.size)
        // Last element: 160 runs / 20 overs = 8.0f
        assertEquals(8.0f, result.last(), 0.01f)
        // First element: 8 / 1 = 8.0f
        assertEquals(8.0f, result.first(), 0.01f)
    }
}
