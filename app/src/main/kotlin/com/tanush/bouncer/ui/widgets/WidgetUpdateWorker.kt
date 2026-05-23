package com.tanush.bouncer.ui.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * AlarmManager-based Widget Updater
 *
 * Periodically updates all Bouncer widgets with fresh match data.
 * Interval adapts: 5 min when a live match is cached, 30 min otherwise.
 * Uses setExactAndAllowWhileIdle + self-rescheduling so the interval can
 * change after each update without cancelling and recreating alarms.
 */
object WidgetUpdateWorker {

    private const val LIVE_INTERVAL_MS   = 5  * 60 * 1000L
    private const val IDLE_INTERVAL_MS   = 30 * 60 * 1000L
    private const val REQUEST_CODE       = 1001
    private const val PREFS_WIDGET_CACHE = "widget_cache"
    private const val KEY_LAST_UPDATE    = "last_update_epoch"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Schedule the first adaptive widget update. Subsequent updates are
     * self-scheduled inside [updateAllWidgets] after each run completes.
     */
    fun schedulePeriodicUpdates(context: Context) {
        scheduleAdaptiveUpdate(context)
    }

    /** Re-schedule using the appropriate interval based on current live status. */
    private fun scheduleAdaptiveUpdate(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
            val isLive = prefs.getBoolean("is_live", false)
            val interval = if (isLive) LIVE_INTERVAL_MS else IDLE_INTERVAL_MS

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Cancel any existing alarm before setting new one
            alarmManager.cancel(pendingIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + interval,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + interval,
                    pendingIntent
                )
            }

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget update scheduled in ${interval / 60_000} min (live=$isLive)"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
    
    /**
     * Trigger an immediate widget update
     */
    fun triggerImmediateUpdate(context: Context) {
        scope.launch {
            try {
                updateAllWidgets(context)
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }
    
    /**
     * Cancel all widget update alarms
     */
    fun cancelUpdates(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget updates cancelled"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private suspend fun updateAllWidgets(context: Context) {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget update triggered"
                level = SentryLevel.INFO
            })

            val matchData = fetchLatestMatchData(context) ?: buildStaleDataFallback(context)

            updateGlanceWidget(context, matchData)
            updateLockScreenWidget(context, matchData)
            updateLegacyWidget(context, matchData)

            // Persist timestamp so next fallback can compute "last updated X min ago"
            context.getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "All widgets updated successfully"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        } finally {
            // Reschedule with the correct live/idle interval after every run
            scheduleAdaptiveUpdate(context)
        }
    }

    /** Returns a placeholder when SharedPrefs hold no cached match. */
    private fun buildStaleDataFallback(context: Context): MatchWidgetData {
        val prefs = context.getSharedPreferences(PREFS_WIDGET_CACHE, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val statusText = if (lastUpdate > 0L) {
            val minAgo = abs(System.currentTimeMillis() - lastUpdate) / 60_000
            "No data · updated ${minAgo}m ago"
        } else {
            "No data available"
        }
        return MatchWidgetData(
            team1Name   = prefs.getString("team1_name", "--") ?: "--",
            team1Score  = prefs.getString("team1_score", "-") ?: "-",
            team2Name   = prefs.getString("team2_name", "--") ?: "--",
            team2Score  = prefs.getString("team2_score", "-") ?: "-",
            matchStatus = statusText,
            isLive      = false,
            winProbability = prefs.getFloat("win_probability", 0.5f)
        )
    }

    private fun fetchLatestMatchData(context: Context): MatchWidgetData? {
        return try {
            val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            
            val team1Name = prefs.getString("team1_name", null)
            val team2Name = prefs.getString("team2_name", null)
            
            if (team1Name != null && team2Name != null) {
                MatchWidgetData(
                    team1Name = team1Name,
                    team1Score = prefs.getString("team1_score", "-") ?: "-",
                    team2Name = team2Name,
                    team2Score = prefs.getString("team2_score", "-") ?: "-",
                    matchStatus = prefs.getString("match_status", "Unknown") ?: "Unknown",
                    isLive = prefs.getBoolean("is_live", false),
                    winProbability = prefs.getFloat("win_probability", 0.5f)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            null
        }
    }

    private suspend fun updateGlanceWidget(context: Context, data: MatchWidgetData) {
        try {
            BouncerGlanceWidget.updateWidgetData(
                context = context,
                team1Name = data.team1Name,
                team1Score = data.team1Score,
                team2Name = data.team2Name,
                team2Score = data.team2Score,
                matchStatus = data.matchStatus,
                isLive = data.isLive,
                winProbability = data.winProbability
            )
            
            // Trigger Glance widget update
            val glanceManager = GlanceAppWidgetManager(context)
            val glanceIds = glanceManager.getGlanceIds(BouncerGlanceWidget::class.java)
            glanceIds.forEach { glanceId ->
                BouncerGlanceWidget().update(context, glanceId)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private suspend fun updateLockScreenWidget(context: Context, data: MatchWidgetData) {
        try {
            val team1Abbr = data.team1Name.take(3).uppercase()
            val team2Abbr = data.team2Name.take(3).uppercase()
            
            BouncerLockScreenWidget.updateLockWidgetData(
                context = context,
                team1Abbr = team1Abbr,
                team1Score = data.team1Score,
                team2Abbr = team2Abbr,
                team2Score = data.team2Score,
                isLive = data.isLive
            )
            
            // Trigger lock screen widget update
            val glanceManager = GlanceAppWidgetManager(context)
            val glanceIds = glanceManager.getGlanceIds(BouncerLockScreenWidget::class.java)
            glanceIds.forEach { glanceId ->
                BouncerLockScreenWidget().update(context, glanceId)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private fun updateLegacyWidget(context: Context, data: MatchWidgetData) {
        try {
            LiveMatchWidgetProvider.triggerUpdate(context)
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    data class MatchWidgetData(
        val team1Name: String,
        val team1Score: String,
        val team2Name: String,
        val team2Score: String,
        val matchStatus: String,
        val isLive: Boolean,
        val winProbability: Float
    )
}
