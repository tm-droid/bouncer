package com.tanush.bouncer.network

import com.google.gson.JsonParser
import com.tanush.bouncer.utils.BallMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ball-number extraction from Cricbuzz commentary payloads.
 *
 * The two HTML fixtures are real `self.__next_f.push(...)` statements captured from live match
 * pages on 2026-07-30 — `overball` from a 50-over List A match (`ballMetric` = `48.6`), `hundred`
 * from The Hundred (`ballMetric` = a flat `72`). Everything here failed before the fix: the parser
 * kept only `commText`/`timestamp`, so no commentary line ever carried a ball number and the LLM
 * substituted the `18.5` from its own prompt.
 */
class CricBuzzCommentaryMetricTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private fun itemsOf(name: String) =
        CricBuzzScraper().parseCommentaryItemsFromNextJs(fixture(name))

    // ── over-based formats ──────────────────────────────────────────────────

    @Test
    fun `over-based fixture yields OverBall metrics`() {
        val items = itemsOf("cricbuzz_commentary_overball.html")
        assertTrue("expected commentary items", items.isNotEmpty())

        val metrics = items.mapNotNull { it.ballMetric }
        assertTrue("expected ball metrics", metrics.isNotEmpty())
        assertTrue(
            "over-based payload must not produce BallCount, got $metrics",
            metrics.all { it is BallMetric.OverBall }
        )
    }

    @Test
    fun `over-based lines are prefixed with a parseable ball number`() {
        val withMetric = itemsOf("cricbuzz_commentary_overball.html").filter { it.ballMetric != null }
        for (item in withMetric) {
            val prefixed = item.prefixedText()
            val expected = item.ballMetric!!.label
            assertTrue(
                "line should start with '$expected: ' but was '${prefixed.take(30)}'",
                prefixed.startsWith("$expected: ")
            )
            // The whole point: downstream reads the ball number off the head of the line.
            assertEquals(expected, com.tanush.bouncer.utils.ballNumberOf(prefixed))
        }
    }

    @Test
    fun `over-based metrics span more than one over`() {
        val overs = itemsOf("cricbuzz_commentary_overball.html")
            .mapNotNull { it.ballMetric as? BallMetric.OverBall }
            .map { it.over }
            .toSet()
        // A single stuck value is exactly the production defect this guards against.
        assertTrue("expected multiple overs, got $overs", overs.size > 1)
    }

    // ── The Hundred ─────────────────────────────────────────────────────────

    @Test
    fun `hundred fixture yields BallCount metrics, never OverBall`() {
        val metrics = itemsOf("cricbuzz_commentary_hundred.html").mapNotNull { it.ballMetric }
        assertTrue("expected ball metrics", metrics.isNotEmpty())
        assertTrue(
            "The Hundred has no overs; got $metrics",
            metrics.all { it is BallMetric.BallCount }
        )
    }

    @Test
    fun `hundred lines are labelled as a ball count, not an over`() {
        val item = itemsOf("cricbuzz_commentary_hundred.html").first { it.ballMetric != null }
        val prefixed = item.prefixedText()
        assertTrue("expected 'Ball N: ' prefix, got '${prefixed.take(30)}'", prefixed.startsWith("Ball "))
        // Must NOT be mistaken for over.ball notation — that would invent an over.
        assertNull(com.tanush.bouncer.utils.ballNumberOf(prefixed))
    }

    // ── non-deliveries and dedup ────────────────────────────────────────────

    @Test
    fun `session notes carry no ball metric`() {
        val items = itemsOf("cricbuzz_commentary_overball.html")
        val eventItems = items.filter { it.ballMetric == null }
        assertTrue("fixture should contain \$undefined items", eventItems.isNotEmpty())
        // An event line must stay unprefixed rather than borrow the neighbouring delivery's number.
        for (item in eventItems) assertEquals(item.text, item.prefixedText())
    }

    @Test
    fun `a no-ball pair shares a metric but stays two distinct items`() {
        val items = itemsOf("cricbuzz_commentary_hundred.html")
            .mapNotNull { item -> item.ballMetric?.let { it to item } }
        val grouped = items.groupBy({ it.first }, { it.second })
        val shared = grouped.filterValues { it.size > 1 }
        assertTrue("fixture should contain a no-ball pair", shared.isNotEmpty())
        for ((metric, pair) in shared) {
            // Timestamps disambiguate what ballMetric cannot — this is why the cache key uses them.
            assertEquals(
                "items at $metric must have distinct timestamps",
                pair.size,
                pair.map { it.timestamp }.toSet().size
            )
        }
    }

    @Test
    fun `items are deduplicated by timestamp and ordered newest first`() {
        val items = itemsOf("cricbuzz_commentary_overball.html")
        assertEquals(items.size, items.map { it.timestamp }.toSet().size)
        assertEquals(items.map { it.timestamp }.sortedDescending(), items.map { it.timestamp })
    }

    // ── direct collector behaviour ──────────────────────────────────────────

    @Test
    fun `collectCommentaryItems reads ballMetric from a numeric field without precision drift`() {
        val element = JsonParser.parseString(
            """{"commText":"Douthwaite to Rhodes, 1 run","timestamp":1785344368208,"ballMetric":48.6}"""
        )
        val results = mutableListOf<ScrapedCommentaryItem>()
        CricBuzzScraper().collectCommentaryItems(element, results)
        assertEquals(1, results.size)
        assertEquals(BallMetric.OverBall(48, 6), results[0].ballMetric)
        assertEquals("48.6", results[0].ballMetric!!.label)
    }

    @Test
    fun `collectCommentaryItems treats undefined sentinel as no ball number`() {
        val element = JsonParser.parseString(
            """{"commText":"Andy Gorvin is back into the attack","timestamp":1,"ballMetric":"${'$'}undefined"}"""
        )
        val results = mutableListOf<ScrapedCommentaryItem>()
        CricBuzzScraper().collectCommentaryItems(element, results)
        assertEquals(1, results.size)
        assertNull(results[0].ballMetric)
    }

    @Test
    fun `collectCommentaryItems drops RSC back-references`() {
        val element = JsonParser.parseString(
            """[{"commText":"${'$'}2a","timestamp":1},{"commText":"Real commentary line","timestamp":2}]"""
        )
        val results = mutableListOf<ScrapedCommentaryItem>()
        CricBuzzScraper().collectCommentaryItems(element, results)
        assertEquals(1, results.size)
        assertEquals("Real commentary line", results[0].text)
    }

    @Test
    fun `collectCommentaryItems tolerates a missing ballMetric field`() {
        val element = JsonParser.parseString("""{"commText":"Legacy payload line","timestamp":7}""")
        val results = mutableListOf<ScrapedCommentaryItem>()
        CricBuzzScraper().collectCommentaryItems(element, results)
        assertEquals(1, results.size)
        assertNotNull(results[0])
        assertNull(results[0].ballMetric)
    }
}
