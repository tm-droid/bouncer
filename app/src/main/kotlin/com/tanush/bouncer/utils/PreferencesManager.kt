package com.tanush.bouncer.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.sentry.Sentry
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "bouncer_preferences",
        Context.MODE_PRIVATE,
    )

    private fun boolPref(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    private fun intPref(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            prefs.getInt(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun stringPref(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            prefs.getString(key, default) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            prefs.edit().putString(key, value).apply()
        }
    }

    // Theme preferences
    var isDarkMode: Boolean by boolPref(KEY_DARK_MODE, true)
    var useSystemTheme: Boolean by boolPref(KEY_USE_SYSTEM_THEME, false)

    // Notifications
    var notificationsEnabled: Boolean by boolPref(KEY_NOTIFICATIONS, true)
    var liveMatchNotifications: Boolean by boolPref(KEY_LIVE_NOTIFICATIONS, true)
    var favouriteTeamNotifications: Boolean by boolPref(KEY_FAVOURITE_NOTIFICATIONS, true)

    // App behavior
    var hapticFeedbackEnabled: Boolean by boolPref(KEY_HAPTIC_FEEDBACK, true)
    var animationsEnabled: Boolean by boolPref(KEY_ANIMATIONS, true)
    var autoRefreshEnabled: Boolean by boolPref(KEY_AUTO_REFRESH, true)
    /** Fallback poll interval in MINUTES. Consumed by WidgetUpdateWorker.resolveUpdateDelayMs. */
    var refreshInterval: Int by intPref(KEY_REFRESH_INTERVAL, 30)

    // Display preferences
    var showMatchPredictions: Boolean by boolPref(KEY_SHOW_PREDICTIONS, true)
    var showDetailedStats: Boolean by boolPref(KEY_DETAILED_STATS, true)
    var compactMode: Boolean by boolPref(KEY_COMPACT_MODE, false)

    // Filter preferences
    var defaultFilter: String by stringPref(KEY_DEFAULT_FILTER, "All")
    var lastSelectedCategory: String by stringPref(KEY_LAST_CATEGORY, "International")

    // Data preferences
    var cacheEnabled: Boolean by boolPref(KEY_CACHE_ENABLED, true)
    var cacheExpiry: Int by intPref(KEY_CACHE_EXPIRY, 300)
    var dataUsageWarning: Boolean by boolPref(KEY_DATA_WARNING, true)

    // First launch
    var isFirstLaunch: Boolean by boolPref(KEY_FIRST_LAUNCH, true)

    // App version for migration
    var lastAppVersion: String by stringPref(KEY_APP_VERSION, "1.0.0")

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun clearCache() {
        try {
            prefs.edit()
                .remove("cached_matches")
                .remove("cached_timestamp")
                .apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_USE_SYSTEM_THEME = "use_system_theme"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_LIVE_NOTIFICATIONS = "live_notifications"
        private const val KEY_FAVOURITE_NOTIFICATIONS = "favourite_notifications"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_ANIMATIONS = "animations"
        private const val KEY_AUTO_REFRESH = "auto_refresh"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval"
        private const val KEY_SHOW_PREDICTIONS = "show_predictions"
        private const val KEY_DETAILED_STATS = "detailed_stats"
        private const val KEY_COMPACT_MODE = "compact_mode"
        private const val KEY_DEFAULT_FILTER = "default_filter"
        private const val KEY_LAST_CATEGORY = "last_category"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_CACHE_EXPIRY = "cache_expiry"
        private const val KEY_DATA_WARNING = "data_warning"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_APP_VERSION = "app_version"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }
}

@Composable
fun rememberPreferencesManager(): PreferencesManager {
    val context = LocalContext.current
    return remember { PreferencesManager.getInstance(context) }
}
