package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import io.appwrite.exceptions.AppwriteException
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

// Server-built live match snapshot (batsmen/bowler/score/scorecard), written by
// bouncer-match-sync into match-live-detail as a JSON `payload`. Field names mirror the
// function's output so it deserializes straight into the existing device models.
//
// @Serializable is load-bearing and was missing from the Gson -> kotlinx migration (02dd2fa): with
// no compile-time serializer, `AppJson.decodeFromString<MatchLiveDetailPayload>` threw
// "Serializer for class 'MatchLiveDetailPayload' is not found" on every read, the whole
// match-live-detail path was dead, and the only symptom was a warning breadcrumb (ANDROID-3X).
// Pinned by MatchLiveDetailPayloadSerializationTest.
@Serializable
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
    // convention above. Null when the server declined to score the match (an unknown format, or a
    // pre-toss page with no miniscore) — render nothing rather than 50%.
    val winProbabilityTeam1: Double? = null,
    val winProbabilityConfidence: Double? = null,
    // Only a Test carries one: it is the one format where the most likely result is often neither
    // side. Null everywhere else, and a null must render a two-segment bar rather than a draw of
    // zero — "no draw is possible" and "a draw is possible but unlikely" are different claims.
    val winProbabilityDraw: Double? = null,
)

object AppwriteMatchDetailClient : AppwriteTableClient("AppwriteMatchDetail") {
    private const val TABLE_ID = "match-live-detail"

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
        runCatching { AppJson.decodeFromString<MatchLiveDetailPayload>(raw) }
            .onFailure { logSilentFailure("Live detail payload parse failed", it) }
            .getOrNull()
}
