package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.COMMENTARY_EVENT_PAGE_LIMIT
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.ScorecardSnapshot
import com.tanush.bouncer.models.TimelineEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole match's wickets and boundaries, fetched when the chip is tapped.
 *
 * Each row here is a billed read, so what this pins is mostly what is NOT fetched: nothing twice,
 * no page behind a short one. And a failed read must not pass for a match with no wickets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentaryEventPagerTest {

    /** Serves [total] events newest-first in real page sizes, or fails while [failing]. */
    private class EventSource(private val total: Int) : LiveMatchDetailSource {
        val asked = mutableListOf<Pair<List<String>, Double?>>()
        var failing = false

        override suspend fun getEventCommentary(
            matchId: String,
            events: List<String>,
            olderThan: Double?,
        ): List<CommentaryEntry>? {
            asked += events to olderThan
            if (failing) return null
            return (total downTo 1)
                .map { it.toDouble() }
                .filter { olderThan == null || it < olderThan }
                .take(COMMENTARY_EVENT_PAGE_LIMIT)
                .map { CommentaryEntry("event $it", TimelineEventType.WICKET, sortKey = it) }
        }

        override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? = null
        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry>? = null
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double): List<CommentaryEntry> =
            emptyList()
        override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? = null
        override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> = emptyFlow()
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow()
        override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> = emptyFlow()
    }

    @Test
    fun `a short first page is the whole list and is fetched once`() = runTest {
        val source = EventSource(total = 14)
        val pager = CommentaryEventPager("170667", source, backgroundScope)

        pager.open(CommentaryEventKind.WICKETS)
        runCurrent()
        pager.open(CommentaryEventKind.WICKETS)
        pager.loadOlder(CommentaryEventKind.WICKETS)
        runCurrent()

        val page = pager.pages.value.getValue(CommentaryEventKind.WICKETS)
        assertEquals(14, page.entries.size)
        assertTrue(page.loaded)
        assertFalse(page.hasOlder)
        assertEquals(listOf(listOf("WICKET") to null), source.asked)
    }

    @Test
    fun `a full page offers the one behind it, paged from its own oldest row`() = runTest {
        val source = EventSource(total = COMMENTARY_EVENT_PAGE_LIMIT + 25)
        val pager = CommentaryEventPager("170667", source, backgroundScope)

        pager.open(CommentaryEventKind.BOUNDARIES)
        runCurrent()
        assertTrue(pager.pages.value.getValue(CommentaryEventKind.BOUNDARIES).hasOlder)

        pager.loadOlder(CommentaryEventKind.BOUNDARIES)
        runCurrent()

        val page = pager.pages.value.getValue(CommentaryEventKind.BOUNDARIES)
        assertEquals(COMMENTARY_EVENT_PAGE_LIMIT + 25, page.entries.size)
        assertEquals(page.entries.map { it.sortKey }, page.entries.map { it.sortKey }.sortedByDescending { it })
        assertFalse(page.hasOlder)
        assertEquals(listOf("FOUR", "SIX") to 26.0, source.asked.last())
    }

    @Test
    fun `a failed read is not an empty match, and the next tap asks again`() = runTest {
        val source = EventSource(total = 3).apply { failing = true }
        val pager = CommentaryEventPager("170667", source, backgroundScope)

        pager.open(CommentaryEventKind.WICKETS)
        runCurrent()
        val failed = pager.pages.value.getValue(CommentaryEventKind.WICKETS)
        assertFalse(failed.loaded)
        assertFalse(failed.loading)

        source.failing = false
        pager.open(CommentaryEventKind.WICKETS)
        runCurrent()
        assertEquals(3, pager.pages.value.getValue(CommentaryEventKind.WICKETS).entries.size)
    }
}
