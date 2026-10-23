package com.tanush.bouncer.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CareerStatsTest {
    // V Kohli's real ODI and Test columns from s/p/ba607b88.json.
    private val odi = listOf(314, 303, 47, 14987, 15892, 183, 0, 77, 55, 662, 680, 5, 1, 13, 167, 0)
    private val test = listOf(123, 210, 13, 9230, 16608, 254, 1, 31, 30, 175, 84, 0, 0, 0, 121, 0)

    @Test
    fun `columns become the figures the profile shows`() {
        val s = CareerStats.formatStats(odi)!!
        assertEquals(314, s.matches)
        assertEquals(14987, s.runs)
        assertEquals(14987.0 / (303 - 47), s.average, 1e-9)
        assertEquals(14987 * 100.0 / 15892, s.strikeRate, 1e-9)
        assertEquals("183", s.highScore)
        assertEquals(55, s.hundreds)
        assertEquals(77, s.fifties)
        assertEquals(5, s.wickets)
        assertEquals(680.0 / 5, s.bowlingAverage, 1e-9)
        assertEquals(680 / (662 / 6.0), s.economy, 1e-9)
        assertEquals("1/13", s.bestBowling)
        assertEquals(167, s.catches)
    }

    @Test
    fun `a not-out high score is starred and a bowler with no wickets has no bowling figures`() {
        val s = CareerStats.formatStats(test)!!
        assertEquals("254*", s.highScore)
        assertEquals(0.0, s.bowlingAverage, 0.0)
        assertEquals("-", s.bestBowling)
    }

    @Test
    fun `no dismissals or balls does not divide by zero`() {
        val s = CareerStats.formatStats(List(16) { 0 })!!
        assertEquals(0.0, s.average, 0.0)
        assertEquals(0.0, s.strikeRate, 0.0)
        assertEquals(0.0, s.economy, 0.0)
    }

    @Test
    fun `boundary columns show only on a record that carries them`() {
        val old = CareerStats.formatStats(odi)!!
        assertEquals(0, old.fours)
        assertEquals(0, old.fiveWickets)
        val s = CareerStats.formatStats(odi + listOf(1311, 26, 0, 0))!!
        assertEquals(1311, s.fours)
        assertEquals(26, s.sixes)
        assertEquals(0, s.fiveWickets)
        val withHaul = CareerStats.formatStats(odi + listOf(0, 0, 3, 2))!!
        assertEquals(2, withHaul.fiveWickets)
        val json = { cols: String -> """{"n":"X","f":{"ODI":[$cols]},"tm":{},"m":[]}""" }
        assertEquals(false, CareerStats.parseCareer(json("314,303,47,14987,15892,183,0,77,55,662,680,5,1,13,167,0"))!!.hasBoundaries)
        assertEquals(true, CareerStats.parseCareer(json("314,303,47,14987,15892,183,0,77,55,662,680,5,1,13,167,0,9,1,0,0"))!!.hasBoundaries)
    }

    @Test
    fun `a short column list is not a record`() {
        assertNull(CareerStats.formatStats(listOf(1, 2, 3)))
    }

    @Test
    fun `a player record parses with sides most first and matches`() {
        val json = """{"n":"V Kohli","f":{"ODI":[314,303,47,14987,15892,183,0,77,55,662,680,5,1,13,167,0]},
            "tm":{"Royal Challengers Bangalore":236,"India":555},
            "m":[["1529229","2026-10-03","ODI","India","West Indies",0,1,1,0,0],["bad"]]}"""
        val career = CareerStats.parseCareer(json)!!
        assertEquals("V Kohli", career.name)
        assertEquals(listOf("India", "Royal Challengers Bangalore"), career.teams.map { it.first })
        assertEquals(setOf("ODI"), career.formats.keys)
        val m = career.matches.single()
        assertEquals(CareerMatch("1529229", LocalDate.of(2026, 10, 3), "ODI", "India", "West Indies"), m)
        assertEquals(PastSource.CRICSHEET, m.toRow().source)
        assertNotNull(CareerStats.parseCareer("""{"n":"X","f":{},"tm":{},"m":[]}"""))
        assertNull(CareerStats.parseCareer("nope"))
    }

    @Test
    fun `a names file parses to rows`() {
        val rows = CareerStats.parseNames("""[["V Kohli","ba607b88","India",837],["x"]]""")!!
        assertEquals(listOf(HistoryNameRow("V Kohli", "ba607b88", "India", 837)), rows)
    }

    @Test
    fun `a names row may carry a Cricbuzz id and a record the register ids`() {
        val rows = CareerStats.parseNames("""[["AF Milne","350bb1b1","New Zealand",50,"7625"],["B One","b","X",1,""]]""")!!
        assertEquals("7625", rows[0].cricbuzzId)
        assertNull(rows[1].cricbuzzId)
        val career = CareerStats.parseCareer("""{"n":"X","f":{},"tm":{},"m":[],"cb":"7625","ci":"450860"}""")!!
        assertEquals("7625", career.cricbuzzId)
        assertEquals("450860", career.cricinfoId)
        assertNull(CareerStats.parseCareer("""{"n":"X","f":{},"tm":{},"m":[]}""")!!.cricbuzzId)
    }
}
