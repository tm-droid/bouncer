package com.tanush.bouncer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.services.MatchPresenceReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The viewer-presence heartbeat that gates server-side commentary generation.
 *
 * `bouncer-match-sync` only spends LLM calls on matches with a fresh viewer ping. When the
 * heartbeat does not run, every sync logs "0 with viewers", only the 45-minute slow tier writes a
 * line, and the detail screen falls through to rendering raw Cricbuzz text — which is exactly the
 * state production was in for five days (6 presence executions, ever).
 *
 * The cause was the gate: `if (match.isLive)`, evaluated once against a snapshot taken from the
 * matches list at construction. `5b86664` had already found and fixed the same staleness for the
 * commentary polling loop; the heartbeat kept it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailPresenceHeartbeatTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(MatchPresenceReporter)
        coEvery { MatchPresenceReporter.ping(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(MatchPresenceReporter)
        Dispatchers.resetMain()
    }

    private class SilentLiveSource : LiveMatchDetailSource {
        override suspend fun getForMatch(matchId: String): MatchLiveDetailPayload? = null
        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> = emptyList()
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double) = emptyList<CommentaryEntry>()
        override suspend fun getScorecardForMatch(matchId: String): Scorecard? = null
        override fun observeDetail(matchId: String): Flow<MatchLiveDetailPayload> = emptyFlow()
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow()
        override fun observeScorecard(matchId: String): Flow<Scorecard> = emptyFlow()
    }

    private fun match(status: String, currentStatus: String) = CricketMatch(
        id = "152507",
        seriesId = "0",
        matchTitle = "West Indies vs Pakistan, 2nd Test",
        matchType = "Test",
        status = status,
        teams = emptyList(),
        venue = "Providence",
        date = "2026-08-03",
        matchTime = "",
        score = null,
        currentStatus = currentStatus,
        info = "",
    )

    private fun repository(): MatchRepository = mockk(relaxed = true) {
        coEvery { fetchLiveCommentary(any()) } returns emptyList()
        coEvery { findCricinfoMatchUrlAndId(any()) } returns null
        coEvery { fetchScorecard(any()) } returns Scorecard(emptyList())
    }

    /**
     * Run [body] against a ViewModel that is cleared afterwards.
     *
     * The heartbeat is an unbounded loop by design — it ends when `viewModelScope` is cancelled,
     * i.e. when the user leaves the screen. A test that just constructs the ViewModel leaves that
     * loop running against the test scheduler for the rest of the JVM's life, which shows up as a
     * suite that takes minutes and a worker pegged at 100% CPU. `ViewModelStore.clear()` is the
     * public route to `onCleared()`.
     */
    private fun TestScope.withViewModel(m: CricketMatch, body: (MatchDetailViewModel) -> Unit) {
        val store = ViewModelStore()
        val vm = MatchDetailViewModel(
            match = m,
            context = mockk<Context>(relaxed = true),
            matchRepository = repository(),
            liveSource = SilentLiveSource(),
        )
        store.put("match-detail", vm)
        try {
            body(vm)
        } finally {
            store.clear()
            advanceUntilIdle()
        }
    }

    @Test
    fun `a match whose list snapshot is stale still reports a viewer`() = runTest(dispatcher) {
        // The regression case: the screen is opened just before the first ball, from a list that
        // still says "starts at". The snapshot is frozen at construction, so under the old gate
        // this match pinged exactly never — not when it was opened, and not once play began while
        // the user sat on the screen watching it. match-sync therefore never generated a line for
        // the whole session.
        val stale = match(status = "Upcoming", currentStatus = "Match starts at 19:30 local time")
        require(!stale.isLive) { "fixture must reproduce the stale snapshot" }
        require(!stale.isCompleted) { "fixture must not be terminal" }

        withViewModel(stale) {
            advanceTimeBy(1_000)
            coVerify(atLeast = 1) { MatchPresenceReporter.ping("152507") }
        }
    }

    @Test
    fun `the heartbeat repeats on its interval while the match is on`() = runTest(dispatcher) {
        withViewModel(match(status = "Live", currentStatus = "Pakistan 220/4")) {
            // Two windows: the immediate ping plus one per interval. Under-pinging lets the
            // server-side TTL lapse mid-match, silently dropping the match back to the slow tier.
            advanceTimeBy(95_000)
            coVerify(atLeast = 2) { MatchPresenceReporter.ping("152507") }
        }
    }

    @Test
    fun `a finished match never claims a viewer`() = runTest(dispatcher) {
        // The gate still has to close: pinging a finished match spends an execution per 90s and
        // asks match-sync to generate for something that will never produce another ball.
        val done = match(status = "Complete", currentStatus = "West Indies won by 6 wickets")
        require(done.isCompleted) { "fixture must be terminal" }

        withViewModel(done) {
            advanceTimeBy(200_000)
            coVerify(exactly = 0) { MatchPresenceReporter.ping(any()) }
        }
    }
}
