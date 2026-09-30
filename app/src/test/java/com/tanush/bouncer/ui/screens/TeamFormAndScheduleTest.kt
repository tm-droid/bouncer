package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.utils.Reminder
import com.tanush.bouncer.utils.upcomingOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TeamFormAndScheduleTest {

    private fun match(
        id: String,
        status: String,
        currentStatus: String,
        startDateMs: Long? = null,
        teams: List<String> = listOf("India", "West Indies"),
    ) = CricketMatch(
        id = id,
        seriesId = "0",
        matchTitle = "${teams[0]} vs ${teams[1]}",
        matchType = "ODI",
        status = status,
        teams = teams.map { CricketMatch.Team(name = it, score = "", status = "", isBatting = false, logoUrl = null) },
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = currentStatus,
        info = "",
        startDateMs = startDateMs,
    )

    @Test
    fun `form reads the winner from the status line`() {
        val won = match("1", "Complete", "India won by 5 wkts")
        val lost = match("2", "Complete", "West Indies won by 20 runs")
        assertEquals(FormResult.WON, formResult(won, "India"))
        assertEquals(FormResult.LOST, formResult(lost, "India"))
        assertEquals(FormResult.WON, formResult(lost, "west indies"))
    }

    @Test
    fun `draws, ties and washouts have their own letter`() {
        assertEquals(FormResult.DRAWN, formResult(match("1", "Complete", "Match drawn"), "India"))
        assertEquals(FormResult.TIED, formResult(match("2", "Complete", "Match tied"), "India"))
        assertEquals(FormResult.NO_RESULT, formResult(match("3", "Complete", "No result"), "India"))
    }

    @Test
    fun `a winner that is neither side as spelled here is no letter, not a loss`() {
        assertNull(formResult(match("1", "Complete", "IND won by 5 wkts"), "India"))
    }

    @Test
    fun `an unfinished match has no result`() {
        assertNull(formResult(match("1", "Live", "India need 20 runs in 12 balls"), "India"))
    }

    @Test
    fun `results are newest first and only the team's finished matches`() {
        val older = match("1", "Complete", "India won by 5 wkts", startDateMs = 1_000)
        val newer = match("2", "Complete", "India won by 1 run", startDateMs = 2_000)
        val live = match("3", "Live", "India need 20", startDateMs = 3_000)
        val other = match("4", "Complete", "England won", startDateMs = 4_000, teams = listOf("England", "Australia"))
        assertEquals(listOf("2", "1"), resultsFor(listOf(older, live, newer, other), "India").map { it.id })
    }

    @Test
    fun `matches group by local day, ascending, and undated ones are left out`() {
        val day = 86_400_000L
        val a = match("a", "Complete", "x", startDateMs = 10 * day + 3_600_000)
        val b = match("b", "Upcoming", "", startDateMs = 11 * day + 7_200_000)
        val c = match("c", "Upcoming", "", startDateMs = 10 * day + 60_000)
        val undated = match("d", "Upcoming", "", startDateMs = null)
        val grouped = groupByDay(listOf(b, a, undated, c), ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.ofEpochDay(10), LocalDate.ofEpochDay(11)), grouped.map { it.first })
        assertEquals(listOf("c", "a"), grouped[0].second.map { it.id })
    }

    @Test
    fun `by date opens on today - past days and their matches are skipped`() {
        val day = 86_400_000L
        val past1 = match("a", "Complete", "x", startDateMs = 10 * day)
        val past2 = match("b", "Complete", "x", startDateMs = 10 * day + 1)
        val todays = match("c", "Upcoming", "", startDateMs = 11 * day)
        val all = listOf(past1, past2, todays)
        assertEquals(3, todayOffset(all, ZoneOffset.UTC, LocalDate.ofEpochDay(11)))
        assertEquals("all past: land on the last day", 3, todayOffset(all, ZoneOffset.UTC, LocalDate.ofEpochDay(20)))
        assertEquals(0, todayOffset(all, ZoneOffset.UTC, LocalDate.ofEpochDay(1)))
    }

    @Test
    fun `day labels`() {
        val today = LocalDate.of(2026, 9, 30)
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Tomorrow", dayLabel(today.plusDays(1), today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("Fri 2 Oct", dayLabel(today.plusDays(2), today, java.util.Locale.UK))
    }

    @Test
    fun `reminders already past are dropped and the rest sorted soonest first`() {
        val r = listOf(Reminder(1, "a", "", 300), Reminder(2, "b", "", 100), Reminder(3, "c", "", 200))
        assertEquals(listOf(3, 1), upcomingOf(r, nowMs = 150).map { it.id })
    }
}
