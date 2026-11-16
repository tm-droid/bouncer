package com.tanush.bouncer.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import com.tanush.bouncer.utils.PreferencesHelper

/**
 * Minutes, matching `WidgetUpdateWorker.resolveUpdateDelayMs`: how often the widgets' fallback poll
 * runs when push is unavailable. 5m, 15m, 30m, 60m, round again.
 */
@Composable
internal fun RefreshIntervalItem() {
    val context = LocalContext.current
    var minutes by remember {
        mutableIntStateOf(
            PreferencesHelper.getInt(context, PreferencesHelper.KEY_REFRESH_INTERVAL, WidgetUpdateWorker.DEFAULT_INTERVAL_MIN)
                .coerceIn(WidgetUpdateWorker.MIN_INTERVAL_MIN, WidgetUpdateWorker.MAX_INTERVAL_MIN),
        )
    }
    var choosing by remember { mutableStateOf(false) }
    SettingsItemV2(
        icon = Icons.Default.Timer,
        title = stringResource(R.string.settings_refresh_interval),
        subtitle = stringResource(R.string.settings_refresh_interval_subtitle, minutes),
        trailing = SettingsTrailing.Value(stringResource(R.string.settings_minutes_short, minutes)),
        onClick = { choosing = true },
    )
    if (choosing) {
        RefreshIntervalDialog(
            selected = minutes,
            onDismiss = { choosing = false },
            onPick = { picked ->
                choosing = false
                minutes = picked
                PreferencesHelper.setInt(context, PreferencesHelper.KEY_REFRESH_INTERVAL, picked)
                // Re-armed now, so the new interval does not wait out the old one.
                WidgetUpdateWorker.schedulePeriodicUpdates(context)
            },
        )
    }
}

@Composable
private fun RefreshIntervalDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_refresh_interval)) },
        text = {
            Column(Modifier.selectableGroup()) {
                REFRESH_STEPS.forEach { steps ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = steps == selected, role = Role.RadioButton, onClick = { onPick(steps) })
                            .padding(vertical = BouncerThemeV2.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = steps == selected, onClick = null)
                        Spacer(Modifier.width(BouncerThemeV2.spacing.md))
                        Text(pluralStringResource(R.plurals.settings_refresh_every_minutes, steps, steps))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private val REFRESH_STEPS = listOf(5, 15, 30, 60)
