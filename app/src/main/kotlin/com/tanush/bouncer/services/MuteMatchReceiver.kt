package com.tanush.bouncer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.tanush.bouncer.managers.MatchAlertPrefs
import com.tanush.bouncer.managers.MatchSubscriptionManager
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * The "Stop alerts" action on a match alert. Turns the match's alerts off through the same switch
 * as the bell's sheet, so the user can turn them back on there; it used to add the match to a
 * private mute set that nothing in the UI could clear.
 */
class MuteMatchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MuteMatchReceiver"
        const val EXTRA_MATCH_ID = "extra_match_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID)
        if (matchId.isNullOrEmpty()) {
            Log.w(TAG, "Received mute action without a match ID")
            return
        }

        MatchSubscriptionManager.initialize(context.applicationContext)
        MatchSubscriptionManager.setAlerts(matchId, MatchAlertPrefs.NONE)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val tag = BouncerFirebaseMessagingService.notificationTag(matchId)
            MatchAlertType.ALL.indices.forEach { notificationManager.cancel(tag, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notifications for match $matchId", e)
            Sentry.captureException(e)
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "mute"
            message = "Match alerts stopped: $matchId"
            level = SentryLevel.INFO
            setData("match_id", matchId)
        })
    }
}
