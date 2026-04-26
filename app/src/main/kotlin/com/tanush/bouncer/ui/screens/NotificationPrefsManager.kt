package com.tanush.bouncer.ui.screens

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuietHoursSettings(
    val startMinuteOfDay: Int = 22 * 60,
    val endMinuteOfDay: Int = 7 * 60
)

data class NotificationSettings(
    val quietHoursEnabled: Boolean = false,
    val allowLiveMatchAlertsDuringQuietHours: Boolean = true,
    val quietHours: QuietHoursSettings = QuietHoursSettings()
)

object NotificationPrefsManager {
    private const val PREFS_NAME = "bouncer_notification_prefs"
    private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
    private const val KEY_ALLOW_LIVE_DURING_QUIET = "allow_live_during_quiet"
    private const val KEY_QUIET_START = "quiet_start_minute"
    private const val KEY_QUIET_END = "quiet_end_minute"

    private val _settings = MutableStateFlow(NotificationSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    fun getInstance(context: Context): NotificationPrefsManager {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            load()
        }
        return this
    }

    fun update(block: NotificationSettings.() -> NotificationSettings) {
        _settings.value = block(_settings.value)
        persist()
    }

    private fun load() {
        val p = prefs ?: return
        _settings.value = NotificationSettings(
            quietHoursEnabled = p.getBoolean(KEY_QUIET_HOURS_ENABLED, false),
            allowLiveMatchAlertsDuringQuietHours = p.getBoolean(KEY_ALLOW_LIVE_DURING_QUIET, true),
            quietHours = QuietHoursSettings(
                startMinuteOfDay = p.getInt(KEY_QUIET_START, 22 * 60),
                endMinuteOfDay = p.getInt(KEY_QUIET_END, 7 * 60)
            )
        )
    }

    private fun persist() {
        val s = _settings.value
        prefs?.edit()
            ?.putBoolean(KEY_QUIET_HOURS_ENABLED, s.quietHoursEnabled)
            ?.putBoolean(KEY_ALLOW_LIVE_DURING_QUIET, s.allowLiveMatchAlertsDuringQuietHours)
            ?.putInt(KEY_QUIET_START, s.quietHours.startMinuteOfDay)
            ?.putInt(KEY_QUIET_END, s.quietHours.endMinuteOfDay)
            ?.apply()
    }
}
