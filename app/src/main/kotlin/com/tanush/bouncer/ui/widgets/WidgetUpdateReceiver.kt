package com.tanush.bouncer.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.sentry.Sentry

/**
 * Broadcast Receiver for scheduled widget updates
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            WidgetUpdateWorker.triggerImmediateUpdate(context)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}
