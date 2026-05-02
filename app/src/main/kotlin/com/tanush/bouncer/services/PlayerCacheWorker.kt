package com.tanush.bouncer.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.PlayerCacheEntity
import com.tanush.bouncer.network.PlayerProfileScraper
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based one-time worker that fetches and caches a single player's profile on-demand.
 * Skips if the player was cached within the last 7 days.
 */
class PlayerCacheWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val playerName = inputData.getString(KEY_PLAYER_NAME)
            ?: return Result.failure()

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "player_cache"
            message = "PlayerCacheWorker started for '$playerName' (attempt $runAttemptCount)"
            level = SentryLevel.INFO
        })

        return try {
            val playerId = playerName.lowercase().replace(" ", "_")
            val db = BouncerDatabase.getInstance(context)

            val lastUpdated = db.playerCacheDao().getLastUpdated(playerId)
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

            if (lastUpdated != null && (now - lastUpdated) < sevenDaysMs) {
                Log.d(TAG, "Skipping '$playerName' — cached ${(now - lastUpdated) / 1000 / 60 / 60} hours ago")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "player_cache"
                    message = "Skipped '$playerName' — cached recently"
                    level = SentryLevel.INFO
                })
                return Result.success()
            }

            val results = PlayerProfileScraper.searchPlayers(playerName)
            val firstResult = results.firstOrNull() ?: run {
                Log.d(TAG, "Player '$playerName' not found in search — skipping")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "player_cache"
                    message = "Player '$playerName' not found"
                    level = SentryLevel.INFO
                })
                return Result.success()
            }

            val profile = PlayerProfileScraper.scrapeProfile(firstResult.name) ?: run {
                Log.d(TAG, "Could not scrape profile for '$playerName'")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "player_cache"
                    message = "Profile scrape returned null for '$playerName'"
                    level = SentryLevel.WARNING
                })
                return Result.success()
            }

            val entity = PlayerCacheEntity(
                playerId = playerId,
                name = profile.name,
                country = profile.country,
                role = profile.role,
                teamName = firstResult.country,
                statsJson = serializeStats(profile.stats)
            )

            db.playerCacheDao().upsert(entity)

            Log.d(TAG, "Cached player profile for '${profile.name}'")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "player_cache"
                message = "Cached player '${profile.name}' successfully"
                level = SentryLevel.INFO
            })

            Result.success()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Network timeout in PlayerCacheWorker for '$playerName'", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "player_cache"
                message = "Cache failed (timeout) for '$playerName': ${e.message}"
                level = SentryLevel.WARNING
            })
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: UnknownHostException) {
            Log.w(TAG, "No network in PlayerCacheWorker for '$playerName'", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "player_cache"
                message = "Cache failed (no network) for '$playerName': ${e.message}"
                level = SentryLevel.WARNING
            })
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in PlayerCacheWorker for '$playerName'", e)
            Sentry.captureException(e)
            Result.failure()
        }
    }

    private fun serializeStats(stats: Map<String, com.tanush.bouncer.models.FormatStats>): String {
        val root = JSONObject()
        for ((format, fs) in stats) {
            val obj = JSONObject()
            obj.put("matches", fs.matches)
            obj.put("innings", fs.innings)
            obj.put("runs", fs.runs)
            obj.put("highScore", fs.highScore)
            obj.put("average", fs.average)
            obj.put("strikeRate", fs.strikeRate)
            obj.put("hundreds", fs.hundreds)
            obj.put("fifties", fs.fifties)
            obj.put("fours", fs.fours)
            obj.put("sixes", fs.sixes)
            obj.put("wickets", fs.wickets)
            obj.put("bowlingAverage", fs.bowlingAverage)
            obj.put("economy", fs.economy)
            obj.put("bestBowling", fs.bestBowling)
            obj.put("fiveWickets", fs.fiveWickets)
            obj.put("catches", fs.catches)
            root.put(format, obj)
        }
        return root.toString()
    }

    companion object {
        const val TAG = "PlayerCacheWorker"
        const val KEY_PLAYER_NAME = "player_name"
        private const val MAX_RETRIES = 3

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueFor(context: Context, playerName: String) {
            val inputData = workDataOf(KEY_PLAYER_NAME to playerName)

            val request = OneTimeWorkRequestBuilder<PlayerCacheWorker>()
                .setConstraints(networkConstraints)
                .setInputData(inputData)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            val playerId = playerName.lowercase().replace(" ", "_")
            WorkManager.getInstance(context).enqueueUniqueWork(
                "player_cache_$playerId",
                ExistingWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Player cache work enqueued for '$playerName'")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "player_cache"
                message = "One-time work enqueued for '$playerName'"
                level = SentryLevel.INFO
            })
        }
    }
}
