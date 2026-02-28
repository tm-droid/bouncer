package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import com.tanush.bouncer.models.CricsheetOver
import com.tanush.bouncer.models.CricsheetBall
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class WinProbabilityPitchTest {
    @Test
    fun `determinePitchType returns batting friendly when second innings run rate much higher`() {
        val firstInnings = CricsheetInnings(
            team = "A",
            overs = listOf(CricsheetOver(1, emptyList(), runs = 100, wickets = 0)),
            total = 100,
            wickets = 2,
            runRate = 2.0
        )
        val secondInnings = CricsheetInnings(
            team = "B",
            overs = listOf(CricsheetOver(1, emptyList(), runs = 300, wickets = 2)),
            total = 300,
            wickets = 2,
            runRate = 7.0
        )

        val match = CricsheetMatch(
            matchId = "m1",
            date = Date(),
            venue = "Somewhere",
            teams = CricsheetMatch.Teams(batting = "A", bowling = "B"),
            innings = listOf(firstInnings, secondInnings),
            result = com.tanush.bouncer.models.MatchResult(winner = "B", margin = 0, marginType = "runs")
        )

        val calc = WinProbabili        val calc = WinProbabilch = calc.de        val calc = WinProbabili        val calc = WinProbabilch = calc.de        val calc = WinP@Test
    fun `determinePitchType returns bowlin    fun `determisecond innings run rate much lower`() {
        val firstInnings = CricsheetInnings(
            team = "A",
            overs = listOf(CricsheetOver(1, emptyList(), runs = 200,             overs = listOf(CricsheetOver(1,         wickets = 2,
                                                                         Innings(
                                    overs = listOf(CricsheetOver(1, emptyList(), runs = 100, wickets = 5)),
            total = 100,
            wickets = 5,
            runRate = 3.0
        )

        val match = CricsheetMatch(
            matchId = "m2",
            date = Date(),
            venue = "Nowhere",
            teams = CricsheetMatch.Teams(batting = "A", bowling = "B"),
            innings = listOf(firstInnings, secondInnings),
            result = com.tanush.bouncer.models.MatchResult(winner = "A", margin = 0, marginType = "runs")
        )

        val calc = WinProbabilityCalculator()
        val pitch = calc.determinePitchType(match)
        assertEquals(PitchCondition.BOWLING_FRIENDLY, pitch)
    }

    @Test
    fun `processHistoricalData updates team stats and venue stats`() {
        val innings = CricsheetInnings(
            team = "A",
            overs = listOf(CricsheetOver(1, listOf(CricsheetBall(4, false, null, 0, null)), runs = 4, wickets = 0)),
            total = 4,
            wickets = 0,
            runRate = 4.0
        )
        val match = CricsheetMatch(
            matchId = "m3",
            date = Date(),
            venue = "TestVenue",
            teams = CricsheetMatch.Teams(batting = "A", bowling = "B"),
            innings = listOf(innings),
            result = com.tanush.bouncer.models.MatchResult(winner = "A", margin = 1, marginType = "runs")
        )

        val calc = WinProbabilityCalculator()
        calc.loadCricsheetData(listOf(match))
        // After processing, teamStats and venueStats should contain entries
        val teamStats = calc.teamStats["A"]
        assertEquals(true, teamStats != null)
    }
}
