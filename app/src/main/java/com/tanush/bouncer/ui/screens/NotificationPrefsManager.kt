package com.tanush.bouncer.ui.screens

import android.content.Context
import android.content.SharedPreferences
import com.tanush.bouncer.liveactivity.LiveActivityStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class NotificationPrefsManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    fun updateSettings(transform: (NotificationSettings) -> NotificationSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persist(updated)
    }

    fun updateMatchStartAlerts(enabled: Boolean) = updateSettings { it.copy(matchStartAlerts = enabled) }

    fun updateWicketAlerts(enabled: Boolean) = updateSettings { it.copy(wicketAlerts = enabled) }

    fun updateMilestoneAlerts(enabled: Boolean) = updateSettings { it.copy(milestoneAlerts = enabled) }

    fun updateScoreUpdatesEnabled(enabled: Boolean) = updateSettings { it.copy(scoreUpdatesEnabled = enabled) }

    fun updateScoreFrequency(frequency: NotificationFrequency) =
        updateSettings { it.copy(scoreUpdateFrequency = frequency) }

    fun updateTeamPreference(teamId: String, enabled: Boolean) {
        updateSettings { current ->
            val updatedSet = current.selectedTeamIds.toMutableSet().apply {
                if (enabled) add(teamId) else remove(teamId)
            }
            current.copy(selectedTeamIds = updatedSet)
        }
    }

    fun updateSoundEnabled(enabled: Boolean) = updateSettings { it.copy(soundEnabled = enabled) }

    fun updateSoundProfile(soundProfile: SoundProfile) = updateSettings { it.copy(soundProfile = soundProfile) }

    fun updateHapticFeedback(enabled: Boolean) = updateSettings { it.copy(hapticFeedback = enabled) }

    fun updateVibrationIntensity(intensity: Float) {
        updateSettings { it.copy(vibrationIntensity = intensity.coerceIn(0f, 1f)) }
    }

    fun updateQuietHoursEnabled(enabled: Boolean) = updateSettings { it.copy(quietHoursEnabled = enabled) }

    fun updateQuietHours(range: TimeRange) = updateSettings { it.copy(quietHours = range) }

    fun updateAllowLiveDuringQuietHours(enabled: Boolean) {
        updateSettings { it.copy(allowLiveMatchAlertsDuringQuietHours = enabled) }
    }

    fun updateBatteryOptimization(enabled: Boolean) = updateSettings { it.copy(batteryOptimization = enabled) }

    fun updateGroupSimilarNotifications(enabled: Boolean) =
        updateSettings { it.copy(groupSimilarNotifications = enabled) }

    fun updateShowLockScreenPreview(enabled: Boolean) =
        updateSettings { it.copy(showPreviewsOnLockScreen = enabled) }

    fun updateLiveActivityEnabled(enabled: Boolean) =
        updateSettings { it.copy(liveActivityEnabled = enabled) }

    fun updateLiveActivityStyle(style: LiveActivityStyle) =
        updateSettings { it.copy(liveActivityStyle = style) }

    fun updateLiveActivityFrequency(frequency: NotificationFrequency) =
        updateSettings { it.copy(liveActivityUpdateFrequency = frequency) }

    fun clearNotificationHistory() {
        prefs.edit().putLong(KEY_LAST_HISTORY_CLEAR_TS, System.currentTimeMillis()).apply()
    }

    fun applyQuickPreset(preset: NotificationQuickPreset) {
        updateSettings { current ->
            when (preset) {
                NotificationQuickPreset.BALANCED -> current.copy(
                    matchStartAlerts = true,
                    wicketAlerts = false,
                    milestoneAlerts = true,
                    scoreUpdatesEnabled = true,
                    scoreUpdateFrequency = NotificationFrequency.EVERY_OVER
                )

                NotificationQuickPreset.MINIMAL -> current.copy(
                    matchStartAlerts = true,
                    wicketAlerts = false,
                    milestoneAlerts = false,
                    scoreUpdatesEnabled = true,
                    scoreUpdateFrequency = NotificationFrequency.EVERY_WICKET
                )

                NotificationQuickPreset.MAXIMUM -> current.copy(
                    matchStartAlerts = true,
                    wicketAlerts = true,
                    milestoneAlerts = true,
                    scoreUpdatesEnabled = true,
                    scoreUpdateFrequency = NotificationFrequency.EVERY_RUN
                )
            }
        }
    }

    fun exportSettings(): String {
        val s = _settings.value
        return JSONObject()
            .put(KEY_MATCH_START_ALERTS, s.matchStartAlerts)
            .put(KEY_WICKET_ALERTS, s.wicketAlerts)
            .put(KEY_MILESTONE_ALERTS, s.milestoneAlerts)
            .put(KEY_SCORE_UPDATES_ENABLED, s.scoreUpdatesEnabled)
            .put(KEY_SCORE_UPDATE_FREQUENCY, s.scoreUpdateFrequency.name)
            .put(KEY_SELECTED_TEAMS, JSONArray(s.selectedTeamIds.toList()))
            .put(KEY_SOUND_ENABLED, s.soundEnabled)
            .put(KEY_SOUND_PROFILE, s.soundProfile.name)
            .put(KEY_HAPTIC_FEEDBACK, s.hapticFeedback)
            .put(KEY_VIBRATION_INTENSITY, s.vibrationIntensity.toDouble())
            .put(KEY_QUIET_HOURS_ENABLED, s.quietHoursEnabled)
            .put(KEY_QUIET_START_MINUTE, s.quietHours.startMinuteOfDay)
            .put(KEY_QUIET_END_MINUTE, s.quietHours.endMinuteOfDay)
            .put(KEY_ALLOW_LIVE_DURING_QUIET_HOURS, s.allowLiveMatchAlertsDuringQuietHours)
            .put(KEY_BATTERY_OPTIMIZATION, s.batteryOptimization)
            .put(KEY_GROUP_SIMILAR_NOTIFICATIONS, s.groupSimilarNotifications)
            .put(KEY_SHOW_LOCKSCREEN_PREVIEW, s.showPreviewsOnLockScreen)
            .put(KEY_LIVE_ACTIVITY_ENABLED, s.liveActivityEnabled)
            .put(KEY_LIVE_ACTIVITY_STYLE, s.liveActivityStyle.name)
            .put(KEY_LIVE_ACTIVITY_FREQUENCY, s.liveActivityUpdateFrequency.name)
            .toString()
    }

    fun importSettings(serialized: String): Boolean {
        return runCatching {
            val json = JSONObject(serialized)
            val selectedTeams = mutableSetOf<String>()
            val teamsArray = json.optJSONArray(KEY_SELECTED_TEAMS) ?: JSONArray()
            for (index in 0 until teamsArray.length()) {
                selectedTeams.add(teamsArray.optString(index))
            }

            NotificationSettings(
                matchStartAlerts = json.optBoolean(KEY_MATCH_START_ALERTS, true),
                wicketAlerts = json.optBoolean(KEY_WICKET_ALERTS, false),
                milestoneAlerts = json.optBoolean(KEY_MILESTONE_ALERTS, true),
                scoreUpdatesEnabled = json.optBoolean(KEY_SCORE_UPDATES_ENABLED, true),
                scoreUpdateFrequency = NotificationFrequency.valueOf(
                    json.optString(KEY_SCORE_UPDATE_FREQUENCY, NotificationFrequency.EVERY_OVER.name)
                ),
                selectedTeamIds = selectedTeams,
                soundEnabled = json.optBoolean(KEY_SOUND_ENABLED, true),
                soundProfile = SoundProfile.valueOf(
                    json.optString(KEY_SOUND_PROFILE, SoundProfile.WICKET_CHIME.name)
                ),
                hapticFeedback = json.optBoolean(KEY_HAPTIC_FEEDBACK, true),
                vibrationIntensity = json.optDouble(KEY_VIBRATION_INTENSITY, 0.66).toFloat(),
                quietHoursEnabled = json.optBoolean(KEY_QUIET_HOURS_ENABLED, false),
                quietHours = TimeRange(
                    startMinuteOfDay = json.optInt(KEY_QUIET_START_MINUTE, 23 * 60),
                    endMinuteOfDay = json.optInt(KEY_QUIET_END_MINUTE, 8 * 60)
                ),
                allowLiveMatchAlertsDuringQuietHours =
                    json.optBoolean(KEY_ALLOW_LIVE_DURING_QUIET_HOURS, true),
                batteryOptimization = json.optBoolean(KEY_BATTERY_OPTIMIZATION, true),
                groupSimilarNotifications = json.optBoolean(KEY_GROUP_SIMILAR_NOTIFICATIONS, true),
                showPreviewsOnLockScreen = json.optBoolean(KEY_SHOW_LOCKSCREEN_PREVIEW, false),
                liveActivityEnabled = json.optBoolean(KEY_LIVE_ACTIVITY_ENABLED, false),
                liveActivityStyle = json.optString(KEY_LIVE_ACTIVITY_STYLE, LiveActivityStyle.Compact.name)
                    .let { runCatching { LiveActivityStyle.valueOf(it) }.getOrNull() }
                    ?: LiveActivityStyle.Compact,
                liveActivityUpdateFrequency = json.optString(
                    KEY_LIVE_ACTIVITY_FREQUENCY,
                    NotificationFrequency.EVERY_OVER.name
                ).let { runCatching { NotificationFrequency.valueOf(it) }.getOrNull() }
                    ?: NotificationFrequency.EVERY_OVER
            )
        }.mapCatching { imported ->
            _settings.value = imported
            persist(imported)
            true
        }.getOrElse { false }
    }

    private fun loadSettings(): NotificationSettings {
        val teams = prefs.getStringSet(KEY_SELECTED_TEAMS, setOf("IND", "AUS", "ENG")) ?: emptySet()
        return NotificationSettings(
            matchStartAlerts = prefs.getBoolean(KEY_MATCH_START_ALERTS, true),
            wicketAlerts = prefs.getBoolean(KEY_WICKET_ALERTS, false),
            milestoneAlerts = prefs.getBoolean(KEY_MILESTONE_ALERTS, true),
            scoreUpdatesEnabled = prefs.getBoolean(KEY_SCORE_UPDATES_ENABLED, true),
            scoreUpdateFrequency = prefs.getString(KEY_SCORE_UPDATE_FREQUENCY, NotificationFrequency.EVERY_OVER.name)
                ?.let { runCatching { NotificationFrequency.valueOf(it) }.getOrNull() }
                ?: NotificationFrequency.EVERY_OVER,
            selectedTeamIds = teams,
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            soundProfile = prefs.getString(KEY_SOUND_PROFILE, SoundProfile.WICKET_CHIME.name)
                ?.let { runCatching { SoundProfile.valueOf(it) }.getOrNull() }
                ?: SoundProfile.WICKET_CHIME,
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
            vibrationIntensity = prefs.getFloat(KEY_VIBRATION_INTENSITY, 0.66f),
            quietHoursEnabled = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false),
            quietHours = TimeRange(
                startMinuteOfDay = prefs.getInt(KEY_QUIET_START_MINUTE, 23 * 60),
                endMinuteOfDay = prefs.getInt(KEY_QUIET_END_MINUTE, 8 * 60)
            ),
            allowLiveMatchAlertsDuringQuietHours =
                prefs.getBoolean(KEY_ALLOW_LIVE_DURING_QUIET_HOURS, true),
            batteryOptimization = prefs.getBoolean(KEY_BATTERY_OPTIMIZATION, true),
            groupSimilarNotifications = prefs.getBoolean(KEY_GROUP_SIMILAR_NOTIFICATIONS, true),
            showPreviewsOnLockScreen = prefs.getBoolean(KEY_SHOW_LOCKSCREEN_PREVIEW, false),
            liveActivityEnabled = prefs.getBoolean(KEY_LIVE_ACTIVITY_ENABLED, false),
            liveActivityStyle = prefs.getString(KEY_LIVE_ACTIVITY_STYLE, LiveActivityStyle.Compact.name)
                ?.let { runCatching { LiveActivityStyle.valueOf(it) }.getOrNull() }
                ?: LiveActivityStyle.Compact,
            liveActivityUpdateFrequency = prefs.getString(
                KEY_LIVE_ACTIVITY_FREQUENCY,
                NotificationFrequency.EVERY_OVER.name
            )?.let { runCatching { NotificationFrequency.valueOf(it) }.getOrNull() }
                ?: NotificationFrequency.EVERY_OVER
        )
    }

    private fun persist(settings: NotificationSettings) {
        prefs.edit()
            .putBoolean(KEY_MATCH_START_ALERTS, settings.matchStartAlerts)
            .putBoolean(KEY_WICKET_ALERTS, settings.wicketAlerts)
            .putBoolean(KEY_MILESTONE_ALERTS, settings.milestoneAlerts)
            .putBoolean(KEY_SCORE_UPDATES_ENABLED, settings.scoreUpdatesEnabled)
            .putString(KEY_SCORE_UPDATE_FREQUENCY, settings.scoreUpdateFrequency.name)
            .putStringSet(KEY_SELECTED_TEAMS, settings.selectedTeamIds)
            .putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
            .putString(KEY_SOUND_PROFILE, settings.soundProfile.name)
            .putBoolean(KEY_HAPTIC_FEEDBACK, settings.hapticFeedback)
            .putFloat(KEY_VIBRATION_INTENSITY, settings.vibrationIntensity)
            .putBoolean(KEY_QUIET_HOURS_ENABLED, settings.quietHoursEnabled)
            .putInt(KEY_QUIET_START_MINUTE, settings.quietHours.startMinuteOfDay)
            .putInt(KEY_QUIET_END_MINUTE, settings.quietHours.endMinuteOfDay)
            .putBoolean(
                KEY_ALLOW_LIVE_DURING_QUIET_HOURS,
                settings.allowLiveMatchAlertsDuringQuietHours
            )
            .putBoolean(KEY_BATTERY_OPTIMIZATION, settings.batteryOptimization)
            .putBoolean(KEY_GROUP_SIMILAR_NOTIFICATIONS, settings.groupSimilarNotifications)
            .putBoolean(KEY_SHOW_LOCKSCREEN_PREVIEW, settings.showPreviewsOnLockScreen)
            .putBoolean(KEY_LIVE_ACTIVITY_ENABLED, settings.liveActivityEnabled)
            .putString(KEY_LIVE_ACTIVITY_STYLE, settings.liveActivityStyle.name)
            .putString(KEY_LIVE_ACTIVITY_FREQUENCY, settings.liveActivityUpdateFrequency.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "notification_preferences"

        private const val KEY_MATCH_START_ALERTS = "match_start_alerts"
        private const val KEY_WICKET_ALERTS = "wicket_alerts"
        private const val KEY_MILESTONE_ALERTS = "milestone_alerts"
        private const val KEY_SCORE_UPDATES_ENABLED = "score_updates_enabled"
        private const val KEY_SCORE_UPDATE_FREQUENCY = "score_update_frequency"
        private const val KEY_SELECTED_TEAMS = "selected_teams"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SOUND_PROFILE = "sound_profile"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_START_MINUTE = "quiet_hours_start_minute"
        private const val KEY_QUIET_END_MINUTE = "quiet_hours_end_minute"
        private const val KEY_ALLOW_LIVE_DURING_QUIET_HOURS = "allow_live_during_quiet_hours"
        private const val KEY_BATTERY_OPTIMIZATION = "battery_optimization"
        private const val KEY_GROUP_SIMILAR_NOTIFICATIONS = "group_similar_notifications"
        private const val KEY_SHOW_LOCKSCREEN_PREVIEW = "show_lockscreen_preview"
        private const val KEY_LIVE_ACTIVITY_ENABLED = "live_activity_enabled"
        private const val KEY_LIVE_ACTIVITY_STYLE = "live_activity_style"
        private const val KEY_LIVE_ACTIVITY_FREQUENCY = "live_activity_frequency"
        private const val KEY_LAST_HISTORY_CLEAR_TS = "last_history_clear_ts"

        @Volatile
        private var INSTANCE: NotificationPrefsManager? = null

        fun getInstance(context: Context): NotificationPrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationPrefsManager(context).also { INSTANCE = it }
            }
        }
    }
}

enum class NotificationQuickPreset {
    BALANCED,
    MINIMAL,
    MAXIMUM
}
