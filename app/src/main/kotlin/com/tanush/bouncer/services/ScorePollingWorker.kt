package com.tanush.bouncer.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.tanush.bouncer.R
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.ui.notifications.RichNotificationBuilder
import com.tanush.bouncer.ui.widgets.LiveMatchWidgetProvider
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import com.tanush.bouncer.utils.WorkerLogger
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
            when (val response = CricketRepository.getMatches("live")) {
                is AppResult.Success -> {
                    val matchesResponse = response.data
                    val matchCount = matchesResponse.matches.size
                    Log.d(TAG, "Fetched $matchCount live matches")

                    cacheForWidgets(matchesResponse.matches.size)
                    WidgetUpdateWorker.triggerImmediateUpdate(context)
                    context.sendBroadcast(
                        Intent(ACTION_SCORES_UPDATED).setPackage(context.packageName)
                    )
                    notifySubscribedMatches(matchesResponse.matches)

                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "score_polling"
                        message = "Poll succeeded – $matchCount live matches"
                        level = SentryLevel.INFO
                    })

                    // Adaptive polling: chain a 5-min follow-up while live matches exist.
                    // When no live matches, the chain stops and 15-min periodic takes over.
                    if (matchCount > 0) enqueueLiveFollowUp(context)

                    Result.success(workDataOf("match_count" to matchCount))
                }
                is AppResult.Error -> {
                    Log.e(TAG, "Poll failed", response.exception)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "score_polling"
                        message = "Poll failed: ${response.exception.message}"
                        level = SentryLevel.ERROR
                    })
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
                is AppResult.Loading -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
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

    private fun notifySubscribedMatches(matches: List<com.tanush.bouncer.network.models.SanwebMatchSummary>) {
        val subscribed = MatchSubscriptionManager.subscriptions.value
        if (subscribed.isEmpty()) return

        val notifPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifManager = NotificationManagerCompat.from(context)

        matches.filter { it.id in subscribed }.forEach { match ->
            val lastStatus = notifPrefs.getString("last_status_${match.id}", null)
            if (lastStatus == match.status) return@forEach

            notifPrefs.edit().putString("last_status_${match.id}", match.status).apply()

            val channelId = if (match.status.contains("won", ignoreCase = true) ||
                match.status.contains("result", ignoreCase = true))
                RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_END
            else
                RichNotificationBuilder.CricketNotificationChannel.CHANNEL_LIVE_SCORE

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(match.title)
                .setContentText(match.status)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(RichNotificationBuilder.CricketNotificationChannel.GROUP_CRICKET)
                .build()

            try {
                notifManager.notify(("bg_${match.id}").hashCode(), notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission not granted", e)
            }
        }
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

            WorkerLogger.enqueued("score_polling", "Periodic work enqueued (15 min)")
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

            WorkerLogger.enqueued("score_polling", "One-time work enqueued")
        }

        /**
         * Schedule a 5-minute one-time follow-up poll while a live match is active.
         * Uses REPLACE so multiple rapid successes don't stack requests.
         */
        fun enqueueLiveFollowUp(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScorePollingWorker>()
                .setConstraints(networkConstraints)
                .addTag(TAG)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "score_polling_live_followup",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /**
         * Cancel all score-polling work.
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork("score_polling_live_followup")
            WorkManager.getInstance(context).cancelAllWorkByTag(ONE_TIME_WORK_TAG)
            Log.d(TAG, "All score polling work cancelled")
        }
    }
}
