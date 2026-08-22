package com.tanush.bouncer.utils

import android.content.Context
import io.sentry.Sentry

/**
 * Thin wrapper over one SharedPreferences file.
 *
 * Two things were wrong here and both showed up under load rather than in review:
 *
 * 1. Every read and write added a Sentry breadcrumb. Sentry's ring buffer holds ~100 entries, and
 *    `SettingsScreenV2` alone performs nine reads while composing its first frame — so opening
 *    Settings evicted most of the buffer, and whatever crashed next arrived with a timeline of
 *    "Reading boolean preference" and nothing useful. This is the same argument
 *    `.claude/rules/appwrite-patterns.md` already makes for not breadcrumbing per Appwrite
 *    operation. Exceptions are still captured; the routine traffic is not.
 * 2. Every call went through `context.getSharedPreferences(...)`. That is cheap after the first
 *    call but not free, and it obscured that there is exactly one file here. It is resolved once
 *    now, against the application context so a cached instance cannot pin an Activity.
 *
 * Still synchronous, and still SharedPreferences: `apply()` hands the write to a background thread
 * but blocks on `Activity.onPause`, and the first read on a cold start touches disk on whatever
 * thread asks. DataStore is the real fix for both and is a larger change than this file — it
 * turns every accessor into a suspend function or a Flow, which reaches all 30 call sites.
 */
object PreferencesHelper {
    private const val PREFS = "bouncer_prefs"

    @Volatile private var cached: android.content.SharedPreferences? = null

    private fun prefs(context: Context): android.content.SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .also { cached = it }
        }

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
            return prefs(context).getBoolean(key, default)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        try {
            prefs(context).edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun getInt(context: Context, key: String, default: Int): Int {
        try {
            return prefs(context).getInt(key, default)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setInt(context: Context, key: String, value: Int) {
        try {
            prefs(context).edit().putInt(key, value).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    // Follow helpers (simple per-stat follow flag stored under key)
    fun isFollowed(context: Context, statKey: String): Boolean {
        try {
            return prefs(context).getBoolean("follow_$statKey", false)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setFollowed(context: Context, statKey: String, followed: Boolean) {
        try {
            prefs(context).edit().putBoolean("follow_$statKey", followed).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getString(context: Context, key: String, default: String): String {
        try {
            return prefs(context).getString(key, default) ?: default
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setString(context: Context, key: String, value: String) {
        try {
            prefs(context).edit().putString(key, value).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
