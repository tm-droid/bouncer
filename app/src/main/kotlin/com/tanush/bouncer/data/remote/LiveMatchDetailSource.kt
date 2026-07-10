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

    /** Newest-first commentary lines for a match. */
    suspend fun getCommentaryForMatch(matchId: String): List<String>

    /** Realtime stream of live-detail snapshots (all matches; callers filter). */
    val detailEvents: Flow<DetailEvent>

    /** Realtime stream of new commentary lines (all matches; callers filter). */
    val commentaryEvents: Flow<CommentaryEvent>

    /** Win-probability history points for the swing chart. */
    suspend fun getProbabilityHistory(matchId: String): AppResult<List<WinProbabilityPoint>>
}

/** Production wiring: Appwrite Realtime clients + CricketRepository. */
object ProductionLiveDetailSource : LiveMatchDetailSource {

    override suspend fun getForMatch(matchId: String): MatchLiveDetailPayload? =
        AppwriteMatchDetailClient.getForMatch(matchId)

    override suspend fun getCommentaryForMatch(matchId: String): List<String> =
        AppwriteMatchCommentaryClient.getForMatch(matchId)

    override val detailEvents: Flow<DetailEvent>
        get() = AppwriteMatchDetailClient.events

    override val commentaryEvents: Flow<CommentaryEvent>
        get() = AppwriteMatchCommentaryClient.events

    override suspend fun getProbabilityHistory(matchId: String): AppResult<List<WinProbabilityPoint>> =
        CricketRepository.getProbabilityHistory(matchId)
}
