package com.tanush.bouncer.services

import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * Thread-safe cache service for cricket matches
 * Mirrors the iOS MatchCacheService implementation
 */
object MatchCacheService {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    
    private data class CacheEntry(
        val matches: List<CricketMatch>,
        val timestamp: Date
    )
    
    /**
     * Different cache expiration intervals for different match types
     */
    private fun getCacheExpirationInterval(status: String): Long {
        return when (status) {
            "Live" -> 6_000L // Live matches cache expires after 6 seconds
            "Upcoming", "Recent" -> 60_000L // Upcoming and Recent matches cache expires after 60 seconds
            else -> 30_000L // Default fallback
        }
    }
    
    /**
     * Get cached matches for a specific status if cache is still valid
     */
    suspend fun getMatches(status: String): List<CricketMatch>? = mutex.withLock {
        val cached = cache[status] ?: return@withLock null
        val now = Date()
        val ageMs = now.time - cached.timestamp.time
        
        if (ageMs < getCacheExpirationInterval(status)) {
            println("MatchCacheService: Using cached data for $status (valid for ${getCacheExpirationInterval(status)}ms)")
            return@withLock cached.matches
        }
        
        println("MatchCacheService: Cache expired for $status")
        return@withLock null
    }
    
    /**
     * Update cache with new matches for a specific status
     */
    suspend fun updateCache(matches: List<CricketMatch>, status: String) = mutex.withLock {
        println("MatchCacheService: Updating cache for $status with ${matches.size} matches (valid for ${getCacheExpirationInterval(status)}ms)")
        cache[status] = CacheEntry(matches, Date())
    }
    
    /**
     * Clear all cached data
     */
    suspend fun clearCache() = mutex.withLock {
        println("MatchCacheService: Clearing all cached data")
        cache.clear()
    }
    
    /**
     * Check if cache is still valid for a specific status
     */
    suspend fun isCacheValid(status: String): Boolean = mutex.withLock {
        val cached = cache[status] ?: return@withLock false
        val now = Date()
        val ageMs = now.time - cached.timestamp.time
        return@withLock ageMs < getCacheExpirationInterval(status)
    }
}
