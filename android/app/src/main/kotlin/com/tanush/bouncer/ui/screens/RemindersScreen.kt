package com.tanush.bouncer.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.Reminder
import com.tanush.bouncer.utils.ReminderScheduler
import com.tanush.bouncer.utils.RemindersManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * The reminders a user has set from match pages, soonest first, each cancellable, on the Alerts
 * screen's Reminders tab. A reminder for a match opens it.
 *
 * Replaces `RemindersActivity`, which nothing ever launched and whose list nothing ever wrote to:
 * the match page scheduled an alarm and kept no record of it, so a reminder could be set but never
 * seen or undone.
 */
@Composable
internal fun ReminderList(reminders: List<Reminder>, onCancel: (Reminder) -> Unit, onOpenMatch: (String) -> Unit) {
    val colors = BouncerThemeV2.colors
    if (reminders.isEmpty()) {
        EmptyStateV2(
            title = stringResource(R.string.reminders_no_reminders_set),
            subtitle = stringResource(R.string.reminders_empty_subtitle),
            icon = Icons.Outlined.NotificationsNone,
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BouncerThemeV2.spacing.xxl)) {
        itemsIndexed(reminders, key = { index, r -> "${r.id}-$index" }) { index, reminder ->
            if (index > 0) HorizontalDivider(color = colors.divider)
            val matchId = reminder.matchId
            Box(if (matchId.isNullOrBlank()) Modifier else Modifier.clickable { onOpenMatch(matchId) }) {
                ReminderRow(reminder) { onCancel(reminder) }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onCancel: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current
    val whenText = remember(reminder.triggerAt) {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(reminder.triggerAt).atZone(zone).toLocalDate()
        val time = DateFormat.getTimeFormat(context).format(Date(reminder.triggerAt))
        "${dayLabel(day, LocalDate.now(zone))} $time"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(reminder.title, style = typography.bodyMedium, color = colors.onSurface, maxLines = 2)
            Spacer(Modifier.height(spacing.xs))
            Text(
                "$whenText · ${reminder.body}",
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.reminders_delete), color = colors.primaryText)
        }
    }
}
