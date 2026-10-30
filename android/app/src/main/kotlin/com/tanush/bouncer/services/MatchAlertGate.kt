package com.tanush.bouncer.services

import com.tanush.bouncer.managers.MatchAlertPrefs

/** The global switches in Settings → Notifications. Each defaults to on. */
data class GlobalAlertSwitches(
    val matchAlerts: Boolean = true,
    val wickets: Boolean = true,
    val boundaries: Boolean = true,
)

/**
 * Whether a match push of [type] is shown. Three things must all agree: the match's own
 * preferences (what the user picked in the bell's sheet), the global switches, and quiet hours.
 *
 * Unknown types are dropped rather than shown: every type the server sends is listed here, and a
 * new one should be a deliberate addition, not a notification nobody chose.
 */
fun shouldShowMatchAlert(
    type: String,
    prefs: MatchAlertPrefs,
    global: GlobalAlertSwitches,
    quietHours: Boolean,
): Boolean {
    if (quietHours || !global.matchAlerts) return false
    return matchWants(type, prefs) && globalAllows(type, global)
}

private fun matchWants(type: String, prefs: MatchAlertPrefs): Boolean = when (type) {
    MatchAlertType.WICKET -> prefs.wickets
    MatchAlertType.BOUNDARY -> prefs.boundaries
    MatchAlertType.MILESTONE -> prefs.milestones
    MatchAlertType.MATCH_START, MatchAlertType.MATCH_END -> prefs.startAndResult
    else -> false
}

private fun globalAllows(type: String, global: GlobalAlertSwitches): Boolean = when (type) {
    MatchAlertType.WICKET -> global.wickets
    MatchAlertType.BOUNDARY -> global.boundaries
    else -> true
}

private const val ALERT_MAX_AGE_MS = 5 * 60 * 1000L
private const val RESULT_MAX_AGE_MS = 6 * 60 * 60 * 1000L
private const val START_MAX_AGE_MS = 30 * 60 * 1000L

/**
 * How old a push of [type] may be and still be shown. The same figures as `ttlSecondsFor` in
 * `fcm.js`, pinned by `FcmJsVendorParityTest`: FCM drops a message past its TTL, and this is the
 * device's half for one that was already queued, or delivered late by a dozing phone.
 */
fun alertMaxAgeMs(type: String): Long = when (type) {
    MatchAlertType.MATCH_END -> RESULT_MAX_AGE_MS
    MatchAlertType.MATCH_START -> START_MAX_AGE_MS
    else -> ALERT_MAX_AGE_MS
}

/** A push with no send time ([sentAtMs] ≤ 0) is of unknown age, which is never the same as old. */
fun isAlertStale(type: String, sentAtMs: Long, nowMs: Long): Boolean =
    sentAtMs > 0 && nowMs - sentAtMs > alertMaxAgeMs(type)

/** The `type` values `match-sync` and `poll-live-matches` send to a match topic. */
object MatchAlertType {
    const val WICKET = "wicket"
    const val BOUNDARY = "boundary"
    const val MILESTONE = "milestone"
    const val MATCH_START = "match_start"
    const val MATCH_END = "match_end"

    val ALL = listOf(WICKET, BOUNDARY, MILESTONE, MATCH_START, MATCH_END)
}
