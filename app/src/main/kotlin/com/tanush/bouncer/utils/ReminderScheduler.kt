package com.tanush.bouncer.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object ReminderScheduler {
    fun scheduleReminder(context: Context, id: Int, title: String, body: String, delayMillis: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("body", body)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
    }

    fun cancelReminder(context: Context, id: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(it)
            it.cancel()
        }
    }
}
