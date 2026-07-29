package com.tanush.bouncer.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.network.RetrofitClient
import com.tanush.bouncer.network.models.SanwebMatchResponse
import com.tanush.bouncer.network.models.SanwebMatchesListResponse
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import com.tanush.bouncer.utils.sentryBreadcrumb

object CricketRepository {
    private const val TAG = "CricketRepository"

    private val matchesCache = MatchesResponseCache.shared
    private val matchesSingleFlight = SingleFlight<String, AppResult<SanwebMatchesListResponse>>()

    /**
     * Cache-first, then single-flight, then network.
     *
     * The memory check has to come before the download: the Appwrite Storage path used to fetch the
     * whole payload and only then test its embedded `fetchedAt` stamp, so a stale file still cost
     * full bandwidth. Every caller (live poll loop, Android Auto, ScorePollingWorker) already funnels
     * through here, so fixing it here fixes all of them without touching a call site.
     */
    suspend fun getMatches(type: String): AppResult<SanwebMatchesListResponse> {
        matchesCache.get(type)?.let { return AppResult.Success(it) }
        return matchesSingleFlight.run(type) { fetchMatches(type) }
    }

    private suspend fun fetchMatches(type: String): AppResult<SanwebMatchesListResponse> {
        // Re-check under the single-flight: a concurrent caller may have populated the cache while
        // this call was queued behind the lock.
        matchesCache.get(type)?.let { return AppResult.Success(it) }
        sentryBreadcrumb("network.api", "Fetching matches", data = mapOf("type" to type))
        // Try Appwrite Storage first (updated by backend function every minute)
        val cached = tryReadMatchesFromStorage(type)
        if (cached != null) {
            matchesCache.put(type, cached)
            return AppResult.Success(cached)
        }
        // Fall back to direct Vercel API. Cached too — otherwise a Storage outage would send every
        // 6s poll straight to Vercel.
        return AppResult.runCatchingSuspend {
            val resp = RetrofitClient.vercelApi.getMatches(type)
            Log.d(TAG, "Successfully fetched matches for type: $type")
            Sentry.metrics().gauge("repository.matches.count", resp.matches.size.toDouble())
            Sentry.metrics().count("repository.getMatches.success", 1.0)
            Sentry.logger().log(SentryLogLevel.INFO, "Repository getMatches success for %s", type)
            sentryBreadcrumb("network.api", "Successfully fetched matches", data = mapOf("type" to type))
            matchesCache.put(type, resp)
            resp
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching matches for type: $type", it.exception)
                sentryBreadcrumb(
                    "network.api",
                    "Error fetching matches",
                    SentryLevel.ERROR,
                    mapOf("type" to type, "error" to it.exception.message)
                )
                Sentry.captureException(it.exception)
                Sentry.metrics().count("repository.getMatches.error", 1.0)
            }
        }
    }

    private suspend fun tryReadMatchesFromStorage(type: String): SanwebMatchesListResponse? {
        return try {
            val fileId = "$type-matches"
            val bytes = AppwriteClient.downloadFile("cricket-stats", fileId) ?: return null
            val json = org.json.JSONObject(String(bytes))
            val fetchedAt = json.getLong("fetchedAt")
            if (System.currentTimeMillis() - fetchedAt > CacheConfig.matchStorageMaxAgeMs(type)) return null
            val dataJson = json.getJSONObject("data").toString()
            Gson().fromJson(dataJson, SanwebMatchesListResponse::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "tryReadMatchesFromStorage failed for $type", e)
            null
        }
    }

    suspend fun getMatchScore(id: String): AppResult<SanwebMatchResponse> {
        sentryBreadcrumb("network.api", "Fetching match score", data = mapOf("matchId" to id))
        return AppResult.runCatchingSuspend {
            val resp = RetrofitClient.vercelApi.getMatchScore(id)
            Log.d(TAG, "Successfully fetched match score for id: $id")
            Sentry.metrics().count("repository.getMatchScore.success", 1.0)
            sentryBreadcrumb("network.api", "Successfully fetched match score", data = mapOf("matchId" to id))
            resp
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching match score for id: $id", it.exception)
                sentryBreadcrumb(
                    "network.api",
                    "Error fetching match score",
                    SentryLevel.ERROR,
                    mapOf("matchId" to id, "error" to it.exception.message)
                )
                Sentry.captureException(it.exception)
                Sentry.metrics().count("repository.getMatchScore.error", 1.0)
            }
        }
    }

    suspend fun getProbabilityHistory(matchId: String, localApi: com.tanush.bouncer.network.LocalApi? = null): AppResult<List<com.tanush.bouncer.models.WinProbabilityPoint>> {
        sentryBreadcrumb("network.api", "Fetching probability history", data = mapOf("matchId" to matchId))
        return AppResult.runCatchingSuspend {
            val api = localApi ?: RetrofitClient.localApi
            val resp = api.getProbability(matchId)
            val mapped = resp.map { dto ->
                com.tanush.bouncer.models.WinProbabilityPoint(
                    over = dto.over,
                    team1Probability = dto.team1Probability,
                    team2Probability = dto.team2Probability,
                    keyEvent = dto.keyEvent
                )
            }
            Log.d(TAG, "Successfully fetched probability history for matchId: $matchId")
            Sentry.metrics().distribution("repository.probability.points", mapped.size.toDouble())
            sentryBreadcrumb(
                "network.api",
                "Successfully fetched probability history",
                data = mapOf("matchId" to matchId, "pointCount" to mapped.size)
            )
            mapped
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching probability history for matchId: $matchId", it.exception)
                sentryBreadcrumb(
                    "network.api",
                    "Error fetching probability history",
                    SentryLevel.ERROR,
                    mapOf("matchId" to matchId, "error" to it.exception.message)
                )
                Sentry.captureException(it.exception)
                Sentry.metrics().count("repository.getProbabilityHistory.error", 1.0)
            }
        }
    }

    suspend fun getMatchCommentary(matchId: String): AppResult<String> {
        sentryBreadcrumb("network.api", "Fetching match commentary", data = mapOf("matchId" to matchId))
        return AppResult.runCatchingSuspend {
            val resp = RetrofitClient.localApi.getCommentary(matchId)
            Log.d(TAG, "Successfully fetched match commentary for matchId: $matchId")
            Sentry.metrics().count("repository.getMatchCommentary.success", 1.0)
            sentryBreadcrumb("network.api", "Successfully fetched match commentary", data = mapOf("matchId" to matchId))
            resp.latestCommentary
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching match commentary for matchId: $matchId", it.exception)
                sentryBreadcrumb(
                    "network.api",
                    "Error fetching match commentary",
                    SentryLevel.ERROR,
                    mapOf("matchId" to matchId, "error" to it.exception.message)
                )
                Sentry.captureException(it.exception)
                Sentry.metrics().count("repository.getMatchCommentary.error", 1.0)
            }
        }
    }

    fun getMatchScoreCached(context: Context, matchId: String): Flow<MatchEntity?> = flow {
        val db = BouncerDatabase.getInstance(context)
        val dao = db.matchDao()

        val cached = dao.getMatch(matchId).firstOrNull()
        if (cached != null) {
            emit(cached)
        }

        try {
            val result = getMatchScore(matchId)
            result.getOrNull()?.let { response ->
                val entity = mergedMatchEntity(matchId, cached, response)
                dao.upsertMatch(entity)
                emit(entity)
            }
        } catch (e: Exception) {
            sentryBreadcrumb(
                "cache",
                "Network fetch failed, using cached data",
                SentryLevel.WARNING,
                mapOf("matchId" to matchId)
            )
            Sentry.captureException(e)
            if (cached == null) emit(null)
        }
    }

    fun observeCachedMatches(context: Context): Flow<List<MatchEntity>> {
        val db = BouncerDatabase.getInstance(context)
        return db.matchDao().getAllMatches()
    }

    suspend fun cacheMatch(context: Context, entity: MatchEntity) {
        try {
            val db = BouncerDatabase.getInstance(context)
            db.matchDao().upsertMatch(entity)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    suspend fun purgeStaleCache(context: Context) {
        try {
            val db = BouncerDatabase.getInstance(context)
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            db.matchDao().deleteOlderThan(cutoff)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}

private val TITLE_SEPARATORS = listOf(" vs ", " v ", " vs. ", " against ")

private fun parseTeamFromTitle(title: String, index: Int): String {
    for (sep in TITLE_SEPARATORS) {
        val parts = title.split(sep, ignoreCase = true)
        if (parts.size >= 2) {
            return parts.getOrElse(index) { "" }
                .split(",", "|").first().trim()
        }
    }
    return ""
}

private fun mergedMatchEntity(
    matchId: String,
    cached: MatchEntity?,
    response: SanwebMatchResponse
): MatchEntity = MatchEntity(
    matchId = matchId,
    seriesId = cached?.seriesId ?: "",
    matchTitle = response.title,
    matchType = cached?.matchType ?: "",
    status = response.update,
    venue = cached?.venue ?: "",
    date = cached?.date ?: "",
    matchTime = cached?.matchTime ?: "",
    currentStatus = response.update,
    info = response.livescore,
    team1Name = cached?.team1Name?.takeIf { it.isNotBlank() }
        ?: parseTeamFromTitle(response.title, 0),
    team1Score = response.livescore,
    team2Name = cached?.team2Name?.takeIf { it.isNotBlank() }
        ?: parseTeamFromTitle(response.title, 1),
    team2Score = null,
    lastUpdated = System.currentTimeMillis()
)
