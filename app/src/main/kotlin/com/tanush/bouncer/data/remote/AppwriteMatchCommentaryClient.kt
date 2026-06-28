package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ApiKeys
import io.appwrite.Query
import io.appwrite.services.TablesDB
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _events = MutableSharedFlow<CommentaryEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CommentaryEvent> = _events

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    // Newest-first list of commentary lines for the match.
    suspend fun getForMatch(matchId: String): List<String> {
        if (!isReady) return emptyList()
        return withContext(Dispatchers.IO) {
            val db = tablesDB ?: return@withContext emptyList()
            // Server-side order needs a sort_key index; if that's missing the ordered query 400s.
            // Fall back to an unordered fetch + client-side sort so a missing index never blanks the feed.
            runCatching {
                queryRows(db, matchId, ordered = true)
            }.recoverCatching { e ->
                logSilentFailure("Commentary ordered fetch failed; retrying unordered", e)
                queryRows(db, matchId, ordered = false)
            }.onFailure { e ->
                logSilentFailure("Commentary feed fetch failed", e)
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun queryRows(db: TablesDB, matchId: String, ordered: Boolean): List<String> {
        val queries = buildList {
            add(Query.equal("match_id", matchId))
            if (ordered) add(Query.orderDesc("sort_key"))
            add(Query.limit(FEED_LIMIT))
        }
        val rows = db.listRows(databaseId = dbId, tableId = tableId, queries = queries).rows
        val sorted = if (ordered) rows else rows.sortedByDescending { (it.data["sort_key"] as? Number)?.toDouble() ?: 0.0 }
        return sorted.mapNotNull { (it.data["commentary"] as? String)?.takeIf { c -> c.isNotBlank() } }
    }

    // App-lifetime subscription; emits every newly-created commentary row into [events].
    fun startRealtime() {
        if (!isReady) return
        if (!subscriptionStarted.compareAndSet(false, true)) return
        startSubscriptionLoop { response ->
            val isWrite = response.events.any { it.contains(".create") || it.contains(".update") }
            if (!isWrite) return@startSubscriptionLoop
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@startSubscriptionLoop
            val matchId = payload["match_id"] as? String ?: return@startSubscriptionLoop
            val commentary = (payload["commentary"] as? String)?.takeIf { it.isNotBlank() }
                ?: return@startSubscriptionLoop
            val sortKey = (payload["sort_key"] as? Number)?.toDouble() ?: 0.0
            _events.tryEmit(CommentaryEvent(matchId, commentary, sortKey))
        }
    }
}
