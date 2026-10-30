package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryResultTest {

    private fun match(status: String, state: String = "Complete") = CricketMatch(
        id = "1",
        seriesId = "0",
        matchTitle = "India vs England",
        matchType = "ODI",
        status = state,
        teams = listOf("India", "England").map {
            CricketMatch.Team(name = it, score = "", status = "", isBatting = false, logoUrl = null)
        },
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = status,
        info = "",
    )

    private fun bat(name: String, runs: String, balls: String = "10") =
        BatsmanStats(name, "", runs, balls, "0", "0", "0")

    private fun bowl(name: String, wickets: String, runs: String = "20") =
        BowlerStats(name, "4", "0", runs, wickets, "0", "0", "5.0")

    private fun innings(team: String, batsmen: List<BatsmanStats>, bowlers: List<BowlerStats>) =
        Innings(team, "", "", batsmen, bowlers, emptyList(), extras = "", total = "")

    private val card = Scorecard(
        innings = listOf(
            innings("India", listOf(bat("Rohit", "40"), bat("Kohli", "75")), listOf(bowl("Bumrah", "1"))),
            innings("England", listOf(bat("Root", "60")), listOf(bowl("Archer", "3"), bowl("Wood", "2"))),
        ),
    )

    @Test
    fun `hyphen and spacing differences between status and team name still find the winner`() {
        val m = match("KwaZulu - Natal Inland - Tuskers won by 5 wkts").let {
            it.copy(teams = listOf("Eastern Storm", "KwaZulu-Natal Inland - Tuskers").map { n ->
                CricketMatch.Team(name = n, score = "", status = "", isBatting = false, logoUrl = null)
            })
        }
        val r = summaryResultOf(m, card)!!
        assertEquals(ResultKind.Won, r.kind)
        assertEquals("KwaZulu-Natal Inland - Tuskers", r.winner)
        assertEquals("5 wkts", r.margin)
    }

    @Test
    fun `wickets margin`() {
        val r = summaryResultOf(match("India won by 6 wkts"), card)!!
        assertEquals(ResultKind.Won, r.kind)
        assertEquals("India", r.winner)
        assertEquals("6 wkts", r.margin)
    }

    @Test
    fun `runs margin names the second side`() {
        val r = summaryResultOf(match("England won by 23 runs"), card)!!
        assertEquals("England", r.winner)
        assertEquals("23 runs", r.margin)
    }

    @Test
    fun `innings margin`() {
        val r = summaryResultOf(match("India won by an innings and 12 runs"), card)!!
        assertEquals("an innings and 12 runs", r.margin)
    }

    @Test
    fun `DLS suffix is kept`() {
        val r = summaryResultOf(match("India won by 6 wkts (DLS)"), card)!!
        assertEquals("6 wkts (DLS)", r.margin)
    }

    @Test
    fun `best batter and bowler come from the top performers across innings`() {
        val r = summaryResultOf(match("India won by 6 wkts"), card)!!
        assertEquals("Kohli", r.bestBatter?.name)
        assertEquals("Archer", r.bestBowler?.name)
    }

    @Test
    fun `tie has no winner or margin`() {
        val r = summaryResultOf(match("Match tied"), card)!!
        assertEquals(ResultKind.Tied, r.kind)
        assertNull(r.winner)
        assertNull(r.margin)
    }

    @Test
    fun `no result and abandoned are separate kinds`() {
        assertEquals(ResultKind.NoResult, summaryResultOf(match("No result"), card)!!.kind)
        assertEquals(ResultKind.Abandoned, summaryResultOf(match("Match abandoned due to rain"), card)!!.kind)
    }

    @Test
    fun `drawn test`() {
        assertEquals(ResultKind.Drawn, summaryResultOf(match("Match drawn"), card)!!.kind)
    }

    @Test
    fun `live match is null`() {
        assertNull(summaryResultOf(match("India need 40 runs", state = "Live"), card))
    }

    @Test
    fun `completed with an unreadable status is null`() {
        assertNull(summaryResultOf(match("Stumps"), card))
    }

    @Test
    fun `missing scorecard leaves performers null`() {
        val r = summaryResultOf(match("India won by 6 wkts"), null)!!
        assertEquals("6 wkts", r.margin)
        assertNull(r.bestBatter)
        assertNull(r.bestBowler)
        val empty = summaryResultOf(match("India won by 6 wkts"), Scorecard())!!
        assertNull(empty.bestBowler)
    }
}
