package com.tanush.bouncer.ui.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager-based Widget Updater
 * 
 * Periodically updates all Bouncer widgets with fresh match data.
 * Uses AlarmManager for lightweight background scheduling.
 */
object WidgetUpdateWorker {

    private const val UPDATE_INTERVAL = 15 * 60 * 1000L // 15 minutes
    private const val REQUEST_CODE = 1001
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * Schedule periodic widget updates (every 15 minutes when matches are live)
     */
    fun schedulePeriodicUpdates(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + UPDATE_INTERVAL,
                UPDATE_INTERVAL,
                pendingIntent
            )
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget periodic updates scheduled"
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
            
            // Fetch latest match data from cache
            val matchData = fetchLatestMatchData(context)
            
            if (matchData != null) {
                // Update Glance home screen widget
                updateGlanceWidget(context, matchData)
                
                // Update lock screen widget
                updateLockScreenWidget(context, matchData)
                
                // Update legacy AppWidget
                updateLegacyWidget(context, matchData)
                
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "widget"
                    message = "All widgets updated successfully"
                    level = SentryLevel.INFO
                })
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
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
