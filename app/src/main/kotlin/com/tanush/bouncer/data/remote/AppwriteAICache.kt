package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.Query
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import com.tanush.bouncer.services.RetryPolicy
import com.tanush.bouncer.services.withRetry
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

    private val remoteRetry = RetryPolicy(maxAttempts = 3, baseDelayMs = 500L)
    // 429 is a short-term per-endpoint rate limit, not a plan-level cap — retrying with the
    // existing backoff is the correct response, and it is the only transient Appwrite status
    // below 500. A 402 stays non-retryable: the breaker handles it and retrying cannot help.
    private fun isTransient(e: Throwable) =
        e is java.io.IOException ||
            (e is AppwriteException && ((e.code ?: 0) >= 500 || e.code == HTTP_TOO_MANY_REQUESTS))

    private const val HTTP_TOO_MANY_REQUESTS = 429

    // Returns (commentary, isPermanent). isPermanent=true when expires_at is >1 year away —
    // used by AICacheService to decide whether to warm local caches permanently.
    suspend fun get(key: String): Pair<String, Boolean>? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val result = remoteRetry.withRetry(isRetryable = ::isTransient) {
                    guardedRead("aiCache.get") {
                        it.listRows(
                            databaseId = dbId,
                            tableId = tableId,
                            queries = listOf(Query.equal("cache_key", key), Query.limit(1))
                        )
                    }
                } ?: return@runCatching null
                val row = result.rows.firstOrNull() ?: return@runCatching null
                parseCommentaryRow(key, row.data)
            }.onFailure { e ->
                logSilentFailure("Remote cache get failed", e)
            }.getOrNull()
        }
    }

    // Returns null when the row is expired or has blank commentary.
    private fun parseCommentaryRow(key: String, data: Map<String, Any?>): Pair<String, Boolean>? {
        val expiresAt = data["expires_at"] as? String ?: ""
        val now = System.currentTimeMillis()
        if (expiresAt.isNotBlank()) {
            val expiry = runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull() ?: 0L
            if (expiry < now) {
                Log.d(TAG, "Remote cache expired for key $key")
                return null
            }
            val commentary = (data["commentary"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val isPermanent = expiry > now + CacheConfig.AI_CACHE_PERMANENT_THRESHOLD_MS
            return Pair(commentary, isPermanent)
        }
        val commentary = (data["commentary"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return Pair(commentary, false)
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

    /**
     * Mirror one entry to the shared table.
     *
     * @return true only when Appwrite confirmed the write. False covers every reason the mirror did
     *   not land — the write breaker being latched, a network failure, the client not configured —
     *   so the caller can mark the row for replay instead of dropping the cross-device share
     *   silently, which is what used to happen.
     */
    suspend fun set(key: String, commentary: String, permanent: Boolean = false): Boolean {
        // Never cache an empty/blank result: a provider returning "" is a failure, not a value.
        // Caching it would pollute the shared table and squat the deterministic docId (below).
        if (!isReady || commentary.isBlank()) return false
        return withContext(Dispatchers.IO) {
            runCatching {
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
                val written = remoteRetry.withRetry(isRetryable = ::isTransient) {
                    createRowIdempotent(key, data)
                }
                if (written && permanent) maybeEvictOldestPermanent()
                written
            }.onFailure { e ->
                logSilentFailure("Remote cache set failed", e)
            }.getOrDefault(false)
        }
    }

    // Uses a deterministic document ID derived from the cache key so concurrent
    // set() calls for the same key conflict on ID (409) rather than creating
    // duplicate rows. Catches 409 and treats it as success — the row already exists.
    // Returns false when guardedWrite short-circuits (breaker open / rate capped).
    private suspend fun createRowIdempotent(key: String, data: Map<String, String>): Boolean {
        val rowId = cacheKeyToDocId(key)
        try {
            return guardedWrite("aiCache.create") {
                it.createRow(
                    databaseId = dbId,
                    tableId = tableId,
                    rowId = rowId,
                    data = data,
                    permissions = listOf(Permission.read(Role.any()))
                )
            } != null
        } catch (e: AppwriteException) {
            if (e.code != 409) throw e
            // Row exists (concurrent writer, or a stale/expired entry squatting this
            // deterministic docId). Overwrite so a fresh value replaces stale data instead
            // of being silently dropped — otherwise the old row blocks this key forever.
            return guardedWrite("aiCache.update") {
                it.updateRow(
                    databaseId = dbId,
                    tableId = tableId,
                    rowId = rowId,
                    data = data
                )
            } != null
        }
    }

    private val permanentWritesSinceEviction = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Eviction costs two billed list reads, and a completed match writes one permanent row per
     * ball — so running it on every write issued hundreds of extra billed reads per match. Amortise
     * to one check per [CacheConfig.AI_CACHE_EVICTION_CHECK_EVERY_N_WRITES] writes; the ceiling is
     * a soft one and overshooting it by a batch between checks is harmless.
     */
    private suspend fun maybeEvictOldestPermanent() {
        if (permanentWritesSinceEviction.incrementAndGet() <
            CacheConfig.AI_CACHE_EVICTION_CHECK_EVERY_N_WRITES
        ) {
            return
        }
        permanentWritesSinceEviction.set(0)
        evictOldestPermanentIfNeeded()
    }

    private suspend fun evictOldestPermanentIfNeeded() {
        runCatching {
            // These are billed list reads and MUST go through guardedRead. Calling `db` directly
            // made them invisible to the rate windows and to the 402 breaker, so the one path that
            // fired on every permanent write was also the one path the guard could not see.
            val checkResult = guardedRead("aiCache.evictCheck") { db ->
                remoteRetry.withRetry(isRetryable = ::isTransient) {
                    db.listRows(
                        databaseId = dbId,
                        tableId = tableId,
                        queries = listOf(
                            Query.equal("expires_at", CacheConfig.AI_CACHE_PERMANENT_EXPIRY_ISO),
                            Query.limit(1),
                            Query.offset(CacheConfig.AI_CACHE_MAX_PERMANENT_ENTRIES)
                        )
                    )
                }
            }
            if (checkResult == null || checkResult.rows.isEmpty()) return@runCatching

            val toDelete = guardedRead("aiCache.evictList") { db ->
                remoteRetry.withRetry(isRetryable = ::isTransient) {
                    db.listRows(
                        databaseId = dbId,
                        tableId = tableId,
                        queries = listOf(
                            Query.equal("expires_at", CacheConfig.AI_CACHE_PERMANENT_EXPIRY_ISO),
                            Query.orderAsc("\$createdAt"),
                            Query.limit(CacheConfig.AI_CACHE_EVICTION_BATCH)
                        )
                    )
                }
            } ?: return@runCatching

            for (row in toDelete.rows) {
                remoteRetry.withRetry(isRetryable = ::isTransient) {
                    guardedWrite("aiCache.evictDelete") {
                        it.deleteRow(
                            databaseId = dbId,
                            tableId = tableId,
                            rowId = row.id
                        )
                    }
                }
            }
        }.onFailure { e ->
            logSilentFailure("Permanent cache eviction failed", e)
        }
    }

    fun startRealtimeSubscription(scope: CoroutineScope, onNewEntry: (key: String, commentary: String) -> Unit) {
        if (!isReady) return
        if (!subscriptionStarted.compareAndSet(false, true)) return
        acquireChannel(tableChannel()) { response ->
            val isCreate = response.events.any { it.contains(".create") }
            if (!isCreate) return@acquireChannel
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@acquireChannel
            val key = payload["cache_key"] as? String
                ?: (payload["data"] as? Map<*, *>)?.get("cache_key") as? String
            val commentary = payload["commentary"] as? String
                ?: (payload["data"] as? Map<*, *>)?.get("commentary") as? String
            if (key == null || commentary == null) {
                Log.d(TAG, "Realtime AI cache payload unparseable: $payload")
                return@acquireChannel
            }
            scope.launch { onNewEntry(key, commentary) }
        }
    }
}
