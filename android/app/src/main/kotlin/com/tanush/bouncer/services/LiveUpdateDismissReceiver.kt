package com.tanush.bouncer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tanush.bouncer.liveactivity.LiveUpdateDismissals
import com.tanush.bouncer.liveactivity.LiveUpdateNotifier
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.utils.sentryBreadcrumb

/**
 * The two ways a user can end a Live Update from the notification itself.
 *
 * A swipe ([ACTION_DISMISSED], delivered through `setDeleteIntent`) is a soft no: the subscription
 * stands, but nothing reposts this match while the process lives. Reposting a dismissed Live
 * Update is what makes people revoke the app's promotion permission outright.
 *
 * The "Stop" action ([ACTION_STOP]) is the hard no: it unsubscribes, which also drains the last
 * subscription case that stops the service.
 */
class LiveUpdateDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID)?.takeIf { it.isNotBlank() } ?: return
        when (intent.action) {
            ACTION_DISMISSED -> {
                LiveUpdateDismissals.record(matchId)
                sentryBreadcrumb("live_update", "Live Update dismissed", data = mapOf("matchId" to matchId))
            }

            ACTION_STOP -> {
                LiveUpdateDismissals.record(matchId)
                // Clear the card here rather than waiting for the service to observe the
                // subscription change: the service may not be running at all, in which case a
                // detached notification would sit there with nothing left to update it.
                LiveUpdateNotifier(context.applicationContext).cancel(matchId)
                MatchSubscriptionManager.initialize(context.applicationContext)
                MatchSubscriptionManager.unsubscribe(matchId)
                sentryBreadcrumb("live_update", "Live Update stopped by user", data = mapOf("matchId" to matchId))
            }
        }
    }

    companion object {
        const val ACTION_DISMISSED = "com.tanush.bouncer.liveupdate.DISMISSED"
        const val ACTION_STOP = "com.tanush.bouncer.liveupdate.STOP"
        const val EXTRA_MATCH_ID = "live_update_match_id"
    }
}
