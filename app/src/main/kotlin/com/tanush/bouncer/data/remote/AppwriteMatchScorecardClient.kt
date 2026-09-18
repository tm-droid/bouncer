package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.Scorecard
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Full scorecards, split out of `match-live-detail` so they stop riding that row's realtime channel.
 *
 * Measured on live rows, the scorecard was 85-93% of the detail payload (4-9 KB of 4-9 KB) and was
 * pushed to every subscriber of that match once a minute. On its own row it can be subscribed
 * independently, so the live card's 650-byte updates no longer drag the whole card table with them.
 *
 * No index — the row id is the sanitized matchId, so the initial [getForMatch] is one `getRow` and
 * one billed read. Everything after that arrives over [observeMatch], and Realtime is not billed as
 * reads.
 */
object AppwriteMatchScorecardClient : AppwriteTableClient("AppwriteMatchScorecard") {
    private const val TABLE_ID = "match-scorecard"

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    suspend fun getForMatch(matchId: String): ScorecardSnapshot? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val row = try {
                    guardedRead("matchScorecard.fetch") {
                        it.getRow(databaseId = dbId, tableId = tableId, rowId = sanitizeMatchId(matchId))
                    } ?: return@runCatching null
                } catch (e: AppwriteException) {
                    // 404 is the normal "match-sync has not written one yet" case, not an error.
                    if (e.code == 404) return@runCatching null
                    throw e
                }
                val raw = row.data["scorecard"] as? String ?: return@runCatching null
                // Carries the row's `updated_at` for the same reason the detail row does: this row
                // freezes at the same instant, so "stored" and "fresh" have to be distinguishable.
                parseScorecard(raw)?.let { ScorecardSnapshot(it, rowUpdatedAtMs(row.data["updated_at"])) }
            }.onFailure { e ->
                logSilentFailure("Scorecard fetch failed", e)
            }.getOrNull()
        }
    }

    /**
     * Scorecard updates for one match, for as long as the flow is collected.
     *
     * Subscribed for the whole life of the detail screen rather than only while the Scorecard tab is
     * on screen: the Overview's bowler figures, fall-of-wickets phases and innings summary read the
     * same scorecard, so a tab-scoped subscription would leave them frozen at whatever the initial
     * fetch returned. Realtime costs no billed reads, which is the quota this work exists to protect.
     */
    fun observeMatch(matchId: String): Flow<ScorecardSnapshot> = callbackFlow {
        if (!isReady) {
            close()
            return@callbackFlow
        }
        val handle = acquireChannel(rowChannel(sanitizeMatchId(matchId))) { response ->
            scorecardFrom(response, matchId)?.let { trySend(it) }
        }
        awaitClose { handle.close() }
    }

    /** The scorecard carried by a Realtime event, or null if it is not one we should apply. */
    private fun scorecardFrom(
        response: io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>,
        matchId: String,
    ): ScorecardSnapshot? {
        if (response.events.none { it.contains(".create") || it.contains(".update") }) return null
        val payload = response.payload
        // Defensive, same as the detail client: a sanitizeMatchId drift becomes "no events" rather
        // than another match's scorecard.
        if (payload["match_id"] as? String != matchId) return null
        val raw = payload["scorecard"] as? String ?: return null
        return parseScorecard(raw)?.let { ScorecardSnapshot(it, rowUpdatedAtMs(payload["updated_at"])) }
    }

    private fun parseScorecard(raw: String): Scorecard? =
        runCatching { AppJson.decodeFromString<Scorecard>(raw) }
            .onFailure { logSilentFailure("Scorecard parse failed", it) }
            .getOrNull()
}
