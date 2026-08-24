package com.tanush.bouncer.ui.screens.matchdetail.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A required run rate of zero is not a required run rate.
 *
 * The server sends `"0"` wherever no chase is under way — a first innings, a Test that is not a
 * fourth-innings chase, a completed chase — and "RRR: 0" is a false statement about all three.
 */
class RunRateDisplayTest {

    @Test
    fun `zero is not shown`() {
        assertNull(meaningfulRequiredRunRate("0"))
        assertNull(meaningfulRequiredRunRate("0.00"))
    }

    @Test
    fun `absent and blank are not shown`() {
        assertNull(meaningfulRequiredRunRate(null))
        assertNull(meaningfulRequiredRunRate("   "))
    }

    @Test
    fun `a real chase is shown`() {
        assertEquals("7.42", meaningfulRequiredRunRate("7.42"))
        assertEquals("0.85", meaningfulRequiredRunRate("0.85"))
    }

    @Test
    fun `an unparseable figure is not shown`() {
        // Better to render nothing than to put a scraped fragment in the header.
        assertNull(meaningfulRequiredRunRate("--"))
    }
}
