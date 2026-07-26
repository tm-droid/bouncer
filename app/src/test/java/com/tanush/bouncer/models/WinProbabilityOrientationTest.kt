package com.tanush.bouncer.models

import com.tanush.bouncer.analysis.CricketConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The orientation half of the win-probability rebuild: which team the number belongs to.
 */
class WinProbabilityOrientationTest {

    private fun team(name: String, acronym: String, score: String, batting: Boolean) =
        CricketMatch.Team(
            name = name,
            score = score,
            status = "",
            isBatting = batting,
            logoUrl = null,
            acronym = acronym,
        )

    private fun match(
        matchType: String = "T20",
        status: String = "Live",
        currentStatus: String = "India need 45 runs",
        battingTeamIndex: Int = 0,
        battingScore: String = "120/3",
        overs: String? = "15.0",
        requiredRunRate: String? = "9.0",
        team1: String = "India",
        team2: String = "England",
    ) = CricketMatch(
        id = "1",
        seriesId = "s1",
        matchTitle = "$team1 vs $team2",
        matchType = matchType,
        status = status,
        teams = listOf(
            team(team1, "IND", if (battingTeamIndex == 0) battingScore else "165/8", battingTeamIndex == 0),
            team(team2, "ENG", if (battingTeamIndex == 1) battingScore else "165/8", battingTeamIndex == 1),
        ),
        venue = "Venue",
        date = "2026-07-25",
        matchTime = "19:30",
        score = CricketMatch.MatchScore(
            currentInnings = "2",
            batsmen = emptyList(),
            bowlers = emptyList(),
            overs = overs,
            currentRunRate = "8.0",
            requiredRunRate = requiredRunRate,
            lastWicket = null,
            recentBalls = null,
        ),
        currentStatus = currentStatus,
        info = "",
    )

    // ── The inversion bug ──

    @Test
    fun `probability is reported against the team that is actually batting`() {
        val team1Batting = match(battingTeamIndex = 0)
        val team2Batting = match(battingTeamIndex = 1)

        assertEquals(0, team1Batting.winProbability().teamIndex)
        assertEquals(1, team2Batting.winProbability().teamIndex)
    }

    @Test
    fun `forTeam1 inverts when team 2 is batting`() {
        // REGRESSION: calculateHeuristicProbability() returned the batting side's probability but
        // was rendered as team 1's, so this case displayed the wrong team's number entirely.
        val team1Batting = match(battingTeamIndex = 0)
        val team2Batting = match(battingTeamIndex = 1)

        val batting = team1Batting.winProbability().probability
        assertEquals(batting, team1Batting.calculateHeuristicProbability(), 0.001)
        assertEquals(100.0 - batting, team2Batting.calculateHeuristicProbability(), 0.001)
    }

    @Test
    fun `symmetry - swapping which side bats mirrors the team-1 probability`() {
        val a = match(battingTeamIndex = 0).calculateHeuristicProbability()
        val b = match(battingTeamIndex = 1).calculateHeuristicProbability()
        assertEquals(100.0, a + b, 0.001)
    }

    // ── Undetermined rather than guessed ──

    @Test
    fun `a Test match is undetermined, not scored on a guessed T20 budget`() {
        val test = match(matchType = "Test", overs = "140.3", requiredRunRate = "3.2")
        assertFalse(test.winProbability().isDetermined)
        assertEquals(WinProbability.EVEN, test.calculateHeuristicProbability(), 0.001)
    }

    @Test
    fun `an unrecognised format is undetermined`() {
        assertFalse(match(matchType = "Exhibition Game").winProbability().isDetermined)
    }

    @Test
    fun `unparseable overs are undetermined rather than silently wrong`() {
        assertFalse(match(overs = null).winProbability().isDetermined)
        assertFalse(match(overs = "not overs").winProbability().isDetermined)
    }

    @Test
    fun `decorated overs strings parse correctly`() {
        // REGRESSION: split(".") with toIntOrNull() ?: 0 read "19.5 Ov" as 114 balls, not 119.
        assertEquals(119, CricketConstants.ballsFromOvers("19.5 Ov"))
        assertEquals(119, CricketConstants.ballsFromOvers("(19.5)"))
        assertEquals(119, CricketConstants.ballsFromOvers("19.5"))
        assertEquals(114, CricketConstants.ballsFromOvers("19"))
        assertEquals(null, CricketConstants.ballsFromOvers("19.7")) // 7 balls is not valid notation
        assertEquals(null, CricketConstants.ballsFromOvers("Ov"))
    }

    // ── Completed matches ──

    @Test
    fun `a completed match credits the winner named in the result`() {
        val completed = match(
            status = "Complete",
            currentStatus = "England won by 5 wickets",
        )
        val probability = completed.winProbability()
        assertEquals(1, probability.teamIndex)
        assertEquals(100.0, probability.probability, 0.001)
        assertEquals(0.0, completed.calculateHeuristicProbability(), 0.001)
    }

    @Test
    fun `team names are matched on word boundaries`() {
        // "India" is a substring of "West Indies": a West Indies win must not credit India.
        val completed = match(
            status = "Complete",
            currentStatus = "West Indies won by 5 wickets",
            team1 = "India",
            team2 = "West Indies",
        )
        assertEquals(1, completed.winProbability().teamIndex)
    }

    @Test
    fun `a drawn match is even`() {
        val drawn = match(status = "Complete", currentStatus = "Match drawn")
        assertEquals(WinProbability.EVEN, drawn.calculateHeuristicProbability(), 0.001)
        assertTrue(drawn.winProbability().isDetermined)
    }

    // ── Confidence ──

    @Test
    fun `a chase is high confidence and a first innings is low`() {
        assertEquals(
            WinProbability.Confidence.HIGH,
            match(requiredRunRate = "9.0").winProbability().confidence,
        )
        assertEquals(
            WinProbability.Confidence.LOW,
            match(requiredRunRate = null, currentStatus = "India 120/3 (15 Ov)")
                .winProbability().confidence,
        )
    }

    @Test
    fun `undetermined is distinguishable from a computed even split`() {
        val undetermined = match(matchType = "Test").winProbability()
        assertEquals(WinProbability.EVEN, undetermined.forTeam1, 0.001)
        assertFalse("must not look like a real 50-50", undetermined.isDetermined)
    }
}
