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
    
    // Appearance keys
    const val KEY_THEME_VARIANT = "theme_variant"
    const val KEY_BACKGROUND_SKIN = "background_skin"

    // UX behavior and accessibility keys
    const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
    const val KEY_AUTO_REFRESH = "auto_refresh"
    const val KEY_SHOW_WIN_PROBABILITY = "show_win_probability"
    const val KEY_REDUCED_MOTION = "reduced_motion"
    const val KEY_REFRESH_INTERVAL = "refresh_interval"

    // Personalization defaults
    const val KEY_DEFAULT_LANDING = "default_landing"
    const val KEY_ONBOARDING_TEAMS = "onboarding_teams"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_PREFERRED_CATEGORY = "preferred_category"

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

    fun getInt(context: Context, key: String, default: Int): Int {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getInt(key, default)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setInt(context: Context, key: String, value: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putInt(key, value).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    // Follow helpers (simple per-stat follow flag stored under key)
    fun isFollowed(context: Context, statKey: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean("follow_$statKey", false)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setFollowed(context: Context, statKey: String, followed: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("follow_$statKey", followed).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getString(context: Context, key: String, default: String): String {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "util.preferences"
                message = "Reading string preference"
                level = SentryLevel.DEBUG
                setData("key", key)
            })
            
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getString(key, default) ?: default
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setString(context: Context, key: String, value: String) {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "util.preferences"
                message = "Setting string preference"
                level = SentryLevel.DEBUG
                setData("key", key)
                setData("value", value)
            })
            
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
