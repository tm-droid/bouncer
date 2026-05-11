package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object AppwriteHighlightsClient {
    private const val TAG = "AppwriteHighlightsClient"
    private val client = HttpClientProvider.sharedClient

    private var baseUrl = ""
    private var projectId = ""
    var isReady = false
        private set

    fun initialize() {
        val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
        projectId = ApiKeys.appwriteProjectId
        val dbId = ApiKeys.appwriteHighlightsDbId
        val tableId = ApiKeys.appwriteHighlightsColId
        isReady = listOf(endpoint, projectId, dbId, tableId).all { it.isNotBlank() }
        if (isReady) {
            baseUrl = "$endpoint/tablesdb/$dbId/tables/$tableId/rows"
        } else {
            Log.w(TAG, "Appwrite highlights not configured")
        }
    }

    suspend fun getAllHighlights(): List<HighlightRow>? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl?limit=100")
                    .get()
                    .addHeader("X-Appwrite-Project", projectId)
                    .addHeader("X-Appwrite-Response-Format", "1.8.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val json = JSONObject(body)
                    val rows = json.optJSONArray("rows") ?: return@runCatching emptyList()
                    val list = mutableListOf<HighlightRow>()
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        list.add(HighlightRow.fromJson(row))
                    }
                    list
                }
            }.onFailure { e ->
                Log.d(TAG, "Fetch highlights failed (silent): ${e.message}")
            }.getOrNull()
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
        fun fromJson(obj: JSONObject): HighlightRow {
            val data = obj.optJSONObject("data") ?: obj
            return HighlightRow(
                highlightId = data.optString("highlight_id", ""),
                type = data.optString("type", "UNVERIFIED"),
                imgUrl = data.optString("img_url", ""),
                title = data.optString("title", ""),
                url = data.optString("url", ""),
                embedUrl = data.optString("embed_url", "").takeIf { it.isNotBlank() },
                channel = data.optString("channel", ""),
                source = data.optString("source", ""),
                matchHomeTeam = data.optString("match_home_team", ""),
                matchAwayTeam = data.optString("match_away_team", ""),
                matchHomeAbbr = data.optString("match_home_abbr", ""),
                matchAwayAbbr = data.optString("match_away_abbr", ""),
                matchLeagueName = data.optString("match_league_name", ""),
                matchLeagueSeason = data.optInt("match_league_season", 0),
                matchStartDate = data.optString("match_start_date", ""),
                matchEndDate = data.optString("match_end_date", ""),
                rawMatchId = data.optString("raw_match_id", ""),
                fetchedAt = data.optLong("fetched_at", System.currentTimeMillis())
            )
        }
    }
}
