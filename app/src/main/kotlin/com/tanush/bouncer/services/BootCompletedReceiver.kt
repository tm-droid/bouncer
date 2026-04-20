package com.tanush.bouncer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Re-enqueues periodic score polling after the device reboots.
 *
 * Register in AndroidManifest.xml with:
 *   <receiver android:name=".services.BootCompletedReceiver"
 *             android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BOOT_COMPLETED" />
 *       </intent-filter>
 *   </receiver>
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed – re-enqueuing score polling")
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "boot"
            message = "BOOT_COMPLETED received, scheduling ScorePollingWorker"
            level = SentryLevel.INFO
        })

        try {
            ScorePollingWorker.enqueuePeriodicWork(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue score polling on boot", e)
            Sentry.captureException(e)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
