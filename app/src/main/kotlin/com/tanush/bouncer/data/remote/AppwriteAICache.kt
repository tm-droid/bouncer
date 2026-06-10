package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.Query
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

// Shared cross-device AI commentary cache backed by Appwrite TablesDB.
// Reduces OpenRouter free-tier rate limit hits: same match+ball = 1 LLM call
// total across all users instead of N calls. Falls back silently on any error.
object AppwriteAICache : AppwriteTableClient("AppwriteAICache") {
    private const val TAG = "AppwriteAICache"
    private val TTL_MS get() = CacheConfig.AI_CACHE_TTL_MS

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, ApiKeys.appwriteAiCacheColId)
    }

    // Returns (commentary, isPermanent). isPermanent=true when expires_at is >1 year away —
    // used by AICacheService to decide whether to warm local caches permanently.
    suspend fun get(key: String): Pair<String, Boolean>? {
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
                val now = System.currentTimeMillis()
                if (expiresAt.isNotBlank()) {
                    val expiry = runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull() ?: 0L
                    if (expiry < now) {
                        Log.d(TAG, "Remote cache expired for key $key")
                        return@runCatching null
                    }
                    val commentary = (data["commentary"] as? String)?.takeIf { it.isNotBlank() }
                        ?: return@runCatching null
                    val isPermanent = expiry > now + CacheConfig.AI_CACHE_PERMANENT_THRESHOLD_MS
                    return@runCatching Pair(commentary, isPermanent)
                }
                val commentary = (data["commentary"] as? String)?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                Pair(commentary, false)
            }.onFailure { e ->
                if (e is AppwriteException) {
                    Log.d(TAG, "Remote cache get failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Remote cache get failed (silent): ${e.message}")
                }
            }.getOrNull()
        }
    }

    /**
     * Derives a deterministic, Appwrite-safe document ID from a cache key.
     * SHA-256 of the key, first 18 bytes as lowercase hex = 36 chars (within Appwrite's 36-char limit).
     * All-hex chars satisfy Appwrite's alphanumeric+hyphen+underscore constraint.
     */
    private fun cacheKeyToDocId(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.take(18).joinToString("") { "%02x".format(it) }
    }

    suspend fun set(key: String, commentary: String, permanent: Boolean = false) {
        if (!isReady) return
        withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching
                val expiresAt = if (permanent) {
                    CacheConfig.AI_CACHE_PERMANENT_EXPIRY_ISO
                } else {
                    DateTimeFormatter.ISO_INSTANT.format(
                        Instant.ofEpochMilli(System.currentTimeMillis() + TTL_MS)
                    )
                }
                val data = mapOf(
                    "cache_key" to key,
                    "commentary" to commentary,
                    "expires_at" to expiresAt
                )
                // Use a deterministic document ID derived from the cache key so concurrent
                // set() calls for the same key conflict on ID (409) rather than creating
                // duplicate rows. Catch 409 and treat as success — the row already exists.
                val docId = cacheKeyToDocId(key)
                try {
                    db.createRow(
                        databaseId = dbId,
                        tableId = tableId,
                        rowId = docId,
                        data = data,
                        permissions = listOf(Permission.read(Role.any()))
                    )
                } catch (e: AppwriteException) {
                    if (e.code == 409) {
                        // Row already exists (concurrent writer or previous call) — idempotent success.
                        Log.d(TAG, "Remote cache set: row already exists for key $key (409 conflict — OK)")
                    } else {
                        throw e
                    }
                }
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
        if (!subscriptionStarted.compareAndSet(false, true)) return
        startSubscriptionLoop { response ->
            val isCreate = response.events.any { it.contains(".create") }
            if (!isCreate) return@startSubscriptionLoop
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@startSubscriptionLoop
            val key = payload["cache_key"] as? String
                ?: (payload["data"] as? Map<*, *>)?.get("cache_key") as? String
            val commentary = payload["commentary"] as? String
                ?: (payload["data"] as? Map<*, *>)?.get("commentary") as? String
            if (key == null || commentary == null) {
                Log.d(TAG, "Realtime AI cache payload unparseable: $payload")
                return@startSubscriptionLoop
            }
            scope.launch { onNewEntry(key, commentary) }
        }
    }
}
