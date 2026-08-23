package com.tanush.bouncer.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.tanush.bouncer.R
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.data.remote.AppwriteMatchDetailClient
import com.tanush.bouncer.network.models.SanwebMatchSummary
import com.tanush.bouncer.network.models.SanwebMatchesListResponse
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.ui.notifications.RichNotificationBuilder
import com.tanush.bouncer.ui.widgets.LiveMatchWidgetProvider
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import com.tanush.bouncer.utils.WorkerLogger
import com.tanush.bouncer.utils.PrefsRegistry
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import com.tanush.bouncer.utils.sentryBreadcrumb

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
        sentryBreadcrumb("score_polling", "ScorePollingWorker started (attempt ${runAttemptCount})")

        // Early-exit: no subscriptions → nothing to notify, skip network call entirely.
        if (MatchSubscriptionManager.subscriptions.value.isEmpty()) {
            Log.d(TAG, "No subscriptions — skipping poll")
            WorkerLogger.enqueued("score_polling", "Skipped — no active subscriptions")
            return Result.success()
        }

        return try {
            when (val response = CricketRepository.getMatches("live")) {
                is AppResult.Success -> onPollSuccess(response.data)
                is AppResult.Error -> {
                    Log.e(TAG, "Poll failed", response.exception)
                    sentryBreadcrumb("score_polling", "Poll failed: ${response.exception.message}", SentryLevel.ERROR)
                    Sentry.captureException(response.exception)
                    retryOrFail()
                }
                is AppResult.Loading -> retryOrFail()
            }
        } catch (e: CancellationException) {
            // Normal WorkManager lifecycle cancellation — not an error, re-throw immediately.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in ScorePollingWorker", e)
            Sentry.captureException(e)
            retryOrFail()
        }
    }

    private suspend fun onPollSuccess(matchesResponse: SanwebMatchesListResponse): Result {
        val matchCount = matchesResponse.matches.size
        Log.d(TAG, "Fetched $matchCount live matches")

        cacheForWidgets(matchesResponse.matches)
        WidgetUpdateWorker.triggerImmediateUpdate(context)
        context.sendBroadcast(
            Intent(ACTION_SCORES_UPDATED).setPackage(context.packageName)
        )
        notifySubscribedMatches(matchesResponse.matches)

        sentryBreadcrumb("score_polling", "Poll succeeded – $matchCount live matches")

        scheduleAdaptiveFollowUp(matchCount)

        return Result.success(workDataOf("match_count" to matchCount))
    }

    // Adaptive polling: chain a 5-min follow-up while live matches exist.
    // Cap at MAX_LIVE_FOLLOWUPS (1 hour) so an API that never marks the match
    // completed can't lock us into a permanent 5-min poll cycle. Counter resets
    // whenever no live matches are present.
    // Synchronized to prevent a race if WorkManager ever runs two instances
    // concurrently (e.g. expedited + periodic overlap).
    private fun scheduleAdaptiveFollowUp(matchCount: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (matchCount > 0) {
            val enqueue = synchronized(ScorePollingWorker::class.java) {
                val count = prefs.getInt(KEY_FOLLOWUP_COUNT, 0)
                if (count < MAX_LIVE_FOLLOWUPS) {
                    prefs.edit().putInt(KEY_FOLLOWUP_COUNT, count + 1).apply()
                    true
                } else {
                    Log.w(TAG, "Live follow-up cap reached ($MAX_LIVE_FOLLOWUPS); falling back to periodic poll")
                    prefs.edit().remove(KEY_FOLLOWUP_COUNT).apply()
                    false
                }
            }
            if (enqueue) enqueueLiveFollowUp(context)
        } else {
            prefs.edit().remove(KEY_FOLLOWUP_COUNT).apply()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < WORKER_MAX_RETRIES) Result.retry() else Result.failure()

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

        // The widget's live score comes from the same `match-live-detail` row the detail screen
        // reads — one billed getRow on a deterministic id, and only for the single match shown.
        // It used to come from the Vercel API, which was this app's one remaining use of a
        // second, separately-hosted scraper for data Appwrite already had.
        val detail = try {
            AppwriteMatchDetailClient.getForMatch(primary.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Score fetch for widget failed", e)
            Sentry.captureException(e)
            null
        }

        val (team1, team2) = parseWidgetTeamNames(primary.title)

        widgetPrefs.edit().apply {
            putString("team1_name", team1)
            putString("team2_name", team2)
            putString("match_status", detail?.status ?: primary.status)
            putBoolean("is_live", true)
            detail?.score?.let { putString("team1_score", it) }
            // BouncerGlanceWidget.parseBattingPair needs both halves of "N|runs|balls;N|runs|balls"
            // and returns null on one, so a single batsman at the crease is written as no pair
            // rather than as a half-formed string.
            detail?.batsmen?.takeIf { it.size >= 2 }?.let { batsmen ->
                putString(
                    "batting_pair",
                    batsmen.take(2).joinToString(";") { "${it.name}|${it.runs}|${it.balls}" },
                )
            }
            // "name|wickets|runs|overs" — the field order parseBowler expects, which is not the
            // order the fields are declared in.
            detail?.bowler?.let {
                putString("current_bowler", "${it.name}|${it.wickets}|${it.runs}|${it.overs}")
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

    /**
     * Drop `last_status_*` entries for matches the user is no longer subscribed to.
     *
     * One key was written per subscribed match and none was ever removed, so the prefs file grew a
     * permanent entry for every match ever followed — including matches that finished months ago.
     * Subscriptions are the authority: once a match leaves that set its status key is dead weight.
     */
    private fun pruneStaleStatusKeys(prefs: SharedPreferences, subscribed: Set<String>) {
        val stale = prefs.all.keys.filter {
            it.startsWith(LAST_STATUS_PREFIX) && it.removePrefix(LAST_STATUS_PREFIX) !in subscribed
        }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    private fun notifySubscribedMatches(matches: List<SanwebMatchSummary>) {
        val subscribed = MatchSubscriptionManager.subscriptions.value
        if (subscribed.isEmpty()) return

        val notifPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifManager = NotificationManagerCompat.from(context)
        pruneStaleStatusKeys(notifPrefs, subscribed)

        matches.filter { it.id in subscribed }.forEach { match ->
            val lastStatus = notifPrefs.getString("$LAST_STATUS_PREFIX${match.id}", null)
            if (lastStatus == match.status) return@forEach

            notifPrefs.edit().putString("$LAST_STATUS_PREFIX${match.id}", match.status).apply()

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
                sentryBreadcrumb("score_polling", "Notification permission denied: ${e.message}", SentryLevel.WARNING)
            }
        }
    }

    companion object {
        const val TAG = "ScorePollingWorker"

        private const val UNIQUE_WORK_NAME = "score_polling_periodic"
        private const val ONE_TIME_WORK_TAG = "score_polling_once"
        private val PREFS_NAME = PrefsRegistry.PREFS_SCORE_POLLING
        private const val LAST_STATUS_PREFIX = "last_status_"
        private const val MAX_LIVE_FOLLOWUPS = 12 // 12 × 5 min = 1 hour cap
        private val KEY_FOLLOWUP_COUNT = PrefsRegistry.ScorePolling.FOLLOWUP_COUNT

        const val ACTION_SCORES_UPDATED =
            "com.tanush.bouncer.action.SCORES_UPDATED"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Schedule a periodic poll every 30 minutes as an offline fallback.
         * FCM topic messages (TYPE_MATCH_STARTED/ENDED) are the fast path — they trigger
         * [enqueueOneTimeWork] immediately via [BouncerFirebaseMessagingService].
         * Uses [ExistingPeriodicWorkPolicy.KEEP] so duplicate enqueues are no-ops.
         */
        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScorePollingWorker>(
                30, TimeUnit.MINUTES
            ).setConstraints(networkConstraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            WorkerLogger.enqueued("score_polling", "Periodic work enqueued (30 min fallback)")
        }

        /**
         * Fire a one-time immediate refresh (e.g. pull-to-refresh or app foreground).
         */
        fun enqueueOneTimeWork(context: Context, jitterMs: Long = 0L) {
            WorkManager.getInstance(context).enqueue(oneTimeRequest(jitterMs))

            WorkerLogger.enqueued("score_polling", "One-time work enqueued (jitter=${jitterMs}ms)")
        }

        /**
         * Split out from [enqueueOneTimeWork] so the request can be built — and therefore
         * validated — without a WorkManager instance.
         *
         * Delay and expedited are mutually exclusive: expedited means "run now", so WorkManager
         * throws `IllegalArgumentException: Expedited jobs cannot be delayed` from `build()` if
         * both are set. That threw on FirebaseMessagingService's thread, where nothing catches it,
         * so every MATCH_STARTED/MATCH_ENDED topic push killed the process — and because those
         * pushes arrive in bursts, Android then hit "crashed too many times, killing".
         *
         * A jittered poll is a deliberately deferred one, so it takes the delay and gives up the
         * expedited slot. A user-triggered poll passes no jitter and stays expedited.
         */
        internal fun oneTimeRequest(jitterMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ScorePollingWorker>()
                .setConstraints(networkConstraints)
                .addTag(ONE_TIME_WORK_TAG)
                .apply {
                    // A push-triggered poll must not fire on every device at once; a user-triggered
                    // one (pull-to-refresh, foreground) passes no jitter and stays immediate.
                    if (jitterMs > 0L) {
                        setInitialDelay(jitterMs, TimeUnit.MILLISECONDS)
                    } else {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()

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
