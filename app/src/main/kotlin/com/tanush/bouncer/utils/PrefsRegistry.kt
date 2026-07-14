package com.tanush.bouncer.utils

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
    }

    object ReadGuard {
        // Persisted 402 breaker latch (survives process restart so a relaunch after a
        // budget-exhausted 402 keeps short-circuiting reads until the probe interval elapses).
        const val BREAKER_OPEN = "appwrite_read_breaker_open"
        const val BREAKER_OPENED_AT = "appwrite_read_breaker_opened_at"
    }
}
