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
}
