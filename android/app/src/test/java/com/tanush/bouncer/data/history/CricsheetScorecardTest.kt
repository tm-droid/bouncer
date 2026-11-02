package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 2011 World Cup final (`m/433606.json`), trimmed to its cards, overs and six deliveries an
 * innings, against the card Cricbuzz and Cricinfo print for it.
 */
class CricsheetScorecardTest {

    private val match: HistoryMatch = AppJson.decodeFromString(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("history_433606_trimmed.json")) { "missing fixture" }
            .bufferedReader().use { it.readText() },
    )
    private val card = scorecardOf(match)

    @Test
    fun `innings come in batting order with the real totals`() {
        assertEquals(listOf("Sri Lanka", "India"), card.innings.map { it.teamName })
        val (sriLanka, india) = card.innings
        assertEquals("274-6", sriLanka.score)
        assertEquals("50 Ov", sriLanka.overs)
        assertEquals("277-4", india.score)
        assertEquals("48.2 Ov", india.overs)
        assertEquals("277-4 (48.2 Ov)", india.total)
    }

    @Test
    fun `batting rows carry the dismissal or not out, and the strike rate`() {
        val sriLanka = card.innings[0]
        val jayawardene = sriLanka.batsmen.single { it.name == "DPMD Jayawardene" }
        assertEquals("103", jayawardene.runs)
        assertEquals("not out", jayawardene.dismissal)
        assertEquals("c V Sehwag b Z Khan", sriLanka.batsmen.first { it.name == "WU Tharanga" }.dismissal)

        val india = card.innings[1]
        assertEquals("97", india.batsmen.single { it.name == "G Gambhir" }.runs)
        val dhoni = india.batsmen.single { it.name == "MS Dhoni" }
        assertEquals("91", dhoni.runs)
        assertEquals("not out", dhoni.dismissal)
        // 91 off 79 balls.
        assertEquals("115.19", dhoni.strikeRate)
    }

    @Test
    fun `bowling figures are overs-maidens-runs-wickets as on the card`() {
        val zaheer = card.innings[0].bowlers.single { it.name == "Z Khan" }
        assertEquals("10", zaheer.overs)
        assertEquals("3", zaheer.maidens)
        assertEquals("60", zaheer.runs)
        assertEquals("2", zaheer.wickets)
        assertEquals("1", zaheer.wides)
        assertEquals("6.00", zaheer.economy)
        assertEquals("2", card.innings[0].bowlers.single { it.name == "SR Tendulkar" }.overs)
    }

    @Test
    fun `extras and fall of wickets follow the scorecard layout`() {
        val sriLanka = card.innings[0]
        assertEquals("12 (b 1, lb 3, w 6, nb 2)", sriLanka.extras)
        val first = sriLanka.fallOfWickets.first()
        assertEquals("17", first.score)
        assertEquals(1, first.number)
        assertEquals("6.1", first.overs)
        assertEquals("WU Tharanga", first.batsman)
        assertEquals(6, sriLanka.fallOfWickets.size)
    }

    @Test
    fun `over points are running sums, innings numbered from one`() {
        assertEquals(50, card.overs.count { it.innings == 1 })
        assertEquals(49, card.overs.count { it.innings == 2 })
        val last = card.overs.last { it.innings == 1 }
        assertEquals(274, last.score)
        assertEquals(6, last.wickets)
        assertEquals(1, card.overs.first { it.innings == 1 }.over)
        assertEquals(277, card.overs.last { it.innings == 2 }.score)
    }

    @Test
    fun `a super over is not an innings of the card`() {
        val withSuperOver = match.copy(inn = match.inn + match.inn[0].copy(so = 1))
        assertEquals(2, scorecardOf(withSuperOver).innings.size)
    }

    @Test
    fun `the final reads as a result and each side's score as the list writes it`() {
        assertEquals("India won by 6 wickets", resultText(match))
        assertEquals("277/4" to "48.2", sideScoreOf(match, 0))
        assertEquals("274/6" to "50", sideScoreOf(match, 1))
        assertTrue(match.pom.isNotEmpty())
        assertEquals("ICC Cricket World Cup", match.e?.n)
    }
}
