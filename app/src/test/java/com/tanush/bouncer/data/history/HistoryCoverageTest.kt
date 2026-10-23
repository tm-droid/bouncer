package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.SearchRepository.TeamEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCoverageTest {
    private val notice = "n"
    private val teams = listOf(
        TeamEntry("Afghanistan", "AFG"), TeamEntry("Afghanistan Women", null),
        TeamEntry("India", "IND"), TeamEntry("Australia", "AUS"), TeamEntry("Ireland", "IRE"),
    )

    private fun query(q: String) = HistoryCoverage.missingAfghanistan(q, teams, notice)

    @Test
    fun `a query naming Afghanistan by name, prefix or code gets the notice`() {
        listOf("afghanistan", "Afghan", "afg", "afg v ind", "ind vs afg", "afghanistan 2025").forEach {
            assertEquals(it, notice, query(it))
        }
    }

    @Test
    fun `other queries do not`() {
        listOf("india", "a", "ind v aus", "kohli").forEach { assertNull(it, query(it)) }
    }

    @Test
    fun `team names are matched case-insensitively with suffixes`() {
        assertEquals(notice, HistoryCoverage.missingAfghanistan(listOf("India", "AFGHANISTAN Women"), notice))
        assertNull(HistoryCoverage.missingAfghanistan(listOf("India", "Australia"), notice))
    }
}
