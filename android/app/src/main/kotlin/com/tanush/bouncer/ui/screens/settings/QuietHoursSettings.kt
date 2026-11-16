package com.tanush.bouncer.ui.screens.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.screens.NotificationPrefsManager
import java.util.Calendar

private const val MINUTES_PER_HOUR = 60

/**
 * Quiet hours, enforced by `MatchAlertGate` (every match alert is muted) and `LiveUpdateNotifier`
 * (the card loses its lock-screen promotion unless the pinned-card switch is on).
 */
@Composable
internal fun QuietHoursItems() {
    val context = LocalContext.current
    val manager = remember { NotificationPrefsManager.getInstance(context) }
    val settings by manager.settings.collectAsStateWithLifecycle()
    SettingsItemV2(
        icon = Icons.Default.DarkMode,
        title = stringResource(R.string.settings_quiet_hours),
        subtitle = stringResource(R.string.settings_quiet_hours_subtitle),
        trailing = SettingsTrailing.Toggle(settings.quietHoursEnabled),
        onClick = { manager.update { copy(quietHoursEnabled = !quietHoursEnabled) } },
    )
    if (!settings.quietHoursEnabled) return
    QuietTimeItem(R.string.settings_quiet_from, settings.quietHours.startMinuteOfDay) { minute ->
        manager.update { copy(quietHours = quietHours.copy(startMinuteOfDay = minute)) }
    }
    QuietTimeItem(R.string.settings_quiet_until, settings.quietHours.endMinuteOfDay) { minute ->
        manager.update { copy(quietHours = quietHours.copy(endMinuteOfDay = minute)) }
    }
    SettingsItemV2(
        icon = Icons.Default.PushPin,
        title = stringResource(R.string.settings_quiet_live_card),
        subtitle = stringResource(R.string.settings_quiet_live_card_subtitle),
        trailing = SettingsTrailing.Toggle(settings.allowLiveMatchAlertsDuringQuietHours),
        onClick = {
            manager.update { copy(allowLiveMatchAlertsDuringQuietHours = !allowLiveMatchAlertsDuringQuietHours) }
        },
    )
}

@Composable
private fun QuietTimeItem(title: Int, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    SettingsItemV2(
        icon = Icons.Default.Schedule,
        title = stringResource(title),
        trailing = SettingsTrailing.Value(formatMinuteOfDay(context, minuteOfDay)),
        onClick = { picking = true },
    )
    if (picking) {
        TimePickerDialog(
            title = stringResource(title),
            initialMinute = minuteOfDay,
            onDismiss = { picking = false },
            onConfirm = { picking = false; onPicked(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(title: String, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / MINUTES_PER_HOUR,
        initialMinute = initialMinute % MINUTES_PER_HOUR,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) }) {
                Text(stringResource(R.string.settings_time_set))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The time in the device's own 12 or 24 hour style. */
private fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / MINUTES_PER_HOUR)
        set(Calendar.MINUTE, minuteOfDay % MINUTES_PER_HOUR)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
