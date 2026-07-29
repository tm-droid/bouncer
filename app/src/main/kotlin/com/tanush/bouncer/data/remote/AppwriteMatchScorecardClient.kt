package com.tanush.bouncer.data.remote

import com.google.gson.Gson
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.Scorecard
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full scorecards, split out of `match-live-detail` so they stop riding the realtime channel.
 *
 * A scorecard is ~60 KB and was carried inside the detail row's `payload`, which means every device
 * subscribed to that match paid for it once a minute even though only the Scorecard tab renders it.
 * This table is deliberately **never subscribed** and has **no index** — the row id is the sanitized
 * matchId, so a lookup is one `getRow` and one billed read, on demand.
 */
object AppwriteMatchScorecardClient : AppwriteTableClient("AppwriteMatchScorecard") {
    private const val TABLE_ID = "match-scorecard"
    private val gson = Gson()

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    suspend fun getForMatch(matchId: String): Scorecard? {
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
                gson.fromJson(raw, Scorecard::class.java)
            }.onFailure { e ->
                logSilentFailure("Scorecard fetch failed", e)
            }.getOrNull()
        }
    }
}
