package com.tanush.bouncer.data

import com.tanush.bouncer.data.AppJson
import android.content.Context
import android.util.Log
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.network.RetrofitClient
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
    private const val MILLIS_PER_SECOND = 1000L

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

        val stored = readMatchesFromStorage(type)
        if (stored == null) {
            // UnknownException rather than NetworkException: `downloadFile` also returns null when
            // the read guard is latched on a 402, and telling the user to check their connection
            // would be a confident wrong answer.
            val error = AppException.UnknownException(
                "No match list available from Appwrite Storage for $type"
            )
            Log.e(TAG, "Error fetching matches for type: $type", error)
            sentryBreadcrumb(
                "network.api",
                "Error fetching matches",
                SentryLevel.ERROR,
                mapOf("type" to type, "error" to error.message)
            )
            Sentry.captureException(error)
            Sentry.metrics().count("repository.getMatches.error", 1.0)
            return AppResult.Error(error)
        }

        Sentry.metrics().gauge("repository.matches.count", stored.value.matches.size.toDouble())
        Sentry.metrics().count("repository.getMatches.success", 1.0)
        Sentry.logger().log(SentryLogLevel.INFO, "Repository getMatches success for %s", type)

        // A file past its freshness window is served rather than discarded, but deliberately not
        // cached: the next call retries the download instead of being answered from memory with the
        // same stale payload. This replaced a fallback to a separately-hosted scraper API — which
        // was reached on every late cron run, not only during an outage, and which duplicated data
        // Appwrite already had.
        if (stored.isFresh) {
            matchesCache.put(type, stored.value)
        } else {
            sentryBreadcrumb(
                "network.api",
                "Serving stale match list",
                SentryLevel.WARNING,
                mapOf("type" to type, "ageMs" to stored.ageMs),
            )
        }
        return AppResult.Success(stored.value)
    }

    private class StoredMatches(
        val value: SanwebMatchesListResponse,
        val ageMs: Long,
        val isFresh: Boolean,
    )

    private suspend fun readMatchesFromStorage(type: String): StoredMatches? {
        return try {
            val fileId = "$type-matches"
            // The HTTP cache is bounded by the same age this method then reports on, so a body that
            // would only be judged stale is not re-downloaded.
            val maxAgeMs = CacheConfig.matchStorageMaxAgeMs(type)
            val maxAgeSeconds = (maxAgeMs / MILLIS_PER_SECOND).toInt()
            val bytes = AppwriteClient.downloadFile("cricket-stats", fileId, maxAgeSeconds) ?: return null
            val json = org.json.JSONObject(String(bytes))
            val fetchedAt = json.getLong("fetchedAt")
            val ageMs = System.currentTimeMillis() - fetchedAt
            val dataJson = json.getJSONObject("data").toString()
            StoredMatches(
                value = AppJson.decodeFromString<SanwebMatchesListResponse>(dataJson),
                ageMs = ageMs,
                isFresh = ageMs <= maxAgeMs,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "readMatchesFromStorage failed for $type", e)
            null
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
