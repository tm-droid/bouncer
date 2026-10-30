package com.tanush.bouncer.managers

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamColorsLoaderTest {

    @Test
    fun `hex pairs parse and malformed entries are dropped`() {
        val parsed = parseTeamColors(
            mapOf(
                "Nepal" to listOf("#003893", "#DC143C"),
                "Oman" to listOf("#DB161B"),
                "Bad" to listOf("red", "#000000"),
                "Empty" to emptyList(),
            ),
        )
        assertEquals(Color(0xFF003893) to Color(0xFFDC143C), parsed["Nepal"])
        assertEquals("a lone colour is its own secondary", Color(0xFFDB161B) to Color(0xFFDB161B), parsed["Oman"])
        assertNull(parsed["Bad"])
        assertNull(parsed["Empty"])
    }

    @Test
    fun `hex parsing is strict`() {
        assertEquals(Color(0xFF00FF00), parseHexColor("#00FF00"))
        assertEquals(Color(0xFF00FF00), parseHexColor("00ff00"))
        assertNull(parseHexColor("#0F0"))
        assertNull(parseHexColor("#GG0000"))
    }
}
