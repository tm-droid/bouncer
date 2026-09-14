package com.tanush.bouncer.network

import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

/**
 * Pins the Upcoming tab to the schedule page.
 *
 * `live-scores/upcoming-matches` stopped carrying fixtures: captured 2026-09-14 its flight stream
 * held only the live ticker, every entry Complete or at a break, so the Upcoming filter rejected all
 * ~90 and the tab was empty. The fixture is a trimmed capture of `cricket-schedule/upcoming-series/all`
 * — one ticker chunk plus the chunk carrying `matchScheduleList`, where `matchInfo` is an array and
 * there is no status at all.
 */
class CricBuzzScheduleFixtureTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private val scraper = CricBuzzScraper()

    // 2026-09-14T15:33:20Z: after fixture 171671 started, before every other fixture on the page.
    private val now = 1_789_400_000_000L

    private val parsed by lazy { scraper.parseMatchListHtml(fixture("cricbuzz_schedule_upcoming.html"), now) }
    private val upcoming by lazy { scraper.matchesForState(parsed, "Upcoming") }

    @Test
    fun `every fixture that has not started reaches the Upcoming tab`() {
        assertEquals(52, upcoming.size)
    }

    @Test
    fun `a fixture whose start has passed is left to the Live list`() {
        assertFalse(upcoming.any { it.id == "171671" })
    }

    @Test
    fun `the ticker's finished matches do not leak into Upcoming`() {
        assertFalse(upcoming.any { it.id == "171697" })
        assertTrue(scraper.matchesForState(parsed, "Recent").any { it.id == "171697" })
    }

    @Test
    fun `a fixture carries teams, crest ids, venue, format and series`() {
        val match = upcoming.first { it.id == "152731" }
        assertEquals("Zimbabwe vs Australia", match.matchTitle)
        assertEquals("12", match.teams[0].teamId)
        assertEquals("4", match.teams[1].teamId)
        assertEquals("https://static.cricbuzz.com/a/img/v1/720x540/i1/c776198/i.jpg", match.teams[0].logoUrl)
        assertEquals("Harare Sports Club, Harare", match.venue)
        assertEquals("ODI", match.matchType)
        assertEquals("1st ODI", match.matchDesc)
        assertEquals("Australia tour of Zimbabwe 2026", match.seriesName)
        assertEquals("11997", match.seriesId)
        assertEquals(1_789_457_400_000L, match.startDateMs)
    }

    @Test
    fun `every fixture reads as upcoming on the model the UI branches on`() {
        val notUpcoming = upcoming.filterNot { it.isUpcoming }
        assertEquals(emptyList<String>(), notUpcoming.map { "${it.id}: ${it.status}" })
    }

    @Test
    fun `a women's series is marked as such`() {
        assertEquals("Women", upcoming.first { it.id == "159996" }.gender)
    }

    @Test
    fun `the status line classifies as pre-match`() {
        val start = MatchStartTime.of(1_789_457_400_000L, ZoneOffset.UTC, Locale.US)
        assertEquals("Match starts at Tue 15 Sep, 07:30", start.statusLine)
        assertEquals("Tue 15 Sep 2026", start.date)
        assertEquals("07:30", start.time)
        assertEquals(StatusSignal.PRE_MATCH, MatchStatusClassifier.classify(start.statusLine))
    }
}
