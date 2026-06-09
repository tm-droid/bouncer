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
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

object CricketRepository {
    private const val TAG = "CricketRepository"

    suspend fun getMatches(type: String): AppResult<SanwebMatchesListResponse> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching matches"
            level = SentryLevel.INFO
            setData("type", type)
        })
        // Try Appwrite Storage first (updated by backend function every minute)
        val cached = tryReadMatchesFromStorage(type)
        if (cached != null) return AppResult.Success(cached)
        // Fall back to direct Vercel API
        return AppResult.runCatchingSuspend {
            val resp = RetrofitClient.vercelApi.getMatches(type)
            Log.d(TAG, "Successfully fetched matches for type: $type")
            Sentry.metrics().gauge("repository.matches.count", resp.matches.size.toDouble())
            Sentry.metrics().count("repository.getMatches.success", 1.0)
            Sentry.logger().log(SentryLogLevel.INFO, "Repository getMatches success for %s", type)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Successfully fetched matches"
                level = SentryLevel.INFO
                setData("type", type)
            })
            resp
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching matches for type: $type", it.exception)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.api"
                    message = "Error fetching matches"
                    level = SentryLevel.ERROR
                    setData("type", type)
                    setData("error", it.exception.message)
                })
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
            if (System.currentTimeMillis() - fetchedAt > 120_000L) return null // stale > 2 min
            val dataJson = json.getJSONObject("data").toString()
            Gson().fromJson(dataJson, SanwebMatchesListResponse::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // graceful fallback
        }
    }

    suspend fun getMatchScore(id: String): AppResult<SanwebMatchResponse> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching match score"
            level = SentryLevel.INFO
            setData("matchId", id)
        })
        return AppResult.runCatchingSuspend {
            val resp = RetrofitClient.vercelApi.getMatchScore(id)
            Log.d(TAG, "Successfully fetched match score for id: $id")
            Sentry.metrics().count("repository.getMatchScore.success", 1.0)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Successfully fetched match score"
                level = SentryLevel.INFO
                setData("matchId", id)
            })
            resp
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching match score for id: $id", it.exception)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.api"
                    message = "Error fetching match score"
                    level = SentryLevel.ERROR
                    setData("matchId", id)
                    setData("error", it.exception.message)
                })
                Sentry.captureException(it.exception)
                Sentry.metrics().count("repository.getMatchScore.error", 1.0)
            }
        }
    }

    suspend fun getProbabilityHistory(matchId: String, localApi: com.tanush.bouncer.network.LocalApi? = null): AppResult<List<com.tanush.bouncer.ui.components.premium.WinProbabilityPoint>> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching probability history"
            level = SentryLevel.INFO
            setData("matchId", matchId)
        })
        return AppResult.runCatchingSuspend {
            val api = localApi ?: RetrofitClient.localApi
            val resp = api.getProbability(matchId)
            val mapped = resp.map { dto ->
                com.tanush.bouncer.ui.components.premium.WinProbabilityPoint(
                    over = dto.over,
                    team1Probability = dto.team1Probability,
                    team2Probability = dto.team2Probability,
                    keyEvent = dto.keyEvent
                )
            }
            Log.d(TAG, "Successfully fetched probability history for matchId: $matchId")
            Sentry.metrics().distribution("repository.probability.points", mapped.size.toDouble())
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Successfully fetched probability history"
                level = SentryLevel.INFO
                setData("matchId", matchId)
                setData("pointCount", mapped.size)
            })
            mapped
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching probability history for matchId: $matchId", it.exception)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.api"
                    message = "Error fetching probability history"
                    level = SentryLevel.ERROR
                    setData("matchId", matchId)
                    setData("error", it.exception.message)
                })
                Sentry.captureException(it.exception)
                Sentry.metrics().count("repository.getProbabilityHistory.error", 1.0)
            }
        }
    }

    suspend fun getMatchCommentary(matchId: String): AppResult<String> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching match commentary"
            level = SentryLevel.INFO
            setData("matchId", matchId)
        })
        return AppResult.runCatchingSuspend {
            val resp = RetrofitClient.localApi.getCommentary(matchId)
            Log.d(TAG, "Successfully fetched match commentary for matchId: $matchId")
            Sentry.metrics().count("repository.getMatchCommentary.success", 1.0)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Successfully fetched match commentary"
                level = SentryLevel.INFO
                setData("matchId", matchId)
            })
            resp.latestCommentary
        }.also {
            if (it is AppResult.Error) {
                Log.e(TAG, "Error fetching match commentary for matchId: $matchId", it.exception)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.api"
                    message = "Error fetching match commentary"
                    level = SentryLevel.ERROR
                    setData("matchId", matchId)
                    setData("error", it.exception.message)
                })
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
                val entity = MatchEntity(
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
                    team1Name = cached?.team1Name ?: "",
                    team1Score = response.livescore,
                    team2Name = cached?.team2Name ?: "",
                    team2Score = null,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.upsertMatch(entity)
                emit(entity)
            }
        } catch (e: Exception) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "cache"
                message = "Network fetch failed, using cached data"
                level = SentryLevel.WARNING
                setData("matchId", matchId)
            })
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
