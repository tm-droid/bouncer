package com.tanush.bouncer.services

import com.tanush.bouncer.data.remote.AppwriteClient
import org.json.JSONObject

/**
 * Tells the backend "a viewer currently has this match open" so bouncer-match-sync can gate
 * its per-ball LLM generation to matches someone is actually watching (idle matches cost nothing).
 *
 * Fire-and-forget: a failed ping just means match-sync skips this match for one cron tick;
 * the next ~90s heartbeat heals it. Callers invoke [ping] on open + on a heartbeat while a
 * Live match-detail screen is visible.
 */
object MatchPresenceReporter {
    private const val FUNCTION_ID = "bouncer-match-presence"

    suspend fun ping(matchId: String) {
        if (matchId.isBlank()) return
        runCatching {
            AppwriteClient.executeFunction(FUNCTION_ID, JSONObject().put("matchId", matchId).toString())
        }
    }
}
