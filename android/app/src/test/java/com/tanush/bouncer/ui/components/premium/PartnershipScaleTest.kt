package com.tanush.bouncer.ui.components.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The partnership bar fraction must be finite and within `0f..1f` for every scraped input.
 *
 * `Modifier.fillMaxWidth(fraction = …)` rounds the fraction; a NaN there throws
 * `IllegalArgumentException: Cannot round NaN value` and takes the Scorecard tab down with it.
 */
class PartnershipScaleTest {

    @Test
    fun `all-zero partnerships do not produce NaN`() {
        // Every row unparseable -> max 0. The old `maxOfOrNull { … } ?: 1` never fired here: the
        // elvis only covers an empty list, not a non-empty list of zeroes.
        val fraction = partnershipBarFraction(totalRuns = 0, maxRuns = 0, progress = 1f)
        assertTrue("fraction was NaN", !fraction.isNaN())
        assertEquals(0f, fraction, 0f)
    }

    @Test
    fun `a partnership larger than the max is clamped to the full bar`() {
        assertEquals(1f, partnershipBarFraction(totalRuns = 120, maxRuns = 80, progress = 1f), 0f)
    }

    @Test
    fun `an ordinary partnership scales linearly`() {
        assertEquals(0.5f, partnershipBarFraction(totalRuns = 40, maxRuns = 80, progress = 1f), 0.001f)
    }

    @Test
    fun `partial animation progress scales the bar down`() {
        assertEquals(0.25f, partnershipBarFraction(totalRuns = 40, maxRuns = 80, progress = 0.5f), 0.001f)
    }
}
