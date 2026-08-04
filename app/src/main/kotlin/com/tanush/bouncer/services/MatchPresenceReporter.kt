package com.tanush.bouncer.services

import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.utils.sentryBreadcrumb
import com.tanush.bouncer.utils.sentryCaptureThrottled
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Tells the backend "a viewer currently has this match open" so bouncer-match-sync can gate
 * its per-ball LLM generation to matches someone is actually watching (idle matches cost nothing).
 *
 * Fire-and-forget in effect — a failed ping only means match-sync skips this match for one cron
 * tick and the next heartbeat heals it — but **not** silent. This was a bare `runCatching {}` with
 * no handler, and the gate it feeds went dead for five days without a single signal: every
 * match-sync run logged "0 with viewers", so only the 45-minute slow tier wrote commentary and the
 * app fell through to rendering raw Cricbuzz text. A heartbeat nobody can see failing is a feature
 * that switches itself off.
 */
object MatchPresenceReporter {
    private const val FUNCTION_ID = "bouncer-match-presence"

    /** True when the backend accepted the ping; false means match-sync still sees this match as unwatched. */
    suspend fun ping(matchId: String): Boolean {
        if (matchId.isBlank()) return false
        return try {
            val response = AppwriteClient.executeFunction(
                FUNCTION_ID,
                JSONObject().put("matchId", matchId).toString()
            )
            if (response == null) {
                // null covers both the guard-blocked and the not-initialized path; executeFunction
                // reports which one it was, so this only has to say the gate is not being fed.
                sentryCaptureThrottled(
                    key = "presence.ping.unavailable",
                    message = "Match presence ping returned nothing — match-sync will treat this match as unwatched",
                    data = mapOf("matchId" to matchId)
                )
                false
            } else {
                sentryBreadcrumb(
                    "service.presence",
                    "Presence ping accepted",
                    SentryLevel.DEBUG,
                    mapOf("matchId" to matchId)
                )
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sentryCaptureThrottled(
                key = "presence.ping.failed",
                message = "Match presence ping failed: ${e.message}",
                data = mapOf("matchId" to matchId)
            )
            false
        }
    }
}
