package com.tanush.bouncer.data.remote

import com.google.gson.Gson
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import io.appwrite.Query
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Dispatchers
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
)

data class DetailEvent(val matchId: String, val payload: MatchLiveDetailPayload)

object AppwriteMatchDetailClient : AppwriteTableClient("AppwriteMatchDetail") {
    private const val TABLE_ID = "match-live-detail"
    private val gson = Gson()

    private val _events = MutableSharedFlow<DetailEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<DetailEvent> = _events

    fun initialize() {
        initTable(ApiKeys.appwriteAiCacheDbId, TABLE_ID)
    }

    suspend fun getForMatch(matchId: String): MatchLiveDetailPayload? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val result = guardedRead("matchDetail.fetch") {
                    it.listRows(
                        databaseId = dbId,
                        tableId = tableId,
                        queries = listOf(Query.equal("match_id", matchId), Query.limit(1))
                    )
                } ?: return@runCatching null
                val raw = result.rows.firstOrNull()?.data?.get("payload") as? String ?: return@runCatching null
                parsePayload(raw)
            }.onFailure { e ->
                logSilentFailure("Live detail fetch failed", e)
            }.getOrNull()
        }
    }

    // App-lifetime subscription; emits every created/updated snapshot into [events].
    fun startRealtime() {
        if (!isReady) return
        if (!subscriptionStarted.compareAndSet(false, true)) return
        startSubscriptionLoop { response ->
            val isWrite = response.events.any { it.contains(".create") || it.contains(".update") }
            if (!isWrite) return@startSubscriptionLoop
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any> ?: return@startSubscriptionLoop
            val matchId = payload["match_id"] as? String ?: return@startSubscriptionLoop
            val raw = payload["payload"] as? String ?: return@startSubscriptionLoop
            val parsed = parsePayload(raw) ?: return@startSubscriptionLoop
            _events.tryEmit(DetailEvent(matchId, parsed))
        }
    }

    private fun parsePayload(raw: String): MatchLiveDetailPayload? =
        runCatching { gson.fromJson(raw, MatchLiveDetailPayload::class.java) }
            .onFailure { logSilentFailure("Live detail payload parse failed", it) }
            .getOrNull()
}
