package com.tanush.bouncer.ui.components.v2

import com.tanush.bouncer.utils.Reminder
import com.tanush.bouncer.utils.RemindersManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PrefsRegistry
import com.tanush.bouncer.utils.ReminderScheduler
import io.sentry.Sentry
import io.sentry.Breadcrumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderBottomSheet(
    matchId: String,
    matchTitle: String,
    matchStartTime: Long, // epoch millis, 0 if unknown
    onDismiss: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = androidx.compose.ui.platform.LocalContext.current

    val sheetState = rememberModalBottomSheetState()

    data class ReminderOption(val label: String, val minutesBefore: Int)
    val options = listOf(
        ReminderOption(stringResource(R.string.reminder_option_15), 15),
        ReminderOption(stringResource(R.string.reminder_option_30), 30),
        ReminderOption(stringResource(R.string.reminder_option_60), 60),
        ReminderOption(stringResource(R.string.reminder_option_120), 120),
        ReminderOption(stringResource(R.string.reminder_option_start), 0),
    )
    val notifications = rememberNotificationAccess()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.common_set_reminder), style = typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = colors.textSecondary)
                }
            }

            Text(
                matchTitle,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.lg),
            )
            NotificationsOffNotice(notifications, Modifier.padding(bottom = spacing.md))

            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scheduleReminder(context, matchId, matchTitle, matchStartTime, option.minutesBefore)
                            // The reminder is kept either way; it fires once notifications are on.
                            if (!notifications.enabled) notifications.turnOn()
                            onDismiss()
                        }
                        .padding(vertical = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(spacing.lg))
                    Text(option.label, style = typography.bodyLarge, color = colors.onSurface)
                }
                if (option != options.last()) {
                    HorizontalDivider(color = colors.divider)
                }
            }

            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

private fun scheduleReminder(
    context: Context,
    matchId: String,
    matchTitle: String,
    matchStartTime: Long,
    minutesBefore: Int,
) {
    Sentry.addBreadcrumb(Breadcrumb().apply {
        category = "reminder"
        message = "Setting reminder for $matchTitle: $minutesBefore min before"
    })

    if (matchStartTime <= 0L) {
        Toast.makeText(context, R.string.reminder_saved_unconfirmed, Toast.LENGTH_SHORT).show()
        saveReminderPreference(context, matchId, minutesBefore)
        return
    }

    val triggerTime = matchStartTime - (minutesBefore * 60 * 1000L)
    if (triggerTime <= System.currentTimeMillis()) {
        Toast.makeText(context, R.string.reminder_time_passed, Toast.LENGTH_SHORT).show()
        return
    }

    // Goes through ReminderScheduler rather than building its own alarm. The copy that lived here
    // resolved its receiver with
    // `Class.forName("com.tanush.bouncer.receivers.ReminderReceiver")` — a package that does not
    // exist; the real one is `com.tanush.bouncer.utils.ReminderReceiver`, which is also what the
    // manifest registers. So every reminder set from this sheet threw ClassNotFoundException, was
    // swallowed by the catch below, and reported "Reminder saved" to the user while scheduling
    // nothing. The extras did not match the receiver's either ("match_title" vs the "title" it
    // reads), so even the right class would have fired a notification with default text.
    try {
        val id = matchId.hashCode() + minutesBefore
        val body = if (minutesBefore == 0) "Starting now" else "Starts in $minutesBefore minutes"
        ReminderScheduler.scheduleReminder(
            context = context,
            id = id,
            title = matchTitle,
            body = body,
            delayMillis = triggerTime - System.currentTimeMillis(),
            matchId = matchId,
        )
        // Recorded so Settings › Match reminders can list and cancel it. The alarm alone left no
        // trace a user could find.
        RemindersManager.updateReminder(context, Reminder(id, matchTitle, body, triggerTime, matchId))
        saveReminderPreference(context, matchId, minutesBefore)
        val confirmation = if (minutesBefore == 0) {
            context.getString(R.string.reminder_set_at_start)
        } else {
            context.getString(R.string.reminder_set_before, minutesBefore)
        }
        Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Sentry.captureException(e)
        // Deliberately not "Reminder saved". Saying a reminder is set when scheduling failed is
        // how this stayed broken: the user has no way to learn it will not fire.
        Toast.makeText(context, R.string.reminder_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun saveReminderPreference(context: Context, matchId: String, minutesBefore: Int) {
    val prefs = context.getSharedPreferences(PrefsRegistry.PREFS_REMINDERS, Context.MODE_PRIVATE)
    prefs.edit().putInt("${PrefsRegistry.Reminders.REMINDER_PREFIX}$matchId", minutesBefore).apply()
}
