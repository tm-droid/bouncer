package com.tanush.bouncer.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.utils.RemindersManager
import com.tanush.bouncer.utils.Reminder
import com.tanush.bouncer.utils.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.*
import io.sentry.Sentry

class RemindersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContent {
                val colors = BouncerDesignSystem.colors
                Surface(modifier = Modifier.fillMaxSize(), color = colors.backgroundBase) {
                    RemindersScreen()
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

@Composable
fun RemindersScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var reminders by remember { mutableStateOf(RemindersManager.getReminders(ctx).toMutableList()) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders_scheduled_reminders), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = {
                RemindersManager.clearAll(ctx)
                reminders = mutableListOf()
            }) { Text(stringResource(R.string.reminders_clear_all)) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (reminders.isEmpty()) {
            Text(stringResource(R.string.reminders_no_reminders_set), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reminders, key = { it.id }) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(r.title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val sdf = SimpleDateFormat.getDateTimeInstance()
                                    Text(sdf.format(Date(r.triggerAt)))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(r.body, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    TextButton(onClick = {
                                        // delete
                                        RemindersManager.deleteReminder(ctx, r.id)
                                        ReminderScheduler.cancelReminder(ctx, r.id)
                                        reminders = RemindersManager.getReminders(ctx).toMutableList()
                                    }) { Text(stringResource(R.string.reminders_delete)) }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    TextButton(onClick = {
                                        // reschedule via time picker
                                        val now = java.util.Calendar.getInstance()
                                        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
                                        val minute = now.get(java.util.Calendar.MINUTE)
                                        val timePicker = android.app.TimePickerDialog(ctx, { _, h, m ->
                                            val cal = java.util.Calendar.getInstance()
                                            cal.set(java.util.Calendar.HOUR_OF_DAY, h)
                                            cal.set(java.util.Calendar.MINUTE, m)
                                            cal.set(java.util.Calendar.SECOND, 0)
                                            cal.set(java.util.Calendar.MILLISECOND, 0)

                                            if (cal.timeInMillis <= System.currentTimeMillis()) {
                                                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                            }

                                            // cancel old scheduled and schedule new
                                            ReminderScheduler.cancelReminder(ctx, r.id)
                                            ReminderScheduler.scheduleReminder(ctx, r.id, r.title, r.body, cal.timeInMillis - System.currentTimeMillis())
                                            RemindersManager.updateReminder(ctx, Reminder(r.id, r.title, r.body, cal.timeInMillis))
                                            reminders = RemindersManager.getReminders(ctx).toMutableList()
                                            android.widget.Toast.makeText(ctx, "Rescheduled", android.widget.Toast.LENGTH_SHORT).show()
                                        }, hour, minute, true)
                                        timePicker.show()
                                    }) { Text(stringResource(R.string.reminders_reschedule)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
