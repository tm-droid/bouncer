package com.tanush.bouncer.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
            val body = intent.getStringExtra(EXTRA_BODY) ?: "Don't forget to check the match!"
            val id = intent.getIntExtra(EXTRA_ID, DEFAULT_NOTIFICATION_ID)

            NotificationHelper.notify(context, id, title, body, intent.getStringExtra(EXTRA_MATCH_ID))
        } catch (e: Exception) {
            // NotificationHelper has already reported it. Rethrowing from onReceive kills the
            // process for an alarm that fired in the background.
            Log.w(TAG, "Reminder notification failed", e)
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_MATCH_ID = "match_id"
        private const val DEFAULT_NOTIFICATION_ID = 2001
    }
}
