package com.tanush.bouncer.services

import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the matcher's vetoes against the false links it made the first time the `matches` table had
 * rows in it. Every rejected pair below is one it actually linked on a test device (2026-09-14),
 * where no highlight had a true match at all and 154 of 400 linked anyway.
 */
class HighlightFuzzyMatcherTest {

    private fun highlight(home: String, away: String, league: String, date: String) = HighlightEntity(
        id = "h", title = "$home vs $away", imgUrl = "", embedUrl = null, fallbackUrl = null, source = "",
        channel = "", type = "", matchedBouncerMatchId = null, iccMatchId = null, iplMatchId = null,
        leagueName = league, homeTeam = home, awayTeam = away, matchStartDate = date, fetchedAt = 0L,
    )

    private fun match(t1: String, t2: String, series: String, date: String) = MatchEntity(
        matchId = "$t1-$t2-$date", seriesId = "", matchTitle = "$t1 vs $t2, $series", matchType = "",
        status = "", venue = "", date = date, matchTime = "", currentStatus = "", info = "",
        team1Name = t1, team1Score = null, team2Name = t2, team2Score = null,
    )

    private fun link(h: HighlightEntity, vararg m: MatchEntity) =
        HighlightFuzzyMatcher.matchHighlightToBouncerMatch(h, m.toList())

    @Test
    fun `one shared team and the same league weeks apart is not a match`() {
        val h = highlight("Derbyshire", "Durham", "County Championship Division Two", "2026-08-20T10:00:00Z")
        assertNull(link(h, match("Durham", "Middlesex", "County Championship Division Two 2026", "2026-09-08")))
    }

    @Test
    fun `one shared team on the same day is still not a match`() {
        val h = highlight("Derbyshire", "Durham", "County Championship Division Two", "2026-09-08T10:00:00Z")
        assertNull(link(h, match("Durham", "Middlesex", "County Championship Division Two 2026", "2026-09-08")))
    }

    @Test
    fun `a men's side is not its women's side`() {
        val h = highlight("Sri Lanka", "India", "Women's Asia Cup", "2026-09-13T09:00:00Z")
        assertNull(link(h, match("India Women", "Sri Lanka Women", "Women's Asia Cup 2026", "2026-09-13")))
    }

    @Test
    fun `neighbouring county names are different teams`() {
        val h = highlight("Worcestershire", "Northamptonshire", "County Championship Division Two", "2026-09-08T10:00:00Z")
        assertNull(link(h, match("Gloucestershire", "Nottinghamshire", "County Championship Division Two 2026", "2026-09-08")))
    }

    @Test
    fun `the same fixture on the same date links, in either team order`() {
        val m = match("St Kitts and Nevis Patriots", "Barbados Tridents", "Caribbean Premier League 2026", "2026-09-10")
        val h = highlight("Barbados Tridents", "St Kitts and Nevis Patriots", "Caribbean Premier League", "2026-09-10T23:00:00Z")
        assertEquals(m, link(h, m))
    }

    @Test
    fun `a start a timezone apart still links`() {
        val m = match("St Kitts and Nevis Patriots", "Barbados Tridents", "Caribbean Premier League 2026", "2026-09-11")
        val h = highlight("St Kitts and Nevis Patriots", "Barbados Tridents", "Caribbean Premier League", "2026-09-10T23:00:00Z")
        assertEquals(m, link(h, m))
    }

    @Test
    fun `a match with no start date is judged on teams and league alone`() {
        val m = match("St Kitts and Nevis Patriots", "Barbados Tridents", "Caribbean Premier League 2026", "")
        val h = highlight("St Kitts and Nevis Patriots", "Barbados Tridents", "Caribbean Premier League", "2026-09-10T23:00:00Z")
        assertEquals(m, link(h, m))
    }
}
