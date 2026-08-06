package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.models.WinProbabilityPoint
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the server-backed live match detail feeds consumed by
 * MatchDetailViewModel: the live snapshot, the Realtime detail/commentary
 * event streams, and the win-probability history.
 *
 * Follows the [com.tanush.bouncer.data.MatchRepository] pattern: no DI
 * framework, implementations are constructed manually and passed in via
 * default constructor params.
 */
interface LiveMatchDetailSource {

    /** Latest server-built live snapshot for a match, or null when unavailable. */
    suspend fun getForMatch(matchId: String): MatchLiveDetailPayload?

    /** Newest-first commentary lines for a match, each with the server's event verdict. */
    suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry>

    /**
     * The full scorecard, from its own table. Separate from [getForMatch] because it was 85-93% of
     * the detail payload and rode that row's realtime channel to every subscriber once a minute.
     * This is the first-paint read; [observeScorecard] carries every update after it.
     */
    suspend fun getScorecardForMatch(matchId: String): com.tanush.bouncer.models.Scorecard?

    /** Realtime scorecard updates for ONE match, subscribed while collected. */
    fun observeScorecard(matchId: String): Flow<com.tanush.bouncer.models.Scorecard>

    /**
     * Realtime live-detail snapshots for ONE match, subscribed for as long as the flow is
     * collected. Previously a process-wide stream of every match that callers filtered — which
     * meant every device received every watched match's payload whether or not it was on screen.
     */
    fun observeDetail(matchId: String): Flow<MatchLiveDetailPayload>

    /** Realtime new commentary lines for ONE match, subscribed while collected. */
    fun observeCommentary(matchId: String): Flow<CommentaryEvent>

    /** Win-probability history points for the swing chart. */
    suspend fun getProbabilityHistory(matchId: String): AppResult<List<WinProbabilityPoint>>
}

/** Production wiring: Appwrite Realtime clients + CricketRepository. */
object ProductionLiveDetailSource : LiveMatchDetailSource {

    override suspend fun getForMatch(matchId: String): MatchLiveDetailPayload? =
        AppwriteMatchDetailClient.getForMatch(matchId)

    override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> =
        AppwriteMatchCommentaryClient.getForMatch(matchId)

    override suspend fun getScorecardForMatch(matchId: String): com.tanush.bouncer.models.Scorecard? =
        AppwriteMatchScorecardClient.getForMatch(matchId)

    override fun observeScorecard(matchId: String): Flow<com.tanush.bouncer.models.Scorecard> =
        AppwriteMatchScorecardClient.observeMatch(matchId)

    override fun observeDetail(matchId: String): Flow<MatchLiveDetailPayload> =
        AppwriteMatchDetailClient.observeMatch(matchId)

    override fun observeCommentary(matchId: String): Flow<CommentaryEvent> =
        AppwriteMatchCommentaryClient.observeCommentary(matchId)

    override suspend fun getProbabilityHistory(matchId: String): AppResult<List<WinProbabilityPoint>> =
        CricketRepository.getProbabilityHistory(matchId)
}
