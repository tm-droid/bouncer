package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.models.CricketMatch

/**
 * When the matches list outranks the `match-live-detail` row, and what it replaces.
 *
 * Its own file, and top-level rather than methods: `MatchDetailViewModel` is at detekt's
 * TooManyFunctions threshold, and these are pure functions of their arguments anyway — which is
 * also what lets `MatchDetailStaleDetailTest` pin the rule directly instead of through a ViewModel.
 */

/**
 * How old a `match-live-detail` row may be before the matches list is treated as the better source.
 *
 * This screen pings `bouncer-match-presence` every 90 seconds, which earns the match a detail-tier
 * pass on every one-minute `match-sync` run, so a row the server is still writing is at most about
 * a minute old. Three minutes is two missed passes: past it the server is not writing this match —
 * it fell outside `MAX_MATCHES`, or the presence file could not be read — and the list is the only
 * live source left. The margin is deliberately generous: re-seeding early costs nothing (both
 * sources agree while both are fresh) and re-seeding late is the bug.
 */
internal const val DETAIL_STALE_MS = 180_000L

/**
 * Whether the matches list should overwrite the live fields.
 *
 * Three cases, and only three. Before any payload has been applied the list is the only thing the
 * screen has, so it always wins. A list entry that has gone terminal always wins, because nothing
 * will write that row again — that is the `eb91dcd` rule, applied at the moment the result lands
 * rather than at the next screen open. Otherwise the list wins only when the row is demonstrably
 * stale, and a row with no readable age is never called stale: unknown is not old.
 */
internal fun listLeadsDetail(
    updated: CricketMatch,
    hasAppliedDetail: Boolean,
    detailUpdatedAtMs: Long?,
    nowMs: Long = System.currentTimeMillis(),
): Boolean = when {
    !hasAppliedDetail -> true
    updated.isCompleted -> true
    detailUpdatedAtMs == null -> false
    else -> nowMs - detailUpdatedAtMs > DETAIL_STALE_MS
}

/**
 * Re-seed the live fields from a fresher matches-list entry.
 *
 * The mirror image of `UiState.merge`: there a frozen row must not overwrite the list, here the
 * list must be able to overwrite a state the server stopped refreshing. Only the fields the list
 * actually carries are touched — the batting pair, the bowler, the recent balls and the scorecard
 * exist only in the detail payload, and blanking them because the list has nothing to say about
 * them would be the same class of bug pointed the other way.
 *
 * Each field falls back to its current value rather than to blank, for the same reason.
 */
internal fun MatchDetailViewModel.UiState.seededFrom(m: CricketMatch): MatchDetailViewModel.UiState {
    val first = m.teams.getOrNull(0)
    val second = m.teams.getOrNull(1)
    return copy(
        liveTeam1Score = firstNonBlank(first?.score, liveTeam1Score),
        liveTeam2Score = firstNonBlank(second?.score, liveTeam2Score),
        liveTeam1Overs = firstNonBlank(first?.overs, liveTeam1Overs),
        liveTeam2Overs = firstNonBlank(second?.overs, liveTeam2Overs),
        liveCurrentRunRate = m.score?.currentRunRate ?: liveCurrentRunRate,
        liveRequiredRunRate = m.score?.requiredRunRate ?: liveRequiredRunRate,
        liveStatus = firstNonBlank(m.currentStatus, liveStatus.orEmpty()).ifBlank { null },
        liveWinProbability = m.winProbability ?: liveWinProbability,
        liveDrawProbability = m.winProbabilityDraw ?: liveDrawProbability,
        // The flag travels with the number it describes. Keeping the old flag beside a new
        // probability would label a live reading as a pre-match prior, or the reverse.
        liveWinProbabilityIsPrematch =
            if (m.winProbability != null) m.winProbabilityIsPrematch else liveWinProbabilityIsPrematch,
    )
}

/** [candidate] when it says something, else [fallback]. Blank is "no value", never a value. */
private fun firstNonBlank(candidate: String?, fallback: String): String =
    candidate?.takeIf { it.isNotBlank() } ?: fallback
