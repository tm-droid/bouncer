package com.tanush.bouncer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Receives "Mute Match" actions from notifications and persists the muted
 * match ID so future notifications for that match are suppressed.
 *
 * Muted match IDs are stored in SharedPreferences as a string set and can be
 * queried via the [isMatchMuted] companion helper.
 */
class MuteMatchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MuteMatchReceiver"
        const val EXTRA_MATCH_ID = "extra_match_id"
        private const val PREFS_NAME = "bouncer_mute_prefs"
        private const val KEY_MUTED_MATCHES = "muted_matches"

        /**
         * Returns `true` when the given [matchId] has been muted by the user.
         */
        fun isMatchMuted(context: Context, matchId: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val muted = prefs.getStringSet(KEY_MUTED_MATCHES, emptySet()) ?: emptySet()
            return matchId in muted
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID)
        if (matchId.isNullOrEmpty()) {
            Log.w(TAG, "Received mute action without a match ID")
            return
        }

        Log.d(TAG, "Muting match: $matchId")

        // Persist the muted match
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_MUTED_MATCHES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(matchId)
        prefs.edit().putStringSet(KEY_MUTED_MATCHES, current).apply()

        // Cancel any existing notifications for this match
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = 5000 + matchId.hashCode()
            notificationManager.cancel(notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification for match $matchId", e)
            Sentry.captureException(e)
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "mute"
            message = "Match muted: $matchId"
            level = SentryLevel.INFO
            setData("match_id", matchId)
        })
    }
}
