package com.tanush.bouncer.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.PlayerCacheEntity
import com.tanush.bouncer.data.remote.AppwritePlayerProfilesClient
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.utils.WorkerLogger
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import com.tanush.bouncer.utils.sentryBreadcrumb

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

        sentryBreadcrumb("player_cache", "PlayerCacheWorker started for '$playerName' (attempt $runAttemptCount)")

        return try {
            cachePlayer(playerName)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Network timeout in PlayerCacheWorker for '$playerName'", e)
            sentryBreadcrumb(
                "player_cache",
                "Cache failed (timeout) for '$playerName': ${e.message}",
                SentryLevel.WARNING
            )
            retryOrFail()
        } catch (e: UnknownHostException) {
            Log.w(TAG, "No network in PlayerCacheWorker for '$playerName'", e)
            sentryBreadcrumb(
                "player_cache",
                "Cache failed (no network) for '$playerName': ${e.message}",
                SentryLevel.WARNING
            )
            retryOrFail()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in PlayerCacheWorker for '$playerName'", e)
            Sentry.captureException(e)
            Result.failure()
        }
    }

    private suspend fun cachePlayer(playerName: String): Result {
        val playerId = playerName.lowercase().replace(" ", "_")
        val db = BouncerDatabase.getInstance(context)

        val lastUpdated = db.playerCacheDao().getLastUpdated(playerId)
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

        if (lastUpdated != null && (now - lastUpdated) < sevenDaysMs) {
            Log.d(TAG, "Skipping '$playerName' — cached ${(now - lastUpdated) / 1000 / 60 / 60} hours ago")
            sentryBreadcrumb("player_cache", "Skipped '$playerName' — cached recently")
            return Result.success()
        }

        // Check global Appwrite cache before hitting the scraper.
        val remoteProfile = AppwritePlayerProfilesClient.pullProfile(playerId)
        if (remoteProfile != null) {
            db.playerCacheDao().upsert(remoteProfile)
            Log.d(TAG, "Appwrite hit for '$playerName' — skipped scrape, warmed Room")
            sentryBreadcrumb("player_cache", "Appwrite cache hit for '$playerName' — skipped scrape")
            return Result.success()
        }

        val results = PlayerProfileScraper.searchPlayers(playerName)
        val firstResult = results.firstOrNull() ?: run {
            Log.d(TAG, "Player '$playerName' not found in search — skipping")
            sentryBreadcrumb("player_cache", "Player '$playerName' not found")
            return Result.success()
        }

        val profile = PlayerProfileScraper.scrapeProfile(firstResult.name) ?: run {
            Log.d(TAG, "Could not scrape profile for '$playerName'")
            sentryBreadcrumb("player_cache", "Profile scrape returned null for '$playerName'", SentryLevel.WARNING)
            return Result.success()
        }

        storeProfile(context, playerId, profile, teamName = firstResult.country)

        Log.d(TAG, "Cached player profile for '${profile.name}'")
        sentryBreadcrumb("player_cache", "Cached player '${profile.name}' successfully")

        return Result.success()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < WORKER_MAX_RETRIES) Result.retry() else Result.failure()

    companion object {
        const val TAG = "PlayerCacheWorker"
        const val KEY_PLAYER_NAME = "player_name"

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

            WorkerLogger.enqueued("player_cache", "One-time work enqueued for '$playerName'")
        }

        /**
         * Writes a scraped profile to Room and mirrors it to the global Appwrite cache.
         *
         * The detail screen scrapes the full profile to render it, and used to enqueue this worker
         * on open as well — which, on an Appwrite miss, searched and scraped the same player a
         * second time. The screen now hands its own result here instead. Skips the write when Room
         * already holds a copy younger than [FRESH_MS], which is the worker's own dedupe window.
         */
        suspend fun storeProfile(
            context: Context,
            playerId: String,
            profile: com.tanush.bouncer.models.PlayerProfile,
            teamName: String,
        ) {
            val dao = BouncerDatabase.getInstance(context).playerCacheDao()
            val lastUpdated = dao.getLastUpdated(playerId)
            if (lastUpdated != null && System.currentTimeMillis() - lastUpdated < FRESH_MS) return
            val entity = PlayerCacheEntity(
                playerId = playerId,
                name = profile.name,
                country = profile.country,
                role = profile.role,
                teamName = teamName,
                statsJson = serializeStats(profile.stats)
            )
            dao.upsert(entity)
            AppwritePlayerProfilesClient.pushProfile(entity)
        }

        fun playerIdFor(playerName: String): String = playerName.lowercase().replace(" ", "_")

        /**
         * A cached profile for [playerName], from Room and then the shared Appwrite table.
         *
         * The same order this worker already uses, exposed so the profile screen can take it too.
         * That screen scraped Cricbuzz on every open and read neither cache, which meant a page
         * load per visit, a different answer from the one search gives, and a global cache whose
         * whole purpose — the first device to scrape a player saves every other device the
         * scrape — applied to everything except the screen that shows the player.
         *
         * Null when neither tier has a usable copy: no row, a row older than [FRESH_MS], or a row
         * whose stats did not survive. A profile with a name and no figures is worse than a
         * scrape, so it is not offered as one.
         */
        suspend fun cachedProfile(context: Context, playerName: String): PlayerProfile? {
            val playerId = playerIdFor(playerName)
            val dao = BouncerDatabase.getInstance(context).playerCacheDao()
            val local = dao.getById(playerId)
            if (local != null && System.currentTimeMillis() - local.lastUpdated < FRESH_MS) {
                return profileFrom(local)
            }
            val remote = AppwritePlayerProfilesClient.pullProfile(playerId) ?: return null
            dao.upsert(remote)
            return profileFrom(remote)
        }

        private fun profileFrom(entity: PlayerCacheEntity): PlayerProfile? {
            val stats = deserializeStats(entity.statsJson)
            if (stats.isEmpty()) return null
            return PlayerProfile(
                id = entity.playerId,
                name = entity.name,
                country = entity.country,
                role = entity.role,
                teams = listOf(entity.teamName).filter { it.isNotBlank() },
                stats = stats,
            )
        }

        /**
         * The inverse of [serializeStats].
         *
         * Hand-written to match it field for field rather than routed through kotlinx: the stored
         * JSON is this function's own shape, rows written by older builds may be missing fields,
         * and a strict decoder would throw the whole profile away over one absent key. Every field
         * falls back to the model's own default.
         */
        internal fun deserializeStats(json: String): Map<String, FormatStats> = runCatching {
            val root = JSONObject(json)
            root.keys().asSequence().associateWith { format ->
                val o = root.getJSONObject(format)
                FormatStats(
                    matches = o.optInt("matches"),
                    innings = o.optInt("innings"),
                    runs = o.optInt("runs"),
                    highScore = o.optString("highScore", "-"),
                    average = o.optDouble("average", 0.0),
                    strikeRate = o.optDouble("strikeRate", 0.0),
                    hundreds = o.optInt("hundreds"),
                    fifties = o.optInt("fifties"),
                    fours = o.optInt("fours"),
                    sixes = o.optInt("sixes"),
                    wickets = o.optInt("wickets"),
                    bowlingAverage = o.optDouble("bowlingAverage", 0.0),
                    economy = o.optDouble("economy", 0.0),
                    bestBowling = o.optString("bestBowling", "-"),
                    fiveWickets = o.optInt("fiveWickets"),
                    catches = o.optInt("catches"),
                )
            }
        }.getOrDefault(emptyMap())

        private const val FRESH_MS = 7L * 24 * 60 * 60 * 1000

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
    }
}
