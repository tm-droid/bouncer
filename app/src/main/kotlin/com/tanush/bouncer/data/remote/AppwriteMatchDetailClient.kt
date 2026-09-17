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
    // The over [recentBalls] describes, in Cricbuzz's own numbering: balls 48.1-48.6 are over 49.
    // Normally the over being bowled (the miniscore's live `recentOvsStats`); on a page without one,
    // the last COMPLETED over. Without the number the device cannot tell those apart from the over
    // its commentary feed is on. Null on an older row, and for The Hundred, which has no overs.
    val recentBallsOver: Int? = null,
    val score: String? = null,
    val team1Score: String? = null,
    val team1Overs: String? = null,
    val team2Score: String? = null,
    val team2Overs: String? = null,
    // The side each of the pairs above belongs to. "team1" here is the FIRST-INNINGS batting side,
    // which is not necessarily the match's teams[0] — without the names the device had no way to
    // tell the two orders apart, and put each score against the wrong team whenever they differed.
    val team1Name: String? = null,
    val team2Name: String? = null,
    // Cricbuzz team ids for the two slots above — the only reliable way to align them with this
    // screen's team order, since the names are short codes.
    val team1Id: String? = null,
    val team2Id: String? = null,
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
    // Numeric match state, built by `liveSituation` in match-sync/src/cricbuzz.js from the same
    // already-parsed miniscore the strings above come from.
    //
    // This shipped on the wire and was never modelled here, so `AppJson.ignoreUnknownKeys` dropped
    // it on every read. Everything in it was instead being inferred on the device — the format from
    // the cached list entry, the batting side from exact string equality between `score` and a side
    // aggregate — and both of those inferences fail in ways this object simply does not have.
    val situation: LiveSituation? = null,
)

/**
 * The live match state as numbers rather than display strings.
 *
 * Every field is nullable: `liveSituation` returns null outright when the miniscore has no current
 * innings, individual fields are null when Cricbuzz omits them, and rows written by older builds of
 * the function have no `situation` at all. A consumer must degrade to the string paths rather than
 * treat absence as zero.
 *
 * "team1" follows the same convention as [MatchLiveDetailPayload]: the side that batted **first**.
 */
@Serializable
data class LiveSituation(
    val inningsId: Int? = null,
    /** Cricbuzz's own format word — "TEST", "ODI", "T20", "T10", "HUNDRED". */
    val matchFormat: String? = null,
    /** Short code of the side currently batting, e.g. "KENT". Never a full team name. */
    val battingTeamName: String? = null,
    val bowlingTeamName: String? = null,
    /**
     * Whether the side batting now is the first-innings side, compared by **team id** on the
     * server. This is the authoritative answer to a question the payload's strings can only be
     * guessed at.
     */
    val battingTeamIsTeam1: Boolean? = null,
    val firstInningsTeamId: String? = null,
    val runsScored: Int? = null,
    val wicketsLost: Int? = null,
    val ballsBowled: Int? = null,
    /** Set only in the innings the match will finish in, so a Test's fourth innings carries one. */
    val target: Int? = null,
    val runsNeeded: Int? = null,
    /** Cricbuzz's own overs-left figure, which already accounts for a reduced-overs match. */
    val oversRemaining: Double? = null,
    val currentRunRate: Double? = null,
    val requiredRunRate: Double? = null,
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
