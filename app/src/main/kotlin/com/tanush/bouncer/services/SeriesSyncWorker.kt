package com.tanush.bouncer.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity
import com.tanush.bouncer.network.SeriesScraper
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic worker that syncs active cricket series into the series_cache table.
 * Runs every 24 hours.
 */
class SeriesSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "series_sync"
            message = "SeriesSyncWorker started (attempt $runAttemptCount)"
            level = SentryLevel.INFO
        })

        return try {
            val db = BouncerDatabase.getInstance(context)

            val series = SeriesScraper().fetchActiveSeries()

            val entities = series.map { s ->
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
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "series_sync"
                message = "Sync succeeded — ${entities.size} series cached"
                level = SentryLevel.INFO
            })

            Result.success()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Network timeout in SeriesSyncWorker", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "series_sync"
                message = "Sync failed (timeout): ${e.message}"
                level = SentryLevel.WARNING
            })
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: UnknownHostException) {
            Log.w(TAG, "No network in SeriesSyncWorker", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "series_sync"
                message = "Sync failed (no network): ${e.message}"
                level = SentryLevel.WARNING
            })
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in SeriesSyncWorker", e)
            Sentry.captureException(e)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "SeriesSyncWorker"
        private const val UNIQUE_WORK_NAME = "series_sync_periodic"
        private const val MAX_RETRIES = 3

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

            Log.d(TAG, "Periodic series sync enqueued (24h)")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "series_sync"
                message = "Periodic work enqueued (24h)"
                level = SentryLevel.INFO
            })
        }
    }
}
