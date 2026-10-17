package com.tanush.bouncer.network

import com.tanush.bouncer.models.Series
import com.tanush.bouncer.ui.screens.seriesDatesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Pins the Series tab to the page that actually lists series.
 *
 * `SeriesScraper` fetched `/cricket-schedule/series`, which on the App Router carries only the live
 * ticker, and read it with a `__NEXT_DATA__` parse and a link scrape — both found nothing, so the
 * Series screen always said "No series found". The list is on `/cricket-schedule/series/all` as a
 * `seriesScheduleData` flight payload; the fixture is that chunk, captured 2026-09-14.
 */
class SeriesScheduleParseTest {

    private val series by lazy {
        val html = checkNotNull(javaClass.classLoader?.getResourceAsStream("cricbuzz_series_schedule_all.html"))
            .bufferedReader().use { it.readText() }
        SeriesScraper().parseSeriesListHtml(html)
    }

    @Test
    fun `every series on the page is parsed once`() {
        assertEquals(90, series.size)
        assertEquals(series.size, series.map { it.id }.toSet().size)
    }

    @Test
    fun `a series carries its full name and dates`() {
        val cpl = series.first { it.id == "12123" }
        assertEquals("Caribbean Premier League 2026", cpl.name)
        assertTrue(cpl.startDate.isNotBlank() && cpl.endDate.isNotBlank())
        assertEquals(Series.SeriesCategory.LEAGUE, cpl.category)
    }

    @Test
    fun `women's series are flagged for the Women filter`() {
        assertTrue(series.first { it.id == "12843" }.isWomens)
    }

    @Test
    fun `the card shows the series dates, not the epoch strings the page sends`() {
        val cpl = series.first { it.id == "12123" }
        assertEquals("7 Aug – 20 Sep 2026", seriesDatesOf(cpl, ZoneOffset.UTC))
        assertEquals("", seriesDatesOf(Series(id = "x", name = "x"), ZoneOffset.UTC))
        assertEquals("TBC", seriesDatesOf(Series(id = "x", name = "x", startDate = "TBC"), ZoneOffset.UTC))
    }

    @Test
    fun `a page with no schedule payload yields nothing rather than throwing`() {
        assertEquals(emptyList<Series>(), SeriesScraper().parseSeriesListHtml("<html></html>"))
    }
}
