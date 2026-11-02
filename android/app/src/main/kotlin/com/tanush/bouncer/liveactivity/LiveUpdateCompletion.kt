package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal

/**
 * Whether the match on a Live Update card is over, and the line that says so.
 *
 * `match-live-detail` stops being written the moment its match leaves `live-matches.json`, so the
 * last payload a card ever receives is routinely a snapshot from before the final ball — "needs 12
 * runs" over a match that has been decided. The matches list is re-scraped every few seconds and
 * carries the result, so where the two disagree the list is simply right. This is the same rule
 * `eb91dcd` applied to the match-detail screen, on the surface that outlives it.
 *
 * Its own file because `LiveUpdateContent.kt` is at detekt's TooManyFunctions threshold.
 */
internal data class LiveUpdateCompletion(
    /**
     * The result line to show in place of the payload's status, or null to keep the payload's.
     *
     * Null when the list has no terminal status, and also when the payload already reports one: a
     * payload that reports the result is the same fact from the source that also has the
     * scoreboard, and overriding it would discard the wording `match-sync` published.
     */
    val result: String?,
    val isComplete: Boolean,
)

internal fun liveUpdateCompletion(match: CricketMatch?, payloadStatus: String?): LiveUpdateCompletion {
    val payloadIsTerminal = MatchStatusClassifier.classify(payloadStatus) == StatusSignal.TERMINAL
    val listIsTerminal = match?.isCompleted == true
    val result = if (!payloadIsTerminal && listIsTerminal) {
        match.currentStatus.trim().takeIf { it.isNotEmpty() } ?: match.status.trim().takeIf { it.isNotEmpty() }
    } else {
        null
    }
    return LiveUpdateCompletion(result, payloadIsTerminal || listIsTerminal)
}
