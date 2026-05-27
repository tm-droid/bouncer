package com.tanush.bouncer.ui.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * WorkManager-based Widget Updater
 *
 * Replaces the former AlarmManager approach so updates survive app force-stop.
 * WorkManager persists its queue in a local DB; any future process start
 * (reboot, FCM, user re-open) resumes pending work automatically.
 *
 * Interval adapts: 5 min when a live match is cached, 30 min otherwise.
 * Uses self-rescheduling one-time workers to avoid the 15-min minimum
 * of PeriodicWorkRequest while keeping the adaptive-interval behaviour.
 */
object WidgetUpdateWorker {

    private const val LIVE_INTERVAL_MS    = 5  * 60 * 1000L
    private const val IDLE_INTERVAL_MS    = 30 * 60 * 1000L
    internal const val WORK_NAME          = "bouncer_widget_update"
    internal const val PREFS_WIDGET_CACHE = "widget_cache"
    internal const val KEY_LAST_UPDATE    = "last_update_epoch"

    /**
     * Schedule the next adaptive widget update via WorkManager.
     * Called by [WidgetUpdateWorkManagerWorker] after each run to maintain
     * the self-rescheduling loop.
     */
    fun schedulePeriodicUpdates(context: Context) {
        scheduleAdaptiveUpdate(context)
    }

    internal fun scheduleAdaptiveUpdate(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
            val isLive = prefs.getBoolean("is_live", false)
            val delayMs = if (isLive) LIVE_INTERVAL_MS else IDLE_INTERVAL_MS

            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorkManagerWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag("widget_update")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget update scheduled in ${delayMs / 60_000} min (live=$isLive)"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Enqueue an immediate one-time update (no delay). */
    fun triggerImmediateUpdate(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorkManagerWorker>()
                .addTag("widget_immediate_update")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Cancel any pending scheduled update. */
    fun cancelUpdates(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget updates cancelled"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    internal fun buildStaleDataFallback(context: Context): MatchWidgetData {
        val prefs = context.getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val statusText = if (lastUpdate > 0L) {
            val minAgo = abs(System.currentTimeMillis() - lastUpdate) / 60_000
            "No data · updated ${minAgo}m ago"
        } else {
            "No data available"
        }
        return MatchWidgetData(
            team1Name      = prefs.getString("team1_name", "--") ?: "--",
            team1Score     = prefs.getString("team1_score", "-") ?: "-",
            team2Name      = prefs.getString("team2_name", "--") ?: "--",
            team2Score     = prefs.getString("team2_score", "-") ?: "-",
            matchStatus    = statusText,
            isLive         = false,
            winProbability = prefs.getFloat("win_probability", 0.5f),
            recentBalls    = prefs.getString("recent_balls", null),
            battingPair    = prefs.getString("batting_pair", null),
            currentBowler  = prefs.getString("current_bowler", null)
        )
    }

    internal fun fetchLatestMatchData(context: Context): MatchWidgetData? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
            val team1Name = prefs.getString("team1_name", null)
            val team2Name = prefs.getString("team2_name", null)
            if (team1Name != null && team2Name != null) {
                MatchWidgetData(
                    team1Name      = team1Name,
                    team1Score     = prefs.getString("team1_score", "-") ?: "-",
                    team2Name      = team2Name,
                    team2Score     = prefs.getString("team2_score", "-") ?: "-",
                    matchStatus    = prefs.getString("match_status", "Unknown") ?: "Unknown",
                    isLive         = prefs.getBoolean("is_live", false),
                    winProbability = prefs.getFloat("win_probability", 0.5f),
                    recentBalls    = prefs.getString("recent_balls", null),
                    battingPair    = prefs.getString("batting_pair", null),
                    currentBowler  = prefs.getString("current_bowler", null)
                )
            } else null
        } catch (e: Exception) {
            Sentry.captureException(e)
            null
        }
    }

    data class MatchWidgetData(
        val team1Name: String,
        val team1Score: String,
        val team2Name: String,
        val team2Score: String,
        val matchStatus: String,
        val isLive: Boolean,
        val winProbability: Float,
        val recentBalls: String? = null,
        val battingPair: String? = null,
        val currentBowler: String? = null
    )
}

/**
 * WorkManager worker that refreshes all Bouncer widgets.
 * Survives app force-stop — WorkManager re-queues pending work on next
 * process start (reboot, FCM, user re-open).
 */
class WidgetUpdateWorkManagerWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget update triggered"
                level = SentryLevel.INFO
            })

            val matchData = WidgetUpdateWorker.fetchLatestMatchData(context)
                ?: WidgetUpdateWorker.buildStaleDataFallback(context)

            updateGlanceWidget(matchData)
            updateLockScreenWidget(matchData)
            updateLegacyWidget()

            context.getSharedPreferences(WidgetUpdateWorker.PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
                .edit().putLong(WidgetUpdateWorker.KEY_LAST_UPDATE, System.currentTimeMillis()).apply()

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "All widgets updated successfully"
                level = SentryLevel.INFO
            })

            WidgetUpdateWorker.scheduleAdaptiveUpdate(context)
            Result.success()
        } catch (e: Exception) {
            Sentry.captureException(e)
            Result.retry()
        }
    }

    private suspend fun updateGlanceWidget(data: WidgetUpdateWorker.MatchWidgetData) {
        try {
            BouncerGlanceWidget.updateWidgetData(
                context        = context,
                team1Name      = data.team1Name,
                team1Score     = data.team1Score,
                team2Name      = data.team2Name,
                team2Score     = data.team2Score,
                matchStatus    = data.matchStatus,
                isLive         = data.isLive,
                winProbability = data.winProbability,
                recentBalls    = data.recentBalls,
                battingPair    = data.battingPair,
                currentBowler  = data.currentBowler
            )
            val glanceManager = GlanceAppWidgetManager(context)
            glanceManager.getGlanceIds(BouncerGlanceWidget::class.java).forEach { id ->
                BouncerGlanceWidget().update(context, id)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private suspend fun updateLockScreenWidget(data: WidgetUpdateWorker.MatchWidgetData) {
        try {
            val team1Abbr = data.team1Name.take(3).uppercase()
            val team2Abbr = data.team2Name.take(3).uppercase()
            BouncerLockScreenWidget.updateLockWidgetData(
                context    = context,
                team1Abbr  = team1Abbr,
                team1Score = data.team1Score,
                team2Abbr  = team2Abbr,
                team2Score = data.team2Score,
                isLive     = data.isLive
            )
            val glanceManager = GlanceAppWidgetManager(context)
            glanceManager.getGlanceIds(BouncerLockScreenWidget::class.java).forEach { id ->
                BouncerLockScreenWidget().update(context, id)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private fun updateLegacyWidget() {
        try {
            LiveMatchWidgetProvider.triggerUpdate(context)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}
