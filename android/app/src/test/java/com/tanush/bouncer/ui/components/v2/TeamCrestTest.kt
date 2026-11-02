package com.tanush.bouncer.ui.components.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamCrestTest {

    @Test
    fun `the monogram is the team code, capped at three characters`() {
        assertEquals("IND", crestMonogram("IND", "India"))
        assertEquals("RCB", crestMonogram("rcb", "Royal Challengers Bengaluru"))
        assertEquals("NSW", crestMonogram("N.S.W.", "New South Wales"))
        assertEquals("ABC", crestMonogram("ABCD", "Whatever"))
    }

    @Test
    fun `without a code the monogram is the name's first letter`() {
        assertEquals("I", crestMonogram(null, "India"))
        assertEquals("I", crestMonogram("", " india "))
        assertEquals("", crestMonogram(null, ""))
    }

    @Test
    fun `initials are first and last name`() {
        assertEquals("VK", personInitials("Virat Kohli"))
        assertEquals("RK", personInitials("Rashid Khan"))
        assertEquals("AV", personInitials("AB de Villiers"))
        assertEquals("S", personInitials("Shakib"))
        assertEquals("", personInitials("  "))
    }

    @Test
    fun `figures are recognised for tabular digits`() {
        assertTrue(isFigure("142"))
        assertTrue(isFigure("34.50"))
        assertTrue(isFigure("-0.21"))
        assertTrue(isFigure("61%"))
        assertTrue(isFigure("28°C"))
        assertTrue(isFigure("1,204"))
        assertFalse(isFigure("Wankhede Stadium, Mumbai"))
        assertFalse(isFigure("T20"))
    }
}
