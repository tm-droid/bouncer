package com.tanush.bouncer.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.utils.WorkerLogger
import java.util.concurrent.TimeUnit

class DatabaseCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = BouncerDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            db.matchDao().deleteOlderThan(now - MATCH_RETENTION_MS)
            db.commentaryDao().deleteOlderThan(now - COMMENTARY_RETENTION_MS)
            db.playerCacheDao().deleteOlderThan(now - PLAYER_CACHE_RETENTION_MS)
            db.historicalMatchCacheDao().deleteOlderThan(now - HISTORICAL_MATCH_RETENTION_MS)
            db.highlightDao().deleteOlderThan(now - HIGHLIGHTS_RETENTION_MS)
            db.seriesCacheDao().deleteOlderThan(now - SERIES_CACHE_RETENTION_MS)
            // ai_commentary_cache is bounded by row count, not age: completed-match rows carry a
            // permanent sentinel timestamp that no age cutoff can match, so before this the table
            // grew for the life of the install and was not swept here at all.
            db.aiCacheDao().enforceRowCeiling(CacheConfig.AI_CACHE_MAX_ROWS)
            Result.success()
        } catch (e: Exception) {
            WorkerLogger.failed("database_cleanup", "DB cleanup failed", e)
            Result.failure()
        }
    }

    companion object {
        private val MATCH_RETENTION_MS            = 7L  * 24 * 3_600_000
        private val COMMENTARY_RETENTION_MS       = 7L  * 24 * 3_600_000
        private val PLAYER_CACHE_RETENTION_MS     = 7L  * 24 * 3_600_000
        private val HISTORICAL_MATCH_RETENTION_MS = 90L * 24 * 3_600_000
        private val HIGHLIGHTS_RETENTION_MS       = 14L * 24 * 3_600_000
        private val SERIES_CACHE_RETENTION_MS     = 30L * 24 * 3_600_000

        fun enqueueWeekly(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "database_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    // A seven-table delete sweep has no deadline; don't run it on a flat battery.
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true)
                            .build()
                    )
                    .build()
            )
        }
    }
}
