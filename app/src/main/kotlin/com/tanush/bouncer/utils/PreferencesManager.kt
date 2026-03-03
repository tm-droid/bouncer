package com.tanush.bouncer.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.sentry.Sentry

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "bouncer_preferences",
        Context.MODE_PRIVATE
    )
    
    // Theme preferences
    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
    
    var useSystemTheme: Boolean
        get() = prefs.getBoolean(KEY_USE_SYSTEM_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_SYSTEM_THEME, value).apply()
    
    // Notifications
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    
    var liveMatchNotifications: Boolean
        get() = prefs.getBoolean(KEY_LIVE_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_NOTIFICATIONS, value).apply()
    
    var favouriteTeamNotifications: Boolean
        get() = prefs.getBoolean(KEY_FAVOURITE_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_FAVOURITE_NOTIFICATIONS, value).apply()
    
    // App behavior
    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()
    
    var animationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()
    
    var autoRefreshEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REFRESH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REFRESH, value).apply()
    
    var refreshInterval: Int
        get() = prefs.getInt(KEY_REFRESH_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_REFRESH_INTERVAL, value).apply()
    
    // Display preferences
    var showMatchPredictions: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PREDICTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_PREDICTIONS, value).apply()
    
    var showDetailedStats: Boolean
        get() = prefs.getBoolean(KEY_DETAILED_STATS, true)
        set(value) = prefs.edit().putBoolean(KEY_DETAILED_STATS, value).apply()
    
    var compactMode: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_MODE, value).apply()
    
    // Filter preferences
    var defaultFilter: String
        get() = prefs.getString(KEY_DEFAULT_FILTER, "All") ?: "All"
        set(value) = prefs.edit().putString(KEY_DEFAULT_FILTER, value).apply()
    
    var lastSelectedCategory: String
        get() = prefs.getString(KEY_LAST_CATEGORY, "International") ?: "International"
        set(value) = prefs.edit().putString(KEY_LAST_CATEGORY, value).apply()
    
    // Data preferences
    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()
    
    var cacheExpiry: Int
        get() = prefs.getInt(KEY_CACHE_EXPIRY, 300)
        set(value) = prefs.edit().putInt(KEY_CACHE_EXPIRY, value).apply()
    
    var dataUsageWarning: Boolean
        get() = prefs.getBoolean(KEY_DATA_WARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_DATA_WARNING, value).apply()
    
    // First launch
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
    
    // App version for migration
    var lastAppVersion: String
        get() = prefs.getString(KEY_APP_VERSION, "1.0.0") ?: "1.0.0"
        set(value) = prefs.edit().putString(KEY_APP_VERSION, value).apply()
    
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
            // Clear only cache-related preferences
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
        // Keys
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
        
        fun getInstance(context: Context): PreferencesManager {
            try {
                return instance ?: synchronized(this) {
                    instance ?: PreferencesManager(context.applicationContext).also {
                        instance = it
                    }
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}

@Composable
fun rememberPreferencesManager(): PreferencesManager {
    val context = LocalContext.current
    return remember { PreferencesManager.getInstance(context) }
}
