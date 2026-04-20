package com.tanush.bouncer.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.ui.widgets.LiveMatchWidgetProvider
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic score poller.
 *
 * Fetches live match data from the API and pushes updates to widgets.
 * Scheduled every 15 minutes with network constraints.
 */
class ScorePollingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "score_polling"
            message = "ScorePollingWorker started (attempt ${runAttemptCount})"
            level = SentryLevel.INFO
        })

        return try {
            val response = CricketRepository.getMatches("live")

            response.fold(
                onSuccess = { matchesResponse ->
                    val matchCount = matchesResponse.matches.size
                    Log.d(TAG, "Fetched $matchCount live matches")

                    // Persist minimal data for widgets
                    cacheForWidgets(matchesResponse.matches.size)

                    // Trigger widget refresh
                    WidgetUpdateWorker.triggerImmediateUpdate(context)

                    // Notify other components via local broadcast
                    context.sendBroadcast(
                        Intent(ACTION_SCORES_UPDATED).setPackage(context.packageName)
                    )

                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "score_polling"
                        message = "Poll succeeded – $matchCount live matches"
                        level = SentryLevel.INFO
                    })

                    Result.success(
                        workDataOf("match_count" to matchCount)
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Poll failed", error)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "score_polling"
                        message = "Poll failed: ${error.message}"
                        level = SentryLevel.ERROR
                    })
                    // Retry up to 3 times, then give up until next period
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in ScorePollingWorker", e)
            Sentry.captureException(e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun cacheForWidgets(liveCount: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("live_match_count", liveCount)
            .putLong("last_poll_epoch", System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val TAG = "ScorePollingWorker"

        private const val UNIQUE_WORK_NAME = "score_polling_periodic"
        private const val ONE_TIME_WORK_TAG = "score_polling_once"
        private const val PREFS_NAME = "score_polling_cache"
        private const val MAX_RETRIES = 3

        const val ACTION_SCORES_UPDATED =
            "com.tanush.bouncer.action.SCORES_UPDATED"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Schedule a periodic poll every 15 minutes (minimum WorkManager interval).
         * Uses [ExistingPeriodicWorkPolicy.KEEP] so duplicate enqueues are no-ops.
         */
        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScorePollingWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(networkConstraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Periodic score polling enqueued")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "score_polling"
                message = "Periodic work enqueued (15 min)"
                level = SentryLevel.INFO
            })
        }

        /**
         * Fire a one-time immediate refresh (e.g. pull-to-refresh or app foreground).
         */
        fun enqueueOneTimeWork(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScorePollingWorker>()
                .setConstraints(networkConstraints)
                .addTag(ONE_TIME_WORK_TAG)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(request)

            Log.d(TAG, "One-time score poll enqueued")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "score_polling"
                message = "One-time work enqueued"
                level = SentryLevel.INFO
            })
        }

        /**
         * Cancel all score-polling work.
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            WorkManager.getInstance(context).cancelAllWorkByTag(ONE_TIME_WORK_TAG)
            Log.d(TAG, "All score polling work cancelled")
        }
    }
}
