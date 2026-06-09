package com.tanush.bouncer.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.notifications.RichNotificationBuilder.CricketNotificationChannel
import com.tanush.bouncer.ui.screens.NotificationPrefsManager
import com.tanush.bouncer.ui.widgets.WidgetManager
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Handles incoming Firebase Cloud Messaging notifications for live cricket updates.
 *
 * Routes data payloads to the appropriate notification channel based on event type
 * (wicket, milestone, match start/end, boundary, score update) and attaches quick
 * actions for muting a match or sharing the current score.
 */
class BouncerFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "BouncerFCM"

        // Data payload keys
        private const val KEY_TYPE = "type"
        private const val KEY_MATCH_ID = "match_id"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_TEAM1 = "team1"
        private const val KEY_TEAM2 = "team2"
        private const val KEY_SCORE = "score"

        // Notification type values
        private const val TYPE_WICKET = "wicket"
        private const val TYPE_MILESTONE = "milestone"
        private const val TYPE_MATCH_START = "match_start"
        private const val TYPE_MATCH_END = "match_end"
        private const val TYPE_BOUNDARY = "boundary"
        private const val TYPE_SCORE_UPDATE = "score_update"

        // Types sent by poll-live-matches Appwrite Function via cricket-live-updates topic
        private const val TYPE_MATCH_STARTED = "MATCH_STARTED"
        private const val TYPE_MATCH_ENDED = "MATCH_ENDED"

        // Base notification ID offset so FCM notifications don't collide with live ones
        private const val FCM_NOTIFICATION_ID_BASE = 5000
    }

    // ---------------------------------------------------------------
    // Token refresh
    // ---------------------------------------------------------------

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "fcm"
            message = "FCM token refreshed"
            level = SentryLevel.INFO
        })

        // In production, send the token to the backend here.
    }

    // ---------------------------------------------------------------
    // Message handling
    // ---------------------------------------------------------------

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isEmpty()) return

        val type = data[KEY_TYPE] ?: return

        // Topic messages from poll-live-matches (no match_id) — refresh widget + trigger score poll
        if (type == TYPE_MATCH_STARTED || type == TYPE_MATCH_ENDED) {
            Log.d(TAG, "Live match state changed ($type), refreshing widget and triggering score poll")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "fcm"
                message = "Widget refresh + score poll triggered by topic message: type=$type"
                level = SentryLevel.INFO
                setData("type", type)
            })
            WidgetUpdateWorker.triggerImmediateUpdate(applicationContext)
            ScorePollingWorker.enqueueOneTimeWork(applicationContext)
            return
        }

        val matchId = data[KEY_MATCH_ID] ?: return
        val title = data[KEY_TITLE] ?: "Bouncer"
        val body = data[KEY_BODY] ?: ""
        val team1 = data[KEY_TEAM1] ?: ""
        val team2 = data[KEY_TEAM2] ?: ""
        val score = data[KEY_SCORE] ?: ""

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "fcm"
            message = "Message received – type=$type matchId=$matchId"
            level = SentryLevel.INFO
            setData("type", type)
            setData("match_id", matchId)
        })

        // Skip if user muted this match
        if (MuteMatchReceiver.isMatchMuted(applicationContext, matchId)) {
            Log.d(TAG, "Match $matchId is muted – ignoring notification")
            return
        }

        // Check quiet hours
        val notifPrefs = NotificationPrefsManager.getInstance(applicationContext)
        if (notifPrefs.isQuietHoursActive()) {
            val allowThrough = type == TYPE_SCORE_UPDATE &&
                notifPrefs.settings.value.allowLiveMatchAlertsDuringQuietHours
            if (!allowThrough) {
                Log.d(TAG, "Suppressing notification during quiet hours: type=$type matchId=$matchId")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "fcm"
                    message = "Notification suppressed: quiet hours active"
                    level = SentryLevel.INFO
                    setData("type", type)
                    setData("match_id", matchId)
                })
                return
            }
        }

        if (type == TYPE_SCORE_UPDATE) {
            WidgetManager.updateAllWidgets(applicationContext, match = null)
        }

        val channelId = resolveChannelId(type)
        val notificationId = FCM_NOTIFICATION_ID_BASE + matchId.hashCode()

        showNotification(
            notificationId = notificationId,
            channelId = channelId,
            matchId = matchId,
            title = title,
            body = body,
            team1 = team1,
            team2 = team2,
            score = score
        )
    }

    // ---------------------------------------------------------------
    // Channel routing
    // ---------------------------------------------------------------

    private fun resolveChannelId(type: String): String = when (type) {
        TYPE_WICKET -> CricketNotificationChannel.CHANNEL_WICKETS
        TYPE_MILESTONE -> CricketNotificationChannel.CHANNEL_MILESTONES
        TYPE_MATCH_START -> CricketNotificationChannel.CHANNEL_MATCH_START
        TYPE_MATCH_END -> CricketNotificationChannel.CHANNEL_MATCH_END
        TYPE_BOUNDARY -> CricketNotificationChannel.CHANNEL_BOUNDARIES
        TYPE_SCORE_UPDATE -> CricketNotificationChannel.CHANNEL_LIVE_SCORE
        else -> CricketNotificationChannel.CHANNEL_MATCH_EVENTS
    }

    // ---------------------------------------------------------------
    // Notification construction
    // ---------------------------------------------------------------

    private fun showNotification(
        notificationId: Int,
        channelId: String,
        matchId: String,
        title: String,
        body: String,
        team1: String,
        team2: String,
        score: String
    ) {
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Tap → open match in BouncerActivity
        val contentIntent = Intent(this, BouncerActivity::class.java).apply {
            putExtra(MuteMatchReceiver.EXTRA_MATCH_ID, matchId)
            data = android.net.Uri.parse("app://bouncer/match/$matchId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, notificationId, contentIntent, pendingIntentFlags
        )

        // Action: Mute Match
        val muteIntent = Intent(this, MuteMatchReceiver::class.java).apply {
            action = "com.tanush.bouncer.ACTION_MUTE_MATCH"
            putExtra(MuteMatchReceiver.EXTRA_MATCH_ID, matchId)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 1, muteIntent, pendingIntentFlags
        )

        // Action: Share Score
        val shareText = if (score.isNotEmpty()) {
            "$team1 vs $team2 – $score\nFollow live on Bouncer 🏏"
        } else {
            "$team1 vs $team2\nFollow live on Bouncer 🏏"
        }
        val shareIntent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            },
            "Share Score"
        )
        val sharePendingIntent = PendingIntent.getActivity(
            this, notificationId + 2, shareIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setGroup(CricketNotificationChannel.GROUP_CRICKET)
            .addAction(0, "Mute Match", mutePendingIntent)
            .addAction(0, "Share Score", sharePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted", e)
            Sentry.captureException(e)
        }
    }
}
