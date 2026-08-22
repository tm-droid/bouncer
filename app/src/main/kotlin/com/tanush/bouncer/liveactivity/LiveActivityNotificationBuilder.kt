package com.tanush.bouncer.liveactivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R

class LiveActivityNotificationBuilder(
    private val context: Context
) {
    fun createRemoteViews(
        style: LiveActivityStyle,
        state: MatchState,
        relativeTime: String,
        expanded: Boolean
    ): RemoteViews {
        val template = LiveActivityStyleRegistry.templateFor(style)
        val layoutId = if (expanded) template.expandedLayoutRes else template.compactLayoutRes
        return RemoteViews(context.packageName, layoutId).apply {
            if (expanded) {
                template.bindExpanded(this, state, relativeTime)
            } else {
                template.bindCompact(this, state, relativeTime)
            }
        }
    }

    fun buildNotification(
        matchId: String,
        style: LiveActivityStyle,
        state: MatchState,
        relativeTime: String,
        highPriority: Boolean
    ): Notification {
        val compact = createRemoteViews(style, state, relativeTime, expanded = false)
        val expanded = createRemoteViews(style, state, relativeTime, expanded = true)
        val contentIntent = PendingIntent.getActivity(
            context,
            matchId.hashCode(),
            Intent(context, BouncerActivity::class.java).apply {
                putExtra(EXTRA_MATCH_ID, matchId)
                putExtra(EXTRA_FROM_LIVE_ACTIVITY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScoreIntent = PendingIntent.getActivity(
            context,
            matchId.hashCode() + 1,
            Intent(context, BouncerActivity::class.java).apply {
                putExtra(EXTRA_MATCH_ID, matchId)
                putExtra(EXTRA_OPEN_FULL_SCORE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.live_activity_title))
            .setContentText(state.statusText)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.live_activity_action_view_full_score), fullScoreIntent)
            .addAction(0, context.getString(R.string.live_activity_action_open_app), contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                builder.javaClass
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(builder, true)
            } catch (_: Throwable) {
                // Safe no-op for compatibility with different support-library versions.
            }
        }

        return builder.build()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.live_activity_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.live_activity_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            // setAllowBubbles is API 29; minSdk is 26. Unguarded this is a NoSuchMethodError on
            // 26-28, which lint reported as an error and nothing failed on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setAllowBubbles(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "live_activity_updates"
        const val EXTRA_MATCH_ID = "live_activity_match_id"
        const val EXTRA_FROM_LIVE_ACTIVITY = "live_activity_from_notification"
        const val EXTRA_OPEN_FULL_SCORE = "live_activity_open_full_score"
    }
}
