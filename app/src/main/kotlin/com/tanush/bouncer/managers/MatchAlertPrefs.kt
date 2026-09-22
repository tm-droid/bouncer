package com.tanush.bouncer.managers

import kotlinx.serialization.Serializable

/**
 * Which pushes one followed match sends. Separate from the Live Update card on purpose: the card is
 * one ongoing notification that re-renders in place, an alert is a new notification that makes a
 * sound, and a user who wants one very often does not want the other.
 *
 * Each flag matches a `type` the server sends to the match's topic (`match-sync/src/alerts.js`,
 * `poll-live-matches` for the start).
 */
@Serializable
data class MatchAlertPrefs(
    val wickets: Boolean = false,
    val boundaries: Boolean = false,
    val milestones: Boolean = false,
    /** The match going live and its result. */
    val startAndResult: Boolean = false,
) {
    val anyOn: Boolean get() = wickets || boundaries || milestones || startAndResult

    companion object {
        val NONE = MatchAlertPrefs()

        /** What following a match turns on. Boundaries are off: a T20 has forty of them. */
        val DEFAULT = MatchAlertPrefs(wickets = true, boundaries = false, milestones = true, startAndResult = true)
    }
}

/** FCM topic subscription, behind an interface so the manager is testable without Firebase. */
interface MatchTopicSubscriber {
    fun subscribe(topic: String)
    fun unsubscribe(topic: String)
}

/** Mirrors `matchTopic` in the functions' vendored `fcm.js`. */
fun matchTopic(matchId: String): String =
    "match_" + matchId.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_.~%" }
