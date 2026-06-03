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
import com.tanush.bouncer.network.models.SanwebMatchSummary
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.ui.notifications.RichNotificationBuilder
import com.tanush.bouncer.ui.widgets.LiveMatchWidgetProvider
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import com.tanush.bouncer.utils.WorkerLogger
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
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

                    cacheForWidgets(matchesResponse.matches)
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
                    // Cap at MAX_LIVE_FOLLOWUPS (1 hour) so an API that never marks the match
                    // completed can't lock us into a permanent 5-min poll cycle. Counter resets
                    // whenever no live matches are present.
                    // Synchronized to prevent a race if WorkManager ever runs two instances
                    // concurrently (e.g. expedited + periodic overlap).
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    if (matchCount > 0) {
                        val enqueue = synchronized(ScorePollingWorker::class.java) {
                            val count = prefs.getInt(KEY_FOLLOWUP_COUNT, 0)
                            if (count < MAX_LIVE_FOLLOWUPS) {
                                prefs.edit().putInt(KEY_FOLLOWUP_COUNT, count + 1).commit()
                                true
                            } else {
                                Log.w(TAG, "Live follow-up cap reached ($MAX_LIVE_FOLLOWUPS); falling back to periodic poll")
                                prefs.edit().remove(KEY_FOLLOWUP_COUNT).commit()
                                false
                            }
                        }
                        if (enqueue) enqueueLiveFollowUp(context)
                    } else {
                        prefs.edit().remove(KEY_FOLLOWUP_COUNT).apply()
                    }

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
        } catch (e: CancellationException) {
            // Normal WorkManager lifecycle cancellation — not an error, re-throw immediately.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in ScorePollingWorker", e)
            Sentry.captureException(e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private suspend fun cacheForWidgets(matches: List<SanwebMatchSummary>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("live_match_count", matches.size)
            .putLong("last_poll_epoch", System.currentTimeMillis())
            .apply()

        val widgetPrefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
        val primary = matches.firstOrNull()

        if (primary == null) {
            widgetPrefs.edit().putBoolean("is_live", false).apply()
            return
        }

        val scoreData = try {
            (CricketRepository.getMatchScore(primary.id) as? AppResult.Success)?.data
        } catch (e: Exception) {
            Log.w(TAG, "Score fetch for widget failed", e)
            null
        }

        val (team1, team2) = parseWidgetTeamNames(primary.title)

        widgetPrefs.edit().apply {
            putString("team1_name", team1)
            putString("team2_name", team2)
            putString("match_status", scoreData?.update ?: primary.status)
            putBoolean("is_live", true)
            if (scoreData != null) {
                putString("team1_score", scoreData.livescore)
                val battingPair = "${scoreData.batterone}|${scoreData.batsmanonerun}|${scoreData.batsmanoneball}" +
                        ";${scoreData.battertwo}|${scoreData.batsmantworun}|${scoreData.batsmantwoball}"
                putString("batting_pair", battingPair)
                val currentBowler = "${scoreData.bowlerone}|${scoreData.bowleronewickers}|${scoreData.bowleronerun}|${scoreData.bowleroneover}"
                putString("current_bowler", currentBowler)
            }
            apply()
        }
    }

    private fun parseWidgetTeamNames(title: String): Pair<String, String> {
        val vsIdx = title.indexOf(" vs ", ignoreCase = true)
        if (vsIdx < 0) return Pair("Team 1", "Team 2")
        val before = title.substring(0, vsIdx)
        val after = title.substring(vsIdx + 4)
        val team1 = before.substringAfterLast(", ").trim().ifEmpty { before.trim() }
        val team2 = after.substringBefore(",").trim()
        return Pair(team1, team2)
    }

    private fun notifySubscribedMatches(matches: List<SanwebMatchSummary>) {
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
        private const val MAX_LIVE_FOLLOWUPS = 12 // 12 × 5 min = 1 hour cap
        private const val KEY_FOLLOWUP_COUNT = "live_followup_count"

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
