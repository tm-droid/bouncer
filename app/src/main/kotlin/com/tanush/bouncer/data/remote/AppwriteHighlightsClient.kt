package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Realtime
import io.appwrite.services.TablesDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppwriteHighlightsClient {
    private const val TAG = "AppwriteHighlightsClient"

    private var dbId = ""
    private var tableId = ""
    var isReady = false
        private set

    private var realtimeInstance: Realtime? = null
    private var realtimeSubscription: Any? = null

    private val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    fun initialize() {
        dbId = ApiKeys.appwriteHighlightsDbId
        tableId = ApiKeys.appwriteHighlightsColId
        isReady = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()
        if (!isReady) {
            Log.w(TAG, "Appwrite highlights not configured")
        }
    }

    suspend fun getAllHighlights(): List<HighlightRow>? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching null
                val result = db.listRows(
                    databaseId = dbId,
                    tableId = tableId,
                    queries = listOf(Query.limit(100))
                )
                @Suppress("UNCHECKED_CAST")
                result.rows.map { HighlightRow.fromMap(it.data as? Map<String, Any?> ?: emptyMap()) }
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
        val client = AppwriteClient.client ?: return
        val realtime = Realtime(client)
        realtimeInstance = realtime
        realtimeSubscription = realtime.subscribe("databases.$dbId.collections.$tableId.documents") { response ->
            val isCreate = response.events.any { it.contains(".create") }
            if (!isCreate) return@subscribe
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val row = HighlightRow.fromMap(response.payload as? Map<String, Any?> ?: return@subscribe)
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
