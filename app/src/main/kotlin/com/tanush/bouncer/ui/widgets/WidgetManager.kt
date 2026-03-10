package com.tanush.bouncer.ui.widgets

import android.content.Context
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Widget Manager - Centralized API for updating all Bouncer widgets
 * 
 * Call this from anywhere in the app to push updates to all widgets:
 * home screen, lock screen, and legacy widgets.
 * 
 * Usage:
 * ```
 * WidgetManager.updateAllWidgets(context, match, winProbability = 0.67f)
 * ```
 */
object WidgetManager {
    
    /**
     * Update all widgets with fresh match data
     * 
     * @param context Application context
     * @param match Current live match
     * @param winProbability Optional win probability (0.0 to 1.0)
     * @param recentBalls Optional list of recent ball outcomes
     * @param teamColor Optional team color for gradient
     */
    fun updateAllWidgets(
        context: Context,
        match: CricketMatch?,
        winProbability: Float? = null,
        recentBalls: List<String>? = null,
        teamColor: Int? = null
    ) {
        try {
            if (match == null) {
                clearAllWidgets(context)
                return
            }
            
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            
            if (team1 == null || team2 == null) {
                clearAllWidgets(context)
                return
            }
            
            // Update legacy AppWidget (RemoteViews-based)
            updateLegacyWidget(context, match, winProbability, recentBalls, teamColor)
            
            // Update Glance home screen widget
            updateGlanceHomeWidget(context, match, team1, team2, winProbability)
            
            // Update Glance lock screen widget
            updateGlanceLockWidget(context, match, team1, team2)
            
            // Schedule background updates via WorkManager
            WidgetUpdateWorker.schedulePeriodicUpdates(context)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "All widgets updated: ${team1.name} vs ${team2.name}"
                level = SentryLevel.INFO
                setData("match_id", match.id)
                setData("is_live", match.isLive.toString())
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
    
    /**
     * Clear all widgets (show "no matches" state)
     */
    fun clearAllWidgets(context: Context) {
        try {
            // Clear legacy widget
            val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            LiveMatchWidgetProvider.triggerUpdate(context)
            
            // Clear Glance widgets
            val glancePrefs = context.getSharedPreferences(
                "bouncer_glance_widget", 
                Context.MODE_PRIVATE
            )
            glancePrefs.edit().clear().apply()
            
            val lockPrefs = context.getSharedPreferences(
                "bouncer_lock_widget",
                Context.MODE_PRIVATE
            )
            lockPrefs.edit().clear().apply()
            
            // Trigger immediate update to show empty state
            WidgetUpdateWorker.triggerImmediateUpdate(context)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "All widgets cleared"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
    
    /**
     * Force an immediate refresh of all widgets
     */
    fun forceRefresh(context: Context) {
        try {
            LiveMatchWidgetProvider.triggerUpdate(context)
            WidgetUpdateWorker.triggerImmediateUpdate(context)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget force refresh triggered"
                level = SentryLevel.INFO
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
    
    private fun updateLegacyWidget(
        context: Context,
        match: CricketMatch,
        winProbability: Float?,
        recentBalls: List<String>?,
        teamColor: Int?
    ) {
        try {
            LiveMatchWidgetProvider().updateCache(
                context = context,
                match = match,
                winProbability = winProbability,
                recentBalls = recentBalls,
                teamColor = teamColor
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
    
    private fun updateGlanceHomeWidget(
        context: Context,
        match: CricketMatch,
        team1: CricketMatch.Team,
        team2: CricketMatch.Team,
        winProbability: Float?
    ) {
        try {
            val team1Name = team1.acronym.ifEmpty { team1.name }
            val team2Name = team2.acronym.ifEmpty { team2.name }
            
            BouncerGlanceWidget.updateWidgetData(
                context = context,
                team1Name = team1Name,
                team1Score = team1.score ?: "-",
                team2Name = team2Name,
                team2Score = team2.score ?: "-",
                matchStatus = match.status,
                isLive = match.isLive,
                winProbability = winProbability
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
    
    private fun updateGlanceLockWidget(
        context: Context,
        match: CricketMatch,
        team1: CricketMatch.Team,
        team2: CricketMatch.Team
    ) {
        try {
            val team1Abbr = team1.acronym.ifEmpty { team1.name.take(3) }.uppercase()
            val team2Abbr = team2.acronym.ifEmpty { team2.name.take(3) }.uppercase()
            
            BouncerLockScreenWidget.updateLockWidgetData(
                context = context,
                team1Abbr = team1Abbr,
                team1Score = team1.score ?: "-",
                team2Abbr = team2Abbr,
                team2Score = team2.score ?: "-",
                isLive = match.isLive
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}
