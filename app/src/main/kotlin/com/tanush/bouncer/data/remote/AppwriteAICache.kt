package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.Query
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Realtime
import io.appwrite.services.TablesDB
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

// Shared cross-device AI commentary cache backed by Appwrite TablesDB.
// Reduces OpenRouter free-tier rate limit hits: same match+ball = 1 LLM call
// total across all users instead of N calls. Falls back silently on any error.
object AppwriteAICache {
    private const val TAG = "AppwriteAICache"
    private val TTL_MS get() = CacheConfig.AI_CACHE_TTL_MS

    @Volatile private var realtimeSubscription: Any? = null  // RealtimeSubscription handle
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                    val isPermanent = expiry > now + 365L * 24 * 60 * 60 * 1000
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
                    "2099-01-01T00:00:00.000Z"
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
        if (realtimeSubscription != null) return
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

    private fun startSubscriptionLoop(onEvent: (io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>) -> Unit) {
        reconnectScope.launch {
            var backoffMs = 2_000L
            while (isActive) {
                try {
                    val client = AppwriteClient.client ?: break
                    val realtime = Realtime(client)
                    // TablesDB tables are backed by Appwrite collections internally, so the standard
                    // databases.{db}.collections.{table}.documents channel format is correct.
                    // Log the exact channel so a silent subscription miss is visible in logcat.
                    val channel = "databases.$dbId.collections.$tableId.documents"
                    Log.d(TAG, "Subscribing to realtime channel: $channel")
                    @Suppress("UNCHECKED_CAST")
                    realtimeSubscription = realtime.subscribe(channel) { response ->
                        @Suppress("UNCHECKED_CAST")
                        onEvent(response as io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>)
                    }
                    backoffMs = 2_000L  // reset on successful subscribe
                    // Appwrite Realtime is push-based; subscription stays alive until an error
                    // or cancellation. Re-looping immediately after subscribe() returns is safe
                    // — if the socket is healthy the SDK won't throw and we just re-subscribe
                    // (which the SDK deduplicates). If the socket died, we reconnect after backoff.
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    realtimeSubscription = null
                    Log.w(TAG, "Realtime subscription lost, reconnecting in ${backoffMs}ms: ${e.message}")
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, 60_000L)
                }
            }
        }
    }

    fun stop() {
        reconnectScope.cancel()
        realtimeSubscription = null
    }
}
