package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import io.appwrite.Query
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Realtime
import io.appwrite.services.TablesDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

// Shared cross-device AI commentary cache backed by Appwrite TablesDB.
// Reduces OpenRouter free-tier rate limit hits: same match+ball = 1 LLM call
// total across all users instead of N calls. Falls back silently on any error.
object AppwriteAICache {
    private const val TAG = "AppwriteAICache"
    private const val TTL_MS = 3_600_000L // 1 hour, matches AICacheService

    private var realtimeInstance: Realtime? = null
    private var realtimeSubscription: Any? = null  // RealtimeSubscription handle

    private var dbId = ""
    private var tableId = ""
    var isReady = false
        private set

    private val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    fun initialize() {
        dbId = ApiKeys.appwriteAiCacheDbId
        tableId = ApiKeys.appwriteAiCacheColId
        isReady = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()
        if (!isReady) {
            Log.w(TAG, "Appwrite AI cache not configured — remote cache tier disabled")
        }
    }

    suspend fun get(key: String): String? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching null
                val result = db.listRows(
                    databaseId = dbId,
                    tableId = tableId,
                    queries = listOf(Query.equal("cache_key", key), Query.limit(1))
                )
                val row = result.rows.firstOrNull() ?: return@runCatching null
                val data = row.data
                val expiresAt = data["expires_at"] as? String ?: ""
                if (expiresAt.isNotBlank()) {
                    val expiry = runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull() ?: 0L
                    if (expiry < System.currentTimeMillis()) {
                        Log.d(TAG, "Remote cache expired for key $key")
                        return@runCatching null
                    }
                }
                (data["commentary"] as? String)?.takeIf { it.isNotBlank() }
            }.onFailure { e ->
                if (e is AppwriteException) {
                    Log.d(TAG, "Remote cache get failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Remote cache get failed (silent): ${e.message}")
                }
            }.getOrNull()
        }
    }

    suspend fun set(key: String, commentary: String) {
        if (!isReady) return
        withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching
                val expiresAt = DateTimeFormatter.ISO_INSTANT.format(
                    Instant.ofEpochMilli(System.currentTimeMillis() + TTL_MS)
                )
                db.createRow(
                    databaseId = dbId,
                    tableId = tableId,
                    rowId = ID.unique(),
                    data = mapOf(
                        "cache_key" to key,
                        "commentary" to commentary,
                        "expires_at" to expiresAt
                    ),
                    permissions = listOf(Permission.read(Role.any()))
                )
            }.onFailure { e ->
                if (e is AppwriteException) {
                    Log.d(TAG, "Remote cache set failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Remote cache set failed (silent): ${e.message}")
                }
            }
        }
    }

    fun startRealtimeSubscription(scope: CoroutineScope, onNewEntry: (key: String, commentary: String) -> Unit) {
        if (!isReady) return
        val client = AppwriteClient.client ?: return
        val realtime = Realtime(client)
        realtimeInstance = realtime
        realtimeSubscription = realtime.subscribe("databases.$dbId.collections.$tableId.documents") { response ->
            val isCreate = response.events.any { it.contains(".create") }
            if (!isCreate) return@subscribe
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@subscribe
            val key = payload["cache_key"] as? String
                ?: (payload["data"] as? Map<*, *>)?.get("cache_key") as? String
            val commentary = payload["commentary"] as? String
                ?: (payload["data"] as? Map<*, *>)?.get("commentary") as? String
            if (key == null || commentary == null) {
                Log.d(TAG, "Realtime AI cache payload unparseable: $payload")
                return@subscribe
            }
            scope.launch { onNewEntry(key, commentary) }
        }
    }
}
