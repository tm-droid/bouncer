package com.tanush.bouncer.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.sentry.Sentry

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val title = intent.getStringExtra("title") ?: "Reminder"
            val body = intent.getStringExtra("body") ?: "Don't forget to check the match!"

            NotificationHelper.notify(context, 2001, title, body)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
