package com.tanush.bouncer.network

import com.tanush.bouncer.analysis.CricketConstants
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

    // ── Task 3: Fallback selectors + extraction correctness ─────────────────

    @Test
    fun `parseBatsmenFromDoc falls back to partial-class selector when primary class absent`() {
        val html = """
            <div class="cb-col-100 cb-min-itm-rw">
                <div class="cb-col-50"><a href="#">R Sharma</a>*</div>
                <div class="cb-col-10">82</div>
                <div class="cb-col-10">53</div>
                <div class="cb-col-10">9</div>
                <div class="cb-col-10">2</div>
                <div class="cb-col-14">154.7</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val batsmen = parseBatsmenFromDoc(doc)
        assertEquals(1, batsmen.size)
        assertEquals("R Sharma", batsmen[0].name)
        assertTrue(batsmen[0].isStriker)
    }

    @Test
    fun `parseBatsmenFromDoc correctly extracts runs balls fours sixes sr`() {
        val html = """
            <div class="cb-col cb-col-100 cb-min-itm-rw">
                <div class="cb-col-50"><a href="#">V Kohli</a></div>
                <div class="cb-col-10">45</div>
                <div class="cb-col-14">38</div>
                <div class="cb-col-10">5</div>
                <div class="cb-col-10">1</div>
                <div class="cb-col-14">118.4</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val batsmen = parseBatsmenFromDoc(doc)
        assertEquals(1, batsmen.size)
        assertEquals("V Kohli", batsmen[0].name)
        assertEquals("45", batsmen[0].runs)
        assertEquals("38", batsmen[0].balls)
        assertEquals("5", batsmen[0].fours)
        assertEquals("1", batsmen[0].sixes)
        assertEquals("118.4", batsmen[0].strikeRate)
    }

    @Test
    fun `parseBatsmenFromDoc skips row when fewer than 4 stats columns`() {
        val html = """
            <div class="cb-col cb-col-100 cb-min-itm-rw">
                <div class="cb-col-50"><a href="#">Header Row</a></div>
                <div class="cb-col-10">R</div>
                <div class="cb-col-10">B</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        assertTrue(parseBatsmenFromDoc(doc).isEmpty())
    }

    @Test
    fun `parseScorecardBatsmen extracts name and dismissal from standard scorecard HTML`() {
        // Dismissal is inside the name div (real Cricbuzz DOM structure)
        val html = """
            <div>
                <div class="grid scorecard-bat-grid">
                    <div>
                        <a href="#">S Tendulkar</a>
                        <div class="text-cbTxtSec">c Ponting b McGrath</div>
                    </div>
                    <div>100</div><div>120</div><div>14</div><div>1</div><div>83.3</div>
                </div>
            </div>
        """.trimIndent()
        val content = Jsoup.parse(html).body()
        val batsmen = parseScorecardBatsmen(content)
        assertEquals(1, batsmen.size)
        assertEquals("S Tendulkar", batsmen[0].name)
        assertEquals("c Ponting b McGrath", batsmen[0].dismissal)
        assertEquals("100", batsmen[0].runs)
    }

    @Test
    fun `parseScorecardBatsmen falls back when text-cbTxtSec absent`() {
        // Dismissal div has a different class — fallback uses first lettered div after the name div
        val html = """
            <div>
                <div class="grid scorecard-bat-grid">
                    <div>
                        <a href="#">S Tendulkar</a>
                        <div class="text-gray">lbw b McGrath</div>
                    </div>
                    <div>100</div><div>120</div><div>14</div><div>1</div><div>83.3</div>
                </div>
            </div>
        """.trimIndent()
        val content = Jsoup.parse(html).body()
        val batsmen = parseScorecardBatsmen(content)
        assertEquals(1, batsmen.size)
        assertTrue(batsmen[0].dismissal.isNotEmpty())
    }

    @Test
    fun `parseRunRatesFromDoc extracts CRR and RRR from status div`() {
        val html = """<div class="cb-text-live">CRR: 8.45 | RRR: 12.3</div>"""
        val (crr, rrr) = parseRunRatesFromDoc(Jsoup.parse(html))
        assertEquals("8.45", crr)
        assertEquals("12.3", rrr)
    }

    @Test
    fun `parseMiniScoreRowsFromDoc extracts team scores from standard HTML`() {
        val html = """
            <div class="cb-min-bat-rw">
                <span class="cb-font-20">248/3</span>
                <span class="cb-text-gray">(42.3 Ov)</span>
            </div>
            <div class="cb-min-bat-rw">
                <span class="cb-font-20">187/8</span>
                <span class="cb-font-12">(35.1 Ov)</span>
            </div>
        """.trimIndent()
        val rows = parseMiniScoreRowsFromDoc(Jsoup.parse(html))
        assertEquals("248/3", rows.team1Score)
        assertEquals("187/8", rows.team2Score)
        assertEquals("42.3", rows.team1Overs)
        assertEquals("35.1", rows.team2Overs)
    }

    // ── Task 5: Commentary selectors + collectKeyedPairs ────────────────────

    @Test
    fun `parseCommentaryLegacyCss extracts lines from p cb-com-ln`() {
        val html = """
            <p class="cb-com-ln">19.3: Excellent delivery, beats the bat outside off stump.</p>
            <p class="cb-com-ln">19.4: Tossed up, driven through cover for FOUR!</p>
        """.trimIndent()
        val lines = parseCommentaryLegacyCss(Jsoup.parse(html))
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("Excellent delivery"))
    }

    @Test
    fun `parseCommentaryLegacyCss returns empty when no commentary elements present`() {
        val doc = Jsoup.parse("<html><body><p>no cricket here</p></body></html>")
        assertTrue(parseCommentaryLegacyCss(doc).isEmpty())
    }

    @Test
    fun `parseCommentaryLegacyCss filters out lines shorter than 5 chars`() {
        val html = """<p class="cb-com-ln">ok</p><p class="cb-com-ln">Valid commentary line here.</p>"""
        val lines = parseCommentaryLegacyCss(Jsoup.parse(html))
        assertEquals(1, lines.size)
    }

    @Test
    fun `collectKeyedPairs returns empty when commText key absent from JSON`() {
        val element = com.google.gson.JsonParser.parseString("""{"overNumber":10, "ballNumber": 3}""")
        val results = mutableListOf<Pair<Long, String>>()
        CricBuzzScraper().collectKeyedPairs(element, "commText", "timestamp", results) { it.asLong }
        assertTrue(results.isEmpty())
    }

    @Test
    fun `collectKeyedPairs extracts commText and timestamp correctly`() {
        val element = com.google.gson.JsonParser.parseString(
            """{"commText": "Ball hit for six!", "timestamp": 1716700000}"""
        )
        val results = mutableListOf<Pair<Long, String>>()
        CricBuzzScraper().collectKeyedPairs(element, "commText", "timestamp", results) { it.asLong }
        assertEquals(1, results.size)
        assertEquals("Ball hit for six!", results[0].second)
        assertEquals(1716700000L, results[0].first)
    }

    // ── Task 4: selectorCrumb + empty-content robustness ────────────────────

    @Test
    fun `parseScorecardBatsmen handles completely empty content without throwing`() {
        val content = Jsoup.parse("<div></div>").body()
        assertTrue(parseScorecardBatsmen(content).isEmpty())
    }

    @Test
    fun `parseScorecardBowlers handles completely empty content without throwing`() {
        val content = Jsoup.parse("<div></div>").body()
        assertTrue(parseScorecardBowlers(content).isEmpty())
    }

    /** Bowler row shaped like Cricbuzz's grid: name link, O, M, R, W, and a trailing empty cell. */
    private fun bowlerGrid(overs: String, maidens: String, runs: String, wickets: String): String =
        """
        <div class="grid scorecard-bowl-grid">
            <div><a href="/profiles/1/x">J Bumrah</a></div>
            <div>$overs</div><div>$maidens</div><div>$runs</div><div>$wickets</div><div></div>
        </div>
        """.trimIndent()

    @Test
    fun `parseScorecardBowlers computes economy instead of reading the trailing cell`() {
        // The trailing cell is empty, so `stats.lastOrNull()` yielded "" — which renders as a blank
        // column, or as a flawless 0.00 economy once `toFloatOrNull() ?: 0f` gets hold of it.
        val content = Jsoup.parse(bowlerGrid("4", "0", "36", "2")).body()
        val bowlers = parseScorecardBowlers(content)
        assertEquals(1, bowlers.size)
        assertEquals("9.00", bowlers[0].economy)
    }

    @Test
    fun `bowler economy treats overs as ball notation, not a decimal`() {
        // "3.4" is 3 overs and 4 balls = 22 balls. 30 runs off 22 balls is 8.18, not 30/3.4 = 8.82.
        val content = Jsoup.parse(bowlerGrid("3.4", "0", "30", "1")).body()
        assertEquals("8.18", parseScorecardBowlers(content)[0].economy)
    }

    @Test
    fun `a bowler who has not completed a ball gets no economy rather than a divide by zero`() {
        val content = Jsoup.parse(bowlerGrid("0", "0", "0", "0")).body()
        assertEquals("", parseScorecardBowlers(content)[0].economy)
    }

    @Test
    fun `unparseable overs fall back to the grid cell`() {
        val row = """
            <div class="grid scorecard-bowl-grid">
                <div><a href="/profiles/1/x">J Bumrah</a></div>
                <div>-</div><div>0</div><div>36</div><div>2</div><div>9.00</div>
            </div>
        """.trimIndent()
        assertEquals("9.00", parseScorecardBowlers(Jsoup.parse(row).body())[0].economy)
    }

    @Test
    fun `parseFallOfWickets handles missing FOW section without throwing`() {
        // The companion case — a section that is present and correctly parsed — is in
        // CricBuzzFowPartnershipTest, against captured markup. The text-blob test that used to sit
        // here asserted a shape Cricbuzz stopped serving with the App Router rewrite, so it went on
        // passing while the parser returned nothing on every real page.
        val content = Jsoup.parse("<div>No FOW here</div>").body()
        val fows = parseFallOfWickets(content)
        assertNotNull(fows)
        assertTrue(fows.isEmpty())
    }

    // ── Task 6: Malformed data + edge case robustness ────────────────────────

    @Test
    fun `parseBatsmenFromDoc handles row with empty name link gracefully`() {
        val html = """
            <div class="cb-col cb-col-100 cb-min-itm-rw">
                <div class="cb-col-50"><a href="#"></a></div>
                <div class="cb-col-10">0</div><div class="cb-col-10">0</div>
                <div class="cb-col-10">0</div><div class="cb-col-10">0</div>
                <div class="cb-col-14">0.0</div>
            </div>
        """.trimIndent()
        val batsmen = parseBatsmenFromDoc(Jsoup.parse(html))
        assertEquals(1, batsmen.size)
        assertEquals("", batsmen[0].name)
    }

    @Test
    fun `parseFallOfWickets handles malformed FOW text without throwing`() {
        val html = """
            <div>Fall of Wickets</div>
            <div>CORRUPTED DATA (no proper format here)</div>
        """.trimIndent()
        val fows = parseFallOfWickets(Jsoup.parse(html).body())
        assertNotNull(fows)
    }

    @Test
    fun `parseScorecardBatsmen skips header row containing word Batter`() {
        val html = """
            <div>
                <div class="grid scorecard-bat-grid">
                    <div><a href="#">Batter</a></div>
                    <div class="text-cbTxtSec"></div>
                    <div>R</div><div>B</div><div>4s</div><div>6s</div><div>SR</div>
                </div>
                <div class="grid scorecard-bat-grid">
                    <div><a href="#">S Tendulkar</a><div class="text-cbTxtSec">c Ponting b McGrath</div></div>
                    <div>100</div><div>120</div><div>14</div><div>1</div><div>83.3</div>
                </div>
            </div>
        """.trimIndent()
        val batsmen = parseScorecardBatsmen(Jsoup.parse(html).body())
        assertEquals(1, batsmen.size)
        assertEquals("S Tendulkar", batsmen[0].name)
    }

    @Test
    fun `normalizeNextFlightPayload handles empty string`() {
        assertNull(normalizeNextFlightPayload(""))
    }

    @Test
    fun `normalizeNextFlightPayload handles whitespace-only string`() {
        assertNull(normalizeNextFlightPayload("   "))
    }

    @Test
    fun `unwrapNextFlightPayload handles single-element array`() {
        assertNull(unwrapNextFlightPayload("""[1]"""))
    }

    @Test
    fun `unwrapNextFlightPayload handles non-string second element`() {
        assertNull(unwrapNextFlightPayload("""[1, 42]"""))
    }

    // ── Cricbuzz format codes ───────────────────────────────────────────────

    @Test
    fun `the hundred's format code resolves to a 100-ball budget`() {
        // Cricbuzz reports `matchFormat: "HUN"` on The Hundred's live pages (verified against
        // matches 144783 and 145154, 2026-07-30). It used to map to "", and a Hundred title names
        // no format either, so the format resolved to UNKNOWN — no ball budget, and therefore no
        // win probability for the entire match.
        val matchType = CricBuzzScraper().mapMatchFormat("HUN")
        assertEquals("The Hundred", matchType)
        val format = CricketConstants.formatFromMatchType(
            matchType,
            "Southern Brave vs Birmingham Phoenix, 13th Match"
        )
        assertEquals(CricketConstants.MatchFormat.HUNDRED, format)
        assertEquals(100, CricketConstants.inningsBalls(format))
    }

    @Test
    fun `known format codes keep resolving and unknown ones stay blank`() {
        val scraper = CricBuzzScraper()
        assertEquals("Test", scraper.mapMatchFormat("TEST"))
        assertEquals("ODI", scraper.mapMatchFormat("odi"))
        assertEquals("T20", scraper.mapMatchFormat("T20I"))
        assertEquals("T10", scraper.mapMatchFormat("T10"))
        assertEquals("List A", scraper.mapMatchFormat("LIST_A"))
        // Blank rather than a guess: an unrecognised code must not be scored on someone else's
        // ball budget.
        assertEquals("", scraper.mapMatchFormat("SOMETHING_NEW"))
        assertEquals(
            CricketConstants.MatchFormat.UNKNOWN,
            CricketConstants.formatFromMatchType(scraper.mapMatchFormat("SOMETHING_NEW"), "")
        )
    }
}
