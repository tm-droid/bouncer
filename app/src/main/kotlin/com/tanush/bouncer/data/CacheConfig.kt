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
    const val AI_CACHE_MAX_PERMANENT_ENTRIES = 500
    const val AI_CACHE_EVICTION_BATCH = 50
    // Local Room ceiling for ai_commentary_cache, evicted least-recently-accessed first.
    // Permanent rows carry AI_CACHE_PERMANENT_TIMESTAMP, so the time-based deleteExpired() can
    // never touch them — without a row ceiling the table grows for the life of the install.
    const val AI_CACHE_MAX_ROWS = 2_000
    // Remote permanent-row eviction costs two billed list reads, so it is amortised rather than
    // run on every permanent write (a completed match writes one row per ball).
    const val AI_CACHE_EVICTION_CHECK_EVERY_N_WRITES = 25
    // Appwrite read-budget guard (402 limit_databases_reads_exceeded protection).
    // Breaker is a latch: once open, a single probe read is allowed only every 6h —
    // the org read budget resets monthly, so fast retries are pointless and billed.
    const val READ_GUARD_PROBE_INTERVAL_MS = 21_600_000L // 6 hours
    const val READ_GUARD_REPORT_INTERVAL_MS = 21_600_000L // Sentry re-alert at most every 6h per reason
    // Rate caps are counted over SLIDING windows. Fixed windows let 2x the cap through across a
    // boundary — 199 reads at t=59s plus 200 at t=61s is 399 reads in two seconds, untripped.
    //
    // Note these are billed-row counts, not call counts: recordSuccess(rows) charges a limit(100)
    // page as 100. A paging loop therefore gets ~2 pages per minute, not ~100. (An earlier comment
    // here claimed ~100 pages fit under this cap; that was wrong by 50x.)
    const val READ_GUARD_MAX_READS_PER_MINUTE = 200
    const val READ_GUARD_MAX_READS_PER_HOUR = 1_500 // ~36K/day absolute ceiling per device
    const val READ_GUARD_MINUTE_WINDOW_MS = 60_000L
    const val READ_GUARD_HOUR_WINDOW_MS = 3_600_000L
    // Tripping a rate cap pauses reads for this long and then self-clears. It deliberately does
    // NOT latch the 402 breaker: a rate cap is local evidence of too many reads, whereas a 402 is
    // the server telling us the month's budget is gone. Conflating them turned a transient
    // over-count into a 6-hour app-wide read outage that survived process restarts.
    const val READ_GUARD_RATE_CAP_PAUSE_MS = 300_000L // 5 minutes
}
