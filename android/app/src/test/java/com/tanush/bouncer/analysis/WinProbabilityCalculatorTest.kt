package com.tanush.bouncer.analysis

import org.junit.Assert.assertTrue
import org.junit.Test

class WinProbabilityCalculatorTest {
    @Test
    fun `analyzeMatchSituation indicates below required when currentRunRate is lower`() {
        val calculator = WinProbabilityCalculator()
        val situation = MatchSituation(
            currentRunRate = 5.0,
            requiredRunRate = 6.5,
            oversRemaining = 10.0,
            wicketsInHand = 6,
            lastFiveOversRuns = 12,
            isPowerPlay = false,
            isDeathOvers = false
        )

        val analysis = calculator.analyzeMatchSituation(situation)
        assertTrue("Expected mention of below required rate", analysis.contains("below required rate"))
        assertTrue("Expected need to accelerate", analysis.contains("needs to accelerate"))
    }

    @Test
    fun `calculateSimpleWinProbability returns numeric probability between 0 and 100`() {
        val calculator = WinProbabilityCalculator()
        val prob = calculator.calculateSimpleWinProbability(
            runsNeeded = 20,
            ballsRemaining = 120,
            wicketsInHand = 7
        )
        assertTrue(prob >= 0.0 && prob <= 100.0)
    }
}
