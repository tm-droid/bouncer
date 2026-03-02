package com.tanush.bouncer.analysis.ml

import com.tanush.bouncer.analysis.MatchSituation
import com.tanush.bouncer.analysis.TeamStatistics
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the FeatureExtractor — converting match situations into normalized feature vectors.
 *
 * Tests cover:
 *  - Feature vector shape and size
 *  - Feature value normalization (all values in [0, 1])
 *  - Phase flag encoding (one-hot power play / middle / death)
 *  - Edge case inputs (zero runs, max wickets, extreme run rates)
 *  - Pressure index computation
 */
class FeatureExtractorTest {

    // ═══════════════════════════════════════════════════════════
    // Basic Feature Extraction
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `extracted features have correct length`() {
        val situation = createDefaultSituation()
        val features = FeatureExtractor.extractFeatures(situation)
        assertEquals(FeatureExtractor.FEATURE_COUNT, features.size)
    }

    @Test
    fun `all features are normalized between 0 and 1`() {
        val situation = createDefaultSituation()
        val features = FeatureExtractor.extractFeatures(
            situation = situation,
            runsNeeded = 80,
            ballsBowled = 60,
            wicketsLost = 3,
            maxBalls = 120
        )

        for (i in features.indices) {
            assertTrue(
                "Feature[$i] = ${features[i]} should be >= 0",
                features[i] >= 0.0
            )
            assertTrue(
                "Feature[$i] = ${features[i]} should be <= 1",
                features[i] <= 1.0
            )
        }
    }

    @Test
    fun `features normalized for extreme values`() {
        val extremeSituation = MatchSituation(
            currentRunRate = 20.0,  // very high
            requiredRunRate = 25.0,
            oversRemaining = 0.1,
            wicketsInHand = 1,
            lastFiveOversRuns = 100,
            isPowerPlay = false,
            isDeathOvers = true
        )
        val features = FeatureExtractor.extractFeatures(
            situation = extremeSituation,
            runsNeeded = 500,
            ballsBowled = 119,
            wicketsLost = 9,
            maxBalls = 120
        )

        for (i in features.indices) {
            assertTrue("Feature[$i] should be clamped to [0,1], got ${features[i]}",
                features[i] >= 0.0 && features[i] <= 1.0)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Phase Flags (Features 7, 8, 9)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `power play flag is set correctly`() {
        val ppSituation = MatchSituation(
            currentRunRate = 8.0,
            requiredRunRate = 7.0,
            oversRemaining = 15.0,
            wicketsInHand = 10,
            lastFiveOversRuns = 40,
            isPowerPlay = true,
            isDeathOvers = false
        )
        val features = FeatureExtractor.extractFeatures(ppSituation)
        assertEquals("Power play flag should be 1.0", 1.0, features[7], 1e-12)
        assertEquals("Death overs flag should be 0.0", 0.0, features[8], 1e-12)
        assertEquals("Middle overs flag should be 0.0", 0.0, features[9], 1e-12)
    }

    @Test
    fun `death overs flag is set correctly`() {
        val deathSituation = MatchSituation(
            currentRunRate = 10.0,
            requiredRunRate = 12.0,
            oversRemaining = 3.0,
            wicketsInHand = 4,
            lastFiveOversRuns = 50,
            isPowerPlay = false,
            isDeathOvers = true
        )
        val features = FeatureExtractor.extractFeatures(deathSituation)
        assertEquals("Power play flag should be 0.0", 0.0, features[7], 1e-12)
        assertEquals("Death overs flag should be 1.0", 1.0, features[8], 1e-12)
        assertEquals("Middle overs flag should be 0.0", 0.0, features[9], 1e-12)
    }

    @Test
    fun `middle overs flag is set when not PP or death`() {
        val middleSituation = MatchSituation(
            currentRunRate = 6.5,
            requiredRunRate = 7.5,
            oversRemaining = 10.0,
            wicketsInHand = 7,
            lastFiveOversRuns = 30,
            isPowerPlay = false,
            isDeathOvers = false
        )
        val features = FeatureExtractor.extractFeatures(middleSituation)
        assertEquals("Power play flag should be 0.0", 0.0, features[7], 1e-12)
        assertEquals("Death overs flag should be 0.0", 0.0, features[8], 1e-12)
        assertEquals("Middle overs flag should be 1.0", 1.0, features[9], 1e-12)
    }

    // ═══════════════════════════════════════════════════════════
    // Team Statistics Features
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `team statistics affect features 10-13`() {
        val situation = createDefaultSituation()
        val battingStats = TeamStatistics(
            battingAverage = 35.0,
            bowlingAverage = 28.0,
            winRate = 0.65
        )
        val bowlingStats = TeamStatistics(
            battingAverage = 30.0,
            bowlingAverage = 25.0,
            winRate = 0.55
        )

        val features = FeatureExtractor.extractFeatures(
            situation = situation,
            battingTeamStats = battingStats,
            bowlingTeamStats = bowlingStats
        )

        // Feature 10: batting strength = 35/50 = 0.7
        assertEquals(0.7, features[10], 0.01)
        // Feature 12: batting win rate = 0.65
        assertEquals(0.65, features[12], 0.01)
        // Feature 13: bowling win rate = 0.55
        assertEquals(0.55, features[13], 0.01)
    }

    @Test
    fun `default team statistics produce zero features`() {
        val situation = createDefaultSituation()
        val features = FeatureExtractor.extractFeatures(situation)

        // Default TeamStatistics has all zeros
        assertEquals("Batting strength should be 0 with default stats", 0.0, features[10], 1e-12)
        assertEquals("Batting win rate should be 0 with default stats", 0.0, features[12], 1e-12)
        assertEquals("Bowling win rate should be 0 with default stats", 0.0, features[13], 1e-12)
    }

    // ═══════════════════════════════════════════════════════════
    // Pressure Index (Feature 14)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `pressure index is low at start of innings`() {
        val earlySituation = MatchSituation(
            currentRunRate = 6.0,
            requiredRunRate = 6.0,
            oversRemaining = 18.0,
            wicketsInHand = 10,
            lastFiveOversRuns = 30,
            isPowerPlay = true,
            isDeathOvers = false
        )
        val features = FeatureExtractor.extractFeatures(
            situation = earlySituation,
            ballsBowled = 12,
            wicketsLost = 0,
            maxBalls = 120
        )

        assertTrue("Pressure should be low at start", features[14] < 0.3)
    }

    @Test
    fun `pressure index is high in dire situation`() {
        val direSituation = MatchSituation(
            currentRunRate = 5.0,
            requiredRunRate = 14.0,
            oversRemaining = 2.0,
            wicketsInHand = 2,
            lastFiveOversRuns = 25,
            isPowerPlay = false,
            isDeathOvers = true
        )
        val features = FeatureExtractor.extractFeatures(
            situation = direSituation,
            runsNeeded = 28,
            ballsBowled = 108,
            wicketsLost = 8,
            maxBalls = 120
        )

        assertTrue("Pressure should be high in dire situation", features[14] > 0.5)
    }

    // ═══════════════════════════════════════════════════════════
    // Run Rate Features
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `run rate ratio feature for matching rates`() {
        val situation = MatchSituation(
            currentRunRate = 7.0,
            requiredRunRate = 7.0,
            oversRemaining = 10.0,
            wicketsInHand = 7,
            lastFiveOversRuns = 35,
            isPowerPlay = false,
            isDeathOvers = false
        )
        val features = FeatureExtractor.extractFeatures(situation)
        // rrRatio = 7.0/7.0 = 1.0, normalized = 1.0/2.0 = 0.5
        assertEquals("RR ratio for equal rates should be 0.5", 0.5, features[2], 0.01)
    }

    @Test
    fun `run rate ratio when no required rate`() {
        val situation = MatchSituation(
            currentRunRate = 7.0,
            requiredRunRate = 0.0, // No target / first innings
            oversRemaining = 10.0,
            wicketsInHand = 7,
            lastFiveOversRuns = 35,
            isPowerPlay = false,
            isDeathOvers = false
        )
        val features = FeatureExtractor.extractFeatures(situation)
        // When requiredRunRate = 0, rrRatio defaults to 1.0, normalized to 0.5
        assertEquals(0.5, features[2], 0.01)
    }

    // ═══════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `all-zero situation produces valid features`() {
        val zeroSituation = MatchSituation(
            currentRunRate = 0.0,
            requiredRunRate = 0.0,
            oversRemaining = 0.0,
            wicketsInHand = 0,
            lastFiveOversRuns = 0,
            isPowerPlay = false,
            isDeathOvers = false
        )
        val features = FeatureExtractor.extractFeatures(
            situation = zeroSituation,
            runsNeeded = 0,
            ballsBowled = 0,
            wicketsLost = 0,
            maxBalls = 120
        )

        assertEquals(FeatureExtractor.FEATURE_COUNT, features.size)
        for (i in features.indices) {
            assertFalse("Feature[$i] should not be NaN", features[i].isNaN())
            assertFalse("Feature[$i] should not be Infinite", features[i].isInfinite())
        }
    }

    @Test
    fun `maxBalls of 0 does not cause division by zero`() {
        val situation = createDefaultSituation()
        // This should not crash
        val features = FeatureExtractor.extractFeatures(
            situation = situation,
            ballsBowled = 0,
            maxBalls = 0
        )
        assertEquals(FeatureExtractor.FEATURE_COUNT, features.size)
        for (f in features) {
            assertFalse(f.isNaN())
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Consistency
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `same inputs produce same features (deterministic)`() {
        val situation = createDefaultSituation()
        val stats = TeamStatistics(battingAverage = 30.0, winRate = 0.6)

        val f1 = FeatureExtractor.extractFeatures(
            situation, battingTeamStats = stats, runsNeeded = 50, ballsBowled = 60
        )
        val f2 = FeatureExtractor.extractFeatures(
            situation, battingTeamStats = stats, runsNeeded = 50, ballsBowled = 60
        )

        assertArrayEquals(f1, f2, 1e-12)
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private fun createDefaultSituation() = MatchSituation(
        currentRunRate = 7.5,
        requiredRunRate = 8.0,
        oversRemaining = 10.0,
        wicketsInHand = 6,
        lastFiveOversRuns = 35,
        isPowerPlay = false,
        isDeathOvers = false
    )
}
