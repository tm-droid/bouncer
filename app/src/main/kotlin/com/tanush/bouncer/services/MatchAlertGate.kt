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

/** The `type` values `match-sync` and `poll-live-matches` send to a match topic. */
object MatchAlertType {
    const val WICKET = "wicket"
    const val BOUNDARY = "boundary"
    const val MILESTONE = "milestone"
    const val MATCH_START = "match_start"
    const val MATCH_END = "match_end"

    val ALL = listOf(WICKET, BOUNDARY, MILESTONE, MATCH_START, MATCH_END)
}
