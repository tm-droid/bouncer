package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.cache.MemoryLruCache
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import com.tanush.bouncer.data.remote.AppwriteAICache
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

object AICacheService {
    private const val TAG = "AICacheService"
    private const val MAX_MEMORY_ENTRIES = 256
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val CACHE_EXPIRATION_INTERVAL_MS = CacheConfig.AI_CACHE_TTL_MS
    // Shared in-memory LRU tier (256 entries, TTL + permanent semantics). Compound
    // memory+Room operations are still serialized by [mutex]; this only guards the map.
    private val cache = MemoryLruCache<String, String>(
        maxSize = MAX_MEMORY_ENTRIES,
        ttlMs = CACHE_EXPIRATION_INTERVAL_MS,
    )
    private var dao: AICacheDao? = null
    // In-flight deduplication: prevents duplicate remote lookups for the same key
    // when multiple coroutines miss memory+Room simultaneously.
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

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
        val deferred: Deferred<String?> = inFlight.computeIfAbsent(key) {
            scope.async {
                try {
                    val remoteHit = AppwriteAICache.get(key) ?: return@async null
                    val (remoteResult, isPermanent) = remoteHit
                    Log.d(TAG, "Remote hit for key $key (permanent=$isPermanent), warming local caches")
                    // Phase 3: write-back to local caches under mutex
                    mutex.withLock {
                        cache.put(key, remoteResult, permanent = isPermanent)
                        val dbTimestamp = if (isPermanent) CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP else Date().time
                        dao?.insert(AICommentaryCacheEntity(key, remoteResult, dbTimestamp))
                    }
                    remoteResult
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Sentry.captureException(e)
                    null  // treat remote failure as miss, degrade gracefully
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return deferred.await()
    }

    suspend fun setCommentary(commentary: String, key: String, permanent: Boolean = false) = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Setting cached commentary"
                level = SentryLevel.INFO
                setData("cacheKey", key)
                setData("contentLength", commentary.length)
                setData("permanent", permanent)
            })

            val now = Date()
            Log.d(TAG, "Updating cache for key $key (permanent=$permanent)")
            cache.put(key, commentary, permanent = permanent)
            val dbTimestamp = if (permanent) CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP else now.time
            dao?.insert(AICommentaryCacheEntity(key, commentary, dbTimestamp))
            scope.launch {
                try {
                    AppwriteAICache.set(key, commentary, permanent)
                } catch (e: Exception) {
                    Log.w(TAG, "Appwrite AI cache sync failed for key=$key", e)
                    Sentry.captureException(e)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            return@withLock  // degrade gracefully — commentary was generated, cache write failing is non-fatal
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
            dao?.insert(AICommentaryCacheEntity(key, commentary, now.time))
            Log.d(TAG, "Realtime: warmed cache for key $key")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to ingest realtime cache entry for key $key", e)
        }
    }
}
