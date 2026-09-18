package com.tanush.bouncer.viewmodels

import android.content.Context
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.ScorecardSnapshot
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The commentary tab's on-device fallback.
 *
 * `bouncer-match-sync` writes commentary only for a live match with a viewer heartbeat, and a
 * finished match never returns to the live list — so a match nobody watched has no Appwrite rows
 * and no way to gain any. That was both Hundred games of 2026-07-30: scorecard and win probability
 * rendered, commentary tab blank.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailCommentaryFallbackTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Appwrite side with nothing in it — the state every unwatched match ends up in. */
    private class EmptyLiveSource(private val lines: List<String> = emptyList()) : LiveMatchDetailSource {
        override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? = null
        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> =
            lines.map { CommentaryEntry(it, event = null) }
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double) = emptyList<CommentaryEntry>()
        override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? = null
        override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> = emptyFlow()
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow()
        override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> = emptyFlow()
    }

    // A completed Hundred match: terminal, so no presence heartbeat starts (see
    // MatchDetailPresenceHeartbeatTest — the gate is "not over", not "isLive").
    private val hundredMatch = CricketMatch(
        id = "144783",
        seriesId = "0",
        matchTitle = "Southern Brave vs Birmingham Phoenix, 13th Match",
        matchType = "The Hundred",
        status = "Complete",
        teams = emptyList(),
        venue = "The Rose Bowl",
        date = "2026-07-30",
        matchTime = "",
        score = null,
        currentStatus = "Southern Brave won by 1 wicket",
        info = "",
    )

    private val scrapedLines = listOf(
        "Ball 100: Luke Wood to Sean Dickson, 1 run, that's the game for Southern Brave!",
        "Ball 99: Luke Wood to Sean Dickson, no run, a full-toss on middle and off.",
    )

    private fun repository(commentary: List<String>): MatchRepository = mockk(relaxed = true) {
        coEvery { fetchLiveCommentary(any()) } returns commentary
        coEvery { findCricinfoMatchUrlAndId(any()) } returns null
        coEvery { fetchScorecard(any()) } returns Scorecard(emptyList())
    }

    private fun viewModel(source: LiveMatchDetailSource, repo: MatchRepository) =
        MatchDetailViewModel(
            match = hundredMatch,
            context = mockk<Context>(relaxed = true),
            matchRepository = repo,
            liveSource = source,
        )

    @Test
    fun `an empty appwrite feed falls back to on-device scraping`() = runTest(dispatcher) {
        val vm = viewModel(EmptyLiveSource(), repository(scrapedLines))
        advanceUntilIdle()

        assertEquals(scrapedLines, vm.uiState.value.commentary.map { it.text })
        assertNull("a successful fallback is not an error", vm.uiState.value.commentaryError)
        // The Hundred's label must survive the fallback intact — this is the whole point of it.
        assertTrue(vm.uiState.value.commentary.first().text.startsWith("Ball 100: "))
        // A scraped line has no server verdict; the tab classifies it from Cricbuzz's own wording.
        assertNull(vm.uiState.value.commentary.first().event)
    }

    @Test
    fun `a populated appwrite feed is not overwritten by the scraper`() = runTest(dispatcher) {
        val fromServer = listOf("Ball 100: the AI-rewritten line that match-sync already wrote.")
        val repo = repository(listOf("Ball 100: raw scrape that must not win."))
        val vm = viewModel(EmptyLiveSource(fromServer), repo)
        advanceUntilIdle()

        assertEquals(fromServer, vm.uiState.value.commentary.map { it.text })
    }

    @Test
    fun `an empty scrape leaves the tab empty rather than claiming a connection failure`() =
        runTest(dispatcher) {
            // Cricbuzz serves only the newest slice of commentary; an old enough match returns
            // nothing. "No more commentary available" is honest — "check your connection" is not.
            val vm = viewModel(EmptyLiveSource(), repository(emptyList()))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.commentary.isEmpty())
            assertNull(vm.uiState.value.commentaryError)
        }

    @Test
    fun `a scrape failure is swallowed, not surfaced as a commentary error`() = runTest(dispatcher) {
        val repo: MatchRepository = mockk(relaxed = true) {
            coEvery { fetchLiveCommentary(any()) } throws java.io.IOException("no network")
            coEvery { findCricinfoMatchUrlAndId(any()) } returns null
            coEvery { fetchScorecard(any()) } returns Scorecard(emptyList())
        }
        val vm = viewModel(EmptyLiveSource(), repo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commentary.isEmpty())
        assertNull(vm.uiState.value.commentaryError)
    }
}
