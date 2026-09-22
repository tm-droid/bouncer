package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.models.Series
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Series tab's Current list used to *assign* the scrape over the cached rows, so every cached
 * row vanished the moment the scrape landed; and the cache's archived rows, once split out, must
 * not leak back into Current.
 */
class SeriesListMergeTest {

    private val now = 1_800_000_000_000L

    private fun series(id: String, end: String = "", name: String = "S$id") =
        Series(id = id, name = name, endDate = end)

    @Test
    fun `current keeps only cached rows that have not ended`() {
        val cached = listOf(
            series("1", end = (now + 1).toString()),
            series("2", end = (now - 1).toString()),
            // Archived rows the server walked carry no dates; they belong under Past.
            series("3"),
        )
        assertEquals(listOf("1"), currentSeriesOf(cached, now).map { it.id })
    }

    @Test
    fun `the scrape wins per id and keeps its own order, cached extras follow`() {
        val scraped = listOf(series("b", name = "fresh"), series("a"))
        val cached = listOf(series("b", name = "stale"), series("c"))
        val merged = mergeCurrentSeries(scraped, cached)
        assertEquals(listOf("b", "a", "c"), merged.map { it.id })
        assertEquals("fresh", merged.first().name)
    }

    @Test
    fun `an empty scrape does not empty the list`() {
        val cached = listOf(series("c"))
        assertEquals(cached, mergeCurrentSeries(emptyList(), cached))
    }

    @Test
    fun `past drops series still running and keeps undated ones`() {
        val archived = listOf(
            series("done", end = (now - 1).toString()),
            series("running", end = (now + 1).toString()),
            series("undated"),
        )
        assertEquals(listOf("done", "undated"), pastSeriesOf(archived, now).map { it.id })
    }
}
