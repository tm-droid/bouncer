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
}
