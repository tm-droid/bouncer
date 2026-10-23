package com.tanush.bouncer.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VenueKeyTest {

    private val rows = HistoryRecords.parseVenues(
        """
        [["wankhede-stadium-mumbai","Wankhede Stadium","Mumbai",200],
         ["county-ground-derby","County Ground","Derby",20],
         ["county-ground-taunton","County Ground","Taunton",30],
         ["eden-gardens-kolkata","Eden Gardens, Kolkata","Kolkata",150],
         ["seddon-park-hamilton","Seddon Park",null,90]]
        """.trimIndent(),
    )!!

    private fun key(text: String) = VenueKey.lookup(rows, text)?.key

    @Test fun `the slug matches the builder's file key`() {
        assertEquals("wankhede-stadium-mumbai", VenueKey.slug("Wankhede Stadium", "Mumbai"))
        assertEquals("seddon-park", VenueKey.slug("Seddon Park", ""))
        assertEquals("unknown", VenueKey.slug("", ""))
    }

    @Test fun `name and city resolve exactly`() {
        assertEquals("wankhede-stadium-mumbai", key("Wankhede Stadium, Mumbai"))
        assertEquals("eden-gardens-kolkata", key("Eden Gardens, Kolkata"))
    }

    @Test fun `a name alone resolves when one ground has it`() {
        assertEquals("wankhede-stadium-mumbai", key("Wankhede Stadium"))
    }

    @Test fun `an ambiguous name is null`() {
        assertNull(key("County Ground"))
        assertEquals("county-ground-taunton", key("County Ground, Taunton"))
    }

    @Test fun `a different city is not the same ground`() {
        assertNull(key("Wankhede Stadium, Pune"))
        assertNull(key(""))
    }

    @Test fun `a ground with no city reads back`() {
        assertEquals("seddon-park-hamilton", key("Seddon Park"))
    }

    @Test fun `the format follows the match type and competition`() {
        val all = setOf("Test", "ODI", "T20", "T20I")
        assertEquals("T20I", HistoryRecords.venueFormat("T20", true, all))
        assertEquals("T20", HistoryRecords.venueFormat("T20", false, all))
        assertNull(HistoryRecords.venueFormat("T20", true, setOf("T20")))
        assertEquals("Test", HistoryRecords.venueFormat("Test", true, all))
        assertNull(HistoryRecords.venueFormat("Hundred", false, all))
    }

    /** Rows copied from the live `idx/venues.json`: a ground appears with and without a city. */
    private val live = HistoryRecords.parseVenues(
        """
        [["wankhede-stadium-mumbai","Wankhede Stadium","Mumbai",182],["wankhede-stadium","Wankhede Stadium",null,31],
         ["melbourne-cricket-ground-melbourne","Melbourne Cricket Ground","Melbourne",102],
         ["melbourne-cricket-ground","Melbourne Cricket Ground",null,90],
         ["eden-gardens-kolkata","Eden Gardens","Kolkata",159],["eden-gardens","Eden Gardens",null,17],
         ["m-chinnaswamy-stadium-bangalore","M Chinnaswamy Stadium","Bangalore",90],
         ["m-chinnaswamy-stadium-bengaluru","M Chinnaswamy Stadium","Bengaluru",83],
         ["ma-chidambaram-stadium-chennai","MA Chidambaram Stadium, Chepauk","Chennai",141]]
        """.trimIndent(),
    )!!

    @Test fun `Cricbuzz's ground text resolves to the row with its city`() {
        fun live(text: String) = VenueKey.lookup(live, text)?.key
        assertEquals("wankhede-stadium-mumbai", live("Wankhede Stadium, Mumbai"))
        assertEquals("melbourne-cricket-ground-melbourne", live("Melbourne Cricket Ground, Melbourne"))
        assertEquals("eden-gardens-kolkata", live("Eden Gardens, Kolkata"))
        assertEquals("m-chinnaswamy-stadium-bengaluru", live("M Chinnaswamy Stadium, Bengaluru"))
        assertEquals("ma-chidambaram-stadium-chennai", live("MA Chidambaram Stadium, Chennai"))
    }

    @Test fun `a Cricsheet ground with no city resolves to its own row`() {
        assertEquals("melbourne-cricket-ground", VenueKey.lookup(live, "Melbourne Cricket Ground")?.key)
    }

    // Shapes copied from the live s/venue files: the same ground is split by Cricsheet's city.
    private val mcgNoCity = """{"n":"Melbourne Cricket Ground","city":null,"f":{"Test":[13,4398,13,0,0],"ODI":[31,8054,31,15,16]},"m":[]}"""

    @Test fun `a Test carries no win counts, so the record has none to show`() {
        val test = HistoryRecords.parseVenue(mcgNoCity, "Test", true)!!
        assertEquals(13, test.matches)
        assertEquals(338, test.averageFirstInnings)
        assertEquals(0, test.wonBattingFirst + test.wonChasing)
    }

    @Test fun `an ODI at the same ground carries batting first and chasing wins`() {
        val odi = HistoryRecords.parseVenue(mcgNoCity, "ODI", true)!!
        assertEquals(15, odi.wonBattingFirst)
        assertEquals(16, odi.wonChasing)
    }
}
