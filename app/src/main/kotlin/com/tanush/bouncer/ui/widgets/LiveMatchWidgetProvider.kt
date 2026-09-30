package com.tanush.bouncer.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.tanush.bouncer.R
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.sentry.Sentry

/**
 * Enhanced LiveMatchWidgetProvider - Premium Home Screen Widget
 * 
 * Features:
 * - Dynamic score updates with win probability
 * - Team-specific color theming
 * - Animated progress indicators
 * - Rich match status display
 * - Quick actions for match details
 */
class LiveMatchWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "LiveMatchWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        const val ACTION_REFRESH = "com.tanush.bouncer.widget.REFRESH"
        const val ACTION_NEXT_MATCH = "com.tanush.bouncer.widget.NEXT_MATCH"
        const val EXTRA_MATCH_ID = "match_id"
        
        /**
         * Triggers a widget update from anywhere in the app
         */
        fun triggerUpdate(context: Context, match: CricketMatch? = null) {
            try {
                val intent = Intent(context, LiveMatchWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    match?.let { putExtra(EXTRA_MATCH_ID, it.id) }
                }
            
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, LiveMatchWidgetProvider::class.java)
                )
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scope.launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    // See BouncerGlanceWidgetReceiver.onEnabled.
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.triggerImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.cancel()
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
        
            when (intent.action) {
                ACTION_REFRESH -> {
                    // Force refresh all widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, LiveMatchWidgetProvider::class.java)
                    )
                    onUpdate(context, appWidgetManager, widgetIds)
                }
                ACTION_NEXT_MATCH -> {
                    // Switch to next match
                    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    val currentIndex = prefs.getInt("current_match_index", 0)
                    prefs.edit().putInt("current_match_index", currentIndex + 1).apply()
                
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, LiveMatchWidgetProvider::class.java)
                    )
                    onUpdate(context, appWidgetManager, widgetIds)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    private suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            // Main click intent
            val mainIntent = Intent(context, BouncerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        
            // Refresh action intent
            val refreshIntent = Intent(context, LiveMatchWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 1000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        
            // Next match action intent
            val nextMatchIntent = Intent(context, LiveMatchWidgetProvider::class.java).apply {
                action = ACTION_NEXT_MATCH
            }
            val nextMatchPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 2000, nextMatchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build the widget views
            val widgetColors = WidgetRemoteViewsColors.of(context)
            val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            val winProbability = prefs.getFloat("win_probability", 0.5f)
            val team1Color = prefs.getInt("team1_color", widgetColors.primary)

            val views = RemoteViews(context.packageName, R.layout.widget_live_match_premium).apply {
                // Set click handlers
                setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
                setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)
                setOnClickPendingIntent(R.id.next_match_button, nextMatchPendingIntent)
                setInt(R.id.refresh_button, "setColorFilter", widgetColors.primaryLight)
                setInt(R.id.next_match_button, "setColorFilter", widgetColors.primaryLight)

                // Load cached match data
                val team1Name = prefs.getString("team1_name", "Team 1") ?: "Team 1"
                val team2Name = prefs.getString("team2_name", "Team 2") ?: "Team 2"
                val team1Score = prefs.getString("team1_score", "-") ?: "-"
                val team2Score = prefs.getString("team2_score", "-")
                val matchStatus = prefs.getString("match_status", "No live match") ?: "No live match"
                val matchType = prefs.getString("match_type", "Cricket") ?: "Cricket"
                val isLive = prefs.getBoolean("is_live", false)
                val recentBallsJson = prefs.getString("recent_balls", null)
                val team1Leading = winProbability >= 0.5f
            
                // Update team names
                setTextViewText(R.id.team1_name, team1Name)
                setTextViewText(R.id.team2_name, team2Name)
                setTextColor(
                    R.id.team1_name,
                    if (team1Leading) widgetColors.primary else widgetColors.textSecondary
                )
                setTextColor(
                    R.id.team2_name,
                    if (team1Leading) widgetColors.textSecondary else widgetColors.primary
                )
            
                // Update scores
                setTextViewText(R.id.team1_score, team1Score)
                setTextColor(
                    R.id.team1_score,
                    if (team1Leading) widgetColors.primaryLight else widgetColors.textPrimary
                )
                if (team2Score != null && team2Score != "-") {
                    setTextViewText(R.id.team2_score, team2Score)
                    setTextColor(
                        R.id.team2_score,
                        if (team1Leading) widgetColors.textPrimary else widgetColors.primaryLight
                    )
                    setViewVisibility(R.id.team2_score, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.team2_score, View.GONE)
                }
            
                // Update status
                setTextViewText(R.id.match_status, matchStatus)
                setTextViewText(R.id.match_type, matchType)
                setTextColor(R.id.match_type, widgetColors.textTertiary)
            
                // Live indicator visibility
                if (isLive) {
                    setViewVisibility(R.id.live_indicator, View.VISIBLE)
                    setTextColor(R.id.match_status, widgetColors.live)
                } else {
                    setViewVisibility(R.id.live_indicator, View.GONE)
                    setTextColor(R.id.match_status, widgetColors.textSecondary)
                }
            
                // Win probability display
                val winProbPercent = (winProbability * 100).toInt()
                setTextViewText(R.id.win_probability_text, "$winProbPercent%")
                setTextColor(R.id.win_probability_text, widgetColors.primary)
            
                // Recent balls display (iOS Live Activity parity)
                if (recentBallsJson != null && isLive) {
                    try {
                        val recentBalls = recentBallsJson.split(",").filter { it.isNotEmpty() }
                        if (recentBalls.isNotEmpty()) {
                            setViewVisibility(R.id.recent_balls_container, View.VISIBLE)
                        
                            // Update individual ball views
                            val ballIds = listOf(
                                R.id.ball_1, R.id.ball_2, R.id.ball_3,
                                R.id.ball_4, R.id.ball_5, R.id.ball_6
                            )
                        
                            val lastSixBalls = recentBalls.takeLast(6)
                            ballIds.forEachIndexed { index, ballId ->
                                if (index < lastSixBalls.size) {
                                    val ball = lastSixBalls[index]
                                    setTextViewText(ballId, ball)
                                
                                    val (backgroundRes, textColor) = getBallStyle(ball, widgetColors)
                                    setInt(ballId, "setBackgroundResource", backgroundRes)
                                    setTextColor(ballId, textColor)
                                } else {
                                    setTextViewText(ballId, "")
                                    setInt(ballId, "setBackgroundResource", R.drawable.bg_widget_ball_neutral)
                                    setTextColor(ballId, widgetColors.textTertiary)
                                }
                            }
                        } else {
                            setViewVisibility(R.id.recent_balls_container, View.GONE)
                        }
                    } catch (e: Exception) {
                        // Hiding the strip is the right fallback, but a widget that silently stops
                        // showing recent balls is indistinguishable from a match with none.
                        Log.w(TAG, "Recent-balls strip failed to render", e)
                        setViewVisibility(R.id.recent_balls_container, View.GONE)
                    }
                } else {
                    setViewVisibility(R.id.recent_balls_container, View.GONE)
                }
            }

            try {
                val progressBitmap = withContext(Dispatchers.Default) {
                    createTeamGradientProgressBitmap(winProbability, 200, 8, team1Color, widgetColors)
                }
                views.setImageViewBitmap(R.id.win_probability_bar, progressBitmap)
            } catch (e: Exception) {
                // The widget still renders without the win-probability bar; the failure is logged
                // rather than discarded so a bitmap regression is visible.
                Log.w(TAG, "Win-probability bitmap failed", e)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Get ball colors based on ball result (iOS Live Activity parity)
     */
    private fun getBallStyle(ball: String, colors: WidgetRemoteViewsColors): Pair<Int, Int> {
        try {
            return when (ball.uppercase().trim()) {
                "W" -> Pair(R.drawable.bg_widget_ball_wicket, colors.textPrimary)
                "6" -> Pair(R.drawable.bg_widget_ball_six, colors.textPrimary)
                "4" -> Pair(R.drawable.bg_widget_ball_four, colors.textPrimary)
                "0", "•" -> Pair(R.drawable.bg_widget_ball_neutral, colors.textTertiary)
                "NB", "WD" -> Pair(R.drawable.bg_widget_ball_extra, colors.textOnPrimary)
                else -> Pair(R.drawable.bg_widget_ball_neutral, colors.textPrimary)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Creates a team-gradient progress bar bitmap for win probability
     */
    private fun createTeamGradientProgressBitmap(
        probability: Float,
        width: Int,
        height: Int,
        teamColor: Int,
        colors: WidgetRemoteViewsColors,
    ): Bitmap {
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
            // Background
            paint.color = colors.progressTrack
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 4f, 4f, paint)
        
            // Branded gold progress with a slightly warmer leading edge.
            val team1Width = width * probability
            paint.shader = LinearGradient(
                0f, 0f, team1Width, 0f,
                intArrayOf(colors.primaryDim, teamColor, colors.primaryLight),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, team1Width, height.toFloat()), 4f, 4f, paint)
        
            return bitmap
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
}
