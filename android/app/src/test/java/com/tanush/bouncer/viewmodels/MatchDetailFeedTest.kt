package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.models.WinProbabilityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The two feed invariants the match-detail screen depends on and nothing else enforced.
 */
class MatchDetailFeedTest {

    private fun line(text: String, sortKey: Double?) =
        CommentaryEntry(text, TimelineEventType.OTHER, ball = null, sortKey = sortKey)

    // ── Realtime ordering ─────────────────────────────────────────────────────

    @Test
    fun `a late line lands in its place, not at the top`() {
        // Realtime delivery is not ordered. Prepending unconditionally put a late ball above the
        // deliveries that actually followed it, and the over grouping then led with the wrong over.
        val feed = listOf(line("30.2", 300.0), line("30.1", 100.0))
        val merged = insertBySortKey(feed, line("30.1 late", 200.0))
        assertEquals(listOf("30.2", "30.1 late", "30.1"), merged.map { it.text })
    }

    @Test
    fun `the newest line goes to the top`() {
        val feed = listOf(line("30.2", 300.0), line("30.1", 100.0))
        assertEquals("30.3", insertBySortKey(feed, line("30.3", 400.0)).first().text)
    }

    @Test
    fun `the oldest line goes to the bottom`() {
        val feed = listOf(line("30.2", 300.0), line("30.1", 100.0))
        assertEquals("29.6", insertBySortKey(feed, line("29.6", 50.0)).last().text)
    }

    @Test
    fun `a line with no sort key sorts as newest`() {
        // The on-device scrape fallback has no key, and a locally scraped line IS the newest thing
        // the device knows about.
        val feed = listOf(line("30.2", 300.0))
        assertEquals("scraped", insertBySortKey(feed, line("scraped", null)).first().text)
    }

    @Test
    fun `inserting into an empty feed works`() {
        assertEquals(1, insertBySortKey(emptyList(), line("30.1", 100.0)).size)
    }

    // ── Win-probability swing series ──────────────────────────────────────────

    @Test
    fun `an unchanged probability does not add a point`() {
        // match-sync rewrites the detail row every minute whether or not a ball was bowled;
        // recording every rewrite would fill the series with duplicates and flatten the chart.
        val history = listOf(WinProbabilityPoint(over = 10f, team1Probability = 0.6f, team2Probability = 0.4f))
        assertSame(history, appendProbabilityPoint(history, over = 10f, team1Percent = 60.0))
    }

    @Test
    fun `a moved probability adds a point`() {
        val history = listOf(WinProbabilityPoint(over = 10f, team1Probability = 0.6f, team2Probability = 0.4f))
        val next = appendProbabilityPoint(history, over = 10.1f, team1Percent = 72.0)
        assertEquals(2, next.size)
        assertEquals(0.72f, next.last().team1Probability, 0.0001f)
        assertEquals(0.28f, next.last().team2Probability, 0.0001f)
    }

    @Test
    fun `a null probability is not a point`() {
        val history = listOf(WinProbabilityPoint(over = 10f, team1Probability = 0.6f, team2Probability = 0.4f))
        assertSame(history, appendProbabilityPoint(history, over = 11f, team1Percent = null))
    }

    @Test
    fun `the series is bounded`() {
        var history = emptyList<WinProbabilityPoint>()
        repeat(400) { i -> history = appendProbabilityPoint(history, over = i.toFloat(), team1Percent = i % 100 + 1.0) }
        assertEquals(240, history.size)
    }

    @Test
    fun `the batting over is the one still advancing`() {
        assertEquals(66.5f, battingOverOf("12.3", "66.5"), 0.001f)
        assertEquals(12.3f, battingOverOf("12.3", ""), 0.001f)
        assertEquals(0f, battingOverOf("", ""), 0.001f)
    }
}
