package com.tanush.bouncer.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val body = intent.getStringExtra("body") ?: "Don't forget to check the match!"

        NotificationHelper.notify(context, 2001, title, body)
    }
}
