package com.tanush.bouncer.data

import android.content.Context
import android.util.Log
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.network.RetrofitClient
import com.tanush.bouncer.network.models.SanwebMatchResponse
import com.tanush.bouncer.network.models.SanwebMatchesListResponse
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

object CricketRepository {
    private const val TAG = "CricketRepository"
    
    suspend fun getMatches(type: String): Result<SanwebMatchesListResponse> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching matches"
            level = SentryLevel.INFO
            setData("type", type)
        })
        
        return try {
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
            
            Result.success(resp)
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching matches for type: $type", t)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Error fetching matches"
                level = SentryLevel.ERROR
                setData("type", type)
                setData("error", t.message ?: "Unknown error")
            })
            Sentry.captureException(t)
            Sentry.metrics().count("repository.getMatches.error", 1.0)
            Result.failure(t)
        }
    }

    suspend fun getMatchScore(id: String): Result<SanwebMatchResponse> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching match score"
            level = SentryLevel.INFO
            setData("matchId", id)
        })
        
        return try {
            val resp = RetrofitClient.vercelApi.getMatchScore(id)
            Log.d(TAG, "Successfully fetched match score for id: $id")
            Sentry.metrics().count("repository.getMatchScore.success", 1.0)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Successfully fetched match score"
                level = SentryLevel.INFO
                setData("matchId", id)
            })
            
            Result.success(resp)
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching match score for id: $id", t)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Error fetching match score"
                level = SentryLevel.ERROR
                setData("matchId", id)
                setData("error", t.message ?: "Unknown error")
            })
            Sentry.captureException(t)
            Sentry.metrics().count("repository.getMatchScore.error", 1.0)
            Result.failure(t)
        }
    }

    // Fetch probability history from local development API (mock server)

    suspend fun getProbabilityHistory(matchId: String, localApi: com.tanush.bouncer.network.LocalApi? = null): Result<List<com.tanush.bouncer.ui.components.premium.WinProbabilityPoint>> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching probability history"
            level = SentryLevel.INFO
            setData("matchId", matchId)
        })
        
        return try {
            val api = localApi ?: RetrofitClient.localApi
            val resp = api.getProbability(matchId)
            // Map DTO to UI model used by the chart
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
            
            Result.success(mapped)
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching probability history for matchId: $matchId", t)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Error fetching probability history"
                level = SentryLevel.ERROR
                setData("matchId", matchId)
                setData("error", t.message ?: "Unknown error")
            })
            Sentry.captureException(t)
            Sentry.metrics().count("repository.getProbabilityHistory.error", 1.0)
            Result.failure(t)
        }
    }

    suspend fun getMatchCommentary(matchId: String): Result<String> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.api"
            message = "Fetching match commentary"
            level = SentryLevel.INFO
            setData("matchId", matchId)
        })
        
        return try {
            val resp = RetrofitClient.localApi.getCommentary(matchId)
            Log.d(TAG, "Successfully fetched match commentary for matchId: $matchId")
            Sentry.metrics().count("repository.getMatchCommentary.success", 1.0)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Successfully fetched match commentary"
                level = SentryLevel.INFO
                setData("matchId", matchId)
            })
            
            Result.success(resp.latestCommentary)
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching match commentary for matchId: $matchId", t)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.api"
                message = "Error fetching match commentary"
                level = SentryLevel.ERROR
                setData("matchId", matchId)
                setData("error", t.message ?: "Unknown error")
            })
            Sentry.captureException(t)
            Sentry.metrics().count("repository.getMatchCommentary.error", 1.0)
            Result.failure(t)
        }
    }

    /**
     * Cache-first match score fetch. Returns cached data first, then refreshes from network.
     * Fields not available in [SanwebMatchResponse] are preserved from the existing cache entry.
     */
    fun getMatchScoreCached(context: Context, matchId: String): Flow<MatchEntity?> = flow {
        val db = BouncerDatabase.getInstance(context)
        val dao = db.matchDao()

        // Emit cached data first
        val cached = dao.getMatch(matchId).firstOrNull()
        if (cached != null) {
            emit(cached)
        }

        // Fetch fresh from network and cache it
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
            // Already emitted cached data above; if no cache, emit null
            if (cached == null) emit(null)
        }
    }

    /**
     * Observe cached matches reactively.
     */
    fun observeCachedMatches(context: Context): Flow<List<MatchEntity>> {
        val db = BouncerDatabase.getInstance(context)
        return db.matchDao().getAllMatches()
    }

    /**
     * Cache a match entity from external sources (e.g., polling worker, WebSocket).
     */
    suspend fun cacheMatch(context: Context, entity: MatchEntity) {
        try {
            val db = BouncerDatabase.getInstance(context)
            db.matchDao().upsertMatch(entity)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Purge stale cache entries (older than 24 hours).
     */
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
