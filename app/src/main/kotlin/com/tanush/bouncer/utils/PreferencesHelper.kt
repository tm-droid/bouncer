package com.tanush.bouncer.utils

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object PreferencesHelper {
    private const val PREFS = "bouncer_prefs"

    const val KEY_LIVE_NOTIFICATIONS = "live_notifications"
    const val KEY_BOUNDARY_ALERTS = "boundary_alerts"
    const val KEY_WICKET_ALERTS = "wicket_alerts"
    const val KEY_MATCH_REMINDERS = "match_reminders"
    const val KEY_AI_INSIGHTS = "ai_insights"

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "util.preferences"
                message = "Reading boolean preference"
                level = SentryLevel.DEBUG
                setData("key", key)
            })
            
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(key, default)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "util.preferences"
                message = "Setting boolean preference"
                level = SentryLevel.DEBUG
                setData("key", key)
                setData("value", value)
            })
            
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    // Follow helpers (simple per-stat follow flag stored under key)
    fun isFollowed(context: Context, statKey: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean("follow_\$statKey", false)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setFollowed(context: Context, statKey: String, followed: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("follow_\$statKey", followed).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
