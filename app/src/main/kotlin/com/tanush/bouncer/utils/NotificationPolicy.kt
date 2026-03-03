package com.tanush.bouncer.utils

import io.sentry.Sentry

/**
 * Encapsulates policy for whether a key event notification should be delivered
 * based on the event title and user preferences.
 */
object NotificationPolicy {
    fun shouldNotify(
        title: String,
        liveNotificationsEnabled: Boolean,
        wicketAlertsEnabled: Boolean,
        boundaryAlertsEnabled: Boolean
    ): Boolean {
        val lowered = title.lowercase()
        return when {
                lowered.contains("wicket") || lowered.contains(" out ") -> liveNotificationsEnabled && wicketAlertsEnabled
                lowered.contains("six") || lowered.contains("sixes") || lowered.contains("four") || lowered.contains("boundary") -> liveNotificationsEnabled && boundaryAlertsEnabled
                else -> liveNotificationsEnabled
        }
    }
}