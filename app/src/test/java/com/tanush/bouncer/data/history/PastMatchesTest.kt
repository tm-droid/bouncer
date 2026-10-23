package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PastMatchesTest {

    private fun cricsheet(winner: Int = -1, result: String? = null, format: String? = "ODI", women: Boolean = false) =
        PastMatchRow(
            id = "433606", date = LocalDate.of(2011, 4, 2), format = format, women = women,
            team1 = "India", team2 = "Sri Lanka", winner = winner, result = result,
            event = "ICC Cricket World Cup", venue = "Wankhede Stadium", source = PastSource.CRICSHEET,
        )

    @Test
    fun `every kind of Cricsheet result is a completed match`() {
        listOf(cricsheet(winner = 0), cricsheet(winner = 1), cricsheet(result = "tie"), cricsheet(result = "draw"),
            cricsheet(result = "no result"), cricsheet()).forEach { row ->
            val match = row.toCricketMatch()
            assertTrue("${match.currentStatus} must be completed", match.isCompleted)
            assertFalse(match.isLive)
        }
    }

    @Test
    fun `a Cricsheet row gets the prefixed id and the status names its winner`() {
        val match = cricsheet(winner = 1).toCricketMatch()
        assertEquals("cs-433606", match.id)
        assertTrue(match.isCricsheetOnly)
        assertEquals("Sri Lanka won the match", match.currentStatus)
        assertEquals("India vs Sri Lanka", match.matchTitle)
        assertEquals("ICC Cricket World Cup", match.seriesName)
        assertEquals("Wankhede Stadium", match.venue)
        assertEquals("Men", match.gender)
        assertEquals("International", match.competitionType)
        assertEquals("ODI", match.matchType)
        assertEquals(1301702400000L, match.startDateMs)
        assertEquals(listOf("India", "Sri Lanka"), match.teams.map { it.name })
    }

    @Test
    fun `women and league formats carry through`() {
        val match = cricsheet(format = "T20", women = true).toCricketMatch()
        assertEquals("Women", match.gender)
        assertEquals("League", match.competitionType)
        assertEquals("T20", match.matchType)
    }

    private fun archiveRow(result: String?) = PastMatchRow.ofArchive(
        JsonArray(
            listOf("175510", "2026-10-22", "ODI", "male", "Kuwait", "Qatar", result, "ICC CWC Challenge League A", "Ruaraka")
                .map { if (it == null) JsonNull else JsonPrimitive(it) },
        ),
    )!!

    @Test
    fun `an archive row keeps its Cricbuzz id and its own result text`() {
        val match = archiveRow("Qatar won by 5 wkts").toCricketMatch()
        assertEquals("175510", match.id)
        assertFalse(match.isCricsheetOnly)
        assertEquals("Qatar won by 5 wkts", match.currentStatus)
        assertTrue(match.isCompleted)
        assertEquals(PastSource.ARCHIVE, archiveRow(null).source)
    }

    @Test
    fun `an archive result that is not terminal falls back to complete`() {
        for (text in listOf(null, "Live", "Day 2: Stumps")) {
            val match = archiveRow(text).toCricketMatch()
            assertTrue("'$text' became '${match.currentStatus}'", match.isCompleted)
        }
    }

    @Test
    fun `index rows of either site decode`() {
        val row = HistoryIndexRow.of(
            AppJson.parseToJsonElement(
                """["1","2011-04-02","ODI","male","India","Sri Lanka",0,null,"ICC Cricket World Cup","Wankhede"]""",
            ) as JsonArray,
        )!!
        assertEquals(0, row.winner)
        assertNull(row.result)
        assertEquals("ICC Cricket World Cup", row.event)
        assertEquals("Wankhede", row.venue)
        assertEquals(PastSource.CRICSHEET, PastMatchRow.of(row).source)
        assertEquals(LocalDate.of(2011, 4, 2), PastMatchRow.of(row).date)
    }
}
