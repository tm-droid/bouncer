package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.COMMENTARY_EVENT_PAGE_LIMIT
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.utils.bouncerExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A commentary filter the server can answer by itself, and the `event` values it stands for. */
enum class CommentaryEventKind(val events: List<String>) {
    WICKETS(listOf("WICKET")),
    BOUNDARIES(listOf("FOUR", "SIX")),
}

/**
 * What has been fetched for one [CommentaryEventKind].
 *
 * [loaded] is false both before the first fetch and after one that failed, and that is deliberate:
 * a failed read is not "this match has no wickets", so the tab keeps filtering the loaded feed
 * until a fetch answers.
 */
data class CommentaryEventPage(
    val entries: List<CommentaryEntry> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val hasOlder: Boolean = false,
)

/**
 * The whole match's wickets and boundaries, fetched when a reader taps the chip.
 *
 * Held apart from the main feed on purpose. `loadOlderCommentary` pages from the feed's oldest row,
 * so an old wicket merged into that list would move the cursor back past every delivery between and
 * they would never be fetched.
 *
 * A class of its own rather than more of [MatchDetailViewModel], which is at detekt's function
 * limit.
 */
class CommentaryEventPager(
    private val matchId: String,
    private val source: LiveMatchDetailSource,
    private val scope: CoroutineScope,
) {
    private val _pages = MutableStateFlow<Map<CommentaryEventKind, CommentaryEventPage>>(emptyMap())
    val pages: StateFlow<Map<CommentaryEventKind, CommentaryEventPage>> = _pages.asStateFlow()

    private fun page(kind: CommentaryEventKind) = _pages.value[kind] ?: CommentaryEventPage()

    /** Fetch the newest page for [kind] unless it is already here or on its way. */
    fun open(kind: CommentaryEventKind) {
        val current = page(kind)
        if (current.loaded || current.loading) return
        fetch(kind, olderThan = null)
    }

    /** Fetch the page behind the oldest event held for [kind]. */
    fun loadOlder(kind: CommentaryEventKind) {
        val current = page(kind)
        if (!current.loaded || current.loading || !current.hasOlder) return
        fetch(kind, olderThan = current.entries.lastOrNull()?.sortKey ?: return)
    }

    private fun fetch(kind: CommentaryEventKind, olderThan: Double?) {
        put(kind) { it.copy(loading = true) }
        scope.launch(bouncerExceptionHandler) {
            val rows = withTimeoutOrNull(EVENT_FETCH_TIMEOUT_MS) {
                source.getEventCommentary(matchId, kind.events, olderThan)
            }
            put(kind) { held ->
                if (rows == null) {
                    held.copy(loading = false)
                } else {
                    held.copy(
                        entries = held.entries + rows,
                        loaded = true,
                        loading = false,
                        // A short page is the end of the list, so no tap is offered that would
                        // fetch nothing.
                        hasOlder = rows.size >= COMMENTARY_EVENT_PAGE_LIMIT,
                    )
                }
            }
        }
    }

    private fun put(kind: CommentaryEventKind, change: (CommentaryEventPage) -> CommentaryEventPage) {
        _pages.update { it + (kind to change(it[kind] ?: CommentaryEventPage())) }
    }
}

private const val EVENT_FETCH_TIMEOUT_MS = 10_000L
