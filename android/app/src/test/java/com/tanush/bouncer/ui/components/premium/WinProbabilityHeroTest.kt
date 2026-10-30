package com.tanush.bouncer.ui.components.premium

import org.junit.Assert.assertEquals
import org.junit.Test

class WinProbabilityHeroTest {

    @Test
    fun `three shares always total one hundred`() {
        // The case that shipped: independent rounding produced 32 + 17 + 50 = 99.
        assertEquals(100, wholePercents(0.324f, 0.174f, 0.502f).sum())
        assertEquals(100, wholePercents(0.333f, 0.333f, 0.334f).sum())
        assertEquals(100, wholePercents(0.005f, 0.005f, 0.99f).sum())
    }

    @Test
    fun `leftover points go to the largest remainders`() {
        // Floors are 33/33/33 with one point left over; .34 has the largest remainder.
        assertEquals(listOf(33, 33, 34), wholePercents(0.333f, 0.333f, 0.334f))
    }

    @Test
    fun `exact shares are left alone`() {
        assertEquals(listOf(50, 0, 50), wholePercents(0.5f, 0f, 0.5f))
    }

    @Test
    fun `a completed match is final whatever the server said`() {
        assertEquals(
            WinProbabilityState.FINAL,
            winProbabilityStateFor(isCompleted = true, isPrematch = true, isUpcoming = true),
        )
    }

    @Test
    fun `the server's pre-match flag outranks a live-looking status`() {
        assertEquals(
            WinProbabilityState.PRE_MATCH,
            winProbabilityStateFor(isCompleted = false, isPrematch = true, isUpcoming = false),
        )
    }

    @Test
    fun `an in-play match with a live number is live`() {
        assertEquals(
            WinProbabilityState.LIVE,
            winProbabilityStateFor(isCompleted = false, isPrematch = false, isUpcoming = false),
        )
    }
}
