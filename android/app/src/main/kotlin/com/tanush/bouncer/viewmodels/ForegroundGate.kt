package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Collects [source] only while [foreground] is true, and drops it (releasing its Realtime claim)
 * while false.
 *
 * A match-detail ViewModel outlives its backgrounded activity, so a subscription collected in
 * `viewModelScope` stayed open until the process died — Realtime bandwidth, billed from the same
 * pool as API and Storage, for a screen nobody could see.
 *
 * [catchUp] runs on every activation after the first, before [source] resumes, and emits what was
 * missed while the socket was down. Not on the first: the screen's own initial load covers it, and
 * running it there would bill the same read twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> whileForeground(
    foreground: Flow<Boolean>,
    catchUp: suspend () -> List<T> = { emptyList() },
    source: () -> Flow<T>,
): Flow<T> {
    var activations = 0
    return foreground.distinctUntilChanged().flatMapLatest { visible ->
        if (!visible) {
            emptyFlow()
        } else {
            flow {
                if (activations++ > 0) catchUp().forEach { emit(it) }
                emitAll(source())
            }
        }
    }
}

/** How close to its start an upcoming fixture begins to earn presence pings. */
internal const val PRESENCE_LEAD_MS = 15 * 60_000L

/**
 * Whether an open match screen should report a viewer now.
 *
 * A fixture days away used to ping every 90s from the moment its screen opened — an execution and
 * a Storage download each time, for a match `match-sync` does not sync until it is live. A null
 * start time keeps the old behaviour, because an unknown start must not silence a match that is
 * about to begin.
 */
internal fun shouldPingPresence(match: CricketMatch, nowMs: Long): Boolean {
    if (!match.isUpcoming) return true
    val start = match.startDateMs ?: return true
    return start - nowMs <= PRESENCE_LEAD_MS
}

/** The first feed page, from the finished-match cache when the match is over. */
internal suspend fun LiveMatchDetailSource.firstCommentaryPage(match: CricketMatch): List<CommentaryEntry>? =
    if (match.isCompleted) getFinishedCommentary(match.id) else getCommentaryForMatch(match.id)
