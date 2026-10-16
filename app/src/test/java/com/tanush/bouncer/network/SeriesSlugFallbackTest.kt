package com.tanush.bouncer.network

import com.tanush.bouncer.data.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesSlugFallbackTest {

    @Test
    fun `a landing page without slugged links falls back to a second tab`() = runTest {
        val id = "990001"
        val fetched = mutableListOf<String>()
        val scraper = SeriesDetailScraper { url ->
            fetched += url
            when {
                url.endsWith("/$id/matches") -> "<title>Cricbuzz</title>"
                url.endsWith("/$id/squads") -> """<a href="/cricket-series/$id/some-tour-2026/matches">"""
                else -> ""
            }
        }

        val result = scraper.fetchSchedule(id)

        assertTrue(result is AppResult.Success)
        assertEquals("${CricBuzzUrls.BASE}/cricket-series/$id/some-tour-2026/matches", fetched.last())
        assertEquals("some-tour-2026", SeriesSlugs[id])
    }

    @Test
    fun `a remembered slug skips the landing fetch`() = runTest {
        val id = "990002"
        SeriesSlugs[id] = "known-slug"
        val fetched = mutableListOf<String>()
        SeriesDetailScraper { url -> fetched += url; "" }.fetchSchedule(id)

        assertEquals(listOf("${CricBuzzUrls.BASE}/cricket-series/$id/known-slug/matches"), fetched)
    }
}
