package com.tanush.bouncer.data

import android.util.Log
import com.tanush.bouncer.network.RetrofitClient
import com.tanush.bouncer.network.models.SanwebMatchResponse
import com.tanush.bouncer.network.models.SanwebMatchesListResponse
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

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
            Result.failure(t)
        }
    }
}
