package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.TimelineEventType
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryMatchLinkerTest {

    private fun match(date: String, vararg sides: String, gender: String = "Men") = CricketMatch(
        id = "cb", seriesId = "", matchTitle = sides.joinToString(" vs "), matchType = "ODI", status = "",
        teams = sides.map { CricketMatch.Team(name = it, score = "", status = "", isBatting = false, logoUrl = null) },
        venue = "", date = "", matchTime = "", score = null, currentStatus = "", info = "",
        gender = gender,
        // 09:00 UTC, the morning of a day game in Asia.
        startDateMs = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 9 * 3_600_000L,
    )

    private fun row(id: String, date: String, t1: String, t2: String, gender: String = "male") =
        HistoryIndexRow(id = id, date = date, format = "ODI", gender = gender, team1 = t1, team2 = t2)

    @Test
    fun `same day and same sides link, in either order`() {
        val rows = listOf(row("433606", "2011-04-02", "India", "Sri Lanka"))
        assertEquals("433606", HistoryMatchLinker.link(match("2011-04-02", "Sri Lanka", "India"), rows))
    }

    @Test
    fun `a day either side links, two days does not`() {
        val rows = listOf(row("1", "2011-04-03", "India", "Sri Lanka"))
        assertEquals("1", HistoryMatchLinker.link(match("2011-04-02", "India", "Sri Lanka"), rows))
        assertNull(HistoryMatchLinker.link(match("2011-04-01", "India", "Sri Lanka"), rows))
    }

    @Test
    fun `two candidates link to nothing`() {
        val rows = listOf(row("1", "2011-04-02", "India", "Sri Lanka"), row("2", "2011-04-03", "India", "Sri Lanka"))
        assertNull(HistoryMatchLinker.link(match("2011-04-02", "India", "Sri Lanka"), rows))
    }

    @Test
    fun `gender must agree, and the women's marker is not part of the name`() {
        val rows = listOf(row("w", "2022-03-27", "Australia", "England", gender = "female"))
        assertEquals("w", HistoryMatchLinker.link(match("2022-03-27", "Australia Women", "England Women", gender = "Women"), rows))
        assertNull(HistoryMatchLinker.link(match("2022-03-27", "Australia", "England"), rows))
    }

    @Test
    fun `an A side never links to the senior side`() {
        val rows = listOf(row("1", "2019-01-10", "India", "England"))
        assertNull(HistoryMatchLinker.link(match("2019-01-10", "India A", "England"), rows))
    }

    @Test
    fun `a renamed franchise links when the other side is exact and nothing else fits`() {
        val rows = listOf(row("ipl", "2021-04-12", "Kings XI Punjab", "Rajasthan Royals"))
        assertEquals("ipl", HistoryMatchLinker.link(match("2021-04-12", "Punjab Kings", "Rajasthan Royals"), rows))
    }

    @Test
    fun `India is not West Indies`() {
        val rows = listOf(row("1", "2011-03-20", "West Indies", "Sri Lanka"))
        assertNull(HistoryMatchLinker.link(match("2011-03-20", "India", "Sri Lanka"), rows))
    }

    @Test
    fun `a match a day from a month's edge asks for both months`() {
        assertEquals(listOf("2011-03", "2011-04"), HistoryMatchLinker.monthsFor(match("2011-04-01", "A", "B")))
        assertEquals(listOf("2011-04"), HistoryMatchLinker.monthsFor(match("2011-04-15", "A", "B")))
    }
}

class HistoryCommentaryTest {

    // Two deliveries of the 2011 final's shape: a wide, then a caught.
    private val json = """
        {"v":1,"id":"9","t":["A","B"],"p":[["a1"],["a2"],["z"],["y"]],
         "inn":[{"t":0,"b":[[0,1,0,2,1,0,1,1],[0,1,0,2,1,4,0,0],[0,2,1,2,0,0,0,0,[["caught",1,3]]]],
                 "sc":{"bat":[[0,4,1,1,0,null],[1,0,1,0,0,"c y b z"]],"tot":[5,1,2]}}]}
    """.trimIndent()
    private val match = AppJson.decodeFromString<HistoryMatch>(json)

    @Test
    fun `lines are newest first with the scorer's ball label and a structured verdict`() {
        val lines = HistoryCommentary.entries(match)
        assertEquals(listOf("0.2", "0.1", "0.1"), lines.map { it.ball })
        assertEquals("z to a2, OUT! c y b z", lines[0].text)
        assertEquals(TimelineEventType.WICKET, lines[0].event)
        assertEquals("z to a1, FOUR", lines[1].text)
        assertEquals(TimelineEventType.FOUR, lines[1].event)
        assertEquals(4, lines[1].runs)
        assertEquals("z to a1, wide", lines[2].text)
        assertEquals("WIDE", lines[2].extra)
        assertEquals(1, lines[2].runs)
        assertEquals(listOf(1, 1, 1), lines.map { it.innings })
    }

    @Test
    fun `a malformed delivery is skipped, not guessed`() {
        assertNull(HistoryDelivery.of(AppJson.parseToJsonElement("[0,1,2]") as JsonArray))
    }
}
