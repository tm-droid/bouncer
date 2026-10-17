package com.tanush.bouncer.ui.screens.player

import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerProfileSectionsTest {

    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun `cricbuzz's date of birth becomes a short date and an age`() {
        assertEquals("5 Nov 1988 · 37", bornLine("November 05, 1988", today))
        assertEquals("5 Nov 1988 · 37", bornLine("November 05, 1988 (36 years)", today))
        assertEquals("17 Oct 2000 · 26", bornLine("October 17, 2000", today))
    }

    @Test
    fun `an unknown shape is shown as it came, and blank is blank`() {
        assertEquals("c. 1990", bornLine("c. 1990", today))
        assertEquals("", bornLine("  ", today))
    }

    @Test
    fun `only formats with figures are offered`() {
        val all = listOf("Test", "ODI", "T20I", "IPL")
        val profile = PlayerProfile(
            id = "1",
            name = "x",
            country = "India",
            role = "Batter",
            stats = mapOf("ODI" to FormatStats(), "IPL" to FormatStats()),
        )
        assertEquals(listOf("ODI", "IPL"), formatsWithStats(profile, all))
        assertEquals(all, formatsWithStats(profile.copy(stats = emptyMap()), all))
    }
}
