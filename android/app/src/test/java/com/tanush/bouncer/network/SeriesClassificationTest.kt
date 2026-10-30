package com.tanush.bouncer.network

import com.tanush.bouncer.models.Series
import com.tanush.bouncer.ui.screens.filterSeries
import com.tanush.bouncer.ui.screens.seriesLabelOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The old first-match-wins chain tested T20 before Women, so every women's T20 competition came
 * back LEAGUE and the WOMEN arm was all but unreachable. Reordering would only move the error, so
 * the two are now separate axes.
 */
class SeriesClassificationTest {

    @Test
    fun `a womens T20 competition is both a league and womens`() {
        val name = "ICC Women's T20 World Cup"
        assertTrue(isWomensSeries(name))
        assertEquals(Series.SeriesCategory.LEAGUE, seriesCategoryOf(name))
    }

    @Test
    fun `WPL keeps its league classification`() {
        assertTrue(isWomensSeries("Women's Premier League 2024"))
        assertTrue(isWomensSeries("WPL 2024"))
        assertEquals(Series.SeriesCategory.LEAGUE, seriesCategoryOf("WPL 2024"))
    }

    @Test
    fun `the apostrophe is optional`() {
        // Cricbuzz emits both spellings.
        assertTrue(isWomensSeries("Womens Series"))
        assertTrue(isWomensSeries("Women's Series"))
        assertTrue(isWomensSeries("Women Series"))
    }

    @Test
    fun `mens competitions are not womens`() {
        assertFalse(isWomensSeries("Indian Premier League 2024"))
        assertFalse(isWomensSeries("Border-Gavaskar Trophy"))
        assertFalse(isWomensSeries("Ranji Trophy"))
    }

    @Test
    fun `format classification is unchanged for mens competitions`() {
        assertEquals(Series.SeriesCategory.LEAGUE, seriesCategoryOf("IPL 2024"))
        assertEquals(Series.SeriesCategory.LEAGUE, seriesCategoryOf("Big Bash League"))
        assertEquals(Series.SeriesCategory.DOMESTIC, seriesCategoryOf("Ranji Trophy"))
        assertEquals(Series.SeriesCategory.DOMESTIC, seriesCategoryOf("Sheffield Shield"))
        assertEquals(Series.SeriesCategory.INTERNATIONAL, seriesCategoryOf("India tour of Australia"))
    }

    @Test
    fun `a womens Test series stays international`() {
        val name = "Australia Women tour of India, Test"
        assertTrue(isWomensSeries(name))
        assertEquals(Series.SeriesCategory.INTERNATIONAL, seriesCategoryOf(name))
    }

    // --- filtering ----------------------------------------------------------

    private val womensT20 = Series(
        id = "1",
        name = "ICC Women's T20 World Cup",
        category = Series.SeriesCategory.LEAGUE,
        isWomens = true,
    )
    private val mensLeague = Series(id = "2", name = "IPL 2024", category = Series.SeriesCategory.LEAGUE)
    private val domestic = Series(id = "3", name = "Ranji Trophy", category = Series.SeriesCategory.DOMESTIC)

    @Test
    fun `a womens league appears under both filters`() {
        val all = listOf(womensT20, mensLeague, domestic)
        assertEquals(listOf(womensT20), filterSeries(all, "Women"))
        assertEquals(listOf(womensT20, mensLeague), filterSeries(all, "League"))
    }

    @Test
    fun `the Women filter is no longer empty`() {
        // Every series used to classify LEAGUE / DOMESTIC / INTERNATIONAL, so this returned nothing.
        assertTrue(filterSeries(listOf(womensT20), "Women").isNotEmpty())
    }

    @Test
    fun `the label carries both axes`() {
        assertEquals("League · Women", seriesLabelOf(womensT20))
        assertEquals("League", seriesLabelOf(mensLeague))
        assertEquals("Domestic", seriesLabelOf(domestic))
    }
}
