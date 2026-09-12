package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the points-table parser against real captured markup.
 *
 * The screen was empty on every series for three independent reasons, and a fixture is the only
 * thing that would have caught any of them: the page has no `<table>` for the Jsoup path, no
 * `__NEXT_DATA__` for the fallback, and the URL the scraper built has no series slug — which
 * answers 200 with no standings on it rather than 404.
 */
class PointsTableFlightTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private val entries by lazy { parsePointsTableFlight(fixture("cricbuzz_points_table.html")) }

    @Test
    fun `every team in the group is read`() {
        assertEquals(7, entries.size)
    }

    @Test
    fun `the full name is used, not the short code`() {
        val leader = entries.first()
        assertEquals("Guyana Amazon Warriors", leader.teamName)
        assertEquals("GAW", leader.teamAcronym)
        assertEquals("159", leader.teamId)
    }

    @Test
    fun `the crest comes from the row's own image id`() {
        assertEquals(
            "https://static.cricbuzz.com/a/img/v1/720x540/i1/c825126/i.jpg",
            entries.first().logoUrl,
        )
    }

    @Test
    fun `the signed net run rate parses`() {
        // Cricbuzz writes "+1.029". A leading plus parses as a Double and would not as an Int.
        assertEquals(1.029, entries.first().netRunRate, 0.0001)
    }

    @Test
    fun `played won lost and points are read`() {
        val leader = entries.first()
        assertEquals(8, leader.matches)
        assertEquals(7, leader.won)
        assertEquals(1, leader.lost)
        assertEquals(14, leader.points)
    }

    @Test
    fun `recent form and qualification are read`() {
        val leader = entries.first()
        assertEquals(listOf("W", "W", "W", "L", "W"), leader.recentForm)
        assertTrue(leader.qualified)
    }

    @Test
    fun `positions are assigned in listed order`() {
        assertEquals((1..entries.size).toList(), entries.map { it.position })
    }

    @Test
    fun `the unslugged page yields nothing rather than garbage`() {
        assertEquals(emptyList<Any>(), parsePointsTableFlight(fixture("cricbuzz_points_table_noslug.html")))
    }

    @Test
    fun `the slugged url is recovered from the unslugged page`() {
        val html = fixture("cricbuzz_points_table_noslug.html")
        assertEquals(
            "https://www.cricbuzz.com/cricket-series/12123/caribbean-premier-league-2026/points-table",
            sluggedPointsTableUrl(html, "12123"),
        )
    }

    @Test
    fun `a series with no link on the page returns null rather than a wrong url`() {
        assertNull(sluggedPointsTableUrl(fixture("cricbuzz_points_table_noslug.html"), "99999"))
    }
}
