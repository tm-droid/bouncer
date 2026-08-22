package com.tanush.bouncer.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tanush.bouncer.data.cache.MemoryFifoCache
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * Thread-safe cache service for cricket matches
 * Mirrors the iOS MatchCacheService implementation
 * 
 * Provides two layers of caching:
 * 1. In-memory TTL-based cache for fast access during active sessions
 * 2. Persistent disk cache for offline support between app launches
 */
object MatchCacheService {
    private const val TAG = "MatchCacheService"
    private const val DISK_PREF_NAME = "match_cache_disk"
    private const val DISK_CACHE_MAX_AGE_MS = 3_600_000L // 1 hour for offline data
    
    private val mutex = Mutex()
    // Shared in-memory tier scaffolding (lock + eviction). The per-status TTL lives in the
    // cache itself; this service never computes entry age, so there is no second clock to
    // get wrong.
    private val cache = MemoryFifoCache<String, List<CricketMatch>>(
        maxSize = 16,
        ttlResolver = { status -> getCacheExpirationInterval(status) },
    )
    private val gson = Gson()
    private var diskPrefs: android.content.SharedPreferences? = null

    /**
     * Initialize disk persistence for offline support.
     * Call this once during Application.onCreate or BouncerActivity.onCreate.
     */
    fun initDiskCache(context: Context) {
        try {
            diskPrefs = context.getSharedPreferences(DISK_PREF_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "Disk cache initialized")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Different cache expiration intervals for different match types
     */
    private fun getCacheExpirationInterval(status: String): Long {
        try {
            return when (status) {
                "Live" -> 6_000L // Live matches cache expires after 6 seconds
                "Upcoming", "Recent" -> 60_000L // Upcoming and Recent matches cache expires after 60 seconds
                else -> 30_000L // Default fallback
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get cached matches for a specific status if cache is still valid.
     * Falls back to disk cache if memory cache is expired.
     */
    suspend fun getMatches(status: String): List<CricketMatch>? = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.cache"
                message = "Getting cached matches"
                level = SentryLevel.INFO
                setData("status", status)
            })
            
            // Try memory cache first
            val cached = cache.get(status)
            if (cached != null) {
                Log.d(TAG, "Using memory-cached data for $status")
                return@withLock cached
            }

            Log.d(TAG, "Memory cache expired or missing for $status")
            return@withLock null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get offline cached data when network is unavailable.
     * Returns stale data from disk if available (up to 1 hour old).
     */
    suspend fun getOfflineMatches(status: String): List<CricketMatch>? = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.cache"
                message = "Getting offline cached matches"
                level = SentryLevel.INFO
                setData("status", status)
            })
            
            // First try memory cache regardless of TTL
            val memoryCached = cache.peek(status)
            if (memoryCached != null) {
                Log.d(TAG, "Using stale memory cache for offline: $status")
                return@withLock memoryCached
            }
        
            // Fall back to disk cache
            return@withLock loadFromDisk(status)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Update cache with new matches for a specific status
     */
    suspend fun updateCache(matches: List<CricketMatch>, status: String) = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.cache"
                message = "Updating match cache"
                level = SentryLevel.INFO
                setData("status", status)
                setData("matchCount", matches.size)
            })
            
            Log.d(TAG, "Updating cache for $status with ${matches.size} matches")
            cache.put(status, matches)
        
            // Persist to disk for offline access (non-blocking)
            saveToDisk(status, matches)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Clear all cached data
     */
    suspend fun clearCache() = mutex.withLock {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.cache"
                message = "Clearing all match cache"
                level = SentryLevel.INFO
                setData("cacheSize", cache.size())
            })

            Log.d(TAG, "Clearing all cached data")
            cache.clear()
            diskPrefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if cache is still valid for a specific status (suspend version)
     */
    suspend fun isCacheValid(status: String): Boolean = mutex.withLock {
        try {
            return@withLock cache.isValid(status)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    // ── Disk persistence helpers ──
    
    private fun saveToDisk(status: String, matches: List<CricketMatch>) {
        try {
            val prefs = diskPrefs ?: return
            val json = gson.toJson(matches)
            prefs.edit()
                .putString("matches_$status", json)
                .putLong("timestamp_$status", Date().time)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist matches to disk for $status", e)
            Sentry.captureException(e)
        }
    }
    
    private fun loadFromDisk(status: String): List<CricketMatch>? {
        try {
            val prefs = diskPrefs ?: return null
            val timestamp = prefs.getLong("timestamp_$status", 0L)
            val age = Date().time - timestamp
            
            if (age > DISK_CACHE_MAX_AGE_MS) {
                Log.d(TAG, "Disk cache too old for $status (age: ${age}ms)")
                return null
            }
            
            val json = prefs.getString("matches_$status", null) ?: return null
            val type = object : TypeToken<List<CricketMatch>>() {}.type
            val matches: List<CricketMatch> = gson.fromJson(json, type)
            Log.d(TAG, "Loaded ${matches.size} matches from disk cache for $status")
            return matches
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load disk cache for $status", e)
            Sentry.captureException(e)
            return null
        }
    }
}
