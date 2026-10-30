package com.tanush.bouncer.data.repository

import com.tanush.bouncer.data.local.dao.HighlightDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.data.local.entities.toMatchEntity
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins highlights being linked to a match at all.
 *
 * Nothing wrote the Room `matches` table the fuzzy matcher reads, so every highlight was stored with
 * no match and no Highlights tab ever showed one. These cover the write, the re-score of rows that
 * were stored before their match existed, and the date and series signals the written row carries.
 */
class HighlightsRepositoryRematchTest {

    private class FakeMatchDao : MatchDao {
        val rows = MutableStateFlow<Map<String, MatchEntity>>(emptyMap())
        override suspend fun upsertMatch(match: MatchEntity) { rows.value = rows.value + (match.matchId to match) }
        override suspend fun upsertMatches(matches: List<MatchEntity>) = matches.forEach { upsertMatch(it) }
        override fun getMatch(matchId: String): Flow<MatchEntity?> = rows.map { it[matchId] }
        override fun getLiveMatches(): Flow<List<MatchEntity>> = rows.map { m -> m.values.filter { it.isLive } }
        override fun getAllMatches(): Flow<List<MatchEntity>> = rows.map { it.values.toList() }
        override suspend fun deleteOlderThan(before: Long) {
            rows.value = rows.value.filterValues { it.lastUpdated >= before }
        }
    }

    private class FakeHighlightDao : HighlightDao {
        val rows = mutableMapOf<String, HighlightEntity>()
        override suspend fun upsertAll(highlights: List<HighlightEntity>) = highlights.forEach { rows[it.id] = it }
        override fun queryByBouncerMatchId(bouncerMatchId: String): Flow<List<HighlightEntity>> =
            MutableStateFlow(rows.values.filter { it.matchedBouncerMatchId == bouncerMatchId })
        override suspend fun queryCachedByMatchId(bouncerMatchId: String, freshnessCutoff: Long) =
            rows.values.filter { it.matchedBouncerMatchId == bouncerMatchId && it.fetchedAt >= freshnessCutoff }
        override suspend fun queryAllByMatchId(bouncerMatchId: String) =
            rows.values.filter { it.matchedBouncerMatchId == bouncerMatchId }
        override suspend fun queryAllRecent() = rows.values.toList()
        override suspend fun queryUnmatched() = rows.values.filter { it.matchedBouncerMatchId == null }
        override suspend fun cachedIccMatchId(bouncerMatchId: String): String? = null
        override suspend fun cachedIplMatchId(bouncerMatchId: String): String? = null
        override suspend fun deleteOlderThan(threshold: Long) = Unit
    }

    private fun team(name: String, score: String = "") =
        CricketMatch.Team(name = name, score = score, status = "", isBatting = false, logoUrl = null)

    private fun match(id: String, startMs: Long) = CricketMatch(
        id = id, seriesId = "12973", matchTitle = "Barbados Tridents vs Guyana Amazon Warriors",
        matchType = "T20", status = "Barbados Tridents won by 6 wkts", venue = "", date = "", matchTime = "",
        score = null, currentStatus = "Barbados Tridents won by 6 wkts", info = "Complete",
        teams = listOf(team("Barbados Tridents", "171/4"), team("Guyana Amazon Warriors", "168/7")),
        seriesName = "Caribbean Premier League 2026", startDateMs = startMs,
    )

    private val highlight = HighlightEntity(
        id = "h1", title = "Highlights: Barbados Tridents vs Guyana Amazon Warriors", imgUrl = "",
        embedUrl = "https://example.invalid/embed", fallbackUrl = null, source = "", channel = "",
        type = "VERIFIED", matchedBouncerMatchId = null, iccMatchId = null, iplMatchId = null,
        leagueName = "Caribbean Premier League", homeTeam = "Barbados Tridents",
        awayTeam = "Guyana Amazon Warriors", matchStartDate = "2026-09-12T23:00:00.000Z", fetchedAt = 1L,
    )

    // 2026-09-12T23:30Z, and ten days earlier.
    private val sameDay = 1_789_255_800_000L
    private val tenDaysEarlier = sameDay - 10L * 24 * 3_600_000

    @Test
    fun `a highlight stored before its match existed is linked once the match is recorded`() = runBlocking {
        val highlights = FakeHighlightDao().apply { rows[highlight.id] = highlight }
        val repo = HighlightsRepository(highlights, FakeMatchDao())

        repo.recordMatches(listOf(match("170103", sameDay)), rematch = true)

        assertEquals("170103", highlights.rows.getValue("h1").matchedBouncerMatchId)
    }

    @Test
    fun `the same fixture ten days apart loses to the one on the highlight's date`() = runBlocking {
        val highlights = FakeHighlightDao().apply { rows[highlight.id] = highlight }
        val repo = HighlightsRepository(highlights, FakeMatchDao())

        repo.recordMatches(listOf(match("old", tenDaysEarlier), match("170103", sameDay)), rematch = true)

        assertEquals("170103", highlights.rows.getValue("h1").matchedBouncerMatchId)
    }

    @Test
    fun `recording without rematch writes the table and leaves highlights alone`() = runBlocking {
        val matches = FakeMatchDao()
        val highlights = FakeHighlightDao().apply { rows[highlight.id] = highlight }

        HighlightsRepository(highlights, matches).recordMatches(listOf(match("170103", sameDay)), rematch = false)

        assertEquals(setOf("170103"), matches.rows.value.keys)
        assertNull(highlights.rows.getValue("h1").matchedBouncerMatchId)
    }

    @Test
    fun `the written row carries the UTC start date and the series name`() {
        val entity = match("170103", sameDay).toMatchEntity(nowMs = 0L)
        assertEquals("2026-09-12", entity.date)
        assertEquals("Barbados Tridents vs Guyana Amazon Warriors, Caribbean Premier League 2026", entity.matchTitle)
        assertEquals("Barbados Tridents", entity.team1Name)
        assertEquals("168/7", entity.team2Score)
    }

    @Test
    fun `a match with no start time writes a blank date, which the matcher ignores`() {
        assertEquals("", match("x", 0L).copy(startDateMs = null).toMatchEntity().date)
    }
}
