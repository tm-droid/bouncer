package com.tanush.bouncer.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamRecordTest {

    @Test fun `formats come busiest first with the counters in order`() {
        val records = HistoryRecords.parseTeam(
            """{"t":"India","g":"male","f":{"T20I":[280,187,81,6,0,6],"ODI":[529,315,185,6,0,23],""" +
                """"Test":[219,105,60,0,54,0]},"y":{},"m":[]}""",
        )!!
        assertEquals(listOf("ODI", "T20I", "Test"), records.map { it.format })
        assertEquals(FormatRecord("Test", played = 219, won = 105, lost = 60, tied = 0, drawn = 54), records[2])
        assertEquals(6, records[1].tied)
    }

    @Test fun `a format with no matches or short counters is left out`() {
        val records = HistoryRecords.parseTeam("""{"f":{"ODI":[0,0,0,0,0,0],"T20":[3,1,2],"Test":[2,1,1,0,0,0]}}""")!!
        assertEquals(listOf("Test"), records.map { it.format })
    }

    @Test fun `unreadable json is null`() {
        assertNull(HistoryRecords.parseTeam("not json"))
        assertNull(HistoryRecords.parseTeam("""{"t":"x"}"""))
    }

    @Test fun `ground record averages the first innings and counts the toss-up wins`() {
        val json = """{"n":"Wankhede Stadium","city":"Mumbai","f":{"T20I":[20,3584,20,10,10],"Test":[8,2382,8,0,0]},"m":[]}"""
        val t20 = HistoryRecords.parseVenue(json, "T20", international = true)!!
        assertEquals(VenueRecord(matches = 20, averageFirstInnings = 179, wonBattingFirst = 10, wonChasing = 10), t20)
        assertEquals(297, HistoryRecords.parseVenue(json, "Test", international = true)!!.averageFirstInnings)
        assertNull(HistoryRecords.parseVenue(json, "T20", international = false))
    }
}
