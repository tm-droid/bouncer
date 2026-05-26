package com.tanush.bouncer.network

import com.tanush.bouncer.data.AppException
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CricBuzzScraperTest {

    // ── Task 1: HTTP status classification ──────────────────────────────────

    @Test
    fun `classifyHttpException maps 429 to ApiException`() {
        val ex = HttpStatusException("Too Many Requests", 429, "https://cricbuzz.com")
        val result = classifyHttpException(ex)
        assertTrue(result is AppException.ApiException)
        assertEquals(429, (result as AppException.ApiException).statusCode)
    }

    @Test
    fun `classifyHttpException maps 403 to ApiException`() {
        val ex = HttpStatusException("Forbidden", 403, "https://cricbuzz.com")
        val result = classifyHttpException(ex)
        assertTrue(result is AppException.ApiException)
        assertEquals(403, (result as AppException.ApiException).statusCode)
    }

    @Test
    fun `classifyHttpException maps 503 to ApiException`() {
        val ex = HttpStatusException("Service Unavailable", 503, "https://cricbuzz.com")
        val result = classifyHttpException(ex)
        assertTrue(result is AppException.ApiException)
        assertEquals(503, (result as AppException.ApiException).statusCode)
    }

    @Test
    fun `normalizeNextFlightPayload unwraps typed json object payload`() {
        assertEquals("""{"id":2353}""", normalizeNextFlightPayload("""3:I{"id":2353}"""))
    }

    @Test
    fun `normalizeNextFlightPayload ignores non json flight directives`() {
        assertNull(normalizeNextFlightPayload("""1:HL["/_next/static/css/app.css",{"as":"style"}]"""))
    }

    @Test
    fun `unwrapNextFlightPayload extracts nested json array payload`() {
        val scriptContent = """[1,"2:[[[\"$\",\"link\",\"0\",{\"rel\":\"stylesheet\"}]]]"]"""

        assertEquals("""[[["$","link","0",{"rel":"stylesheet"}]]]""", unwrapNextFlightPayload(scriptContent))
    }

    @Test
    fun `unwrapNextFlightPayload ignores non json inner payloads`() {
        val scriptContent = """[1,"1:HL[\"/_next/static/css/app.css\",{\"as\":\"style\"}]"]"""

        assertNull(unwrapNextFlightPayload(scriptContent))
    }

    // ── Task 2: Extracted parser functions ──────────────────────────────────

    @Test
    fun `parseBatsmenFromDoc returns empty list when no batsman rows present`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        assertTrue(parseBatsmenFromDoc(doc).isEmpty())
    }

    @Test
    fun `parseBowlerFromDoc returns null when no bowler header present`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        assertNull(parseBowlerFromDoc(doc))
    }

    @Test
    fun `parseMiniScoreRowsFromDoc returns null scores when rows absent`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val rows = parseMiniScoreRowsFromDoc(doc)
        assertNull(rows.team1Score)
        assertNull(rows.team2Score)
    }

    @Test
    fun `parseRunRatesFromDoc returns null pair when status element absent`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val (crr, rrr) = parseRunRatesFromDoc(doc)
        assertNull(crr)
        assertNull(rrr)
    }
}
