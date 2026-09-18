package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.ScorecardSnapshot
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which side supplies the scorecard, and why it depends on whether the match is over.
 *
 * `match-scorecard` freezes at exactly the same instant `match-live-detail` does — both are written
 * by `bouncer-match-sync`, only for a match still in the live list, and only on the detail tier. So
 * for a finished match the Appwrite row is the truncated one: the closing overs of the innings that
 * ended the match are missing from it, while Cricbuzz's own page has the completed card.
 *
 * While a match is live the order is the other way round and stays that way: the Appwrite row is
 * one billed read for first paint and every update after it arrives free over the row's realtime
 * channel, where the scrape is a page fetch per open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailScorecardSourceTest {

    private fun card(vararg teams: String) = Scorecard(
        teams.map { team ->
            Innings(
                teamName = team,
                score = "189/6",
                overs = "20.0",
                batsmen = emptyList(),
                bowlers = emptyList(),
                fallOfWickets = emptyList(),
                extras = "",
                total = "189/6",
            )
        }
    )

    private class ScorecardSource(private val scorecard: Scorecard?) : LiveMatchDetailSource {
        override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? = null
        override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> = emptyList()
        override suspend fun getOlderCommentary(matchId: String, olderThan: Double) = emptyList<CommentaryEntry>()
        override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? =
            scorecard?.let { ScorecardSnapshot(it, updatedAtMs = null) }
        override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> = emptyFlow()
        override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = emptyFlow()
        override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> = emptyFlow()
    }

    private fun repository(scorecard: Scorecard): MatchRepository = mockk(relaxed = true) {
        coEvery { fetchScorecard(any()) } returns scorecard
    }

    private val truncated = card("Southern Brave")
    private val complete = card("Southern Brave", "Birmingham Phoenix")

    @Test
    fun `a finished match takes the live scrape over the frozen row`() = runTest {
        val resolved = resolveScorecard(
            current = null,
            matchId = "169380",
            liveSource = ScorecardSource(truncated),
            repository = repository(complete),
            matchIsOver = true,
        ) {}
        assertEquals(complete, resolved)
    }

    @Test
    fun `a finished match falls back to the row when the scrape gives nothing`() = runTest {
        val resolved = resolveScorecard(
            current = null,
            matchId = "169380",
            liveSource = ScorecardSource(truncated),
            repository = repository(Scorecard(emptyList())),
            matchIsOver = true,
        ) {}
        assertEquals(truncated, resolved)
    }

    @Test
    fun `a live match still takes the row first`() = runTest {
        val resolved = resolveScorecard(
            current = null,
            matchId = "169380",
            liveSource = ScorecardSource(truncated),
            repository = repository(complete),
            matchIsOver = false,
        ) {}
        assertEquals(truncated, resolved)
    }

    @Test
    fun `a scorecard already in hand is never re-fetched`() = runTest {
        assertNull(
            resolveScorecard(
                current = complete,
                matchId = "169380",
                liveSource = ScorecardSource(truncated),
                repository = repository(truncated),
                matchIsOver = true,
            ) {}
        )
    }

    @Test
    fun `an empty scorecard is never returned from either branch`() = runTest {
        // Both callers gate on `scorecard == null`, so storing an empty one closes the retry gate
        // while the tab still renders "No scorecard available".
        for (over in listOf(true, false)) {
            assertNull(
                resolveScorecard(
                    current = null,
                    matchId = "169380",
                    liveSource = ScorecardSource(Scorecard(emptyList())),
                    repository = repository(Scorecard(emptyList())),
                    matchIsOver = over,
                ) {}
            )
        }
    }

    @Test
    fun `a scrape failure on a finished match still falls back to the row`() = runTest {
        val repo: MatchRepository = mockk(relaxed = true) {
            coEvery { fetchScorecard(any()) } throws java.io.IOException("no network")
        }
        var reported: Throwable? = null
        val resolved = resolveScorecard(
            current = null,
            matchId = "169380",
            liveSource = ScorecardSource(truncated),
            repository = repo,
            matchIsOver = true,
        ) { reported = it }
        assertEquals(truncated, resolved)
        assertEquals("no network", reported?.message)
    }
}
