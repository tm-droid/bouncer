package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.history.HistoryMatch
import com.tanush.bouncer.data.history.PastFeedSource
import com.tanush.bouncer.data.history.PastMatchRow
import com.tanush.bouncer.data.history.PastMatchSource
import com.tanush.bouncer.data.history.PastSource
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.H2HRepository
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.ScorecardSnapshot
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.MatchSquadsSource
import com.tanush.bouncer.services.MatchPresenceReporter
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * A `cs-` match exists only on the Cricsheet site. Opening it must read that one file and nothing
 * else: no Appwrite row, no Realtime channel, no presence ping, no Cricbuzz or Cricinfo page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailPastMatchTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(MatchPresenceReporter, MatchSquadsSource, H2HRepository, WinProbabilityStore)
    }

    @After
    fun tearDown() {
        unmockkObject(MatchPresenceReporter, MatchSquadsSource, H2HRepository, WinProbabilityStore)
        Dispatchers.resetMain()
    }

    private class CountingLiveSource : LiveMatchDetailSource {
        var calls = 0
        override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? = null.also { calls++ }
        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry>? = null.also { calls++ }
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double) = emptyList<CommentaryEntry>().also { calls++ }
        override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? = null.also { calls++ }
        override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> = emptyFlow<LiveDetailSnapshot>().also { calls++ }
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow<CommentaryEvent>().also { calls++ }
        override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> = emptyFlow<ScorecardSnapshot>().also { calls++ }
    }

    private val history: HistoryMatch = AppJson.decodeFromString(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("history_433606_trimmed.json")).bufferedReader().use { it.readText() },
    )

    private class Past(private val match: HistoryMatch?) : PastMatchSource {
        val asked = mutableListOf<String>()
        override suspend fun feed(match: com.tanush.bouncer.models.CricketMatch) = null.also { asked += "feed" }
        override suspend fun scorecard(match: com.tanush.bouncer.models.CricketMatch): Scorecard? = null.also { asked += "scorecard" }
        override suspend fun cricsheetMatch(id: String): HistoryMatch? = match.also { asked += id }
    }

    private val row = PastMatchRow(
        id = "433606", date = LocalDate.of(2011, 4, 2), format = "ODI", women = false, team1 = "India",
        team2 = "Sri Lanka", winner = 0, result = null, event = "ICC Cricket World Cup", venue = "Wankhede Stadium",
        source = PastSource.CRICSHEET,
    )

    @Test
    fun `a cs match fills the screen from one Cricsheet read and calls nothing live`() = runTest(dispatcher) {
        val live = CountingLiveSource()
        val repository: MatchRepository = mockk(relaxed = true)
        val past = Past(history)
        val vm = MatchDetailViewModel(row.toCricketMatch(), repository, live, past)
        advanceUntilIdle()

        assertEquals(listOf("433606"), past.asked)
        assertEquals("a live source was touched", 0, live.calls)
        verify { repository wasNot Called }
        coVerify(exactly = 0) { MatchPresenceReporter.ping(any()) }
        coVerify(exactly = 0) { MatchSquadsSource.get(any(), any()) }
        coVerify(exactly = 0) { H2HRepository.getH2H(any(), any()) }
        coVerify(exactly = 0) { WinProbabilityStore.refresh(any()) }

        val ui = vm.uiState.value
        assertEquals(listOf("Sri Lanka", "India"), ui.scorecard?.innings?.map { it.teamName })
        assertEquals("India won by 6 wickets", ui.liveStatus)
        assertEquals("277/4", ui.liveTeam1Score)
        assertEquals("48.2", ui.liveTeam1Overs)
        assertEquals("274/6", ui.liveTeam2Score)
        assertEquals(PastFeedSource.CRICSHEET, ui.pastFeedSource)
        assertTrue(ui.commentary.isNotEmpty())
        assertEquals(false, ui.hasOlderCommentary)
        assertNull(ui.commentaryError)
        assertNull(ui.loadError)
        assertEquals(false, ui.commentaryLoading)
        assertEquals(false, ui.scorecardLoading)
    }

    @Test
    fun `an unreachable Cricsheet file leaves the screen empty and still touches nothing live`() = runTest(dispatcher) {
        val live = CountingLiveSource()
        val repository: MatchRepository = mockk(relaxed = true)
        val vm = MatchDetailViewModel(row.toCricketMatch(), repository, live, Past(null))
        advanceUntilIdle()

        assertEquals(0, live.calls)
        verify { repository wasNot Called }
        assertNotNull(vm.uiState.value.loadError)
        assertNull(vm.uiState.value.scorecard)
    }
}
