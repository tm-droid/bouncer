package com.tanush.bouncer.services

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * Thread-safe cache service for AI-generated commentary
 * Mirrors the iOS AICacheService implementation
 */
object AICacheService {
    private const val TAG = "AICacheService"
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CommentaryEntry>()
    
    private data class CommentaryEntry(
        val commentary: String,
        val timestamp: Date
    )
    
    // AI commentary is less volatile, so cache for 1 hour by default
    private const val CACHE_EXPIRATION_INTERVAL_MS = 3600_000L // 1 hour
    
    /**
     * Get cached commentary for a specific key if cache is still valid
     */
    suspend fun getCommentary(key: String): String? = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Getting cached commentary"
                level = SentryLevel.INFO
                setData("cacheKey", key)
            })
            
            val cached = cache[key] ?: return@withLock null
            val now = Date()
            val ageMs = now.time - cached.timestamp.time
            
            if (ageMs < CACHE_EXPIRATION_INTERVAL_MS) {
                Log.d(TAG, "Using cached commentary for key $key (valid for ${CACHE_EXPIRATION_INTERVAL_MS}ms)")
                return@withLock cached.commentary
            }
            
            Log.d(TAG, "Cache expired for key $key")
            return@withLock null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Set cached commentary for a specific key
     */
    suspend fun setCommentary(commentary: String, key: String) = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.ai"
                message = "Setting cached commentary"
                level = SentryLevel.INFO
                setData("cacheKey", key)
                setData("contentLength", commentary.length)
            })
            
            Log.d(TAG, "Updating cache for key $key")
            cache[key] = CommentaryEntry(commentary, Date())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Clear all cached AI commentary
     */
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
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if cache is still valid for a specific key
     */
    suspend fun isCacheValid(key: String): Boolean = mutex.withLock {
        try {
            val cached = cache[key] ?: return@withLock false
            val now = Date()
            val ageMs = now.time - cached.timestamp.time
            return@withLock ageMs < CACHE_EXPIRATION_INTERVAL_MS
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
