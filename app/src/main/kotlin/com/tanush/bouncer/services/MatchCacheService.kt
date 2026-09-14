package com.tanush.bouncer.services

import com.tanush.bouncer.data.AppJson
import android.content.Context
import android.util.Log
import com.tanush.bouncer.data.cache.MemoryFifoCache
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /**
     * The status keys every tier of this cache is addressed by.
     *
     * They are exported because they are not free-form: `getMatches`, `getOfflineMatches` and
     * `getCacheExpirationInterval` all key on the exact string, so a caller that writes its own
     * literal gets a permanent, silent miss. `LiveUpdateSources` used `"live"` against this
     * service's `"Live"` and therefore never resolved a cached match on any device — which cost the
     * Live Update its series name, its team crest and its format at once.
     */
    const val LIVE = "Live"
    const val UPCOMING = "Upcoming"
    const val RECENT = "Recent"

    private const val TAG = "MatchCacheService"
    private const val DISK_PREF_NAME = "match_cache_disk"
    private const val DISK_CACHE_MAX_AGE_MS = 3_600_000L // 1 hour for offline data
    // Deliberately outside DISK_CACHE_MAX_AGE_MS: fixtures stay valid until they start, and each
    // entry's own start time is what expires it. See [lastKnownUpcoming].
    private const val LAST_KNOWN_UPCOMING_KEY = "matches_upcoming_last_known"
    
    private val mutex = Mutex()
    // Shared in-memory tier scaffolding (lock + eviction). The per-status TTL lives in the
    // cache itself; this service never computes entry age, so there is no second clock to
    // get wrong.
    private val cache = MemoryFifoCache<String, List<CricketMatch>>(
        maxSize = 16,
        ttlResolver = { status -> getCacheExpirationInterval(status) },
    )
    private var diskPrefs: android.content.SharedPreferences? = null
    // Last list written per status. Read and written only under [mutex].
    private val lastPersisted = mutableMapOf<String, List<CricketMatch>>()

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
    /**
     * Per-status TTL for the in-memory tier.
     *
     * The Live value is deliberately equal to `LiveActivityViewModel.LIVE_POLL_INTERVAL_MS`, which
     * means `get("Live")` essentially never hits: by the time the poll loop asks again, the entry
     * has just expired. That is the intended behaviour and not a dead tier — a 6-second-old score
     * is not worth serving on a 6-second poll, and the redundant *transfer* is already prevented a
     * layer up by [com.tanush.bouncer.data.MatchesResponseCache]. What this tier is actually for is
     * the offline/stale path, which reads through `peek()` and ignores TTL entirely.
     *
     * If the poll interval changes, this changes with it: the two are one decision.
     */
    private fun getCacheExpirationInterval(status: String): Long {
        try {
            return when (status) {
                LIVE -> 6_000L // matches the Live poll interval; see the KDoc above
                UPCOMING, RECENT -> 60_000L // Upcoming and Recent matches cache expires after 60 seconds
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
        
            // Persist to disk for offline access. Off the caller's thread: the Live tab calls this
            // from viewModelScope (Main) on every 6 s poll, and encoding the list is the cost.
            withContext(Dispatchers.Default) { saveToDisk(status, matches) }
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
            lastPersisted.clear()
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
    
    /**
     * The last non-empty Upcoming list this device saw, minus every fixture that has started.
     *
     * Cricbuzz's Upcoming page is sometimes genuinely empty — it shows "Nothing to show" while
     * fixtures exist — and the `upcoming-matches` Storage file cannot stand in: it is overwritten with
     * the same empty list and its entries carry no start time. Empty on a fresh install.
     */
    suspend fun lastKnownUpcoming(nowMs: Long = System.currentTimeMillis()): List<CricketMatch> = mutex.withLock {
        val json = diskPrefs?.getString(LAST_KNOWN_UPCOMING_KEY, null) ?: return@withLock emptyList()
        upcomingStillAhead(json, nowMs) ?: run {
            Log.w(TAG, "Unreadable last-known Upcoming list")
            Sentry.captureMessage("match_cache: unreadable last-known Upcoming list", SentryLevel.WARNING)
            emptyList()
        }
    }

    // ── Disk persistence helpers ──
    
    private fun saveToDisk(status: String, matches: List<CricketMatch>) {
        try {
            val prefs = diskPrefs ?: return
            val editor = prefs.edit().putLong("timestamp_$status", Date().time)
            // Most polls return the list unchanged. The timestamp must still move (loadFromDisk
            // judges freshness by it), but re-encoding and rewriting an identical blob is waste.
            if (lastPersisted[status] != matches) {
                val json = AppJson.encodeToString(matches)
                editor.putString("matches_$status", json)
                if (status == UPCOMING && matches.isNotEmpty()) editor.putString(LAST_KNOWN_UPCOMING_KEY, json)
                lastPersisted[status] = matches
            }
            editor.apply()
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
            val matches: List<CricketMatch> = AppJson.decodeFromString(json)
            Log.d(TAG, "Loaded ${matches.size} matches from disk cache for $status")
            return matches
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load disk cache for $status", e)
            Sentry.captureException(e)
            return null
        }
    }
}

/**
 * Fixtures from a stored Upcoming list that start after [nowMs], or null when [json] cannot be read.
 * A fixture with no start time is dropped: nothing could ever expire it.
 */
internal fun upcomingStillAhead(json: String, nowMs: Long): List<CricketMatch>? =
    runCatching { AppJson.decodeFromString<List<CricketMatch>>(json) }
        .getOrNull()
        ?.filter { match -> match.startDateMs?.let { it > nowMs } == true }
