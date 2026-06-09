package com.tanush.bouncer.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tanush.bouncer.data.local.BouncerDatabase
import java.util.concurrent.TimeUnit

class DatabaseCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = BouncerDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()
        db.matchDao().deleteOlderThan(now - MATCH_RETENTION_MS)
        db.commentaryDao().deleteOlderThan(now - COMMENTARY_RETENTION_MS)
        db.playerCacheDao().deleteOlderThan(now - PLAYER_CACHE_RETENTION_MS)
        db.historicalMatchCacheDao().deleteOlderThan(now - HISTORICAL_MATCH_RETENTION_MS)
        db.highlightDao().deleteOlderThan(now - HIGHLIGHTS_RETENTION_MS)
        return Result.success()
    }

    companion object {
        private val MATCH_RETENTION_MS            = 7L  * 24 * 3_600_000
        private val COMMENTARY_RETENTION_MS       = 7L  * 24 * 3_600_000
        private val PLAYER_CACHE_RETENTION_MS     = 7L  * 24 * 3_600_000
        private val HISTORICAL_MATCH_RETENTION_MS = 90L * 24 * 3_600_000
        private val HIGHLIGHTS_RETENTION_MS       = 14L * 24 * 3_600_000

        fun enqueueWeekly(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "database_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build()
            )
        }
    }
}
