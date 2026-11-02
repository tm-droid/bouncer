package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.network.parseSeriesSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The schedule opens on the next match. The fixture is five played days (Feb 2024) then three
 * fixtures (15, 17, 19 Oct 2026), one match a day, so each day is two list items.
 */
class SeriesScheduleAnchorTest {

    private val days by lazy {
        val html = checkNotNull(javaClass.classLoader?.getResourceAsStream("cricbuzz_series_matches.html"))
            .bufferedReader().use { it.readText() }
        parseSeriesSchedule(html)
    }

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `mid-series it opens on the heading of the first fixture, results above it`() {
        assertEquals(10, scheduleAnchorIndex(days, at("2026-09-22T00:00:00Z")))
    }

    @Test
    fun `a fixture that started over a day ago is not taken as next`() {
        // 15 Oct's match would be a day and more old; 17 Oct's is the next one.
        assertEquals(12, scheduleAnchorIndex(days, at("2026-10-16T12:00:00Z")))
    }

    @Test
    fun `with nothing left to play it opens at the start`() {
        assertEquals(0, scheduleAnchorIndex(days, at("2027-01-01T00:00:00Z")))
    }

    @Test
    fun `an empty schedule opens at the start`() {
        assertEquals(0, scheduleAnchorIndex(emptyList(), at("2026-09-22T00:00:00Z")))
    }
}
