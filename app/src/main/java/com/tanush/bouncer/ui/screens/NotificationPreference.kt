package com.tanush.bouncer.ui.screens

import androidx.annotation.StringRes
import com.tanush.bouncer.R
import com.tanush.bouncer.liveactivity.LiveActivityStyle
import java.util.Locale

/**
 * Full notification configuration persisted by NotificationPrefsManager.
 */
data class NotificationSettings(
    val matchStartAlerts: Boolean = true,
    val wicketAlerts: Boolean = false,
    val milestoneAlerts: Boolean = true,
    val scoreUpdatesEnabled: Boolean = true,
    val scoreUpdateFrequency: NotificationFrequency = NotificationFrequency.EVERY_OVER,
    val selectedTeamIds: Set<String> = setOf("IND", "AUS", "ENG"),
    val soundEnabled: Boolean = true,
    val soundProfile: SoundProfile = SoundProfile.WICKET_CHIME,
    val hapticFeedback: Boolean = true,
    val vibrationIntensity: Float = 0.66f,
    val quietHoursEnabled: Boolean = false,
    val quietHours: TimeRange = TimeRange(23 * 60, 8 * 60),
    val allowLiveMatchAlertsDuringQuietHours: Boolean = true,
    val batteryOptimization: Boolean = true,
    val groupSimilarNotifications: Boolean = true,
    val showPreviewsOnLockScreen: Boolean = false,
    val liveActivityEnabled: Boolean = false,
    val liveActivityStyle: LiveActivityStyle = LiveActivityStyle.Compact,
    val liveActivityUpdateFrequency: NotificationFrequency = NotificationFrequency.EVERY_OVER
)

enum class NotificationCategory(@StringRes val titleRes: Int) {
    LIVE_MATCH(R.string.notification_section_live_match_alerts),
    TEAM_SPECIFIC(R.string.notification_section_team_preferences),
    SOUND_HAPTICS(R.string.notification_section_sound_haptics),
    DO_NOT_DISTURB(R.string.notification_section_do_not_disturb),
    ADVANCED(R.string.notification_section_advanced)
}

enum class NotificationFrequency(@StringRes val labelRes: Int) {
    EVERY_RUN(R.string.notification_frequency_every_run),
    EVERY_OVER(R.string.notification_frequency_every_over),
    EVERY_WICKET(R.string.notification_frequency_every_wicket)
}

enum class SoundProfile(@StringRes val labelRes: Int) {
    WICKET_CHIME(R.string.notification_sound_wicket_chime),
    GOAL_HORN(R.string.notification_sound_goal_horn),
    STADIUM_ROAR(R.string.notification_sound_stadium_roar),
    SOFT_PULSE(R.string.notification_sound_soft_pulse),
    CLASSIC_BELL(R.string.notification_sound_classic_bell)
}

data class TimeRange(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
) {
    fun startFormatted(): String = startMinuteOfDay.toTwelveHourFormat()

    fun endFormatted(): String = endMinuteOfDay.toTwelveHourFormat()
}

data class TeamPreference(
    val id: String,
    @StringRes val nameRes: Int,
    val colorHex: String,
    val emoji: String
)

object NotificationTeams {
    val catalog: List<TeamPreference> = listOf(
        TeamPreference(id = "IND", nameRes = R.string.team_india, colorHex = "#1E5AC8", emoji = "\uD83C\uDDEE\uD83C\uDDF3"),
        TeamPreference(id = "AUS", nameRes = R.string.team_australia, colorHex = "#D4AF37", emoji = "\uD83C\uDDE6\uD83C\uDDFA"),
        TeamPreference(id = "ENG", nameRes = R.string.team_england, colorHex = "#224A9A", emoji = "\uD83C\uDDEC\uD83C\uDDE7"),
        TeamPreference(id = "PAK", nameRes = R.string.team_pakistan, colorHex = "#0F8A45", emoji = "\uD83C\uDDF5\uD83C\uDDF0"),
        TeamPreference(id = "SA", nameRes = R.string.team_south_africa, colorHex = "#1E7D47", emoji = "\uD83C\uDDFF\uD83C\uDDE6")
    )
}

fun Float.vibrationBandLabelRes(): Int {
    return when {
        this < 0.34f -> R.string.notification_vibration_low
        this < 0.67f -> R.string.notification_vibration_medium
        else -> R.string.notification_vibration_high
    }
}

fun Int.toTwelveHourFormat(): String {
    val safeMinute = ((this % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hour24 = safeMinute / 60
    val minute = safeMinute % 60
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
}
