package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class MatchupAnalysisTest {

    private fun makeBall(
        batter: String,
        bowler: String,
        batterRuns: Int = 1,
    ) = CricsheetBall(
        runs = batterRuns,
        isWicket = false,
        wicketType = null,
        extras = 0,
        extraType = null,
        batter = batter,
        bowler = bowler,
        batterRuns = batterRuns,
    )

    private fun makeMatch(vararg balls: CricsheetBall): CricsheetMatch {
        return CricsheetMatch(
            matchId = "test",
            date = Date(),
            venue = "Ground",
            matchType = "T20",
            teams = CricsheetMatch.Teams(batting = "A", bowling = "B"),
            innings = listOf(
                CricsheetInnings(
                    team = "A",
                    overs = listOf(CricsheetOver(overNumber = 0, balls = balls.toList(), runs = 0, wickets = 0)),
                    total = 0,
                    wickets = 0,
                    runRate = 0.0,
                )
            ),
            result = MatchResult(winner = null, margin = 0, marginType = "runs"),
        )
    }

    @Test
    fun `getBatterMatchups returns only matchups for that batter`() {
        val analysis = MatchupAnalysis()
        val balls = (1..7).map { makeBall("BatterA", "BowlerX") } +
                    (1..7).map { makeBall("BatterB", "BowlerX") }
        analysis.processMatches(listOf(makeMatch(*balls.toTypedArray())))

        val result = analysis.getBatterMatchups("BatterA")
        assertTrue(result.all { it.batter == "BatterA" })
        assertEquals(1, result.size)
    }

    @Test
    fun `getBatterMatchups excludes pairs with fewer than 6 balls`() {
        val analysis = MatchupAnalysis()
        val balls = (1..5).map { makeBall("BatterA", "BowlerX") }
        analysis.processMatches(listOf(makeMatch(*balls.toTypedArray())))

        assertTrue(analysis.getBatterMatchups("BatterA").isEmpty())
    }

    @Test
    fun `getBowlerMatchups returns only matchups for that bowler`() {
        val analysis = MatchupAnalysis()
        val balls = (1..8).map { makeBall("BatterA", "BowlerX") } +
                    (1..8).map { makeBall("BatterA", "BowlerY") }
        analysis.processMatches(listOf(makeMatch(*balls.toTypedArray())))

        val result = analysis.getBowlerMatchups("BowlerX")
        assertTrue(result.all { it.bowler == "BowlerX" })
        assertEquals(1, result.size)
    }

    @Test
    fun `getBatterMatchups returns empty list for unknown batter`() {
        val analysis = MatchupAnalysis()
        assertTrue(analysis.getBatterMatchups("Nobody").isEmpty())
    }

    @Test
    fun `getBatterMatchups sorted by balls faced descending`() {
        val analysis = MatchupAnalysis()
        val balls = (1..10).map { makeBall("BatterA", "BowlerX") } +
                    (1..7).map { makeBall("BatterA", "BowlerY") }
        analysis.processMatches(listOf(makeMatch(*balls.toTypedArray())))

        val result = analysis.getBatterMatchups("BatterA")
        assertEquals(2, result.size)
        assertTrue(result[0].ballsFaced >= result[1].ballsFaced)
    }
}
