package com.tanush.bouncer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorkManagerWorker
import io.sentry.Breadcrumb
import com.tanush.bouncer.utils.RemindersManager
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.TimeUnit

/**
 * Re-enqueues periodic score polling and re-sets match reminders after the device reboots.
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

        // AlarmManager forgets every alarm at reboot. getReminders degrades to none on a bad record
        // and rescheduleAll isolates each reminder, so this cannot throw out of onReceive.
        RemindersManager.rescheduleAll(context)

        // Refresh widgets 30s after boot using cached data — don't wait up to 15 min
        // for the first ScorePollingWorker tick.
        try {
            val initialWidgetUpdate = OneTimeWorkRequestBuilder<WidgetUpdateWorkManagerWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag("widget_boot_update")
                .build()
            WorkManager.getInstance(context).enqueue(initialWidgetUpdate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue initial widget update on boot", e)
            Sentry.captureException(e)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
