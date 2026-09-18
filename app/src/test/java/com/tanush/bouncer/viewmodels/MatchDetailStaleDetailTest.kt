package com.tanush.bouncer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.services.MatchPresenceReporter
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A finished match reads its score from the matches list, never from `match-live-detail`.
 *
 * The row is written only while a match sits in `live-matches.json`, and only on the detail tier —
 * once per `SLOW_TIER_INTERVAL_MS` (15 minutes) for a match nobody has open. When the match ends it
 * leaves that list and **nothing writes, finalises or prunes the row again**, so the last write can
 * be a quarter of an hour before the final ball and stays that way for good. The payload carries no
 * timestamp and the client never reads `updated_at`, so staleness is not detectable field by field.
 *
 * Meanwhile the Home list re-scrapes Cricbuzz `recent-matches` every 60s and has the real result.
 * That was the reported defect: the Recent row and the detail header of the same match disagreed,
 * and the detail header — which prefers the payload through `pick(new ?: old)` — was the wrong one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailStaleDetailTest {

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

    private class RecordingLiveSource(
        private val oneShot: MatchLiveDetailPayload? = null,
        private val stream: Flow<MatchLiveDetailPayload> = emptyFlow(),
    ) : LiveMatchDetailSource {
        var detailReads = 0
        var detailSubscriptions = 0

        override suspend fun getForMatch(matchId: String): MatchLiveDetailPayload? {
            detailReads++
            return oneShot
        }

        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> = emptyList()
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double) = emptyList<CommentaryEntry>()
        override suspend fun getScorecardForMatch(matchId: String): Scorecard? = null
        override fun observeDetail(matchId: String): Flow<MatchLiveDetailPayload> {
            detailSubscriptions++
            return stream
        }
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow()
        override fun observeScorecard(matchId: String): Flow<Scorecard> = emptyFlow()
    }

    private fun team(name: String, score: String, overs: String, id: String) =
        CricketMatch.Team(
            name = name,
            score = score,
            status = "",
            isBatting = false,
            logoUrl = null,
            acronym = name.take(3).uppercase(),
            overs = overs,
            teamId = id,
        )

    private fun match(status: String, currentStatus: String) = CricketMatch(
        id = "169380",
        seriesId = "0",
        matchTitle = "Southern Brave vs Birmingham Phoenix, 13th Match",
        matchType = "T20",
        status = status,
        teams = listOf(
            team("Southern Brave", "189/6", "20.0", "1"),
            team("Birmingham Phoenix", "150", "18.2", "2"),
        ),
        venue = "The Rose Bowl",
        date = "2026-09-17",
        matchTime = "",
        score = null,
        currentStatus = currentStatus,
        info = "",
    )

    /** The row as match-sync last left it: mid-chase, fifteen minutes before the end. */
    private val frozen = MatchLiveDetailPayload(
        batsmen = listOf(
            ScrapedBatsman(
                name = "Sean Dickson",
                runs = "31",
                balls = "20",
                fours = "3",
                sixes = "1",
                strikeRate = "155.0",
            )
        ),
        bowler = ScrapedBowler(
            name = "Craig Overton",
            overs = "2.4",
            maidens = "0",
            runs = "19",
            wickets = "1",
            economy = "7.12",
        ),
        recentBalls = listOf("1", "0", "4"),
        team1Score = "189/6",
        team1Overs = "20.0",
        team2Score = "94/3",
        team2Overs = "12.4",
        currentRunRate = "7.42",
        requiredRunRate = "13.02",
        status = "Birmingham Phoenix need 96 runs in 44 balls",
    )

    private fun repository(): MatchRepository = mockk(relaxed = true) {
        coEvery { fetchLiveCommentary(any()) } returns emptyList()
        coEvery { findCricinfoMatchUrlAndId(any()) } returns null
        coEvery { fetchScorecard(any()) } returns Scorecard(emptyList())
    }

    private fun TestScope.withViewModel(
        m: CricketMatch,
        source: LiveMatchDetailSource,
        body: (MatchDetailViewModel) -> Unit,
    ) {
        // The presence heartbeat is an unbounded loop; clearing the store is the public route to
        // onCleared(). See MatchDetailPresenceHeartbeatTest for why leaving it running is costly.
        val store = ViewModelStore()
        val vm = MatchDetailViewModel(
            match = m,
            context = mockk<Context>(relaxed = true),
            matchRepository = repository(),
            liveSource = source,
        )
        store.put("match-detail", vm)
        try {
            body(vm)
        } finally {
            store.clear()
            advanceUntilIdle()
        }
    }

    // ── The reported defect ───────────────────────────────────────────────────

    @Test
    fun `a finished match keeps the list score rather than the frozen row`() = runTest(dispatcher) {
        val done = match(status = "Complete", currentStatus = "Southern Brave won by 39 runs")
        require(done.isCompleted) { "fixture must be terminal" }
        val source = RecordingLiveSource(oneShot = frozen, stream = flowOf(frozen))

        withViewModel(done, source) { vm ->
            advanceUntilIdle()
            val st = vm.uiState.value
            assertEquals("189/6", st.liveTeam1Score)
            assertEquals("150", st.liveTeam2Score)
            assertEquals("18.2", st.liveTeam2Overs)
            assertEquals("Southern Brave won by 39 runs", st.liveStatus)
        }
    }

    @Test
    fun `a finished match shows no live batting pair, bowler, balls or rates`() = runTest(dispatcher) {
        val done = match(status = "Complete", currentStatus = "Southern Brave won by 39 runs")
        val source = RecordingLiveSource(oneShot = frozen, stream = flowOf(frozen))

        withViewModel(done, source) { vm ->
            advanceUntilIdle()
            val st = vm.uiState.value
            assertTrue("a dismissed pair is not the state of a finished match", st.batsmen.isEmpty())
            assertNull(st.bowler)
            assertTrue(st.recentBalls.isEmpty())
            assertNull(st.liveCurrentRunRate)
            assertNull(st.liveRequiredRunRate)
        }
    }

    @Test
    fun `a finished match pays no read and opens no subscription for a dead row`() = runTest(dispatcher) {
        // A billed DB read and a realtime claim (bandwidth, the shared pool a 402 takes out) for a
        // row that nothing will ever write again.
        val done = match(status = "Complete", currentStatus = "Southern Brave won by 39 runs")
        val source = RecordingLiveSource(oneShot = frozen, stream = flowOf(frozen))

        withViewModel(done, source) { _ ->
            advanceUntilIdle()
            assertEquals(0, source.detailReads)
            assertEquals(0, source.detailSubscriptions)
        }
    }

    // ── The case the gate must not break ──────────────────────────────────────

    @Test
    fun `a live match still takes its score from the payload`() = runTest(dispatcher) {
        val live = match(status = "Live", currentStatus = "Birmingham Phoenix need 96 runs in 44 balls")
        require(!live.isCompleted) { "fixture must not be terminal" }

        withViewModel(live, RecordingLiveSource(oneShot = frozen)) { vm ->
            advanceUntilIdle()
            val st = vm.uiState.value
            assertEquals("94/3", st.liveTeam2Score)
            assertEquals("12.4", st.liveTeam2Overs)
            assertEquals("13.02", st.liveRequiredRunRate)
            assertEquals(1, st.batsmen.size)
        }
    }

    @Test
    fun `the payload that reports the result lands, and the one after it does not`() = runTest(dispatcher) {
        // Over-ness is judged from the state BEFORE each payload, so the update that finishes the
        // match still applies. Anything after it is the same frozen row being re-read.
        val finalPayload = frozen.copy(
            team2Score = "150",
            team2Overs = "18.2",
            status = "Southern Brave won by 39 runs",
        )
        val live = match(status = "Live", currentStatus = "Birmingham Phoenix need 96 runs in 44 balls")

        val source = RecordingLiveSource(stream = flowOf(finalPayload, frozen))
        withViewModel(live, source) { vm ->
            advanceUntilIdle()
            val st = vm.uiState.value
            assertEquals("150", st.liveTeam2Score)
            assertEquals("18.2", st.liveTeam2Overs)
            assertEquals("Southern Brave won by 39 runs", st.liveStatus)
        }
    }

    // ── The merge itself ──────────────────────────────────────────────────────

    @Test
    fun `merge over a finished match changes nothing`() {
        val st = MatchDetailViewModel.UiState(
            liveTeam1Score = "189/6",
            liveTeam2Score = "150",
            liveStatus = "Southern Brave won by 39 runs",
        )
        assertSame(st, st.merge(frozen, matchIsOver = true))
    }

    @Test
    fun `merge over a finished match still fills a scorecard the state lacks`() {
        // Rows written before the scorecard moved to its own table still carry one, and an empty
        // Scorecard tab is worse than an old one when there is nothing else to show.
        val card = Scorecard(
            listOf(
                com.tanush.bouncer.models.Innings(
                    teamName = "Southern Brave",
                    score = "189/6",
                    overs = "20.0",
                    batsmen = emptyList(),
                    bowlers = emptyList(),
                    fallOfWickets = emptyList(),
                    extras = "",
                    total = "189/6",
                )
            )
        )
        val merged = MatchDetailViewModel.UiState(liveTeam1Score = "189/6")
            .merge(frozen.copy(scorecard = card), matchIsOver = true)
        assertEquals(card, merged.scorecard)
        // ...and nothing else moved.
        assertEquals("189/6", merged.liveTeam1Score)
        assertEquals("", merged.liveTeam2Score)
    }

    @Test
    fun `merge over a live match applies the payload`() {
        val merged = MatchDetailViewModel.UiState(liveTeam1Score = "189/6", liveTeam2Score = "40/1")
            .merge(frozen, matchIsOver = false)
        assertEquals("94/3", merged.liveTeam2Score)
        assertEquals("Birmingham Phoenix need 96 runs in 44 balls", merged.liveStatus)
    }
}
