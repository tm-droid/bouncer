package com.tanush.bouncer.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object NotificationHelper {
    private const val CHANNEL_ID = "live_match_events"
    private const val CHANNEL_NAME = "Live Match Events"

    fun createNotificationChannel(context: Context) {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "util.notification"
                message = "Creating notification channel"
                level = SentryLevel.INFO
                setData("channelId", CHANNEL_ID)
            })
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_live_events_desc)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun notify(context: Context, id: Int, title: String, body: String, matchId: String? = null) {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "util.notification"
                message = "Sending notification"
                level = SentryLevel.INFO
                setData("notificationId", id)
                setData("title", title)
            })
            
            createNotificationChannel(context)

            // A reminder for a match opens that match, the same link a match alert carries.
            val intent = Intent(context, BouncerActivity::class.java).apply {
                if (matchId != null) {
                    data = Uri.parse("app://bouncer/match/$matchId")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                } else {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ball)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            // Silently skipped when the user has denied notifications. This used to call
            // notify() unguarded and rethrow the resulting SecurityException, which took
            // ReminderReceiver.onReceive down with it.
            NotificationPermission.notify(context, id, builder.build())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
