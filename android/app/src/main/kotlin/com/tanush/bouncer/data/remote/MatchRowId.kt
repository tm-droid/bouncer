package com.tanush.bouncer.data.remote

private val UNSAFE_ROW_ID_CHARS = Regex("[^a-zA-Z0-9._-]")

/** Appwrite's row-id length cap, which the server-side sanitiser truncates to. */
private const val MAX_ROW_ID_LENGTH = 36

/**
 * The Appwrite row id `bouncer-match-sync` writes a match under.
 *
 * MUST stay byte-for-byte equivalent to `sanitizeMatchId` in
 * `appwrite/functions/match-sync/src/main.js`:
 *
 * ```js
 * const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 36);
 * ```
 *
 * Deriving the id locally is what allows a per-row Realtime subscription — Appwrite has no
 * query-filtered channels, so without it the only option is the whole-table firehose. A mismatch
 * would produce a silently dead subscription rather than an error, so the parity is pinned by
 * `MatchRowIdTest` and callers additionally verify `match_id` on each event.
 *
 * The regex strips every non-ASCII character before truncation, so JS `slice` (UTF-16 code units)
 * and Kotlin `take` (also UTF-16 code units) cannot diverge here.
 */
internal fun sanitizeMatchId(matchId: String): String =
    matchId.replace(UNSAFE_ROW_ID_CHARS, "").take(MAX_ROW_ID_LENGTH)
