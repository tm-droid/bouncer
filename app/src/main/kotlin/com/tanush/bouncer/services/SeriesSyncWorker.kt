package com.tanush.bouncer.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.network.SeriesScraper
import com.tanush.bouncer.utils.WorkerLogger
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import com.tanush.bouncer.utils.sentryBreadcrumb

/**
 * WorkManager-based periodic worker that syncs active cricket series into the series_cache table.
 * Runs every 24 hours.
 */
class SeriesSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        sentryBreadcrumb("series_sync", "SeriesSyncWorker started (attempt $runAttemptCount)")

        return try {
            val db = BouncerDatabase.getInstance(context)

            // Prune highlights older than 7 days. deleteOlderThan exists in the DAO but was never wired — table would grow unbounded.
            val highlightCutoff = System.currentTimeMillis() - (CacheConfig.HIGHLIGHTS_FRESHNESS_MS * 7 * 24)
            db.highlightDao().deleteOlderThan(highlightCutoff)
            Log.d(TAG, "Pruned highlights older than 7 days")

            // Read from server-side Appwrite Storage first; fall back to on-device Cricbuzz scrape.
            val entities = tryReadSeriesFromStorage()
                ?: SeriesScraper().fetchActiveSeries().map { s ->
                    SeriesCacheEntity(
                        seriesId = s.id,
                        name = s.name,
                        category = s.category.name,
                        startDate = s.startDate,
                        endDate = s.endDate
                    )
                }

            db.seriesCacheDao().upsertAll(entities)

            Log.d(TAG, "Series sync succeeded — ${entities.size} series cached")
            sentryBreadcrumb("series_sync", "Sync succeeded — ${entities.size} series cached")

            Result.success()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Network timeout in SeriesSyncWorker", e)
            sentryBreadcrumb("series_sync", "Sync failed (timeout): ${e.message}", SentryLevel.WARNING)
            if (runAttemptCount < WORKER_MAX_RETRIES) {
                Result.retry()
            } else {
                Sentry.captureMessage("SeriesSyncWorker exhausted retries on timeout after $runAttemptCount attempts", SentryLevel.ERROR)
                Result.failure()
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "No network in SeriesSyncWorker", e)
            sentryBreadcrumb("series_sync", "Sync failed (no network): ${e.message}", SentryLevel.WARNING)
            if (runAttemptCount < WORKER_MAX_RETRIES) {
                Result.retry()
            } else {
                Sentry.captureMessage("SeriesSyncWorker exhausted retries (no network) after $runAttemptCount attempts", SentryLevel.ERROR)
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in SeriesSyncWorker", e)
            Sentry.captureException(e)
            Result.failure()
        }
    }

    private suspend fun tryReadSeriesFromStorage(): List<SeriesCacheEntity>? {
        return try {
            // Bounded by the same age the `fetchedAt` check below enforces.
            val bytes = AppwriteClient.downloadFile("cricket-stats", "active-series", SERIES_HTTP_MAX_AGE_SECONDS)
                ?: return null
            val json = org.json.JSONObject(String(bytes))
            val fetchedAt = json.getLong("fetchedAt")
            if (System.currentTimeMillis() - fetchedAt > SERIES_STORAGE_TTL_MS) return null
            val array = json.getJSONArray("data")
            (0 until array.length()).map { i ->
                val s = array.getJSONObject(i)
                SeriesCacheEntity(
                    seriesId = s.getString("id"),
                    name = s.getString("name"),
                    category = s.optString("category", "INTERNATIONAL"),
                    startDate = s.optString("startDate", ""),
                    endDate = s.optString("endDate", "")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Series Storage read failed, falling back to scrape", e)
            sentryBreadcrumb("series_sync", "Appwrite storage read failed (falling back to scrape): ${e.message}", SentryLevel.WARNING)
            null
        }
    }

    companion object {
        const val TAG = "SeriesSyncWorker"
        private const val UNIQUE_WORK_NAME = "series_sync_periodic"
        private const val SERIES_STORAGE_TTL_MS = 24 * 3_600_000L
        private const val SERIES_HTTP_MAX_AGE_SECONDS = 24 * 60 * 60

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<SeriesSyncWorker>(
                24, TimeUnit.HOURS
            ).setConstraints(networkConstraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            WorkerLogger.enqueued("series_sync", "Periodic work enqueued (24h)")
        }
    }
}
