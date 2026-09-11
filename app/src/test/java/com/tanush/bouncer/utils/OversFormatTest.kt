package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class OversFormatTest {

    @Test
    fun `a sixth ball rolls into the next whole over`() {
        assertEquals("20", displayOvers("19.6"))
        assertEquals("1", displayOvers("0.6"))
        assertEquals("50", displayOvers("49.6"))
    }

    @Test
    fun `an over in progress is unchanged`() {
        assertEquals("12.3", displayOvers("12.3"))
        assertEquals("88.5", displayOvers(" 88.5 "))
    }

    @Test
    fun `non over text passes through`() {
        assertEquals("", displayOvers(""))
        assertEquals("0.0", displayOvers("0.0"))
        assertEquals("20", displayOvers("20"))
        assertEquals("Ball 72", displayOvers("Ball 72"))
    }
}
