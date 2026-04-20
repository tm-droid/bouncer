package com.tanush.bouncer.ui.components.v2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
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
        ReminderOption("15 minutes before", 15),
        ReminderOption("30 minutes before", 30),
        ReminderOption("1 hour before", 60),
        ReminderOption("2 hours before", 120),
        ReminderOption("At match start", 0),
    )

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
                Text("Set Reminder", style = typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = colors.textSecondary)
                }
            }

            Text(
                matchTitle,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.lg),
            )

            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scheduleReminder(context, matchId, matchTitle, matchStartTime, option.minutesBefore)
                            onDismiss()
                        }
                        .padding(vertical = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = colors.primary,
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
        Toast.makeText(context, "Reminder saved — we'll notify you when times are confirmed", Toast.LENGTH_SHORT).show()
        saveReminderPreference(context, matchId, minutesBefore)
        return
    }

    val triggerTime = matchStartTime - (minutesBefore * 60 * 1000L)
    if (triggerTime <= System.currentTimeMillis()) {
        Toast.makeText(context, "This time has already passed", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, Class.forName("com.tanush.bouncer.receivers.ReminderReceiver")).apply {
            putExtra("match_id", matchId)
            putExtra("match_title", matchTitle)
            putExtra("minutes_before", minutesBefore)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            matchId.hashCode() + minutesBefore,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        saveReminderPreference(context, matchId, minutesBefore)
        val label = if (minutesBefore == 0) "at match start" else "$minutesBefore min before"
        Toast.makeText(context, "Reminder set: $label", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Sentry.captureException(e)
        saveReminderPreference(context, matchId, minutesBefore)
        Toast.makeText(context, "Reminder saved", Toast.LENGTH_SHORT).show()
    }
}

private fun saveReminderPreference(context: Context, matchId: String, minutesBefore: Int) {
    val prefs = context.getSharedPreferences("bouncer_reminders", Context.MODE_PRIVATE)
    prefs.edit().putInt("reminder_$matchId", minutesBefore).apply()
}
