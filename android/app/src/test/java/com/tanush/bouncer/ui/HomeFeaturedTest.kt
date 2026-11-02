package com.tanush.bouncer.ui

import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class HomeFeaturedTest {
    private val now = 1_000_000_000L

    private fun hours(h: Long) = TimeUnit.HOURS.toMillis(h)

    private fun match(id: String, a: String, b: String, live: Boolean = false, startsInH: Long? = null) = CricketMatch(
        id = id, seriesId = "s", matchTitle = "$a v $b", matchType = "T20",
        status = if (live) "Live" else "Upcoming",
        teams = listOf(a, b).map { CricketMatch.Team(name = it, score = "", status = "", isBatting = false, logoUrl = null) },
        venue = "", date = "", matchTime = "", score = null,
        currentStatus = if (live) "Live" else "Match starts soon", info = "",
        startDateMs = startsInH?.let { now + hours(it) },
    )

    private fun pick(matches: List<CricketMatch>, favs: List<String>, allowUpcoming: Boolean = true) =
        featuredFor(matches, favs, now, allowUpcoming)

    @Test
    fun `no followed teams features the first live match`() {
        val r = pick(listOf(match("1", "A", "B"), match("2", "C", "D", live = true)), emptyList())
        assertEquals("2", r.featured?.match?.id)
        assertEquals(FeaturedKind.Live, r.featured?.kind)
        assertEquals(listOf("1"), r.rows.map { it.id })
    }

    @Test
    fun `one followed team beats an earlier live match`() {
        val r = pick(listOf(match("1", "A", "B", live = true), match("2", "India", "D", live = true)), listOf("india"))
        assertEquals("2", r.featured?.match?.id)
    }

    @Test
    fun `favourites order decides among three followed live matches`() {
        val ms = listOf(
            match("1", "Aus", "X", live = true),
            match("2", "Eng", "Y", live = true),
            match("3", "India", "Z", live = true),
        )
        assertEquals("3", pick(ms, listOf("india", "eng", "aus")).featured?.match?.id)
        assertEquals("1", pick(ms, listOf("aus", "india", "eng")).featured?.match?.id)
    }

    @Test
    fun `both sides followed uses the higher ranked team`() {
        val ms = listOf(match("1", "Eng", "Aus", live = true), match("2", "India", "Z", live = true))
        assertEquals("1", pick(ms, listOf("aus", "india", "eng")).featured?.match?.id)
    }

    @Test
    fun `followed upcoming within 24h is picked when nothing followed is live`() {
        val ms = listOf(match("1", "A", "B", live = true), match("2", "India", "D", startsInH = 5), match("3", "India", "E", startsInH = 9))
        val r = pick(ms, listOf("india"))
        assertEquals("2", r.featured?.match?.id)
        assertEquals(FeaturedKind.Upcoming, r.featured?.kind)
    }

    @Test
    fun `followed upcoming at 30h is not picked so any live match wins`() {
        val ms = listOf(match("1", "India", "D", startsInH = 30), match("2", "A", "B", live = true))
        val r = pick(ms, listOf("india"))
        assertEquals("2", r.featured?.match?.id)
        assertEquals(FeaturedKind.Live, r.featured?.kind)
        assertEquals(listOf("1"), r.rows.map { it.id })
    }

    @Test
    fun `upcoming is skipped when the hero cannot show it`() {
        val r = pick(listOf(match("1", "India", "D", startsInH = 5)), listOf("india"), allowUpcoming = false)
        assertNull(r.featured)
    }

    @Test
    fun `nothing live and nothing followed gives no hero`() {
        val r = pick(listOf(match("1", "A", "B", startsInH = 2)), emptyList())
        assertNull(r.featured)
        assertEquals(listOf("1"), r.rows.map { it.id })
    }
}
