package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server orients its team1/team2 to the first-innings batting side; the screen is in Cricbuzz's
 * listing order. Where they disagree, every score and the probability bar were shown against the
 * wrong team, and nothing on screen said so.
 */
class MatchLiveDetailAlignmentTest {

    private fun team(name: String, id: String) =
        CricketMatch.Team(name, "", "", false, null, "", "0.0", id)

    // Central Zone vs East Zone, as Cricbuzz ids and names them — the codes on the innings list
    // ("CZONE", "EZONE") match neither full name, which is why alignment is by id.
    private val teams = listOf(team("Central Zone", "237"), team("East Zone", "235"))

    @Test
    fun `a payload already in our order is returned untouched`() {
        val payload = MatchLiveDetailPayload(
            team1Score = "266 & 158", team1Id = "237",
            team2Score = "266 & 125/5", team2Id = "235",
            winProbabilityTeam1 = 0.7,
        )
        assertSame(payload, payload.alignedTo(teams))
    }

    @Test
    fun `a payload oriented to the other side is swapped, probability included`() {
        val aligned = MatchLiveDetailPayload(
            team1Score = "266 & 125/5", team1Overs = "33", team1Name = "EZONE", team1Id = "235",
            team2Score = "266 & 158", team2Overs = "50.2", team2Name = "CZONE", team2Id = "237",
            winProbabilityTeam1 = 0.7,
        ).alignedTo(teams)

        assertEquals("266 & 158", aligned.team1Score)
        assertEquals("50.2", aligned.team1Overs)
        assertEquals("CZONE", aligned.team1Name)
        assertEquals("237", aligned.team1Id)
        assertEquals("266 & 125/5", aligned.team2Score)
        assertEquals("235", aligned.team2Id)
        assertEquals(0.3, aligned.winProbabilityTeam1!!, 1e-9)
    }

    @Test
    fun `a swap takes the draw out of the other side's share`() {
        val aligned = MatchLiveDetailPayload(
            team1Id = "235", team2Id = "237",
            winProbabilityTeam1 = 0.5, winProbabilityDraw = 0.2,
        ).alignedTo(teams)
        // The bar computes its second segment as 1 - draw - team1, so the swap must match it.
        assertEquals(0.3, aligned.winProbabilityTeam1!!, 1e-9)
        assertEquals(0.2, aligned.winProbabilityDraw!!, 1e-9)
    }

    @Test
    fun `an unknown or missing id leaves the payload alone`() {
        val unknown = MatchLiveDetailPayload(team1Id = "999", team1Score = "100/2")
        assertSame(unknown, unknown.alignedTo(teams))
        val unidentified = MatchLiveDetailPayload(team1Score = "100/2")
        assertSame(unidentified, unidentified.alignedTo(teams))
    }

    @Test
    fun `a match cached before team ids existed is left alone`() {
        val idless = listOf(team("Central Zone", ""), team("East Zone", ""))
        val payload = MatchLiveDetailPayload(team1Id = "235", team1Score = "266 & 125/5")
        assertSame(payload, payload.alignedTo(idless))
    }

    @Test
    fun `the id decides which side the server means`() {
        assertTrue(serverTeam1IsSecondTeam("235", teams))
        assertFalse(serverTeam1IsSecondTeam("237", teams))
    }

    @Test
    fun `the aggregate file is swapped when its team1 is our second team`() {
        val probability = WinProbabilityStore.Probability(
            team1Percent = 70.0, drawPercent = null, team1Id = "235",
        )
        assertEquals(30.0, probability.alignedTo(teams).team1Percent, 1e-9)
    }

    @Test
    fun `the aggregate file is left alone when the two orders agree`() {
        val probability = WinProbabilityStore.Probability(
            team1Percent = 70.0, drawPercent = 10.0, team1Id = "237",
        )
        assertSame(probability, probability.alignedTo(teams))
    }

    @Test
    fun `an unknown orientation must not move the bar`() {
        val probability = WinProbabilityStore.Probability(team1Percent = 70.0, drawPercent = null)
        assertSame(probability, probability.alignedTo(teams))
    }

    @Test
    fun `a swap flips who is batting, and an unknown stays unknown`() {
        fun swapped(batting: Boolean?) = MatchLiveDetailPayload(
            team1Id = "235", team2Id = "237",
            situation = LiveSituation(battingTeamIsTeam1 = batting),
        ).alignedTo(teams).situation!!.battingTeamIsTeam1

        assertEquals(false, swapped(true))
        assertEquals(true, swapped(false))
        assertNull(swapped(null))
    }

    @Test
    fun `the situation is left alone when the orders agree`() {
        val payload = MatchLiveDetailPayload(
            team1Id = "237", team2Id = "235",
            situation = LiveSituation(battingTeamIsTeam1 = true),
        )
        assertEquals(true, payload.alignedTo(teams).situation!!.battingTeamIsTeam1)
    }

    @Test
    fun `the score stamp moves only when a score or overs figure changes`() {
        val seen = MatchDetailViewModel.UiState(liveTeam1Score = "100/2", liveTeam1Overs = "12.0")
        assertNull(MatchDetailViewModel.UiState().withScoreStamp(MatchDetailViewModel.UiState(), 1_000L).lastUpdatedMs)
        // The first score seen starts the clock even though nothing changed yet.
        val first = seen.withScoreStamp(seen, 5_000L)
        assertEquals(5_000L, first.lastUpdatedMs)
        // A poll tick that re-delivers the same figures must not look like news.
        assertEquals(5_000L, first.withScoreStamp(first, 5_500L).lastUpdatedMs)
        val moved = first.copy(liveTeam1Overs = "12.1").withScoreStamp(first, 6_000L)
        assertEquals(6_000L, moved.lastUpdatedMs)
        assertEquals(6_000L, moved.copy().withScoreStamp(moved, 9_000L).lastUpdatedMs)
    }
}
