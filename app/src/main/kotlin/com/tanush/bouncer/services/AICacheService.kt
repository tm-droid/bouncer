package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import com.tanush.bouncer.data.remote.AppwriteAICache
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

object AICacheService {
    private const val TAG = "AICacheService"
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CommentaryEntry>()
    private var dao: AICacheDao? = null

    private data class CommentaryEntry(
        val commentary: String,
        val timestamp: Date
    )

    private const val CACHE_EXPIRATION_INTERVAL_MS = 3600_000L // 1 hour

    fun initialize(dao: AICacheDao) {
        this.dao = dao
        // Prune stale DB entries from previous sessions at startup
        GlobalScope.launch(Dispatchers.IO) {
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
                if (ageMs < CACHE_EXPIRATION_INTERVAL_MS) {
                    Log.d(TAG, "Memory hit for key $key")
                    return@withLock cached.commentary
                }
                Log.d(TAG, "Memory expired for key $key")
                cache.remove(key)
            }

            // Memory miss — check Room DB
            val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
            val dbResult = dao?.getCommentary(key, cutoffMs)
            if (dbResult != null) {
                Log.d(TAG, "DB hit for key $key, warming memory cache")
                cache[key] = CommentaryEntry(dbResult, Date())
                return@withLock dbResult
            }

            // Room miss — check Appwrite shared cache
            val remoteResult = AppwriteAICache.get(key)
            if (remoteResult != null) {
                Log.d(TAG, "Remote hit for key $key, warming local caches")
                cache[key] = CommentaryEntry(remoteResult, Date())
                dao?.insert(AICommentaryCacheEntity(key, remoteResult, Date().time))
                return@withLock remoteResult
            }

            return@withLock null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    suspend fun setCommentary(commentary: String, key: String) = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Setting cached commentary"
                level = SentryLevel.INFO
                setData("cacheKey", key)
                setData("contentLength", commentary.length)
            })

            val now = Date()
            Log.d(TAG, "Updating cache for key $key")
            cache[key] = CommentaryEntry(commentary, now)
            dao?.insert(AICommentaryCacheEntity(key, commentary, now.time))
            GlobalScope.launch(Dispatchers.IO) {
                AppwriteAICache.set(key, commentary)
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
                return@withLock ageMs < CACHE_EXPIRATION_INTERVAL_MS
            }
            val cutoffMs = System.currentTimeMillis() - CACHE_EXPIRATION_INTERVAL_MS
            return@withLock dao?.getCommentary(key, cutoffMs) != null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
