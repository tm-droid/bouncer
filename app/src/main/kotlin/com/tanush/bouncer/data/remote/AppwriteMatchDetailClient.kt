package com.tanush.bouncer.data.remote

import com.google.gson.Gson
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

// Server-built live match snapshot (batsmen/bowler/score/scorecard), written by
// bouncer-match-sync into match-live-detail as a JSON `payload`. Field names mirror the
// function's output so Gson deserializes straight into the existing device models.
data class MatchLiveDetailPayload(
    val batsmen: List<ScrapedBatsman> = emptyList(),
    val bowler: ScrapedBowler? = null,
    val recentBalls: List<String> = emptyList(),
    val score: String? = null,
    val team1Score: String? = null,
    val team1Overs: String? = null,
    val team2Score: String? = null,
    val team2Overs: String? = null,
    val currentRunRate: String? = null,
    val requiredRunRate: String? = null,
    val scorecard: Scorecard? = null,
    val status: String? = null,
    // Win probability for the FIRST-INNINGS batting side, 0..1, matching the team1Score/team2Score
    // convention above. Null when the server declined to score the match (a Test, an unknown
    // format, or a pre-toss page with no miniscore) — render nothing rather than 50%.
    val winProbabilityTeam1: Double? = null,
    val winProbabilityConfidence: Double? = null,
)

object AppwriteMatchDetailClient : AppwriteTableClient("AppwriteMatchDetail") {
    private const val TABLE_ID = "match-live-detail"
    private val gson = Gson()

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    suspend fun getForMatch(matchId: String): MatchLiveDetailPayload? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                // getRow on the deterministic row id rather than a filtered list: same single
                // billed read, no dependency on idx_match_id, and it exercises sanitizeMatchId on
                // the same path as the subscription below so a mismatch surfaces as a logged 404.
                val row = try {
                    guardedRead("matchDetail.fetch") {
                        it.getRow(databaseId = dbId, tableId = tableId, rowId = sanitizeMatchId(matchId))
                    } ?: return@runCatching null
                } catch (e: AppwriteException) {
                    if (e.code == 404) return@runCatching null
                    throw e
                }
                val raw = row.data["payload"] as? String ?: return@runCatching null
                parsePayload(raw)
            }.onFailure { e ->
                logSilentFailure("Live detail fetch failed", e)
            }.getOrNull()
        }
    }

    /**
     * Live snapshots for one match, for as long as the flow is collected.
     *
     * Subscribes to that match's ROW rather than the whole table. The table channel delivered every
     * row anyone was watching to every device — a 15-30 KB payload per match per minute (up to
     * ~80 KB for a Test), so a device paid bandwidth proportional to what the entire user base had
     * open. Collection is the lifetime: `awaitClose` releases the refcount, and the base class
     * lingers briefly so a rotation does not re-handshake.
     */
    fun observeMatch(matchId: String): Flow<MatchLiveDetailPayload> = callbackFlow {
        if (!isReady) {
            close()
            return@callbackFlow
        }
        val handle = acquireChannel(rowChannel(sanitizeMatchId(matchId))) { response ->
            detailFrom(response, matchId)?.let { trySend(it) }
        }
        awaitClose { handle.close() }
    }

    /** The snapshot carried by a Realtime event, or null if it is not one we should apply. */
    private fun detailFrom(
        response: io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>,
        matchId: String,
    ): MatchLiveDetailPayload? {
        if (response.events.none { it.contains(".create") || it.contains(".update") }) return null
        @Suppress("UNCHECKED_CAST")
        val payload = response.payload as? Map<String, Any> ?: return null
        // Defensive: if sanitizeMatchId ever drifted from the server's, this turns "someone else's
        // match" into "no events" rather than showing the wrong score.
        if (payload["match_id"] as? String != matchId) return null
        val raw = payload["payload"] as? String ?: return null
        return parsePayload(raw)
    }

    private fun parsePayload(raw: String): MatchLiveDetailPayload? =
        runCatching { gson.fromJson(raw, MatchLiveDetailPayload::class.java) }
            .onFailure { logSilentFailure("Live detail payload parse failed", it) }
            .getOrNull()
}
