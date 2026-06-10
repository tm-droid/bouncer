package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppwriteHighlightsClient : AppwriteTableClient("AppwriteHighlightsClient") {
    private const val TAG = "AppwriteHighlightsClient"

    fun initialize() {
        initTable(ApiKeys.appwriteHighlightsDbId, ApiKeys.appwriteHighlightsColId)
    }

    suspend fun getAllHighlights(): List<HighlightRow>? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching null
                // Cursor-based pagination — static limit(100) silently truncates as the table grows
                // (Appwrite default is 25; even 100 misses rows once the table exceeds that cap).
                val allRows = mutableListOf<HighlightRow>()
                var lastId: String? = null
                var pageCount = 0
                while (pageCount++ < 50) {
                    val queries = buildList {
                        add(Query.limit(100))
                        if (lastId != null) add(Query.cursorAfter(lastId!!))
                    }
                    val page = db.listRows(databaseId = dbId, tableId = tableId, queries = queries)
                    @Suppress("UNCHECKED_CAST")
                    allRows.addAll(page.rows.map { HighlightRow.fromMap(it.data as? Map<String, Any?> ?: emptyMap()) })
                    if (page.rows.size < 100) break
                    lastId = page.rows.last().id
                }
                allRows
            }.onFailure { e ->
                if (e is AppwriteException) {
                    Log.d(TAG, "Fetch highlights failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Fetch highlights failed (silent): ${e.message}")
                }
            }.getOrNull()
        }
    }

    fun startRealtimeSubscription(scope: CoroutineScope, onNewHighlight: (HighlightRow) -> Unit) {
        if (!isReady) return
        if (!subscriptionStarted.compareAndSet(false, true)) return
        startSubscriptionLoop { response ->
            val isCreate = response.events.any { it.contains(".create") }
            if (!isCreate) return@startSubscriptionLoop
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val row = HighlightRow.fromMap(response.payload as? Map<String, Any?> ?: return@startSubscriptionLoop)
                scope.launch { onNewHighlight(row) }
            }.onFailure {
                Log.d(TAG, "Realtime highlight payload unparseable: ${it.message}")
            }
        }
    }
}

data class HighlightRow(
    val highlightId: String,
    val type: String,
    val imgUrl: String,
    val title: String,
    val url: String,
    val embedUrl: String?,
    val channel: String,
    val source: String,
    val matchHomeTeam: String,
    val matchAwayTeam: String,
    val matchHomeAbbr: String,
    val matchAwayAbbr: String,
    val matchLeagueName: String,
    val matchLeagueSeason: Int,
    val matchStartDate: String,
    val matchEndDate: String,
    val rawMatchId: String,
    val fetchedAt: Long
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): HighlightRow {
            // data may come from SDK row.data or from Realtime payload directly
            val inner = (data["data"] as? Map<*, *>) ?: data
            return HighlightRow(
                highlightId = inner["highlight_id"] as? String ?: "",
                type = inner["type"] as? String ?: "UNVERIFIED",
                imgUrl = inner["img_url"] as? String ?: "",
                title = inner["title"] as? String ?: "",
                url = inner["url"] as? String ?: "",
                embedUrl = (inner["embed_url"] as? String)?.takeIf { it.isNotBlank() },
                channel = inner["channel"] as? String ?: "",
                source = inner["source"] as? String ?: "",
                matchHomeTeam = inner["match_home_team"] as? String ?: "",
                matchAwayTeam = inner["match_away_team"] as? String ?: "",
                matchHomeAbbr = inner["match_home_abbr"] as? String ?: "",
                matchAwayAbbr = inner["match_away_abbr"] as? String ?: "",
                matchLeagueName = inner["match_league_name"] as? String ?: "",
                matchLeagueSeason = (inner["match_league_season"] as? Number)?.toInt() ?: 0,
                matchStartDate = inner["match_start_date"] as? String ?: "",
                matchEndDate = inner["match_end_date"] as? String ?: "",
                rawMatchId = inner["raw_match_id"] as? String ?: "",
                fetchedAt = (inner["fetched_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
