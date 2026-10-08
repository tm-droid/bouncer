package com.tanush.bouncer.ui.widgets

import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which match the countdown widget counts down to. Nothing chose one before, so it showed none. */
class CountdownFixtureTest {

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun fixture(id: String, startsIn: Long?, vararg sides: String) = CricketMatch(
        id = id, seriesId = "", matchTitle = sides.joinToString(" vs "), matchType = "T20", status = "",
        teams = sides.map { side(it) }, venue = "", date = "", matchTime = "", score = null,
        currentStatus = "", info = "", startDateMs = startsIn?.let { now + it },
    )

    private fun side(name: String) =
        CricketMatch.Team(name = name, score = "", status = "", isBatting = false, logoUrl = null)

    @Test
    fun `the soonest fixture ahead is chosen`() {
        val list = listOf(fixture("late", 9 * hour, "A", "B"), fixture("soon", 2 * hour, "C", "D"))
        assertEquals("soon", nextFixture(list, emptySet(), now)?.id)
    }

    @Test
    fun `a followed team's next match comes before a sooner one`() {
        val list = listOf(fixture("soon", 2 * hour, "C", "D"), fixture("mine", 9 * hour, "India", "B"))
        assertEquals("mine", nextFixture(list, setOf("india"), now)?.id)
    }

    @Test
    fun `a fixture that has started, or has no start time, is not counted down to`() {
        val list = listOf(fixture("started", -hour, "A", "B"), fixture("undated", null, "C", "D"))
        assertNull(nextFixture(list, emptySet(), now))
    }

    @Test
    fun `a fixture without two sides cannot be drawn`() {
        assertNull(nextFixture(listOf(fixture("tbc", hour, "A")), emptySet(), now))
        assertNull(nextFixture(emptyList(), setOf("india"), now))
    }
}
