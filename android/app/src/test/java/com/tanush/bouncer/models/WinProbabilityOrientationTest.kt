package com.tanush.bouncer.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Win probability is computed server-side and stored oriented to `teams[0]`. These cover the two
 * things the device still does with it: re-orienting to the batting side, and reading a finished
 * match's result.
 */
class WinProbabilityOrientationTest {

    private fun team(name: String, batting: Boolean = false, acronym: String = "") =
        CricketMatch.Team(
            name = name,
            score = "",
            status = "",
            isBatting = batting,
            logoUrl = null,
            acronym = acronym,
        )

    private fun match(
        team1: CricketMatch.Team,
        team2: CricketMatch.Team,
        probability: Double? = null,
        status: String = "Live",
        currentStatus: String = "",
    ) = CricketMatch(
        id = "1",
        seriesId = "s",
        matchTitle = "${team1.name} vs ${team2.name}",
        matchType = "T20",
        status = status,
        teams = listOf(team1, team2),
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = currentStatus,
        info = "",
        winProbability = probability,
    )

    // --- batting-side orientation ------------------------------------------

    @Test
    fun `batting probability is the stored value when team 1 bats`() {
        val m = match(team("India", batting = true), team("Australia"), probability = 70.0)
        assertEquals(70.0, m.battingWinProbability!!, 1e-9)
    }

    @Test
    fun `batting probability is inverted when team 2 bats`() {
        // The defect this guards: the number was reported as team 1's while being computed from
        // the batting side, so it was backwards whenever teams[1] was at the crease.
        val m = match(team("India"), team("Australia", batting = true), probability = 70.0)
        assertEquals(30.0, m.battingWinProbability!!, 1e-9)
    }

    @Test
    fun `batting probability is null when nobody is batting`() {
        assertNull(match(team("India"), team("Australia"), probability = 70.0).battingWinProbability)
    }

    @Test
    fun `batting probability is null when there is no probability`() {
        assertNull(match(team("India", batting = true), team("Australia")).battingWinProbability)
    }

    // --- completed matches --------------------------------------------------

    @Test
    fun `a completed match resolves from the result string, not the server`() {
        val m = match(
            team("India"), team("Australia"),
            probability = null,
            status = "Complete",
            currentStatus = "India won by 5 wickets",
        )
        assertEquals(100.0, m.resolvedWinProbability()!!, 1e-9)
    }

    @Test
    fun `a team 2 win resolves to zero for team 1`() {
        val m = match(
            team("India"), team("Australia"),
            status = "Complete",
            currentStatus = "Australia won by 3 runs",
        )
        assertEquals(0.0, m.resolvedWinProbability()!!, 1e-9)
    }

    @Test
    fun `India does not win when West Indies do`() {
        // "India" is a substring of "West Indies"; the match is word-anchored to prevent it.
        val m = match(
            team("India"), team("West Indies"),
            status = "Complete",
            currentStatus = "West Indies won by 7 wickets",
        )
        assertEquals(0.0, m.resolvedWinProbability()!!, 1e-9)
    }

    @Test
    fun `a drawn match is even`() {
        val m = match(
            team("India"), team("Australia"),
            status = "Complete",
            currentStatus = "Match drawn",
        )
        assertEquals(50.0, m.resolvedWinProbability()!!, 1e-9)
    }

    @Test
    fun `an abandoned or no-result match has no probability, not an even one`() {
        for (status in listOf("Match abandoned due to rain (No toss)", "No result")) {
            val m = match(team("Bangladesh"), team("Malaysia"), status = "Complete", currentStatus = status)
            assertNull(status, m.resolvedWinProbability())
        }
    }

    @Test
    fun `an unreadable result yields null rather than a guess`() {
        val m = match(
            team("India"), team("Australia"),
            status = "Complete",
            currentStatus = "Result unclear",
        )
        assertNull(m.resolvedWinProbability())
    }

    @Test
    fun `a live match passes the server value straight through`() {
        val m = match(team("India", batting = true), team("Australia"), probability = 62.0)
        assertEquals(62.0, m.resolvedWinProbability()!!, 1e-9)
    }

    @Test
    fun `a live match with no server value stays null`() {
        assertNull(match(team("India", batting = true), team("Australia")).resolvedWinProbability())
    }
}
