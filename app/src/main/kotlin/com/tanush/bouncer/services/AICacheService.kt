package com.tanush.bouncer.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * Thread-safe cache service for AI-generated commentary
 * Mirrors the iOS AICacheService implementation
 */
object AICacheService {
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
        val cached = cache[key] ?: return@withLock null
        val now = Date()
        val ageMs = now.time - cached.timestamp.time
        
        if (ageMs < CACHE_EXPIRATION_INTERVAL_MS) {
            println("AICacheService: Using cached commentary for key $key (valid for ${CACHE_EXPIRATION_INTERVAL_MS}ms)")
            return@withLock cached.commentary
        }
        
        println("AICacheService: Cache expired for key $key")
        return@withLock null
    }
    
    /**
     * Set cached commentary for a specific key
     */
    suspend fun setCommentary(commentary: String, key: String) = mutex.withLock {
        println("AICacheService: Updating cache for key $key")
        cache[key] = CommentaryEntry(commentary, Date())
    }
    
    /**
     * Clear all cached AI commentary
     */
    suspend fun clearCache() = mutex.withLock {
        println("AICacheService: Clearing all cached AI commentary")
        cache.clear()
    }
    
    /**
     * Check if cache is still valid for a specific key
     */
    suspend fun isCacheValid(key: String): Boolean = mutex.withLock {
        val cached = cache[key] ?: return@withLock false
        val now = Date()
        val ageMs = now.time - cached.timestamp.time
        return@withLock ageMs < CACHE_EXPIRATION_INTERVAL_MS
    }
}
