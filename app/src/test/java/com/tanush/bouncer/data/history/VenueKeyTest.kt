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
}
