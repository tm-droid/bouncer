package com.tanush.bouncer.utils

/**
 * Names of the SharedPreferences files the app opens, and the keys inside them.
 *
 * The reason this is centralised: two preference *files* holding the same key *name* is invisible
 * in review, silent at runtime, and not findable by searching for the key — the names match, the
 * file differs. The app shipped two instances of exactly that. `haptic_feedback` was written by
 * `PreferencesHelper` into `bouncer_prefs` and read by a second facade out of
 * `bouncer_preferences`, so the Settings toggle changed nothing. `muted_matches` lived in three
 * files, and the live-notification suppression check read the one that nothing ever wrote.
 *
 * Before adding a file here, check whether an existing one already owns the keys.
 * `SharedPreferencesFileInventoryTest` pins the set so a new file cannot arrive unnoticed.
 */
object PrefsRegistry {
    // SharedPreferences file names
    const val PREFS_APP = "bouncer_prefs"
    const val PREFS_REMINDERS = "bouncer_reminders"
    const val PREFS_MATCH_NOTIF = "bouncer_match_notif_prefs"
    const val PREFS_SCORE_POLLING = "score_polling_cache"
    const val PREFS_MATCH_SUBSCRIPTIONS = "match_subscriptions"
    const val PREFS_NOTIFICATION = "bouncer_notification_prefs"
    const val PREFS_FAVOURITES = "favourite_teams"

    object ScorePolling {
        const val FOLLOWUP_COUNT = "live_followup_count"
    }

    object MatchSubscriptions {
        const val SUBSCRIBED_IDS = "subscribed_match_ids"
    }

    object Reminders {
        const val REMINDER_PREFIX = "reminder_"
    }

    object Highlights {
        // Device-global timestamp of the last full highlights-table scan (throttles the
        // table-wide Appwrite read that would otherwise fire on every match-tab open).
        const val LAST_FULL_SYNC_AT = "highlights_last_full_sync_at"

        // Highest server-side `fetched_at` already pulled, so each sync asks only for rows added
        // since. Zero means "never synced" and triggers a page-capped backfill.
        const val LAST_ROW_FETCHED_AT = "highlights_last_row_fetched_at"
    }

    object ReadGuard {
        // Persisted 402 breaker latch (survives process restart so a relaunch after a
        // budget-exhausted 402 keeps short-circuiting reads until the probe interval elapses).
        // Per-quota since the guard learned to tell a bandwidth 402 from a reads 402: the suffix
        // is the Quota enum name.
        const val BREAKER_OPEN_PREFIX = "appwrite_breaker_open_"
        const val BREAKER_OPENED_AT_PREFIX = "appwrite_breaker_opened_at_"
        const val BREAKER_PROBE_INTERVAL_PREFIX = "appwrite_breaker_probe_interval_"

        // Pre-per-quota keys. Read once at startup and folded into the DB_READ breaker, then
        // cleared — without that an in-flight 402 latch would silently reset on upgrade.
        const val LEGACY_BREAKER_OPEN = "appwrite_read_breaker_open"
        const val LEGACY_BREAKER_OPENED_AT = "appwrite_read_breaker_opened_at"
    }
}
