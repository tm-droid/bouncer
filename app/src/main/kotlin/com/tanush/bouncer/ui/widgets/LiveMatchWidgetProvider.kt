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
import android.view.View
import android.widget.RemoteViews
import com.tanush.bouncer.R
import com.tanush.bouncer.MainActivity
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        const val ACTION_REFRESH = "com.tanush.bouncer.widget.REFRESH"
        const val ACTION_NEXT_MATCH = "com.tanush.bouncer.widget.NEXT_MATCH"
        const val EXTRA_MATCH_ID = "match_id"
        
        // Premium Colors (matching theme)
        const val COLOR_GOLD = 0xFFFFD700.toInt()
        const val COLOR_GOLD_DIM = 0xFFB39800.toInt()
        const val COLOR_BLACK = 0xFF050505.toInt()
        const val COLOR_BLACK_ELEVATED = 0xFF141414.toInt()
        const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        const val COLOR_TEXT_SECONDARY = 0xFF94A3B8.toInt()
        const val COLOR_TEXT_TERTIARY = 0xFF64748B.toInt()
        const val COLOR_ERROR_RED = 0xFFEF4444.toInt()
        const val COLOR_SUCCESS_GREEN = 0xFF22C55E.toInt()
        const val COLOR_LIVE_RED = 0xFFFF3B30.toInt()
        
        // iOS Live Activity parity colors
        const val COLOR_SIX_PURPLE = 0xFF8B5CF6.toInt()
        const val COLOR_FOUR_GREEN = 0xFF22C55E.toInt()
        const val COLOR_WARNING_YELLOW = 0xFFFACC15.toInt()
        
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
        try {
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
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
    
    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            // Main click intent
            val mainIntent = Intent(context, MainActivity::class.java).apply {
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
            val views = RemoteViews(context.packageName, R.layout.widget_live_match_premium).apply {
                // Set click handlers
                setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
                setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)
                setOnClickPendingIntent(R.id.next_match_button, nextMatchPendingIntent)
            
                // Load cached match data
                val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
                val team1Name = prefs.getString("team1_name", "Team 1") ?: "Team 1"
                val team2Name = prefs.getString("team2_name", "Team 2") ?: "Team 2"
                val team1Score = prefs.getString("team1_score", "-") ?: "-"
                val team2Score = prefs.getString("team2_score", "-")
                val matchStatus = prefs.getString("match_status", "No live match") ?: "No live match"
                val matchType = prefs.getString("match_type", "Cricket") ?: "Cricket"
                val winProbability = prefs.getFloat("win_probability", 0.5f)
                val isLive = prefs.getBoolean("is_live", false)
                val recentBallsJson = prefs.getString("recent_balls", null)
                val team1Color = prefs.getInt("team1_color", COLOR_GOLD)
            
                // Update team names
                setTextViewText(R.id.team1_name, team1Name)
                setTextViewText(R.id.team2_name, team2Name)
            
                // Update scores
                setTextViewText(R.id.team1_score, team1Score)
                if (team2Score != null && team2Score != "-") {
                    setTextViewText(R.id.team2_score, team2Score)
                    setViewVisibility(R.id.team2_score, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.team2_score, View.GONE)
                }
            
                // Update status
                setTextViewText(R.id.match_status, matchStatus)
                setTextViewText(R.id.match_type, matchType)
            
                // Live indicator visibility
                if (isLive) {
                    setViewVisibility(R.id.live_indicator, View.VISIBLE)
                    setTextColor(R.id.match_status, COLOR_LIVE_RED)
                } else {
                    setViewVisibility(R.id.live_indicator, View.GONE)
                    setTextColor(R.id.match_status, COLOR_TEXT_SECONDARY)
                }
            
                // Win probability display
                val winProbPercent = (winProbability * 100).toInt()
                setTextViewText(R.id.win_probability_text, "$winProbPercent%")
                val probColor = if (winProbability > 0.5f) COLOR_SUCCESS_GREEN else if (winProbability < 0.5f) COLOR_ERROR_RED else COLOR_GOLD
                setTextColor(R.id.win_probability_text, probColor)
            
                // Set win probability progress bar with team gradient
                try {
                    val progressBitmap = createTeamGradientProgressBitmap(winProbability, 200, 8, team1Color)
                    setImageViewBitmap(R.id.win_probability_bar, progressBitmap)
                } catch (e: Exception) {
                    // Fallback if bitmap creation fails
                }
            
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
                                
                                    // Color based on ball type
                                    val (bgColor, textColor) = getBallColors(ball)
                                    setInt(ballId, "setBackgroundColor", bgColor)
                                    setTextColor(ballId, textColor)
                                } else {
                                    setTextViewText(ballId, "")
                                    setInt(ballId, "setBackgroundColor", COLOR_BLACK_ELEVATED)
                                }
                            }
                        } else {
                            setViewVisibility(R.id.recent_balls_container, View.GONE)
                        }
                    } catch (e: Exception) {
                        setViewVisibility(R.id.recent_balls_container, View.GONE)
                    }
                } else {
                    setViewVisibility(R.id.recent_balls_container, View.GONE)
                }
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
    private fun getBallColors(ball: String): Pair<Int, Int> {
        try {
            return when (ball.uppercase().trim()) {
                "W" -> Pair(COLOR_ERROR_RED, COLOR_TEXT_PRIMARY)                    // Wicket - Red
                "6" -> Pair(COLOR_SIX_PURPLE, COLOR_TEXT_PRIMARY)                   // Six - Purple
                "4" -> Pair(COLOR_FOUR_GREEN, COLOR_TEXT_PRIMARY)                   // Four - Green
                "0", "•" -> Pair(COLOR_BLACK_ELEVATED, COLOR_TEXT_TERTIARY)        // Dot - Gray
                "NB", "WD" -> Pair(COLOR_WARNING_YELLOW, COLOR_BLACK)               // Extras - Yellow
                else -> Pair(COLOR_BLACK_ELEVATED, COLOR_TEXT_PRIMARY)              // Runs - Default
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Creates a team-gradient progress bar bitmap for win probability
     */
    private fun createTeamGradientProgressBitmap(probability: Float, width: Int, height: Int, teamColor: Int): Bitmap {
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
            // Background
            paint.color = COLOR_BLACK_ELEVATED
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 4f, 4f, paint)
        
            // Team 1 progress with team-based gradient
            val team1Width = width * probability
            val gradientStartColor = (teamColor and 0x00FFFFFF) or 0x99000000.toInt() // Dimmed version
            paint.shader = LinearGradient(
                0f, 0f, team1Width, 0f,
                intArrayOf(gradientStartColor, teamColor),
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
    
    /**
     * Updates the widget cache with new match data
     * Enhanced with iOS Live Activity parity: recent balls, team colors
     */
    fun updateCache(
        context: Context,
        match: CricketMatch,
        winProbability: Float? = null,
        recentBalls: List<String>? = null,
        teamColor: Int? = null
    ) {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("team1_name", team1?.acronym?.ifEmpty { team1.name.take(3) } ?: "Team 1")
            putString("team2_name", team2?.acronym?.ifEmpty { team2.name.take(3) } ?: "Team 2")
            putString("team1_score", team1?.score ?: "-")
            putString("team2_score", team2?.score)
            putString("match_status", match.status)
            putString("match_type", match.matchType)
            putFloat("win_probability", winProbability ?: 0.5f)
            putBoolean("is_live", match.isLive)
            
            // Store recent balls as comma-separated string
            recentBalls?.let { balls ->
                putString("recent_balls", balls.joinToString(","))
            }
            
            // Store team color for gradient
            putInt("team1_color", teamColor ?: COLOR_GOLD)
            
            apply()
        }
        
        // Trigger widget update
        triggerUpdate(context, match)
    }
}
