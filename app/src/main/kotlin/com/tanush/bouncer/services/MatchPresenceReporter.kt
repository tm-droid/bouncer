package com.tanush.bouncer.services

import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.FunctionOutcome
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
            // Detailed, not `executeFunction`: that flattens a latched EXECUTIONS guard, an
            // uninitialised SDK, a FAILED execution and a 4xx/5xx into one null, and the event this
            // path raises then could not say which. The server side is healthy far more often than
            // not, so the outcome class is the whole diagnosis.
            val outcome = AppwriteClient.executeFunctionDetailed(
                FUNCTION_ID,
                JSONObject().put("matchId", matchId).toString()
            )
            if (outcome !is FunctionOutcome.Success) {
                // A breadcrumb, not an event. Every way to get here is already reported, once, by
                // `executeFunctionDetailed` under this function's id: an uninitialised SDK, a
                // latched EXECUTIONS guard, a FAILED execution, and "returned HTTP <status>"
                // (ANDROID-3H). The one case it deliberately does not report is the device being
                // offline — and that case alone was most of what this capture raised (ANDROID-3M),
                // because a `captureMessage` never reaches the exception filter that drops it.
                sentryBreadcrumb(
                    "service.presence",
                    "Presence ping not accepted — match-sync will treat this match as unwatched",
                    SentryLevel.WARNING,
                    mapOf("matchId" to matchId, "outcome" to outcomeLabel(outcome))
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

    /**
     * Names the failure without carrying a body: an HTTP status is the actionable half, and the
     * response body can hold match text that has no business being a Sentry tag.
     */
    private fun outcomeLabel(outcome: FunctionOutcome): String = when (outcome) {
        is FunctionOutcome.HttpError -> "http_${outcome.statusCode}"
        FunctionOutcome.Unavailable -> "unavailable"
        is FunctionOutcome.Success -> "success"
    }
}
