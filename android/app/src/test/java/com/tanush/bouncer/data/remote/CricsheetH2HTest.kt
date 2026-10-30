package com.tanush.bouncer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A trimmed `s/h2h/australia__india.json` as the history site publishes it. */
class CricsheetH2HTest {

    private val json = """
        {"t":["Australia","India"],"g":"male",
         "f":{"Test":[48,16,19,0,13,0],"ODI":[86,45,34,0,0,7],"T20I":[37,12,22,0,0,3]},
         "m":[["1","2025-11-08","T20I",-1,"no result"],["2","2025-11-06","T20I",1,null],
              ["3","2025-10-25","ODI",1,null],["4","2025-10-23","ODI",0,null],["5","2025-10-19","ODI",0,null],
              ["6","2025-03-04","ODI",1,null],["7","2025-01-03","Test",0,null],["8","2024-12-14","Test",-1,"draw"],
              ["9","2024-12-06","ODI",0,null],["10","2024-11-22","ODI",1,null]]}
    """.trimIndent()

    @Test
    fun `each format reports played, wins, draws and no results`() {
        val test = cricsheetH2H(json, "Australia", "India", "TEST")!!
        assertEquals(48, test.stats.totalMatches)
        assertEquals(16, test.stats.team1Wins)
        assertEquals(19, test.stats.team2Wins)
        assertEquals(13, test.stats.draws)
        val odi = cricsheetH2H(json, "Australia", "India", "ODI")!!
        assertEquals(86, odi.stats.totalMatches)
        assertEquals(7, odi.stats.noResults)
        assertEquals(0, odi.stats.draws)
        val t20 = cricsheetH2H(json, "Australia", "India", "T20")!!
        assertEquals(3, t20.stats.noResults)
        assertEquals(setOf("Test", "ODI", "T20I"), t20.history.formatStats.keys)
        assertEquals(86, t20.history.formatStats.getValue("ODI").totalMatches)
    }

    @Test
    fun `the caller's team order is kept when the file lists the sides the other way`() {
        val pair = cricsheetH2H(json, "India", "Australia", "TEST")!!
        assertEquals(19, pair.stats.team1Wins)
        assertEquals(16, pair.stats.team2Wins)
        assertEquals("India", pair.history.team1Name)
        // Meeting 7 was won by index 0 (Australia): that is the caller's second side.
        assertEquals(listOf("W2", "D"), pair.stats.lastFiveResults)
    }

    @Test
    fun `the last five meetings are of the shown format, newest first`() {
        val odi = cricsheetH2H(json, "Australia", "India", "ODI")!!
        assertEquals(listOf("W2", "W1", "W1", "W2", "W1"), odi.stats.lastFiveResults)
        assertEquals(5, odi.history.recentMatches.size)
        assertEquals("India", odi.history.recentMatches.first().winner)
        assertEquals("2025-10-25", odi.history.recentMatches.first().date)
    }

    @Test
    fun `no match type picks the busiest format`() {
        assertEquals(86, cricsheetH2H(json, "Australia", "India", null)!!.stats.totalMatches)
    }

    @Test
    fun `a format with no counters, or a file that is not one, is null`() {
        assertNull(cricsheetH2H(json, "Australia", "India", "FC"))
        assertNull(cricsheetH2H("<html>", "Australia", "India", "ODI"))
        assertNotNull(cricsheetH2H(json, "Australia", "India", "T20"))
    }

    @Test
    fun `the pair file carries no scores, so the card has no records to draw`() {
        val stats = cricsheetH2H(json, "Australia", "India", "ODI")!!.stats
        assertEquals("", stats.team1HighestScore + stats.team2LowestScore)
    }
}
