package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.TimelineEventType
import io.appwrite.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One commentary line plus what actually happened on the delivery.
 *
 * [event] is `bouncer-match-sync`'s verdict, classified from the RAW Cricbuzz text *before* the
 * line was rewritten into broadcast prose. It has to travel as data because it is not recoverable
 * from [text]: the rewrite turns a four into "races away to the rope", so the `\bFOUR\b` regex the
 * commentary filters used to rely on matched only the rows where generation failed and the raw text
 * was written through.
 *
 * Three distinct states, and the difference between the last two is the point:
 *  - `WICKET` / `SIX` / `FOUR` — the server saw it.
 *  - [TimelineEventType.OTHER] — the server saw an ordinary delivery. Not a boundary, full stop.
 *  - `null` — the server never had an opinion: a scraped fallback line, or a row written before
 *    the `event` column existed. Only here do callers fall back to text classification.
 *
 * Collapsing the last two would undo the fix: a dot ball with no verdict sends the filter back to
 * searching broadcast prose for the word "six", which is a word commentators write about the
 * target score. See `CommentaryEntry.eventType`.
 */
data class CommentaryEntry(val text: String, val event: TimelineEventType?)

data class CommentaryEvent(
    val matchId: String,
    val commentary: String,
    val sortKey: Double,
    val event: TimelineEventType?,
)

/**
 * Parse the `event` column. Unknown or absent values are null, never a throw: the column is a
 * string rather than an enum precisely so a server-side vocabulary change degrades to "the client
 * will classify this one from text" instead of failing the write and losing the commentary.
 */
internal fun parseCommentaryEvent(raw: String?): TimelineEventType? = when (raw) {
    "WICKET" -> TimelineEventType.WICKET
    "SIX" -> TimelineEventType.SIX
    "FOUR" -> TimelineEventType.FOUR
    "NONE" -> TimelineEventType.OTHER
    else -> null
}

// Reads the server-generated per-ball commentary feed (written by bouncer-match-sync)
// for a single match, and exposes an app-lifetime Realtime stream of new lines. The
// on-device scrape is gone — this is the sole source of match-detail commentary.
// The Realtime channel is table-wide (started once); ViewModels filter [events] by matchId.
object AppwriteMatchCommentaryClient : AppwriteTableClient("AppwriteMatchCommentary") {
    private const val TABLE_ID = "match-commentary"
    private const val FEED_LIMIT = 60

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    // Newest-first list of commentary lines for the match.
    suspend fun getForMatch(matchId: String): List<CommentaryEntry> {
        if (!isReady) return emptyList()
        return withContext(Dispatchers.IO) {
            // Server-side order needs a sort_key index; if that's missing the ordered query 400s.
            // Fall back to an unordered fetch + client-side sort so a missing index never blanks the feed.
            runCatching {
                queryRows(matchId, ordered = true)
            }.recoverCatching { e ->
                logSilentFailure("Commentary ordered fetch failed; retrying unordered", e)
                queryRows(matchId, ordered = false)
            }.onFailure { e ->
                logSilentFailure("Commentary feed fetch failed", e)
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun queryRows(matchId: String, ordered: Boolean): List<CommentaryEntry> {
        val queries = buildList {
            add(Query.equal("match_id", matchId))
            if (ordered) add(Query.orderDesc("sort_key"))
            add(Query.limit(FEED_LIMIT))
        }
        val name = if (ordered) "commentary.ordered" else "commentary.unordered"
        val rows = guardedRead(name) {
            it.listRows(databaseId = dbId, tableId = tableId, queries = queries)
        }?.rows ?: return emptyList()
        val sorted = if (ordered) rows else rows.sortedByDescending { (it.data["sort_key"] as? Number)?.toDouble() ?: 0.0 }
        return sorted.mapNotNull { row ->
            val text = (row.data["commentary"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CommentaryEntry(text, parseCommentaryEvent(row.data["event"] as? String))
        }
    }

    /**
     * New commentary lines for one match, for as long as the flow is collected.
     *
     * Row ids here are `sha256("$matchId:$timestamp")`, unknowable client-side, and Appwrite has no
     * query-filtered channels — so unlike the detail client this must use the table channel and
     * filter locally. The lever is therefore DURATION, not selectivity: the socket is held only
     * while a match-detail screen is alive instead of for the whole process. Concurrent collectors
     * share one socket via the base class refcount.
     */
    fun observeCommentary(matchId: String): Flow<CommentaryEvent> = callbackFlow {
        if (!isReady) {
            close()
            return@callbackFlow
        }
        val handle = acquireChannel(tableChannel()) { response ->
            val isWrite = response.events.any { it.contains(".create") || it.contains(".update") }
            if (!isWrite) return@acquireChannel
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@acquireChannel
            if (payload["match_id"] as? String != matchId) return@acquireChannel
            val commentary = (payload["commentary"] as? String)?.takeIf { it.isNotBlank() }
                ?: return@acquireChannel
            val sortKey = (payload["sort_key"] as? Number)?.toDouble() ?: 0.0
            val event = parseCommentaryEvent(payload["event"] as? String)
            trySend(CommentaryEvent(matchId, commentary, sortKey, event))
        }
        awaitClose { handle.close() }
    }
}
