package com.tanush.bouncer.data.remote

import android.content.SharedPreferences
import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Cross-device favourites sync backed by Appwrite TablesDB.
// Uses a stable device UUID as the row ID — no auth required.
// Table-level permissions: create("any"), read("any"), update("any").
// PUT endpoint = upsert, so one call handles both create and update.
object AppwriteFavouritesSync {
    private const val TAG = "AppwriteFavouritesSync"
    private const val PREF_DEVICE_ID = "appwrite_device_id"
    private val client = HttpClientProvider.sharedClient

    private var baseUrl = ""   // .../tablesdb/{dbId}/tables/{tableId}/rows
    private var projectId = ""
    private var deviceId = ""
    var isReady = false
        private set

    fun initialize(prefs: SharedPreferences) {
        val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
        projectId = ApiKeys.appwriteProjectId
        val dbId = ApiKeys.appwriteAiCacheDbId
        val tableId = ApiKeys.appwriteFavouritesColId

        isReady = listOf(endpoint, projectId, dbId, tableId).all { it.isNotBlank() }
        if (!isReady) {
            Log.w(TAG, "Appwrite favourites sync not configured — sync disabled")
            return
        }
        baseUrl = "$endpoint/tablesdb/$dbId/tables/$tableId/rows"

        deviceId = prefs.getString(PREF_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, id).apply()
            id
        }
    }

    suspend fun pull(): List<String>? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/$deviceId")
                    .get()
                    .addHeader("X-Appwrite-Project", projectId)
                    .addHeader("X-Appwrite-Response-Format", "1.8.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) return@runCatching null
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val arr = JSONObject(body).optJSONArray("teams") ?: return@runCatching null
                    (0 until arr.length()).map { arr.getString(it) }
                }
            }.onFailure { e ->
                Log.d(TAG, "Remote favourites pull failed (silent): ${e.message}")
            }.getOrNull()
        }
    }

    suspend fun push(teams: List<String>) {
        if (!isReady) return
        withContext(Dispatchers.IO) {
            runCatching {
                // PUT = upsert: creates if not exists, updates if exists
                val body = JSONObject().apply {
                    put("data", JSONObject().apply {
                        put("teams", JSONArray(teams))
                    })
                    put("permissions", JSONArray().apply {
                        put("read(\"any\")")
                        put("update(\"any\")")
                    })
                }.toString()
                val request = Request.Builder()
                    .url("$baseUrl/$deviceId")
                    .put(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("X-Appwrite-Project", projectId)
                    .addHeader("X-Appwrite-Response-Format", "1.8.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.d(TAG, "Remote favourites push failed (silent): HTTP ${response.code}")
                    }
                }
            }.onFailure { e ->
                Log.d(TAG, "Remote favourites push failed (silent): ${e.message}")
            }
        }
    }
}
