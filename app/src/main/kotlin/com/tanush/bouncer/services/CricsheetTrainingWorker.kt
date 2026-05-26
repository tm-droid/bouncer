package com.tanush.bouncer.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import com.tanush.bouncer.analysis.MatchupAnalysis
import com.tanush.bouncer.analysis.PlayerStatisticsEngine
import com.tanush.bouncer.analysis.VenueAnalysis
import com.tanush.bouncer.analysis.WinProbabilityCalculator
import com.tanush.bouncer.analysis.ml.ModelTrainer
import com.tanush.bouncer.network.CricsheetDataFetcher
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.TimeUnit

/**
 * One-time WorkManager job that downloads T20 matches from Cricsheet, processes them
 * through all analysis layers, and trains the ML model on real data.
 *
 * Skips if the model was trained within the last 30 days. Requires network.
 * Safe to enqueue at every startup — unique work policy prevents concurrent runs.
 */
class CricsheetTrainingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTrainedMs = prefs.getLong(KEY_LAST_TRAINED_MS, 0L)
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

        if (System.currentTimeMillis() - lastTrainedMs < thirtyDaysMs) {
            Log.d(TAG, "Model trained recently — skipping Cricsheet download")
            return Result.success()
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "ml.training"
            message = "CricsheetTrainingWorker started"
            level = SentryLevel.INFO
        })

        return try {
            Log.d(TAG, "Downloading T20 matches from Cricsheet…")
            val fetcher = CricsheetDataFetcher(context)
            val matches = fetcher.downloadByMatchType("t20s")

            if (matches.isEmpty()) {
                Log.w(TAG, "No T20 matches downloaded — retrying later")
                return Result.retry()
            }

            Log.d(TAG, "Downloaded ${matches.size} T20 matches — processing analysis layers…")

            // Build team stats via WinProbabilityCalculator (updates its internal teamStats map)
            val calculator = WinProbabilityCalculator()
            calculator.loadCricsheetData(matches)

            // Process player-level and matchup/venue stats
            val playerStats = PlayerStatisticsEngine()
            playerStats.processMatches(matches)

            val matchupAnalysis = MatchupAnalysis()
            matchupAnalysis.processMatches(matches)

            val venueAnalysis = VenueAnalysis()
            venueAnalysis.processMatches(matches)

            Log.d(TAG, "Analysis complete — starting model training…")

            val trainer = ModelTrainer.shared ?: ModelTrainer(context)
            val result = trainer.trainOnHistoricalData(
                matches = matches,
                teamStats = calculator.teamStats,
                playerStats = playerStats,
                matchupAnalysis = matchupAnalysis,
                venueAnalysis = venueAnalysis
            )

            Log.d(TAG, "Training complete: epochs=${result.totalEpochs}, converged=${result.converged}")

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "ml.training"
                message = "CricsheetTrainingWorker finished"
                level = SentryLevel.INFO
                setData("matchCount", matches.size)
                setData("totalEpochs", result.totalEpochs)
                setData("converged", result.converged)
            })

            prefs.edit().putLong(KEY_LAST_TRAINED_MS, System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CricsheetTrainingWorker failed: ${e.message}", e)
            Sentry.captureException(e)
            // Retry once; if it fails again, don't disrupt the user
            if (runAttemptCount < 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "CricsheetTrainingWorker"
        private const val PREFS_NAME = "cricsheet_training"
        private const val KEY_LAST_TRAINED_MS = "last_trained_ms"
        private const val WORK_NAME = "cricsheet_model_training"

        fun enqueueIfNeeded(context: Context) {
            val request = OneTimeWorkRequestBuilder<CricsheetTrainingWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
