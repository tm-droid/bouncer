package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.SearchRepository.TeamEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PastMatchIndexTest {

    private val teams = listOf(
        TeamEntry("India", "IND"), TeamEntry("Australia", "AUS"), TeamEntry("Sri Lanka", "SL"),
        TeamEntry("New Zealand", "NZ"), TeamEntry("India Women", "INDW"), TeamEntry("Australia Women", "AUSW"),
    )

    private fun cs(id: String, date: String, a: String, b: String, event: String?, format: String = "ODI") =
        HistoryIndexRow(id, date, format, "male", a, b, winner = 0, event = event)

    private fun csWomen(id: String, date: String, format: String) =
        HistoryIndexRow(id, date, format, "female", "India Women", "Australia Women", winner = 1, event = "Australia Women tour of India")

    private val cricsheet = listOf(
        cs("7", "2003-02-20", "Australia", "Sri Lanka", "ICC Cricket World Cup"),
        cs("8", "2025-11-08", "India", "Australia", "India tour of Australia", "T20I"),
        csWomen("9", "2026-03-06", "Test"),
        cs("1", "2003-03-23", "Australia", "India", "ICC Cricket World Cup"),
        cs("2", "2003-02-15", "India", "Australia", "ICC Cricket World Cup"),
        cs("3", "2011-03-24", "India", "Australia", "ICC Cricket World Cup"),
        cs("4", "2011-04-02", "India", "Sri Lanka", "ICC Cricket World Cup"),
        cs("5", "2011-12-26", "Australia", "India", "Border-Gavaskar Trophy", "Test"),
        cs("6", "2007-09-24", "India", "Australia", "ICC World Twenty20", "T20I"),
    )

    private val index = PastMatchIndex.build(cricsheet, emptyList())

    private fun ids(query: String, index: PastMatchIndex = this.index) =
        index.search(query, emptyList(), teams).map { it.row.id }

    @Test
    fun `a team pair needs both sides and the year facet ranks first`() {
        val found = ids("ind v aus 2011")
        assertEquals(listOf("3", "5"), found.take(2).sorted())
        assertFalse("4" in found)
        assertEquals(setOf("1", "2", "3", "5", "6", "8"), ids("ind v aus").toSet())
    }

    @Test
    fun `the last match of a tournament is its final`() {
        assertEquals("1", ids("ind v aus 2003 final").first())
        assertEquals("4", ids("ind v sl 2011 final").first())
    }

    @Test
    fun `a year outranks recency and a bilateral series has no final`() {
        // The Test and the World Cup quarter-final are 2011; the 2025 T20I and the 2003 final are not.
        val found = ids("ind v aus 2011 final")
        assertEquals(setOf("3", "5"), found.take(2).toSet())
        assertTrue(found.indexOf("1") > 1)
        assertTrue(found.indexOf("8") > 1)
        assertTrue("9" !in found)
    }

    @Test
    fun `a men's query leaves out women's matches and a women's query leaves out men's`() {
        assertFalse("9" in ids("ind v aus"))
        assertFalse("9" in ids("ind v aus test"))
        assertEquals(listOf("9"), ids("india women v australia women"))
        assertEquals(listOf("9"), ids("ind v aus women"))
    }

    @Test
    fun `a stage and year alone find finals`() {
        assertTrue("4" in ids("2011 final"))
    }

    @Test
    fun `format boosts`() {
        assertEquals(setOf("6", "8"), ids("ind v aus t20i").take(2).toSet())
    }

    @Test
    fun `a typo still finds the match`() {
        assertTrue("4" in ids("sri lnaka"))
        assertTrue("5" in ids("gavaskr trophy"))
    }

    @Test
    fun `an unknown word finds nothing`() {
        assertTrue(ids("zzzzqqq").isEmpty())
    }

    private fun archiveRow(id: String, date: String, a: String, b: String) = PastMatchRow.ofArchive(
        JsonArray(listOf(id, date, "ODI", "male", a, b, "$a won", "Some series", null).map {
            if (it == null) JsonNull else JsonPrimitive(it)
        }),
    )!!

    @Test
    fun `an archive row replaces the Cricsheet row for the same match`() {
        val merged = PastMatchIndex.build(cricsheet, listOf(archiveRow("999", "2011-04-02", "India", "Sri Lanka")))
        val found = merged.search("ind v sl", emptyList(), teams)
        assertEquals(listOf("999"), found.map { it.row.id }.filter { it == "999" || it == "4" })
        assertEquals(PastSource.ARCHIVE, found.first { it.row.id == "999" }.row.source)
    }

    @Test
    fun `an archive row already loaded is not repeated`() {
        val merged = PastMatchIndex.build(cricsheet, listOf(archiveRow("999", "2020-01-01", "India", "Australia")))
        val loaded = archiveRowMatch("999")
        assertTrue("999" in merged.search("ind v aus", emptyList(), teams).map { it.row.id })
        assertFalse("999" in merged.search("ind v aus", listOf(loaded), teams).map { it.row.id })
    }

    @Test
    fun `a Cricsheet row a loaded match links to is not repeated`() {
        val loaded = cricsheet.first { it.id == "4" }.let { PastMatchRow.of(it).toCricketMatch().copy(id = "175000") }
        assertFalse("4" in index.search("ind v sl", listOf(loaded), teams).map { it.row.id })
    }

    private fun archiveRowMatch(id: String) = archiveRow(id, "2020-01-01", "India", "Australia").toCricketMatch()
}
