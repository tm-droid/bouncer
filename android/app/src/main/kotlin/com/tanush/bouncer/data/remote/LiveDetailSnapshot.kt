package com.tanush.bouncer.data.remote

import com.tanush.bouncer.models.Scorecard

/**
 * A server row together with the moment it was last written.
 *
 * Every row `bouncer-match-sync` writes carries an `updated_at`, and until now the device read
 * none of them. That single omission is what made the staleness in `eb91dcd` invisible: a
 * `match-live-detail` row is written only while its match sits in `live-matches.json`, so the
 * instant the match ends the row freezes — up to `SLOW_TIER_INTERVAL_MS` short of the result — and
 * a payload that carries no age looks exactly like one written a second ago, field for field.
 *
 * With an age attached, every surface that merges a payload into list-derived state can state its
 * rule as a comparison rather than a guess: the fresher producer wins, and "fresher" is a number.
 *
 * [updatedAtMs] is null when the column is missing or unparseable. Null means **unknown**, never
 * "old" and never "new" — a caller must fall back to whatever rule it had before rather than
 * treating the row as stale, or a single malformed timestamp would blank a live score.
 */
data class LiveDetailSnapshot(
    val payload: MatchLiveDetailPayload,
    val updatedAtMs: Long?,
) {
    /** See [ScorecardSnapshot.isFresh]: same question, same answer for an unknown age. */
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        updatedAtMs == null || nowMs - updatedAtMs <= ROW_STALE_MS
}

/** As [LiveDetailSnapshot], for the `match-scorecard` row, which freezes by the same mechanism. */
data class ScorecardSnapshot(
    val scorecard: Scorecard,
    val updatedAtMs: Long?,
) {
    /**
     * Whether `match-sync` still appears to be writing this row.
     *
     * A screen holding this row pings `bouncer-match-presence` every 90 seconds, which earns the
     * match a detail-tier pass on every one-minute run, so a row still being written is about a
     * minute old at worst. [ROW_STALE_MS] is two missed passes beyond that.
     *
     * An unreadable `updated_at` returns **true**. Unknown is not stale: the row was the only
     * source before this field existed, and a malformed timestamp must not cost a match its
     * scorecard.
     */
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        updatedAtMs == null || nowMs - updatedAtMs <= ROW_STALE_MS
}

/**
 * How old a `match-sync` row may be before a device-side scrape outranks it.
 *
 * Mirrored deliberately by `DETAIL_STALE_MS` in `MatchDetailViewModel`, which answers the same
 * question for the live-detail row against the matches list. They are separate constants because
 * they are separate decisions — one chooses between two server rows and a scrape, the other
 * between a row and the list — and collapsing them would hide that.
 */
private const val ROW_STALE_MS = 180_000L

/**
 * `updated_at` as epoch millis, or null when it is absent or unreadable.
 *
 * The server writes `new Date().toISOString()`, which `java.time.Instant` parses directly;
 * `java.time` is available from API 26 and `minSdk` is 26. Deliberately silent on a parse failure —
 * a caller treats null as "no age information", which is the same path it took before this existed.
 */
internal fun rowUpdatedAtMs(value: Any?): Long? {
    val raw = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
}
