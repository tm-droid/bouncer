package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.ScorecardSnapshot
import com.tanush.bouncer.data.remote.SHARED_DETAIL_TIMEOUT_MS
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.utils.firstAnswer
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.coroutines.withTimeoutOrNull

/** Keep row reads bounded at their existing deadline; only the new route gets twenty seconds. */
internal suspend fun loadInitialDetail(
    match: CricketMatch,
    source: LiveMatchDetailSource,
    repository: MatchRepository,
    apply: (LiveDetailSnapshot?) -> Unit,
) {
    val row = if (match.isCompleted) null else withTimeoutOrNull(10_000L) { source.getForMatch(match.id) }
    apply(row)
    if (row != null && row.isFresh()) return
    AppResult.runCatchingSuspend {
        withTimeoutOrNull(SHARED_DETAIL_TIMEOUT_MS) { repository.fetchSharedDetail(match.id) }
    }.onError { error ->
        sentryBreadcrumb("match_detail.shared", "Shared detail failed: ${error.message}", SentryLevel.WARNING)
    }.getOrNull()?.detailSnapshot()?.let(apply)
}

internal suspend fun fetchSharedScorecard(
    repository: MatchRepository,
    matchId: String,
    onFailure: (Throwable) -> Unit,
): ScorecardSnapshot? = AppResult.runCatchingSuspend {
    val result = withTimeoutOrNull(SHARED_DETAIL_TIMEOUT_MS) {
        val shared = repository.fetchSharedDetail(matchId)
        val snapshot = if (shared != null) shared.scorecardSnapshot()
        else repository.fetchScorecard(matchId).takeIf { it.innings.isNotEmpty() }?.let { ScorecardSnapshot(it, null) }
        Result.success(snapshot)
    } ?: throw java.net.SocketTimeoutException("Shared scorecard deadline exceeded")
    result.getOrThrow()
}.onError { onFailure(it.cause ?: it) }.getOrNull()

/** A final archive outranks a section the shared endpoint explicitly marks stale. */
internal suspend fun resolveFinishedScorecard(
    shared: ScorecardSnapshot?,
    stored: suspend () -> ScorecardSnapshot?,
    archived: suspend () -> Scorecard?,
): Scorecard? {
    if (shared?.stale == true) return firstAnswer(archived, { stored()?.scorecard }, { shared.scorecard })
    return firstAnswer({ shared?.scorecard }, { stored()?.scorecard }, archived)
}
