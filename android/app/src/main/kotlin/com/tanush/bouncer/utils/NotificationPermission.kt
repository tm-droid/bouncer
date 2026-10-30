package com.tanush.bouncer.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * POST_NOTIFICATIONS check plus the two `notify` wrappers that need it.
 *
 * On Android 13+ `NotificationManagerCompat.notify` throws `SecurityException` when the permission
 * has been denied. Three call sites posted without checking — `NotificationHelper.notify` and both
 * branches of `showCricketNotification` — and `NotificationHelper` rethrows, so a match reminder
 * arriving on a device where the user had declined notifications took down
 * `ReminderReceiver.onReceive` with it. Lint flags all of them as MissingPermission; the errors
 * were invisible because `lint.abortOnError` was false and CI skipped `lintVital` on release.
 *
 * Declining notifications is a normal state, not an error, so a blocked post is a silent no-op that
 * reports whether it happened rather than an exception or a Sentry capture.
 */
object NotificationPermission {

    /** True when notifications may be posted. Always true below API 33, where there is no runtime grant. */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Posts [notification], or returns false without posting when the permission is not granted. */
    // The isGranted() call above the notify() is the check lint asks for; it cannot follow it
    // across the function boundary. Suppressed here, once, rather than at each call site.
    @SuppressLint("MissingPermission")
    fun notify(context: Context, id: Int, notification: Notification): Boolean {
        if (!isGranted(context)) return false
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    /** Tagged variant of [notify]. */
    @SuppressLint("MissingPermission")
    fun notify(context: Context, tag: String, id: Int, notification: Notification): Boolean {
        if (!isGranted(context)) return false
        NotificationManagerCompat.from(context).notify(tag, id, notification)
        return true
    }
}
