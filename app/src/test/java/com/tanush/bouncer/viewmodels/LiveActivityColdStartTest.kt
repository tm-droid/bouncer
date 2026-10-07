package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.CricBuzzScraper.ScrapedMatchDetails
import com.tanush.bouncer.network.MatchListUnavailableException
import com.tanush.bouncer.services.MatchCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

/**
 * The lists saved on the last poll are what a cold start paints, and a fetch that fails must
 * leave them alone.
 *
 * Both halves were broken together. The saved list was read only after a failure, and the scraper
 * reported "could not fetch" as an empty list — so nothing counted as a failure, the saved list
 * was never read, and the empty one was written over it.
 */
class LiveActivityColdStartTest {

    private val saved = listOf(match("a"), match("b"))

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking {
            MatchCacheService.clearCache()
            MatchCacheService.updateCache(saved, MatchCacheService.LIVE)
        }
    }

    @After
    fun tearDown() {
        runBlocking { MatchCacheService.clearCache() }
        Dispatchers.resetMain()
    }

    @Test
    fun `the saved list is on screen before any request has answered`() = runBlocking {
        val vm = LiveActivityViewModel(Repo { awaitCancellation() })
        try {
            val shown = withTimeout(TIMEOUT_MS) { vm.currentMatches.first { it.isNotEmpty() } }
            assertEquals(listOf("a", "b"), shown.map { it.id })
            assertFalse("a seeded list is not a loading state", vm.isLoading.value)
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `a fetch that fails keeps the saved list, on screen and in the cache`() = runBlocking {
        val vm = LiveActivityViewModel(
            Repo { throw MatchListUnavailableException("No internet connection", UnknownHostException()) },
        )
        try {
            val state = withTimeout(TIMEOUT_MS) { vm.loadingState.first { it is LoadingState.Error } }
            assertEquals("Showing cached data — couldn't reach server", (state as LoadingState.Error).message)
            assertEquals(listOf("a", "b"), vm.currentMatches.value.map { it.id })
            // The failure used to arrive as an empty list and be stored as one.
            assertEquals(listOf("a", "b"), MatchCacheService.getOfflineMatches(MatchCacheService.LIVE)?.map { it.id })
        } finally {
            vm.stopPolling()
        }
    }

    private class Repo(private val list: suspend () -> List<CricketMatch>) : MatchRepository {
        override suspend fun fetchLiveMatches(url: String, status: String): List<CricketMatch> = list()
        override suspend fun fetchMatches(url: String, status: String): AppResult<List<CricketMatch>> = unused()
        override suspend fun fetchBallByBallAndBatsmen(matchId: String): ScrapedMatchDetails = unused()
        override suspend fun fetchScorecard(matchId: String): Scorecard = unused()
        override suspend fun fetchLiveCommentary(matchUrl: String): List<String> = unused()
        override suspend fun findCricinfoMatchUrlAndId(matchTitle: String): Pair<String, String>? = unused()
        override suspend fun fetchMatchFlow(matchUrl: String): MatchFlowData = unused()
        override suspend fun fetchPreMatchData(matchUrl: String): PreMatchData? = unused()

        private fun unused(): Nothing = error("not used by the match list")
    }

    private fun match(id: String) = CricketMatch(
        id = id,
        seriesId = "",
        matchTitle = "match $id",
        matchType = "",
        status = "",
        teams = emptyList(),
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = "",
        info = "",
    )

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
