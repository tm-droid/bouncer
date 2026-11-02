package com.tanush.bouncer.data

import android.os.SystemClock
import com.tanush.bouncer.data.cache.MemoryFifoCache
import com.tanush.bouncer.network.models.SanwebMatchesListResponse

/**
 * Short-lived memory cache for the raw match-list response, keyed by list type.
 *
 * This exists because [CricketRepository.getMatches] had no cache of its own: it downloaded the
 * whole matches JSON from Appwrite Storage and only *then* checked the embedded `fetchedAt`
 * freshness stamp. The Live tab polls every 6s while the backend cron writes the file once a
 * minute, so roughly nine of every ten downloads paid full bandwidth for bytes the caller already
 * had.
 *
 * Deliberately separate from [com.tanush.bouncer.services.MatchCacheService], which caches mapped
 * domain models for the offline/stale-read path and whose 6s Live TTL is tuned to the poll loop
 * (equal to the poll interval, so it can never hit). This one caches the untouched network
 * response and exists purely to stop redundant transfers.
 */
class MatchesResponseCache(
    uptimeMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val cache = MemoryFifoCache<String, SanwebMatchesListResponse>(
        maxSize = MAX_ENTRIES,
        ttlResolver = ::ttlFor,
        uptimeMs = uptimeMs,
    )

    fun get(type: String): SanwebMatchesListResponse? = cache.get(normalize(type))

    fun put(type: String, response: SanwebMatchesListResponse) = cache.put(normalize(type), response)

    fun clear() = cache.clear()

    companion object {
        /**
         * The instance [CricketRepository] serves from. Exposed so Settings → Clear cache can drop
         * it directly; tests construct their own instance with a fake clock instead.
         */
        val shared = MatchesResponseCache()

        // Only three list types exist (live/upcoming/recent); the ceiling is just a guard against
        // an unexpected caller inventing keys.
        private const val MAX_ENTRIES = 8

        private fun normalize(type: String) = type.lowercase()

        /**
         * Live scores genuinely change; upcoming and recent fixtures change a few times a day, so
         * they tolerate a far longer TTL. Both sit above the backend's one-minute write cadence,
         * which is what makes the redundant downloads disappear.
         */
        fun ttlFor(type: String): Long = when (normalize(type)) {
            "live" -> CacheConfig.MATCHES_RESPONSE_TTL_LIVE_MS
            else -> CacheConfig.MATCHES_RESPONSE_TTL_STATIC_MS
        }
    }
}
