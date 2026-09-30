package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModelStore
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.ScorecardSnapshot
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.services.MatchPresenceReporter
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Paging back through the commentary feed, and what a realtime delivery may do to it.
 *
 * Every row on screen cost a billed Appwrite read, and a page the reader tapped for cost 150 of
 * them. The in-memory cap exists to bound rows that arrive on their own — it must not delete rows
 * the reader bought, which `hasOlderCommentary` would then offer to buy again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailFeedPagingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** The real page sizes: 48 on open, 150 on every tap behind it. */
    private class PagingSource(private val events: Flow<CommentaryEvent>) : LiveMatchDetailSource {
        var pagesServed = 0
            private set

        override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? = null

        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> =
            (0 until FIRST_PAGE).map {
                CommentaryEntry("open $it", TimelineEventType.OTHER, sortKey = (1_000_000 - it).toDouble())
            }

        override suspend fun getOlderCommentary(matchId: String, olderThan: Double): List<CommentaryEntry> {
            pagesServed++
            return (1..DEEP_PAGE).map {
                CommentaryEntry("page $pagesServed line $it", TimelineEventType.OTHER, sortKey = olderThan - it)
            }
        }

        override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? = null
        override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> = emptyFlow()
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = events
        override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> = emptyFlow()

        companion object {
            const val FIRST_PAGE = 48
            const val DEEP_PAGE = 150
        }
    }

    private val baseMatch = CricketMatch(
        id = "170667",
        seriesId = "0",
        matchTitle = "Australia vs India, 3rd Test",
        matchType = "Test",
        status = "Complete",
        teams = emptyList(),
        venue = "MCG",
        date = "2026-09-16",
        matchTime = "",
        score = null,
        currentStatus = "Australia won by 4 wickets",
        info = "",
    )

    private fun repository(): MatchRepository = mockk(relaxed = true) {
        coEvery { fetchLiveCommentary(any()) } returns emptyList()
        coEvery { findCricinfoMatchUrlAndId(any()) } returns null
        coEvery { fetchScorecard(any()) } returns Scorecard(emptyList())
    }

    /**
     * Live, because a finished match subscribes to no commentary stream at all. A live match runs
     * the presence heartbeat — an unbounded loop — so this test steps with `runCurrent` and clears
     * the ViewModel itself instead of draining the scheduler.
     */
    @Test
    fun `a realtime line never truncates pages the reader paid for`() = runTest(dispatcher) {
        mockkObject(MatchPresenceReporter)
        coEvery { MatchPresenceReporter.ping(any()) } returns true
        val liveMatch = baseMatch.copy(status = "Live", currentStatus = "India need 120 runs")
        require(!liveMatch.isCompleted) { "fixture must not be terminal" }
        val events = MutableSharedFlow<CommentaryEvent>(extraBufferCapacity = 4)
        val store = ViewModelStore()
        val vm = MatchDetailViewModel(
            match = liveMatch,
            matchRepository = repository(),
            liveSource = PagingSource(events),
        ).also { store.put("match", it) }
        try {
            runCurrent()
            assertEquals("the match opens on one small page", 48, vm.uiState.value.commentary.size)

            repeat(3) {
                vm.loadOlderCommentary()
                runCurrent()
            }
            val paged = vm.uiState.value.commentary.size
            assertEquals("48 + three 150-row pages", 498, paged)

            events.tryEmit(
                CommentaryEvent(
                    matchId = liveMatch.id,
                    commentary = "a new delivery",
                    sortKey = 2_000_000.0,
                    event = TimelineEventType.OTHER,
                )
            )
            runCurrent()

            // The cap is 400. Truncating to it here would delete 99 rows that cost billed reads.
            assertEquals(paged + 1, vm.uiState.value.commentary.size)
            assertTrue(
                "the reader's oldest paid-for line is still there",
                vm.uiState.value.commentary.last().text.startsWith("page 3 "),
            )
        } finally {
            store.clear()
            unmockkObject(MatchPresenceReporter)
        }
    }
}
