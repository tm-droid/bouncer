package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ApiKeys
import io.appwrite.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CommentaryEvent(val matchId: String, val commentary: String, val sortKey: Double)

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
    suspend fun getForMatch(matchId: String): List<String> {
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

    private suspend fun queryRows(matchId: String, ordered: Boolean): List<String> {
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
        return sorted.mapNotNull { (it.data["commentary"] as? String)?.takeIf { c -> c.isNotBlank() } }
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
            trySend(CommentaryEvent(matchId, commentary, sortKey))
        }
        awaitClose { handle.close() }
    }
}
