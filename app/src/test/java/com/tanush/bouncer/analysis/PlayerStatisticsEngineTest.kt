package com.tanush.bouncer.analysis

import com.tanush.bouncer.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class PlayerStatisticsEngineTest {

    private fun makeBall(
        batter: String = "BatterA",
        bowler: String = "BowlerX",
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

    private fun makeMatch(vararg overs: List<CricsheetBall>): CricsheetMatch {
        val cricketOvers = overs.mapIndexed { idx, balls ->
            CricsheetOver(overNumber = idx, balls = balls, runs = 0, wickets = 0)
        }
        return CricsheetMatch(
            matchId = "test-match",
            date = Date(),
            venue = "TestGround",
            matchType = "T20",
            teams = CricsheetMatch.Teams(batting = "TeamA", bowling = "TeamB"),
            innings = listOf(
                CricsheetInnings(team = "TeamA", overs = cricketOvers, total = 0, wickets = 0, runRate = 0.0)
            ),
            result = MatchResult(winner = null, margin = 0, marginType = "runs"),
        )
    }

    @Test
    fun `bowling innings count increments once per innings for each bowler`() {
        val engine = PlayerStatisticsEngine()
        val balls = listOf(
            makeBall(bowler = "BowlerX"),
            makeBall(bowler = "BowlerX"),
            makeBall(bowler = "BowlerX"),
        )
        engine.processMatch(makeMatch(balls))
        val stats = engine.getBowlerStats("BowlerX")
        assertNotNull(stats)
        assertEquals(1, stats!!.bowlingInnings)
    }

    @Test
    fun `two bowlers in one innings each get innings count of 1`() {
        val engine = PlayerStatisticsEngine()
        val balls = listOf(
            makeBall(bowler = "BowlerA"),
            makeBall(bowler = "BowlerB"),
            makeBall(bowler = "BowlerA"),
        )
        engine.processMatch(makeMatch(balls))
        assertEquals(1, engine.getBowlerStats("BowlerA")!!.bowlingInnings)
        assertEquals(1, engine.getBowlerStats("BowlerB")!!.bowlingInnings)
    }

    @Test
    fun `blank bowler balls are skipped and do not add to innings count`() {
        val engine = PlayerStatisticsEngine()
        val balls = listOf(
            makeBall(bowler = ""),
            makeBall(bowler = "BowlerX"),
        )
        engine.processMatch(makeMatch(balls))
        assertNull(engine.getBowlerStats(""))
        assertEquals(1, engine.getBowlerStats("BowlerX")!!.bowlingInnings)
    }
}
