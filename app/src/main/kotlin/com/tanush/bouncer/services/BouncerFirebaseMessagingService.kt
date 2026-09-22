package com.tanush.bouncer.services

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.MatchAlertPrefs
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.alertsFor
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.managers.NotificationItem
import com.tanush.bouncer.ui.notifications.RichNotificationBuilder.CricketNotificationChannel
import com.tanush.bouncer.ui.screens.NotificationPrefsManager
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.ui.widgets.WidgetSnapshotStore
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlin.random.Random

/**
 * Handles incoming Firebase Cloud Messaging notifications for live cricket updates.
 *
 * Match alerts arrive on the match's topic (`match_{id}`), sent by `bouncer-match-sync` (wicket,
 * boundary, milestone, result) and `bouncer-poll-live-matches` (start). Until those senders
 * existed this class handled types nothing produced, so no alert was ever shown. Each is gated by
 * [shouldShowMatchAlert] and routed to its channel.
 */
class BouncerFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "BouncerFCM"

        /**
         * Window across which a topic-broadcast refresh is spread.
         *
         * Topic messages reach every installed device simultaneously, so an unspread fan-out is a
         * self-inflicted thundering herd against both Cricbuzz and the Glance APIs.
         */
        private const val TOPIC_FANOUT_JITTER_MS = 60_000L

        // Data payload keys
        private const val KEY_TYPE = "type"
        private const val KEY_MATCH_ID = "match_id"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_EVENT = "event"
        private const val KEY_MATCH_TITLE = "match_title"
        private const val KEY_TEAM1 = "team1"
        private const val KEY_TEAM2 = "team2"
        private const val KEY_SCORE = "score"

        // Notification type values
        private const val TYPE_WICKET = MatchAlertType.WICKET
        private const val TYPE_MILESTONE = MatchAlertType.MILESTONE
        private const val TYPE_MATCH_START = MatchAlertType.MATCH_START
        private const val TYPE_MATCH_END = MatchAlertType.MATCH_END
        private const val TYPE_BOUNDARY = MatchAlertType.BOUNDARY
        private const val TYPE_SCORE_UPDATE = "score_update"

        // Types sent by poll-live-matches Appwrite Function via cricket-live-updates topic
        private const val TYPE_MATCH_STARTED = "MATCH_STARTED"
        private const val TYPE_MATCH_ENDED = "MATCH_ENDED"

        /**
         * A match's alerts share one tag and take their id from the type, so a second wicket
         * replaces the first rather than stacking, a wicket never replaces a boundary, and
         * [MuteMatchReceiver] can clear them all. The tag also keeps them clear of the numeric
         * ids every other notification in the app uses.
         */
        fun notificationTag(matchId: String) = "match_alert_$matchId"
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        CricketNotificationChannel.createNotificationChannels(this)
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
            // This arrives as a TOPIC broadcast, so every installed device handles it at the same
            // instant. Without a spread they all hit Cricbuzz and the Glance APIs together.
            val jitterMs = Random.nextLong(0, TOPIC_FANOUT_JITTER_MS)
            WidgetUpdateWorker.triggerImmediateUpdate(applicationContext, jitterMs)
            ScorePollingWorker.enqueueOneTimeWork(applicationContext, jitterMs)
            return
        }

        val matchId = data[KEY_MATCH_ID] ?: return
        val title = data[KEY_TITLE] ?: "Bouncer"
        val body = data[KEY_BODY] ?: ""

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "fcm"
            message = "Message received – type=$type matchId=$matchId"
            level = SentryLevel.INFO
            setData("type", type)
            setData("match_id", matchId)
        })

        if (type == TYPE_SCORE_UPDATE) {
            // This used to call `updateAllWidgets(match = null)`, which took the clear-everything
            // branch: the freshest signal the app receives BLANKED every widget. It now folds the
            // score into the one snapshot and redraws. Nothing on the server sends this type today.
            WidgetSnapshotStore.applyPush(
                applicationContext, matchId, data[KEY_TEAM1] ?: "", data[KEY_TEAM2] ?: "", data[KEY_SCORE] ?: "",
            )
            WidgetUpdateWorker.triggerImmediateUpdate(applicationContext)
            return
        }

        // Everything else is a match alert from the match's topic. A device receives it only if it
        // subscribed, but the preferences may have changed since, and the global switches and quiet
        // hours are never known server-side — so the decision is made here, every time.
        MatchSubscriptionManager.initialize(applicationContext)
        val show = shouldShowMatchAlert(
            type = type,
            prefs = MatchSubscriptionManager.alertsFor(matchId),
            global = GlobalAlertSwitches(
                matchAlerts = PreferencesHelper.getBoolean(applicationContext, PreferencesHelper.KEY_LIVE_NOTIFICATIONS, true),
                wickets = PreferencesHelper.getBoolean(applicationContext, PreferencesHelper.KEY_WICKET_ALERTS, true),
                boundaries = PreferencesHelper.getBoolean(applicationContext, PreferencesHelper.KEY_BOUNDARY_ALERTS, true),
            ),
            quietHours = NotificationPrefsManager.getInstance(applicationContext).isQuietHoursActive(),
        )
        if (type == TYPE_MATCH_END) {
            // Nothing follows a result, so the topic is left whatever the user chose to see.
            MatchSubscriptionManager.setAlerts(matchId, MatchAlertPrefs.NONE)
        }
        if (!show) {
            Log.d(TAG, "Alert not shown: type=$type matchId=$matchId")
            return
        }

        NotificationInboxManager.add(
            NotificationItem(
                matchId = matchId,
                title = title,
                body = body,
                type = inboxType(type, data[KEY_EVENT]),
            )
        )

        showNotification(
            tag = notificationTag(matchId),
            notificationId = MatchAlertType.ALL.indexOf(type).coerceAtLeast(0),
            channelId = resolveChannelId(type),
            matchId = matchId,
            title = title,
            body = body,
            subText = data[KEY_MATCH_TITLE],
        )
    }

    /** The inbox's own type vocabulary, which splits a boundary into four and six. */
    private fun inboxType(type: String, event: String?): String = when (type) {
        TYPE_BOUNDARY -> if (event == "six") "six" else "four"
        else -> type
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
        else -> CricketNotificationChannel.CHANNEL_MATCH_EVENTS
    }

    // ---------------------------------------------------------------
    // Notification construction
    // ---------------------------------------------------------------

    private fun showNotification(
        tag: String,
        notificationId: Int,
        channelId: String,
        matchId: String,
        title: String,
        body: String,
        subText: String?,
    ) {
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // One request-code space per (match, type), so two matches' actions never share an intent.
        val requestBase = (tag.hashCode() * 8) + notificationId * 2

        // Tap → open match in BouncerActivity
        val contentIntent = Intent(this, BouncerActivity::class.java).apply {
            data = android.net.Uri.parse("app://bouncer/match/$matchId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, requestBase, contentIntent, pendingIntentFlags
        )

        // Action: stop this match's alerts — the same switch as the bell's sheet, so it is undoable there.
        val muteIntent = Intent(this, MuteMatchReceiver::class.java).apply {
            action = "com.tanush.bouncer.ACTION_MUTE_MATCH"
            putExtra(MuteMatchReceiver.EXTRA_MATCH_ID, matchId)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            this, requestBase + 1, muteIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setGroup(CricketNotificationChannel.GROUP_CRICKET)
            .addAction(0, getString(R.string.match_alert_action_stop), mutePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(tag, notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted", e)
            Sentry.captureException(e)
        }
    }
}
