package com.tanush.bouncer.ui.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.sentry.Sentry

/**
 * The match notification channels. Alerts are built and posted by
 * `BouncerFirebaseMessagingService`; the builders that used to live here (wicket, six, four,
 * milestone, toss, scorecard, commentary and ~20 more) had no caller anywhere and were deleted.
 * Channel ids are permanent once created on a device, so none is renamed or removed here.
 */
object RichNotificationBuilder {

    object CricketNotificationChannel {

        const val GROUP_CRICKET = "cricket_notifications"

        const val CHANNEL_LIVE_SCORE = "live_score_updates"
        const val CHANNEL_WICKETS = "wicket_alerts"
        const val CHANNEL_BOUNDARIES = "boundary_alerts"
        const val CHANNEL_MILESTONES = "milestone_alerts"
        const val CHANNEL_MATCH_EVENTS = "match_events"
        const val CHANNEL_MATCH_START = "match_start"
        const val CHANNEL_MATCH_END = "match_end"
        const val CHANNEL_SUMMARY = "match_summary"

        private const val COLOR_GOLD = 0xFFFFD700.toInt()
        private const val COLOR_WICKET = 0xFFFF3B30.toInt()

        fun createNotificationChannels(context: Context) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_CRICKET, "Cricket Notifications"))

                fun channel(
                    id: String,
                    name: String,
                    importance: Int,
                    desc: String,
                    badge: Boolean = true,
                    configure: NotificationChannel.() -> Unit = {}
                ) = NotificationChannel(id, name, importance).apply {
                    description = desc
                    group = GROUP_CRICKET
                    setShowBadge(badge)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    configure()
                }

                nm.createNotificationChannels(
                    listOf(
                        channel(CHANNEL_LIVE_SCORE, "Live Score Updates", NotificationManager.IMPORTANCE_LOW,
                            "Ongoing live match score updates", badge = false) {
                            enableVibration(false)
                            setSound(null, null)
                        },
                        channel(CHANNEL_WICKETS, "Wicket Alerts", NotificationManager.IMPORTANCE_HIGH,
                            "Alerts when a wicket falls") {
                            enableVibration(true)
                            vibrationPattern = longArrayOf(0, 300, 100, 300)
                            enableLights(true)
                            lightColor = COLOR_WICKET
                        },
                        channel(CHANNEL_BOUNDARIES, "Boundary Alerts", NotificationManager.IMPORTANCE_DEFAULT,
                            "Alerts for fours and sixes") {
                            enableVibration(true)
                            vibrationPattern = longArrayOf(0, 200)
                        },
                        channel(CHANNEL_MILESTONES, "Milestone Alerts", NotificationManager.IMPORTANCE_DEFAULT,
                            "Player milestones (50s, 100s, 5-wicket hauls)") {
                            enableLights(true)
                            lightColor = COLOR_GOLD
                        },
                        channel(CHANNEL_MATCH_EVENTS, "Match Events", NotificationManager.IMPORTANCE_LOW,
                            "General match events (innings break, powerplay, etc.)", badge = false),
                        channel(CHANNEL_MATCH_START, "Match Start Alerts", NotificationManager.IMPORTANCE_HIGH,
                            "Notifications when a match you follow starts") { enableVibration(true) },
                        channel(CHANNEL_MATCH_END, "Match End Alerts", NotificationManager.IMPORTANCE_DEFAULT,
                            "Notifications when a match ends"),
                        channel(CHANNEL_SUMMARY, "Match Summaries", NotificationManager.IMPORTANCE_LOW,
                            "Daily and tournament summaries", badge = false)
                    )
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}
