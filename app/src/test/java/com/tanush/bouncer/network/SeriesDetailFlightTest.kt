package com.tanush.bouncer.network

import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.models.Series
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the series Schedule, Stats and archive parsers against captured Cricbuzz markup.
 *
 * Schedule and Stats were "coming soon" placeholders on every series with no parser behind them,
 * and the Series tab had no way to reach a finished series. Every page here is App Router: the data
 * is in the flight stream, and each page also carries the site-wide live ticker, which a parser that
 * keyed on bare `matchInfo` would list under every series.
 */
class SeriesDetailFlightTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private val schedule by lazy { parseSeriesSchedule(fixture("cricbuzz_series_matches.html")) }

    @Test
    fun `every date group on the matches page is read, in page order`() {
        assertEquals(8, schedule.size)
        assertEquals("Thu, 15 Feb 2024", schedule.first().label)
        assertEquals("Mon, 19 Oct 2026", schedule.last().label)
    }

    @Test
    fun `the site-wide ticker on the same page is not listed under the series`() {
        val matches = schedule.flatMap { it.matches }
        assertEquals(8, matches.size)
        assertTrue(matches.all { it.seriesId == "7572" })
    }

    @Test
    fun `a played match carries its result and a fixture its state`() {
        val first = schedule.first().matches.single()
        assertEquals("89241", first.id)
        assertEquals("Namibia won by 4 wkts", first.status)
        assertEquals("Complete", first.info)
        assertEquals("ICC Cricket World Cup League Two 2023-27", first.seriesName)
        assertEquals("Upcoming", schedule.last().matches.single().info)
    }

    @Test
    fun `the unslugged page has no schedule but names the slug`() {
        val landing = fixture("cricbuzz_series_matches_noslug.html")
        assertTrue(parseSeriesSchedule(landing).isEmpty())
        assertEquals("icc-cricket-world-cup-league-two-2023-27", seriesSlugOf(landing, "7572"))
    }

    @Test
    fun `the scraper resolves the slug once and fetches the slugged page`() = runBlocking {
        val requested = mutableListOf<String>()
        val scraper = SeriesDetailScraper { url ->
            requested += url
            if (url.endsWith("/7572/matches")) {
                fixture("cricbuzz_series_matches_noslug.html")
            } else {
                fixture("cricbuzz_series_matches.html")
            }
        }
        val result = scraper.fetchSchedule("7572")
        assertTrue(result is AppResult.Success)
        assertEquals(8, (result as AppResult.Success).data.size)
        assertEquals(
            "${CricBuzzUrls.BASE}/cricket-series/7572/icc-cricket-world-cup-league-two-2023-27/matches",
            requested.last(),
        )
    }

    private val stats by lazy { checkNotNull(parseSeriesStats(fixture("cricbuzz_series_stats.html"))) }

    @Test
    fun `the whole stat menu is read, without its category headings`() {
        assertEquals(15, stats.types.size)
        assertEquals("mostRuns", stats.types.first().value)
        assertEquals("Most Runs", stats.types.first().header)
        assertEquals("Batting", stats.types.first().category)
        assertTrue(stats.types.any { it.value == "mostWickets" && it.category == "Bowling" })
        assertEquals("mostRuns", stats.selectedType)
    }

    @Test
    fun `a multi-format tour carries every format on one page`() {
        assertEquals(setOf("ODI", "T20"), stats.tables.map { it.format }.toSet())
        // Cricbuzz selects matchTypeId 3, which its own filter list names T20.
        assertEquals("T20", stats.defaultFormat)
    }

    @Test
    fun `a row drops the player id and lines up with the headers`() {
        val odi = stats.tables.first { it.format == "ODI" }
        assertEquals(listOf("PLAYER", "MATCHES", "INNS", "RUNS", "AVG", "SR", "4s", "6s"), odi.headers)
        val top = odi.rows.first()
        assertEquals("10713", top.playerId)
        assertEquals("Daryl Mitchell", top.playerName)
        assertEquals(odi.headers.size - 1, top.cells.size)
        assertEquals("178", top.cells[2])
    }

    @Test
    fun `a bowling stat has its own columns`() {
        val wickets = checkNotNull(parseSeriesStats(fixture("cricbuzz_series_stats_wickets.html")))
        assertEquals("mostWickets", wickets.selectedType)
        val table = wickets.tables.single()
        assertTrue("WKTS" in table.headers)
        val top = table.rows.first()
        assertEquals("Bernard Scholtz", top.playerName)
        assertEquals(table.headers.size - 1, top.cells.size)
        assertEquals("50", top.cells[table.headers.indexOf("WKTS") - 1])
    }

    @Test
    fun `a page with no stats section parses to null, not a crash`() {
        assertNull(parseSeriesStats(fixture("cricbuzz_series_matches_noslug.html")))
    }

    private val archive by lazy { SeriesScraper().parseArchiveHtml(fixture("cricbuzz_scorecard_archive_2025.html")) }

    @Test
    fun `every archived series of the year is read once`() {
        assertEquals(264, archive.size)
        assertEquals(archive.size, archive.map { it.id }.toSet().size)
    }

    @Test
    fun `the archive section outranks the name-based category`() {
        val smash = archive.first { it.id == "10763" }
        assertEquals("Super Smash 2025-26", smash.name)
        assertEquals(Series.SeriesCategory.LEAGUE, smash.category)
    }

    @Test
    fun `a series in the women section is women's cricket`() {
        val worldCup = archive.first { it.id == "10009" }
        assertEquals("ICC Womens World Cup 2025", worldCup.name)
        assertTrue(worldCup.isWomens)
        assertTrue(worldCup.startDate.toLong() > 0)
    }

    @Test
    fun `the archive is newest first`() {
        val starts = archive.map { it.startDate.toLong() }
        assertEquals(starts.sortedDescending(), starts)
    }
}
