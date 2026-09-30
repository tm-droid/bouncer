package com.tanush.bouncer.ui.screens

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
import java.text.DateFormat
import java.util.Date

/**
 * The reminders a user has set from match pages, soonest first, each cancellable.
 *
 * Replaces `RemindersActivity`, which nothing ever launched and whose list nothing ever wrote to:
 * the match page scheduled an alarm and kept no record of it, so a reminder could be set but never
 * seen or undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(RemindersManager.upcoming(context, System.currentTimeMillis())) }

    Scaffold(
        topBar = {
            RemindersTopBar(showClearAll = reminders.isNotEmpty(), onBack = onBack) {
                reminders.forEach { ReminderScheduler.cancelReminder(context, it.id) }
                RemindersManager.clearAll(context)
                reminders = emptyList()
            }
        },
        containerColor = colors.background,
    ) { innerPadding ->
        if (reminders.isEmpty()) {
            EmptyStateV2(
                title = stringResource(R.string.reminders_no_reminders_set),
                subtitle = stringResource(R.string.reminders_empty_subtitle),
                icon = Icons.Outlined.NotificationsNone,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = BouncerThemeV2.spacing.xxl),
            ) {
                itemsIndexed(reminders, key = { index, r -> "${r.id}-$index" }) { index, reminder ->
                    if (index > 0) HorizontalDivider(color = colors.divider)
                    ReminderRow(reminder) {
                        ReminderScheduler.cancelReminder(context, reminder.id)
                        RemindersManager.deleteReminder(context, reminder.id)
                        reminders = RemindersManager.upcoming(context, System.currentTimeMillis())
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onCancel: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val whenText = remember(reminder.triggerAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(reminder.triggerAt))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersTopBar(showClearAll: Boolean, onBack: () -> Unit, onClearAll: () -> Unit) {
    val colors = BouncerThemeV2.colors
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(R.string.reminders_scheduled_reminders),
                style = BouncerThemeV2.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = colors.onSurface,
                )
            }
        },
        actions = {
            if (showClearAll) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.reminders_clear_all), color = colors.primaryText)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
    )
}
