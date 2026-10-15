package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModelStore
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.history.PastMatchSource
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.ScorecardSnapshot
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? {
            detailReads++
            // `updatedAtMs = null` on purpose: unknown age must behave exactly as this test did
            // before the column was read at all, so these cases still pin the status-gated rule
            // rather than the freshness one.
            return oneShot?.let { LiveDetailSnapshot(it, updatedAtMs = null) }
        }

        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> = emptyList()
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double) = emptyList<CommentaryEntry>()
        override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? = null
        override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> {
            detailSubscriptions++
            return stream.map { LiveDetailSnapshot(it, updatedAtMs = null) }
        }
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow()
        override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> = emptyFlow()
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
    /** A live match as the list has it, and the same fixture once the result is in. */
    private val live = match(status = "Live", currentStatus = "Southern Brave need 40 runs in 18 balls")
    private val finished = match(status = "Complete", currentStatus = "Southern Brave won by 39 runs")

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

    /**
     * Drains what is ready, and deliberately does NOT advance virtual time.
     *
     * The presence heartbeat is an unbounded `while (…) { ping(); delay(90s) }`, so
     * `advanceUntilIdle()` asks the scheduler to run a loop that never ends. It used to return
     * anyway — the loop's `WinProbabilityStore.refresh()` hops to `Dispatchers.IO`, which the test
     * scheduler stops tracking — which made every live-match case here a race against wall-clock
     * load, and under a full suite run it lost: `UncompletedCoroutinesError: After waiting for 1m,
     * the test body did not run to completion`, attributed to whichever case ran first.
     *
     * `runCurrent()` completes the initial load, which is all these assertions are about, and
     * leaves the heartbeat parked on its first `delay`.
     */
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
            matchRepository = repository(),
            liveSource = source,
            // Never GitHub Pages from a unit test; the archive and Cricsheet have their own tests.
            pastSource = PastMatchSource.None,
        )
        store.put("match-detail", vm)
        try {
            body(vm)
        } finally {
            store.clear()
            runCurrent()
        }
    }

    // ── The reported defect ───────────────────────────────────────────────────

    @Test
    fun `a finished match keeps the list score rather than the frozen row`() = runTest(dispatcher) {
        val done = match(status = "Complete", currentStatus = "Southern Brave won by 39 runs")
        require(done.isCompleted) { "fixture must be terminal" }
        val source = RecordingLiveSource(oneShot = frozen, stream = flowOf(frozen))

        withViewModel(done, source) { vm ->
            runCurrent()
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
            runCurrent()
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
            runCurrent()
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
            runCurrent()
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
            runCurrent()
            val st = vm.uiState.value
            assertEquals("150", st.liveTeam2Score)
            assertEquals("18.2", st.liveTeam2Overs)
            assertEquals("Southern Brave won by 39 runs", st.liveStatus)
        }
    }

    // ── The merge itself ──────────────────────────────────────────────────────

    // ── The same defect pointed the other way: a LIVE match the server is not writing ──────
    //
    // `match-sync` writes a detail row only for a match inside `MAX_MATCHES = 12` of
    // `live-matches.json`. The state is seeded from the list at construction, so it is never blank
    // and MatchDetailViewV2's `ifBlank` fallback to the list can never fire — a match past that cap
    // sat on its open-time score for as long as the screen was open while the list advanced behind
    // it. These pin the rule that lets the list take over.

    @Test
    fun `before any payload arrives the list is the only source`() {
        assertTrue(listLeadsDetail(live, hasAppliedDetail = false, detailUpdatedAtMs = null))
    }

    @Test
    fun `a row still being written outranks the list`() {
        val now = 1_000_000_000L
        assertFalse(
            listLeadsDetail(live, hasAppliedDetail = true, detailUpdatedAtMs = now - 60_000L, nowMs = now)
        )
    }

    @Test
    fun `a row that stopped being written loses to the list`() {
        val now = 1_000_000_000L
        assertTrue(
            listLeadsDetail(
                live,
                hasAppliedDetail = true,
                detailUpdatedAtMs = now - DETAIL_STALE_MS - 1,
                nowMs = now,
            )
        )
    }

    @Test
    fun `a row with no readable age is never called stale`() {
        // Unknown is not old. A malformed `updated_at` must leave the screen exactly as it behaved
        // before the column was read at all, not blank a live score.
        assertFalse(listLeadsDetail(live, hasAppliedDetail = true, detailUpdatedAtMs = null))
    }

    @Test
    fun `a list entry that has gone terminal always wins`() {
        val now = 1_000_000_000L
        assertTrue(
            listLeadsDetail(finished, hasAppliedDetail = true, detailUpdatedAtMs = now, nowMs = now)
        )
    }

    @Test
    fun `re-seeding takes the list's figures and leaves the payload-only ones`() {
        val st = MatchDetailViewModel.UiState(
            liveTeam1Score = "120/4",
            liveTeam2Score = "301",
            liveStatus = "Day 2: Session 2",
            bowler = ScrapedBowler("Cummins", overs = "12.0", maidens = "2", runs = "45", wickets = "2", economy = "3.75"),
            recentBalls = listOf("1", "0", "4"),
        )
        val seeded = st.seededFrom(live)

        assertEquals(live.teams[0].score, seeded.liveTeam1Score)
        assertEquals(live.teams[1].score, seeded.liveTeam2Score)
        assertEquals(live.currentStatus, seeded.liveStatus)
        // The crease belongs to the payload alone. The list says nothing about it, and saying
        // nothing must not be read as saying "empty".
        assertEquals(st.bowler, seeded.bowler)
        assertEquals(st.recentBalls, seeded.recentBalls)
    }

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
