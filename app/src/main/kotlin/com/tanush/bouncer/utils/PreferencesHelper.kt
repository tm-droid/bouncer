package com.tanush.bouncer.utils

import android.content.Context
import com.tanush.bouncer.data.prefs.BouncerPreferences
import com.tanush.bouncer.data.prefs.PreferenceStore
import kotlinx.coroutines.flow.Flow

/**
 * Thin, synchronous facade over [BouncerPreferences] — the app's DataStore-backed main preference
 * store.
 *
 * Two things used to be wrong here and both showed up under load rather than in review:
 *
 * 1. Every read and write added a Sentry breadcrumb. Sentry's ring buffer holds ~100 entries, and
 *    `SettingsScreenV2` alone performs nine reads while composing its first frame — so opening
 *    Settings evicted most of the buffer, and whatever crashed next arrived with a timeline of
 *    "Reading boolean preference" and nothing useful. This is the same argument
 *    `.claude/rules/appwrite-patterns.md` already makes for not breadcrumbing per Appwrite
 *    operation.
 * 2. Storage was SharedPreferences, whose `apply()` is drained on `Activity.onPause` — a burst of
 *    Settings toggles stalls the way out of the screen — and which offers no way to observe a
 *    change, so every consumer cached a value in `remember` and went stale.
 *
 * The 34 call sites keep taking a [Context] and keep returning values synchronously, deliberately:
 * swapping the storage engine and reshaping every accessor into a suspend function in one change
 * would make a storage bug and a threading bug indistinguishable. The [Context] is now used only
 * to initialize the store on first touch. Prefer the `flowOf*` accessors in new code — they are
 * the reason the migration was worth doing.
 */
object PreferencesHelper {
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
    const val KEY_SHARE_DIAGNOSTICS = "share_diagnostics"

    // Personalization defaults
    const val KEY_DEFAULT_LANDING = "default_landing"
    const val KEY_ONBOARDING_TEAMS = "onboarding_teams"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_PREFERRED_CATEGORY = "preferred_category"
    const val KEY_PREFERRED_GENDER = "preferred_gender"

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean {
        PreferenceStore.initialize(context)
        return BouncerPreferences.getBoolean(key, default)
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        PreferenceStore.initialize(context)
        BouncerPreferences.putBoolean(key, value)
    }

    fun getInt(context: Context, key: String, default: Int): Int {
        PreferenceStore.initialize(context)
        return BouncerPreferences.getInt(key, default)
    }

    fun setInt(context: Context, key: String, value: Int) {
        PreferenceStore.initialize(context)
        BouncerPreferences.putInt(key, value)
    }

    fun getString(context: Context, key: String, default: String): String {
        PreferenceStore.initialize(context)
        return BouncerPreferences.getString(key, default)
    }

    fun setString(context: Context, key: String, value: String) {
        PreferenceStore.initialize(context)
        BouncerPreferences.putString(key, value)
    }

    // Follow helpers (simple per-stat follow flag stored under key)
    fun isFollowed(context: Context, statKey: String): Boolean =
        getBoolean(context, followKey(statKey), false)

    fun setFollowed(context: Context, statKey: String, followed: Boolean) =
        setBoolean(context, followKey(statKey), followed)

    private fun followKey(statKey: String) = "follow_$statKey"

    /**
     * Observable read. Unlike [getBoolean] this re-emits when the value changes, so a toggle on
     * the Settings screen reaches a consumer that is already composed.
     */
    fun flowOfBoolean(context: Context, key: String, default: Boolean): Flow<Boolean> {
        PreferenceStore.initialize(context)
        return BouncerPreferences.flowOfBoolean(key, default)
    }
}
