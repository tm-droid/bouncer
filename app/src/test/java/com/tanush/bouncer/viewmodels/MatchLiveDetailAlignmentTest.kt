package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server orients its team1/team2 to the first-innings batting side; the screen is in Cricbuzz's
 * listing order. Where they disagree, every score and the probability bar were shown against the
 * wrong team, and nothing on screen said so.
 */
class MatchLiveDetailAlignmentTest {

    private fun team(name: String) = CricketMatch.Team(name, "", "", false, null, "")

    private val teams = listOf(team("England"), team("India"))

    @Test
    fun `a payload already in our order is returned untouched`() {
        val payload = MatchLiveDetailPayload(
            team1Score = "266 & 83/2", team1Name = "England",
            team2Score = "214", team2Name = "India",
            winProbabilityTeam1 = 0.7,
        )
        assertSame(payload, payload.alignedTo(teams))
    }

    @Test
    fun `a payload oriented to the other side is swapped, probability included`() {
        val aligned = MatchLiveDetailPayload(
            team1Score = "266 & 83/2", team1Overs = "25.1", team1Name = "India",
            team2Score = "214", team2Overs = "71.4", team2Name = "England",
            winProbabilityTeam1 = 0.7,
        ).alignedTo(teams)

        assertEquals("214", aligned.team1Score)
        assertEquals("71.4", aligned.team1Overs)
        assertEquals("England", aligned.team1Name)
        assertEquals("266 & 83/2", aligned.team2Score)
        assertEquals("India", aligned.team2Name)
        assertEquals(0.3, aligned.winProbabilityTeam1!!, 1e-9)
    }

    @Test
    fun `a swap takes the draw out of the other side's share`() {
        val aligned = MatchLiveDetailPayload(
            team1Name = "India", team2Name = "England",
            winProbabilityTeam1 = 0.5, winProbabilityDraw = 0.2,
        ).alignedTo(teams)
        // The bar computes the second segment as 1 - draw - team1, so the swap must match it.
        assertEquals(0.3, aligned.winProbabilityTeam1!!, 1e-9)
        assertEquals(0.2, aligned.winProbabilityDraw!!, 1e-9)
    }

    @Test
    fun `an unknown or missing name leaves the payload alone`() {
        val unknown = MatchLiveDetailPayload(team1Name = "Australia", team1Score = "100/2")
        assertSame(unknown, unknown.alignedTo(teams))
        val unnamed = MatchLiveDetailPayload(team1Score = "100/2")
        assertSame(unnamed, unnamed.alignedTo(teams))
    }

    @Test
    fun `name matching ignores punctuation and case`() {
        assertTrue(serverTeam1IsSecondTeam("india", teams))
        assertFalse(serverTeam1IsSecondTeam("England", teams))
    }

    @Test
    fun `the aggregate file is swapped when its team1 is our second team`() {
        // Batting side is our teams[0], and the file says the batting side is NOT its team1 —
        // so the file's team1 is our teams[1].
        val probability = WinProbabilityStore.Probability(
            team1Percent = 70.0,
            drawPercent = null,
            battingTeamName = "England",
            battingTeamIsTeam1 = false,
        )
        assertEquals(30.0, probability.alignedTo(teams).team1Percent, 1e-9)
    }

    @Test
    fun `the aggregate file is left alone when the two orders agree`() {
        val probability = WinProbabilityStore.Probability(
            team1Percent = 70.0,
            drawPercent = 10.0,
            battingTeamName = "England",
            battingTeamIsTeam1 = true,
        )
        assertSame(probability, probability.alignedTo(teams))
    }

    @Test
    fun `an unknown orientation must not move the bar`() {
        val probability = WinProbabilityStore.Probability(team1Percent = 70.0, drawPercent = null)
        assertSame(probability, probability.alignedTo(teams))
    }
}
