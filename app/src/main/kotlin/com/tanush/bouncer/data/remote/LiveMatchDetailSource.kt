package com.tanush.bouncer.data.remote

import com.tanush.bouncer.utils.BoundedLruCache
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the server-backed live match detail feeds consumed by
 * MatchDetailViewModel: the live snapshot, the scorecard, and the Realtime
 * detail/commentary event streams.
 *
 * Follows the [com.tanush.bouncer.data.MatchRepository] pattern: no DI
 * framework, implementations are constructed manually and passed in via
 * default constructor params.
 */
interface LiveMatchDetailSource {

    /**
     * Latest server-built live snapshot for a match, or null when unavailable.
     *
     * Carries the row's `updated_at`: the row stops being written the moment its match leaves the
     * live list, so a caller merging it into list-derived state needs to know how old it is.
     */
    suspend fun getForMatch(matchId: String): LiveDetailSnapshot?

    /**
     * Newest-first commentary lines for a match, each with the server's event verdict. Null when
     * the store could not be read; empty when it was read and holds nothing for this match.
     */
    suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry>?

    /**
     * [getCommentaryForMatch] for a match that is over, whose feed will never gain another row, so
     * a source may answer a re-open from memory instead of billing the first page again.
     */
    suspend fun getFinishedCommentary(matchId: String): List<CommentaryEntry>? = getCommentaryForMatch(matchId)

    /**
     * The page of commentary strictly older than [olderThan], newest-first, or empty when there is
     * none. Fetched only when a reader asks for it — reads bill per row.
     */
    suspend fun getOlderCommentary(matchId: String, olderThan: Double): List<CommentaryEntry>

    /**
     * Commentary strictly newer than [newerThan], newest-first — what a feed missed while its
     * socket was down. Empty by default, so a source without it simply does not catch up.
     */
    suspend fun getNewerCommentary(matchId: String, newerThan: Double): List<CommentaryEntry> = emptyList()

    /**
     * One page of the match's lines whose server `event` is one of [events], newest-first, strictly
     * older than [olderThan] when given. Null when it could not be read — the default, so a source
     * without it leaves the filters on the loaded feed.
     */
    suspend fun getEventCommentary(
        matchId: String,
        events: List<String>,
        olderThan: Double?,
    ): List<CommentaryEntry>? = null

    /**
     * The full scorecard, from its own table. Separate from [getForMatch] because it was 85-93% of
     * the detail payload and rode that row's realtime channel to every subscriber once a minute.
     * This is the first-paint read; [observeScorecard] carries every update after it.
     */
    suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot?

    /** Realtime scorecard updates for ONE match, subscribed while collected. */
    fun observeScorecard(matchId: String): Flow<ScorecardSnapshot>

    /**
     * Realtime live-detail snapshots for ONE match, subscribed for as long as the flow is
     * collected. Previously a process-wide stream of every match that callers filtered — which
     * meant every device received every watched match's payload whether or not it was on screen.
     */
    fun observeDetail(matchId: String): Flow<LiveDetailSnapshot>

    /** Realtime new commentary lines for ONE match, subscribed while collected. */
    fun observeCommentary(matchId: String): Flow<CommentaryEvent>
}

private const val FINISHED_FEED_CACHE_SIZE = 8

/** Production wiring: Appwrite Realtime clients + CricketRepository. */
object ProductionLiveDetailSource : LiveMatchDetailSource {

    override suspend fun getForMatch(matchId: String): LiveDetailSnapshot? =
        AppwriteMatchDetailClient.getForMatch(matchId)

    override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry>? =
        AppwriteMatchCommentaryClient.getForMatch(matchId)

    override suspend fun getOlderCommentary(matchId: String, olderThan: Double): List<CommentaryEntry> =
        AppwriteMatchCommentaryClient.getOlderThan(matchId, olderThan)

    // First pages of finished matches. Reopening one billed its 48 rows every time, for a feed that
    // cannot change. Small: the realistic re-open set is the handful of results on the Recent tab.
    private val finishedFeeds = BoundedLruCache<String, List<CommentaryEntry>>(FINISHED_FEED_CACHE_SIZE)

    override suspend fun getFinishedCommentary(matchId: String): List<CommentaryEntry>? =
        synchronized(finishedFeeds) { finishedFeeds[matchId] }
            ?: AppwriteMatchCommentaryClient.getForMatch(matchId)?.also { lines ->
                // An empty answer is not cached: the rows may simply not have landed yet.
                if (lines.isNotEmpty()) synchronized(finishedFeeds) { finishedFeeds[matchId] = lines }
            }

    override suspend fun getNewerCommentary(matchId: String, newerThan: Double): List<CommentaryEntry> =
        AppwriteMatchCommentaryClient.getNewerThan(matchId, newerThan)

    override suspend fun getEventCommentary(
        matchId: String,
        events: List<String>,
        olderThan: Double?,
    ): List<CommentaryEntry>? = AppwriteMatchCommentaryClient.getEvents(matchId, events, olderThan)

    override suspend fun getScorecardForMatch(matchId: String): ScorecardSnapshot? =
        AppwriteMatchScorecardClient.getForMatch(matchId)

    override fun observeScorecard(matchId: String): Flow<ScorecardSnapshot> =
        AppwriteMatchScorecardClient.observeMatch(matchId)

    override fun observeDetail(matchId: String): Flow<LiveDetailSnapshot> =
        AppwriteMatchDetailClient.observeMatch(matchId)

    override fun observeCommentary(matchId: String): Flow<CommentaryEvent> =
        AppwriteMatchCommentaryClient.observeCommentary(matchId)
}
