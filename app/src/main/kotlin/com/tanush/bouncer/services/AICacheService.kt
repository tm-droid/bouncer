package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import com.tanush.bouncer.data.remote.AppwriteAICache
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

object AICacheService {
    private const val TAG = "AICacheService"
    private const val MAX_MEMORY_ENTRIES = 256
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    // LinkedHashMap with access-order = true gives us LRU semantics for free.
    private val cache: LinkedHashMap<String, CommentaryEntry> =
        object : LinkedHashMap<String, CommentaryEntry>(MAX_MEMORY_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommentaryEntry>?): Boolean =
                size > MAX_MEMORY_ENTRIES
        }
    private var dao: AICacheDao? = null

    private data class CommentaryEntry(
        val commentary: String,
        val timestamp: Date,
        val permanent: Boolean = false
    )

    private val CACHE_EXPIRATION_INTERVAL_MS = CacheConfig.AI_CACHE_TTL_MS

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

    suspend fun getCommentary(key: String): String? = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Getting cached commentary"
                level = SentryLevel.INFO
                setData("cacheKey", key)
            })

            val cached = cache[key]
            if (cached != null) {
                val ageMs = Date().time - cached.timestamp.time
                if (cached.permanent || ageMs < CACHE_EXPIRATION_INTERVAL_MS) {
                    Log.d(TAG, "Memory hit for key $key")
                    return@withLock cached.commentary
                }
                Log.d(TAG, "Memory expired for key $key")
                cache.remove(key)
            }

            // Memory miss — check Room DB (permanent entries have timestamp=Long.MAX_VALUE, always pass cutoff)
            val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
            val dbResult = dao?.getCommentary(key, cutoffMs)
            if (dbResult != null) {
                Log.d(TAG, "DB hit for key $key, warming memory cache")
                cache[key] = CommentaryEntry(dbResult, Date())
                return@withLock dbResult
            }

            // Room miss — check Appwrite shared cache
            val remoteHit = AppwriteAICache.get(key)
            if (remoteHit != null) {
                val (remoteResult, isPermanent) = remoteHit
                Log.d(TAG, "Remote hit for key $key (permanent=$isPermanent), warming local caches")
                cache[key] = CommentaryEntry(remoteResult, Date(), permanent = isPermanent)
                val dbTimestamp = if (isPermanent) CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP else Date().time
                dao?.insert(AICommentaryCacheEntity(key, remoteResult, dbTimestamp))
                return@withLock remoteResult
            }

            return@withLock null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
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
            cache[key] = CommentaryEntry(commentary, now, permanent = permanent)
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
            Sentry.captureException(e)
            throw e
        }
    }

    suspend fun clearCache() = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Clearing AI commentary cache"
                level = SentryLevel.INFO
                setData("cacheSize", cache.size)
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
            val cached = cache[key]
            if (cached != null) {
                val ageMs = Date().time - cached.timestamp.time
                return@withLock cached.permanent || ageMs < CACHE_EXPIRATION_INTERVAL_MS
            }
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
            val existing = cache[key]
            if (existing != null) {
                val ageMs = Date().time - existing.timestamp.time
                if (ageMs < CACHE_EXPIRATION_INTERVAL_MS) return@withLock
            }
            val now = Date()
            cache[key] = CommentaryEntry(commentary, now)
            dao?.insert(AICommentaryCacheEntity(key, commentary, now.time))
            Log.d(TAG, "Realtime: warmed cache for key $key")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ingest realtime cache entry for key $key", e)
        }
    }
}
