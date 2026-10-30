package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.SingleFlight
import com.tanush.bouncer.data.cache.MemoryFifoCache
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import com.tanush.bouncer.data.remote.AppwriteAICache
import com.tanush.bouncer.data.remote.AppwriteReadGuard
import com.tanush.bouncer.utils.CommentaryFormatter
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

object AICacheService {
    private const val TAG = "AICacheService"
    // Sentinel returned by commentary generators when every provider fails. Must never be
    // cached: it would be served back (permanently, for completed matches) and never regenerate.
    const val UNAVAILABLE_PLACEHOLDER = "[Summary unavailable]"
    private const val MAX_MEMORY_ENTRIES = 256
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val CACHE_EXPIRATION_INTERVAL_MS = CacheConfig.AI_CACHE_TTL_MS
    // Shared in-memory tier (256 entries, TTL + permanent semantics, FIFO eviction). Compound
    // memory+Room operations are still serialized by [mutex]; this only guards the map.
    private val cache = MemoryFifoCache<String, String>(
        maxSize = MAX_MEMORY_ENTRIES,
        ttlMs = CACHE_EXPIRATION_INTERVAL_MS,
    )
    private var dao: AICacheDao? = null
    // In-flight deduplication: prevents duplicate remote lookups for the same key when multiple
    // coroutines miss memory+Room simultaneously.
    //
    // This used to be a hand-rolled `ConcurrentHashMap.computeIfAbsent { scope.async { … } }` with
    // the entry cleared from inside the coroutine's own `finally`. Two things were wrong with it:
    // ConcurrentHashMap documents that the mapping function must not modify the map, and that code
    // launched a coroutine which removed the very key being installed — it only survived because
    // the coroutine landed on a different thread. And nothing stopped an already-completed
    // Deferred being handed to a later caller, which would serve a stale result. SingleFlight
    // solves both, and it is what CricketRepository already uses.
    private val inFlight = SingleFlight<String, String?>(scope)

    fun close() { scope.cancel() }

    fun initialize(dao: AICacheDao) {
        this.dao = dao
        // Prune stale DB entries from previous sessions at startup
        scope.launch {
            runCatching {
                dao.deleteExpired(System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS)
            }.onFailure { e ->
                Log.w(TAG, "Failed to prune expired AI cache entries", e)
                Sentry.captureException(e)
            }
            // Anything dropped by the last session's outage is still marked unsynced.
            replayUnsyncedPermanent()
        }
        // The write breaker closing is the other moment a backlog can drain: the budget that
        // rejected those writes is demonstrably serving again.
        AppwriteReadGuard.onBreakerClosed = { quota ->
            if (quota == AppwriteReadGuard.Quota.DB_WRITE) {
                scope.launch { replayUnsyncedPermanent() }
            }
        }
    }

    /**
     * Push permanent rows whose Appwrite mirror was never confirmed, oldest backlog first.
     *
     * Bounded to [CacheConfig.AI_CACHE_REPLAY_BATCH] per pass and spaced by
     * [CacheConfig.AI_CACHE_REPLAY_SPACING_MS], because one trigger is a breaker closing and an
     * unbounded drain would spike the window that was just unblocked. Not draining the backlog is
     * fine: rows keep `syncedAt = null`, so the next close or app start resumes. The remote write
     * is idempotent (deterministic rowId, 409 handled), so a replayed row cannot duplicate.
     *
     * Local caches are untouched — the completed-match permanent TTL in memory and Room was already
     * written before the sync was attempted and does not depend on this succeeding.
     */
    private suspend fun replayUnsyncedPermanent() {
        val dao = this.dao ?: return
        runCatching {
            val pending = dao.unsyncedPermanent(
                CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP,
                CacheConfig.AI_CACHE_REPLAY_BATCH,
            )
            if (pending.isEmpty()) return
            Log.d(TAG, "Replaying ${pending.size} unsynced permanent AI cache rows")
            var synced = 0
            for (entity in pending) {
                if (AppwriteAICache.set(entity.cacheKey, entity.commentary, permanent = true)) {
                    dao.markSynced(entity.cacheKey, System.currentTimeMillis())
                    synced++
                } else {
                    // The guard is blocking again (or the network is gone). Continuing would just
                    // burn the rest of the batch against a closed door.
                    break
                }
                delay(CacheConfig.AI_CACHE_REPLAY_SPACING_MS)
            }
            Sentry.addBreadcrumb("AI cache replay: $synced/${pending.size} rows synced")
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "AI cache replay failed", e)
            Sentry.captureException(e)
        }
    }

    suspend fun getCommentary(key: String): String? {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.ai"
            message = "Getting cached commentary"
            level = SentryLevel.INFO
            setData("cacheKey", key)
        })

        // Phase 1: memory + Room under mutex. Both are fast local I/O — holding mutex here is fine.
        val localHit: String? = mutex.withLock {
            try {
                val memHit = cache.get(key)
                if (memHit != null) {
                    Log.d(TAG, "Memory hit for key $key")
                    return@withLock memHit
                }
                val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
                val dbResult = dao?.getCommentary(key, cutoffMs)
                if (dbResult != null) {
                    Log.d(TAG, "DB hit for key $key, warming memory cache")
                    cache.put(key, dbResult)
                    // Recency feeds the row-ceiling eviction; without it every row looks equally
                    // cold and eviction degrades to arbitrary.
                    dao?.touch(key, System.currentTimeMillis())
                }
                dbResult
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
                return@withLock null  // treat local cache failure as miss, degrade gracefully
            }
        }
        if (localHit != null) return localHit

        // Phase 2: Appwrite network call — mutex NOT held.
        // Holding it during remote I/O would block setCommentary (called every ball) and
        // isCacheValid for the full round-trip, stalling live commentary generation.
        //
        // In-flight deduplication: if another coroutine is already fetching this key from
        // Appwrite, reuse its Deferred rather than launching a second remote call. This
        // prevents duplicate LLM invocations when multiple callers miss the local caches
        // simultaneously (e.g. cold start or concurrent ball commentary requests).
        return inFlight.run(key) {
            try {
                val remoteHit = AppwriteAICache.get(key)
                if (remoteHit == null) {
                    null
                } else {
                    val (remoteResult, isPermanent) = remoteHit
                    Log.d(TAG, "Remote hit for key $key (permanent=$isPermanent), warming local caches")
                    // Phase 3: write-back to local caches under mutex
                    mutex.withLock {
                        cache.put(key, remoteResult, permanent = isPermanent)
                        val dbTimestamp =
                            if (isPermanent) CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP else Date().time
                        // Came FROM Appwrite, so it is by definition already mirrored — never queue
                        // it for replay.
                        dao?.insert(
                            AICommentaryCacheEntity(key, remoteResult, dbTimestamp, Date().time, Date().time)
                        )
                    }
                    remoteResult
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
                null  // treat remote failure as miss, degrade gracefully
            }
        }
    }

    // A failed generation must never be persisted: caching the placeholder (or blank) poisons all
    // three tiers and, when permanent, is served forever without ever regenerating.
    // Meta/refusal text is uncacheable for the same reason: "Since there is no specific ball
    // number..." is a failed generation wearing the costume of a successful one, and 76 such rows
    // are sitting permanently in the shared Appwrite cache because nothing rejected them here.
    private fun isUncacheable(commentary: String) =
        commentary.isBlank() ||
            commentary == UNAVAILABLE_PLACEHOLDER ||
            CommentaryFormatter.isMetaResponse(commentary)

    suspend fun setCommentary(commentary: String, key: String, permanent: Boolean = false) = mutex.withLock {
        if (isUncacheable(commentary)) {
            Log.d(TAG, "Skipping cache write for key $key — placeholder/blank commentary")
            return@withLock
        }
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Setting cached commentary"
                level = SentryLevel.INFO
                setData("cacheKey", key)
                setData("contentLength", commentary.length)
                setData("permanent", permanent)
            })

            Log.d(TAG, "Updating cache for key $key (permanent=$permanent)")
            cache.put(key, commentary, permanent = permanent)
            val dbTimestamp = if (permanent) CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP else Date().time
            dao?.insert(AICommentaryCacheEntity(key, commentary, dbTimestamp, Date().time))
            syncToAppwrite(key, commentary, permanent)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            return@withLock  // degrade gracefully — commentary was generated, cache write failing is non-fatal
        }
    }

    // Fire-and-forget mirror to the Appwrite tier; local caches are already written by the caller.
    // The row is inserted with syncedAt = null, so anything that does not land here is picked up by
    // replayUnsyncedPermanent rather than lost.
    private fun syncToAppwrite(key: String, commentary: String, permanent: Boolean) {
        scope.launch {
            try {
                if (AppwriteAICache.set(key, commentary, permanent)) {
                    dao?.markSynced(key, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Appwrite AI cache sync failed for key=$key", e)
                Sentry.captureException(e)
            }
        }
    }

    suspend fun clearCache() = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Clearing AI commentary cache"
                level = SentryLevel.INFO
                setData("cacheSize", cache.size())
            })

            Log.d(TAG, "Clearing all cached AI commentary")
            cache.clear()
            dao?.clearAll()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    suspend fun isCacheValid(key: String): Boolean = mutex.withLock {
        try {
            if (cache.isValid(key)) return@withLock true
            val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
            return@withLock dao?.getCommentary(key, cutoffMs) != null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    suspend fun ingestFromRealtime(key: String, commentary: String) = mutex.withLock {
        try {
            // Skip if this device already has a valid entry (we originated the write)
            if (cache.isValid(key)) return@withLock
            // Memory cold/expired — check Room before accepting the Realtime push.
            // Prevents a stale Realtime replay from overwriting a fresher Room entry
            // after app restart.
            val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
            if (dao?.getCommentary(key, cutoffMs) != null) return@withLock
            val now = Date()
            cache.put(key, commentary)
            // Arrived over Realtime from the Appwrite table, so it is already mirrored.
            dao?.insert(AICommentaryCacheEntity(key, commentary, now.time, now.time, now.time))
            Log.d(TAG, "Realtime: warmed cache for key $key")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to ingest realtime cache entry for key $key", e)
        }
    }
}
