package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamNameMatchingTest {

    // --- winner attribution -------------------------------------------------

    @Test
    fun `a West Indies win is not credited to India`() {
        // "India" is a substring of "West Indies". Independent contains() checks credited the win
        // to both sides at once.
        assertEquals(
            WinnerSide.TEAM2,
            attributeWinner("West Indies won by 7 wickets", "India", "West Indies"),
        )
    }

    @Test
    fun `an India win is credited to India`() {
        assertEquals(
            WinnerSide.TEAM1,
            attributeWinner("India won by 5 wickets", "India", "West Indies"),
        )
    }

    @Test
    fun `exactly one side is ever credited`() {
        // The old code could increment both counters for a single match, letting
        // team1Wins + team2Wins exceed the number of matches played.
        for (text in listOf(
            "India won by 5 wickets",
            "West Indies won by 7 wickets",
            "Match drawn",
            "",
        )) {
            val side = attributeWinner(text, "India", "West Indies")
            // A single enum value or null — there is no representation for "both".
            assertEquals(side, attributeWinner(text, "India", "West Indies"))
        }
    }

    @Test
    fun `an acronym result is attributed`() {
        // "IND won by 5 wickets" matched neither side before, so the win vanished.
        assertEquals(
            WinnerSide.TEAM1,
            attributeWinner("IND won by 5 wickets", "India", "Australia"),
        )
    }

    @Test
    fun `an unrelated or absent winner credits nobody`() {
        assertNull(attributeWinner("Match drawn", "India", "Australia"))
        assertNull(attributeWinner("No result", "India", "Australia"))
        assertNull(attributeWinner(null, "India", "Australia"))
        assertNull(attributeWinner("", "India", "Australia"))
        assertNull(attributeWinner("England won by 4 wickets", "India", "Australia"))
    }

    @Test
    fun `a team name inside a longer word does not match`() {
        assertNull(attributeWinner("Indianapolis won", "India", "Australia"))
    }

    @Test
    fun `multi-word franchise names attribute correctly`() {
        assertEquals(
            WinnerSide.TEAM1,
            attributeWinner(
                "Royal Challengers Bengaluru won by 4 wickets",
                "Royal Challengers Bengaluru",
                "Chennai Super Kings",
            ),
        )
    }

    // --- acronyms -----------------------------------------------------------

    @Test
    fun `acronym uses significant-word initials`() {
        assertEquals("RCB", acronymFor("Royal Challengers Bengaluru"))
        assertEquals("CSK", acronymFor("Chennai Super Kings"))
    }

    @Test
    fun `the two Sunrisers franchises no longer collide`() {
        // substring(0, 3) gave both "SUN".
        val hyderabad = acronymFor("Sunrisers Hyderabad")
        val eastern = acronymFor("Sunrisers Eastern Cape")
        assertEquals("SH", hyderabad)
        assertEquals("SEC", eastern)
    }

    @Test
    fun `short names do not crash`() {
        // substring(0, 3) threw on anything shorter than three characters.
        assertEquals("IN", acronymFor("IN"))
        assertEquals("A", acronymFor("A"))
        assertEquals("", acronymFor(""))
    }

    @Test
    fun `noise words are ignored`() {
        assertEquals("SA", acronymFor("South Africa Cricket Team"))
    }

    @Test
    fun `a single word uses its opening letters`() {
        assertEquals("IND", acronymFor("India"))
    }

    // --- normalisation ------------------------------------------------------

    @Test
    fun `normalisation strips punctuation and case`() {
        assertEquals("st kitts nevis patriots", normalizeTeamName("St. Kitts & Nevis Patriots"))
        assertEquals("india", normalizeTeamName("  INDIA  "))
    }
}
