package com.tanush.bouncer.utils

import android.content.Context

object PreferencesHelper {
    private const val PREFS = "bouncer_prefs"

    const val KEY_LIVE_NOTIFICATIONS = "live_notifications"
    const val KEY_BOUNDARY_ALERTS = "boundary_alerts"
    const val KEY_WICKET_ALERTS = "wicket_alerts"
    const val KEY_MATCH_REMINDERS = "match_reminders"
    const val KEY_AI_INSIGHTS = "ai_insights"

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, default)
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    // Follow helpers (simple per-stat follow flag stored under key)
    fun isFollowed(context: Context, statKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("follow_\$statKey", false)
    }

    fun setFollowed(context: Context, statKey: String, followed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("follow_\$statKey", followed).apply()
    }
}
