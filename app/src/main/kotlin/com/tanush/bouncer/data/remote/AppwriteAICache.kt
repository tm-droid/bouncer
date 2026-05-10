package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

// Shared cross-device AI commentary cache backed by Appwrite TablesDB.
// Reduces OpenRouter free-tier rate limit hits: same match+ball = 1 LLM call
// total across all users instead of N calls. Falls back silently on any error.
object AppwriteAICache {
    private const val TAG = "AppwriteAICache"
    private val client = HttpClientProvider.sharedClient
    private const val TTL_MS = 3_600_000L // 1 hour, matches AICacheService

    private var baseUrl = ""   // e.g. "https://cloud.appwrite.io/v1/tablesdb/{dbId}/tables/{colId}/rows"
    private var projectId = ""
    var isReady = false
        private set

    fun initialize() {
        val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
        projectId = ApiKeys.appwriteProjectId
        val dbId = ApiKeys.appwriteAiCacheDbId
        val tableId = ApiKeys.appwriteAiCacheColId
        isReady = listOf(endpoint, projectId, dbId, tableId).all { it.isNotBlank() }
        if (isReady) {
            baseUrl = "$endpoint/tablesdb/$dbId/tables/$tableId/rows"
        } else {
            Log.w(TAG, "Appwrite AI cache not configured — remote cache tier disabled")
        }
    }

    suspend fun get(key: String): String? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val query = URLEncoder.encode(
                    "{\"method\":\"equal\",\"attribute\":\"cache_key\",\"values\":[\"$key\"]}",
                    "UTF-8"
                )
                val request = Request.Builder()
                    .url("$baseUrl?queries[]=$query&limit=1")
                    .get()
                    .addHeader("X-Appwrite-Project", projectId)
                    .addHeader("X-Appwrite-Response-Format", "1.8.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val rows = JSONObject(body).optJSONArray("rows") ?: return@runCatching null
                    if (rows.length() == 0) return@runCatching null
                    val row = rows.getJSONObject(0)
                    val expiresAt = row.optString("expires_at", "")
                    if (expiresAt.isNotBlank()) {
                        val expiry = runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull() ?: 0L
                        if (expiry < System.currentTimeMillis()) {
                            Log.d(TAG, "Remote cache expired for key $key")
                            return@runCatching null
                        }
                    }
                    row.optString("commentary").takeIf { it.isNotBlank() }
                }
            }.onFailure { e ->
                Log.d(TAG, "Remote cache get failed (silent): ${e.message}")
            }.getOrNull()
        }
    }

    suspend fun set(key: String, commentary: String) {
        if (!isReady) return
        withContext(Dispatchers.IO) {
            runCatching {
                val expiresAt = DateTimeFormatter.ISO_INSTANT.format(
                    Instant.ofEpochMilli(System.currentTimeMillis() + TTL_MS)
                )
                val body = JSONObject().apply {
                    put("rowId", UUID.randomUUID().toString())
                    put("data", JSONObject().apply {
                        put("cache_key", key)
                        put("commentary", commentary)
                        put("expires_at", expiresAt)
                    })
                    put("permissions", org.json.JSONArray().apply { put("read(\"any\")") })
                }.toString()
                val request = Request.Builder()
                    .url(baseUrl)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("X-Appwrite-Project", projectId)
                    .addHeader("X-Appwrite-Response-Format", "1.8.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.d(TAG, "Remote cache set failed (silent): HTTP ${response.code}")
                    }
                }
            }.onFailure { e ->
                Log.d(TAG, "Remote cache set failed (silent): ${e.message}")
            }
        }
    }
}
