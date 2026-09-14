package com.tanush.bouncer.services

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the fallback served when Cricbuzz's Upcoming page is empty: the last list the device saw,
 * with every fixture that has started removed, so a match already underway is never called upcoming.
 */
class UpcomingFallbackTest {

    private val now = 1_789_400_000_000L

    private fun fixture(id: String, startDateMs: Long?) = CricketMatch(
        id = id, seriesId = "", matchTitle = "A vs B", matchType = "ODI",
        status = "Match starts at Sep 15, 07:30 GMT", teams = emptyList(), venue = "", date = "",
        matchTime = "", score = null, currentStatus = "Match starts at Sep 15, 07:30 GMT",
        info = "Upcoming", startDateMs = startDateMs,
    )

    private fun stored(vararg matches: CricketMatch) = AppJson.encodeToString(matches.toList())

    @Test
    fun `fixtures that have not started are served`() {
        val kept = upcomingStillAhead(stored(fixture("later", now + 1), fixture("tomorrow", now + 86_400_000)), now)
        assertEquals(listOf("later", "tomorrow"), kept?.map { it.id })
    }

    @Test
    fun `a fixture that has started is dropped`() {
        val kept = upcomingStillAhead(stored(fixture("started", now), fixture("earlier", now - 1), fixture("later", now + 1)), now)
        assertEquals(listOf("later"), kept?.map { it.id })
    }

    @Test
    fun `a fixture with no start time is dropped, since nothing could expire it`() {
        assertEquals(emptyList<String>(), upcomingStillAhead(stored(fixture("undated", null)), now)?.map { it.id })
    }

    @Test
    fun `an unreadable list is reported as unreadable, not as empty`() {
        assertNull(upcomingStillAhead("{not json", now))
    }
}
