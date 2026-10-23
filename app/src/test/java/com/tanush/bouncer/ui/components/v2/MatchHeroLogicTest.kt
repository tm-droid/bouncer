package com.tanush.bouncer.ui.components.v2

import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchHeroLogicTest {

    private fun team(name: String, score: String = "", acronym: String = "", batting: Boolean = false) =
        CricketMatch.Team(name, score, "", batting, null, acronym)

    private fun match(
        status: String = "",
        type: String = "T20",
        info: String = "",
        a: CricketMatch.Team = team("India", acronym = "IND"),
        b: CricketMatch.Team = team("Australia", acronym = "AUS"),
    ) = CricketMatch(
        id = "1", seriesId = "s", matchTitle = "t", matchType = type, status = "", teams = listOf(a, b),
        venue = "", date = "", matchTime = "", score = null, currentStatus = status, info = info,
    )

    @Test
    fun `status text wins over situation`() {
        val situation = LiveSituation(runsNeeded = 99, oversRemaining = 5.0, battingTeamIsTeam1 = false)
        val line = chaseLineFor(match("India need 23 runs in 18 balls"), situation)
        assertEquals(HeroLine.Chase("India", 23, 18), line)
    }

    @Test
    fun `status text without balls gives runs only`() {
        assertEquals(HeroLine.Chase("India", 23, null), chaseLineFor(match("India need 23 runs to win")))
    }

    @Test
    fun `derives chase from situation when status lacks it`() {
        val situation = LiveSituation(runsNeeded = 40, oversRemaining = 2.5, battingTeamIsTeam1 = false)
        assertEquals(HeroLine.Chase("Australia", 40, 15), chaseLineFor(match("Live"), situation))
    }

    @Test
    fun `derives needed runs from target and score`() {
        val situation = LiveSituation(target = 180, runsScored = 150, battingTeamIsTeam1 = true)
        assertEquals(HeroLine.Chase("India", 30, null), chaseLineFor(match("Live"), situation))
    }

    @Test
    fun `falls back to the batting flag when the side is unknown`() {
        val m = match("Live", b = team("Australia", batting = true))
        assertEquals(HeroLine.Chase("Australia", 10, null), chaseLineFor(m, LiveSituation(runsNeeded = 10)))
    }

    @Test
    fun `test chase is runs only`() {
        val situation = LiveSituation(matchFormat = "TEST", runsNeeded = 120, oversRemaining = 30.0, battingTeamIsTeam1 = true)
        assertEquals(HeroLine.TestNeed("India", 120), chaseLineFor(match("Day 5: Live", type = "Test"), situation))
        assertEquals(HeroLine.TestNeed("India", 50), chaseLineFor(match("India need 50 runs in 100 balls", type = "Test")))
    }

    @Test
    fun `no chase gives null`() {
        assertNull(chaseLineFor(match("Toss delayed")))
        assertNull(chaseLineFor(match("Live"), LiveSituation(runsNeeded = 0)))
        assertNull(chaseLineFor(match("Live"), LiveSituation(runsNeeded = 5)))
    }

    @Test
    fun `ballsFromOvers uses cricket notation`() {
        assertEquals(196, ballsFromOvers("32.4"))
        assertEquals(120, ballsFromOvers("20"))
        assertEquals(120, ballsFromOvers("20.0"))
        assertEquals(0, ballsFromOvers("0.0"))
        assertEquals(196, ballsFromOvers(32.4))
    }

    @Test
    fun `ballsFromOvers rejects invalid input`() {
        assertNull(ballsFromOvers("32.6"))
        assertNull(ballsFromOvers("32.45"))
        assertNull(ballsFromOvers(""))
        assertNull(ballsFromOvers("abc"))
        assertNull(ballsFromOvers("-1.0"))
    }

    @Test
    fun `splitInningsScore handles variants`() {
        assertEquals(listOf(InningsScore(186, 4)), splitInningsScore("186/4"))
        assertEquals(listOf(InningsScore(186, 4)), splitInningsScore("186/4 (20.0 ov)"))
        assertEquals(listOf(InningsScore(312), InningsScore(145, 3)), splitInningsScore("312 & 145/3"))
        assertEquals(listOf(InningsScore(312, 9, declared = true)), splitInningsScore("312/9d"))
        assertEquals(listOf(InningsScore(312, 9, declared = true)), splitInningsScore("312/9 dec"))
        assertEquals(
            listOf(InningsScore(312, followOn = true), InningsScore(100, 2)),
            splitInningsScore("312 f/o & 100/2"),
        )
        assertEquals(emptyList<InningsScore>(), splitInningsScore(""))
        assertEquals(emptyList<InningsScore>(), splitInningsScore("  "))
    }

    @Test
    fun `lead trail and level from innings totals`() {
        val lead = match(a = team("India", "312 & 145/3"), b = team("Australia", "250"), type = "Test")
        assertEquals(HeroLine.Lead("India", 207), testLeadLine(lead))
        val trail = match(a = team("India", "200"), b = team("Australia", "250/6"), type = "Test")
        assertEquals(HeroLine.Trail("India", 50), testLeadLine(trail))
        val level = match(a = team("India", "200"), b = team("Australia", "200"), type = "Test")
        assertEquals(HeroLine.Level, testLeadLine(level))
        assertNull(testLeadLine(match()))
    }

    @Test
    fun `hero test line yields to a status that already states it`() {
        val a = team("India", "312 & 145/3")
        val b = team("Australia", "250")
        assertEquals(HeroLine.Lead("India", 207), testHeroLine(match("Day 2: Stumps", "Test", a = a, b = b)))
        assertNull(testHeroLine(match("Day 2: Stumps - India lead by 207 runs", "Test", a = a, b = b)))
        assertNull(testHeroLine(match("Day 4: Australia need 120 runs", "Test", a = a, b = b)))
    }

    @Test
    fun `lead counts a side that has not batted yet as zero`() {
        val m = match(a = team("India", "312/9d"), b = team("Australia"), type = "Test")
        assertEquals(HeroLine.Lead("India", 312), testLeadLine(m))
    }

    @Test
    fun `day and session parse from status`() {
        assertEquals(TestDay(2, HeroSession.Stumps), testDayFor(match("Day 2: Stumps")))
        assertEquals(TestDay(3, HeroSession.Lunch), testDayFor(match("Day 3: Lunch")))
        assertEquals(TestDay(4, HeroSession.Other), testDayFor(match("Day 4 - Rain delay")))
        assertEquals(TestDay(1, null), testDayFor(match("Day 1")))
        assertNull(testDayFor(match("India need 20 runs")))
    }

    @Test
    fun `collapsed summary uses acronyms and the latest innings`() {
        val m = match(
            info = "In Progress",
            a = team("Mumbai Indians", "186/4", "MI"),
            b = team("Chennai Super Kings", "100 & 142/3", "CSK"),
        )
        val summary = collapsedSummary(m)
        assertEquals(listOf(CollapsedTeamScore("MI", "186/4"), CollapsedTeamScore("CSK", "142/3")), summary.teams)
        assertTrue(summary.isLive)
    }

    @Test
    fun `collapsed summary with missing scores and acronyms`() {
        val m = match(a = team("Mumbai Indians", "186/4"), b = team("Chennai"), status = "Starts at 7 PM")
        val summary = collapsedSummary(m)
        assertEquals(listOf(CollapsedTeamScore("MUM", "186/4"), CollapsedTeamScore("CHE", null)), summary.teams)
        assertFalse(summary.isLive)
    }
}
