package com.tanush.bouncer.data

object CacheConfig {
    const val AI_CACHE_TTL_MS = 3_600_000L       // 1 hour
    const val HIGHLIGHTS_FRESHNESS_MS = 3_600_000L // 1 hour
    // Sentinel for Room DB rows that should never expire (completed-match commentary).
    // Long.MAX_VALUE > any cutoffMs query → always returned by getCommentary().
    const val AI_CACHE_PERMANENT_TIMESTAMP = Long.MAX_VALUE
    const val PLAYER_FORM_TTL_MS = 24 * 3_600_000L // 24 hours
    const val PLAYER_PROFILE_TTL_MS = 7L * 24 * 3_600_000L // 7 days — mirrors Room worker TTL
    // 2.5 min: Appwrite poll-live-matches runs every 1 min; 150s gives a half-cycle buffer
    // so a slow or skipped run doesn't immediately fall through to direct Vercel polling.
    const val MATCH_CACHE_TTL_MS = 150_000L
    // Appwrite Realtime reconnect backoff (exponential, reset on successful subscribe).
    const val REALTIME_RECONNECT_MIN_MS = 2_000L
    const val REALTIME_RECONNECT_MAX_MS = 60_000L
    // Appwrite sentinel for rows that should never expire (completed-match commentary).
    const val AI_CACHE_PERMANENT_EXPIRY_ISO = "2099-01-01T00:00:00.000Z"
    // Any expiry more than a year out is treated as the permanent sentinel on read.
    const val AI_CACHE_PERMANENT_THRESHOLD_MS = 365L * 24 * 60 * 60 * 1000
}
