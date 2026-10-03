package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.OverPoint
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server's per-over probability stamps, read into the series the swing chart draws. */
class ProbabilityHistoryTest {

    private fun team(id: String, name: String) =
        CricketMatch.Team(name = name, score = "", status = "", isBatting = false, logoUrl = null, teamId = id)
    private val teams = listOf(team("2", "India"), team("10", "West Indies"))

    private val overs = listOf(
        OverPoint(innings = 1, over = 1, runs = 6, score = 6, wickets = 0, p = 0.52),
        OverPoint(innings = 1, over = 2, runs = 3, score = 9, wickets = 1, p = 0.47),
        OverPoint(innings = 1, over = 3, runs = 17, score = 26, wickets = 1, p = 0.55),
        // Over 4 was bowled while no container held the history.
        OverPoint(innings = 1, over = 5, runs = 4, score = 40, wickets = 3, p = 0.40),
        OverPoint(innings = 2, over = 1, runs = 8, score = 8, wickets = 0),
        OverPoint(innings = 2, over = 2, runs = 2, score = 10, wickets = 2, p = 0.7),
    )

    @Test
    fun `stamped overs become points, in order, with the second innings carried on`() {
        val points = probabilityHistoryFrom(Scorecard(overs = overs, probabilityTeamId = "2"), teams)
        assertEquals(listOf(1f, 2f, 3f, 5f, 7f), points.map { it.over })
        assertEquals(0.52f, points.first().team1Probability, 1e-6f)
        assertEquals(0.48f, points.first().team2Probability, 1e-6f)
    }

    @Test
    fun `an over names its wicket or its runs, and nothing it cannot know`() {
        val events = probabilityHistoryFrom(Scorecard(overs = overs, probabilityTeamId = "2"), teams).map { it.keyEvent }
        assertEquals(listOf(null, "Wicket", "17 runs off the over", null, "2 wickets"), events)
        // Over 5 follows a gap: two wickets fell somewhere in overs 4 and 5, and it claims neither.
    }

    @Test
    fun `the probability follows the screen's team order`() {
        val points = probabilityHistoryFrom(Scorecard(overs = overs, probabilityTeamId = "10"), teams)
        assertEquals(0.48f, points.first().team1Probability, 1e-6f)
    }

    @Test
    fun `no team id means no series, not a guess at the orientation`() {
        assertTrue(probabilityHistoryFrom(Scorecard(overs = overs), teams).isEmpty())
        assertTrue(probabilityHistoryFrom(null, teams).isEmpty())
        assertTrue(probabilityHistoryFrom(Scorecard(probabilityTeamId = "2"), teams).isEmpty())
    }

    @Test
    fun `the chart ends on the session's latest reading, and falls back to the session alone`() {
        val server = probabilityHistoryFrom(Scorecard(overs = overs, probabilityTeamId = "2"), teams)
        val session = listOf(WinProbabilityPoint(over = 2.3f, team1Probability = 0.74f, team2Probability = 0.26f))
        val merged = mergedProbabilityHistory(server, session)
        assertEquals(server.size + 1, merged.size)
        assertEquals(0.74f, merged.last().team1Probability, 1e-6f)
        assertTrue(merged.last().over > server.last().over)
        assertNull(merged.last().keyEvent)

        assertEquals(session, mergedProbabilityHistory(server.take(1), session))
        assertEquals(server, mergedProbabilityHistory(server, emptyList()))
    }
}
