package com.tanush.bouncer.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * LiveNotificationManager - Provides iOS-like Live Activity experience on Android
 * 
 * Features:
 * - Ongoing notifications with custom layouts mimicking Dynamic Island
 * - Color-coded alerts (Purple for Sixes, Red for Wickets, Gold for Milestones)
 * - Real-time score updates with win probability
 * - Rich media support with team logos and animated indicators
 */
class LiveNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID_LIVE = "bouncer_live_match"
        const val CHANNEL_ID_ALERTS = "bouncer_match_alerts"
        const val NOTIFICATION_ID_LIVE = 1001
        const val NOTIFICATION_ID_WICKET = 2001
        const val NOTIFICATION_ID_BOUNDARY = 2002
        const val NOTIFICATION_ID_MILESTONE = 2003
        
        // Event Colors (matching iOS Live Activity colors)
        const val COLOR_WICKET = 0xFFFF3B30.toInt()      // Red
        const val COLOR_SIX = 0xFF8B5CF6.toInt()          // Purple
        const val COLOR_FOUR = 0xFFFACC15.toInt()         // Yellow/Gold
        const val COLOR_MILESTONE = 0xFFFFD700.toInt()    // Gold
        const val COLOR_WIN_PROB_HIGH = 0xFF22C55E.toInt() // Green
        const val COLOR_WIN_PROB_LOW = 0xFFEF4444.toInt()  // Red
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Creates notification channels for different types of alerts
     */
    private fun createNotificationChannels() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
                // Live Match Channel (Ongoing, Low priority for persistent display)
                val liveChannel = NotificationChannel(
                    CHANNEL_ID_LIVE,
                    "Live Match Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Ongoing live match score updates"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            
                // Alerts Channel (High priority for wickets, boundaries, milestones)
                val alertsChannel = NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Match Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Wickets, boundaries, and milestone alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            
                systemManager.createNotificationChannel(liveChannel)
                systemManager.createNotificationChannel(alertsChannel)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Shows/updates the ongoing live match notification
     * Enhanced with iOS Live Activity parity: batsmen, bowler, recent balls
     */
    fun showLiveMatchNotification(
        match: CricketMatch,
        winProbability: Float? = null,
        lastEvent: MatchEvent? = null,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null,
        recentBalls: List<String>? = null
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Showing live match notification"
            level = SentryLevel.INFO
            setData("matchId", match.id)
            setData("hasWinProbability", winProbability != null)
            setData("hasLastEvent", lastEvent != null)
        })
        
        val intent = Intent(context, BouncerActivity::class.java).apply {
            try {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("match_id", match.id)
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification with custom content
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"}")
            .setContentText(buildScoreText(match))
            .setSubText(match.matchType)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(createCompactView(match, winProbability))
            .setCustomBigContentView(createExpandedView(match, winProbability, lastEvent, batsmen, bowler, recentBalls))
        
        // Add action buttons
        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "View Details",
            pendingIntent
        )
        
        try {
            notificationManager.notify(NOTIFICATION_ID_LIVE, builder.build())
        } catch (e: SecurityException) {
            // Handle missing notification permission gracefully
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.notification"
                message = "Missing notification permission"
                level = SentryLevel.WARNING
                setData("matchId", match.id)
            })
        }
    }
    
    /**
     * Creates compact notification view (collapsed state)
     */
    private fun createCompactView(match: CricketMatch, winProbability: Float?): RemoteViews {
        try {
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
        
            return RemoteViews(context.packageName, R.layout.notification_live_compact).apply {
                setTextViewText(R.id.team1_name, team1?.acronym?.ifEmpty { team1.name.take(3) } ?: "T1")
                setTextViewText(R.id.team2_name, team2?.acronym?.ifEmpty { team2.name.take(3) } ?: "T2")
                setTextViewText(R.id.score_text, buildScoreText(match))
                setTextViewText(R.id.status_text, match.status)
            
                // Win probability indicator
                winProbability?.let { prob ->
                    setTextViewText(R.id.win_prob_text, "${(prob * 100).toInt()}%")
                    val color = if (prob > 0.5f) COLOR_WIN_PROB_HIGH else COLOR_WIN_PROB_LOW
                    setTextColor(R.id.win_prob_text, color)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Creates expanded notification view (expanded state with more details)
     * Enhanced with iOS Live Activity parity: batsmen, bowler, recent balls
     */
    private fun createExpandedView(
        match: CricketMatch,
        winProbability: Float?,
        lastEvent: MatchEvent?,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null,
        recentBalls: List<String>? = null
    ): RemoteViews {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        return RemoteViews(context.packageName, R.layout.notification_live_expanded).apply {
            setTextViewText(R.id.team1_name, team1?.name ?: "Team 1")
            setTextViewText(R.id.team2_name, team2?.name ?: "Team 2")
            setTextViewText(R.id.team1_score, team1?.score ?: "-")
            setTextViewText(R.id.team2_score, team2?.score ?: "-")
            setTextViewText(R.id.match_status, match.status)
            setTextViewText(R.id.match_info, match.matchType)
            
            // Win probability bar
            winProbability?.let { prob ->
                setTextViewText(R.id.win_prob_team1, "${(prob * 100).toInt()}%")
                setTextViewText(R.id.win_prob_team2, "${((1 - prob) * 100).toInt()}%")
            }
            
            // Batsmen stats (iOS Live Activity parity)
            batsmen?.let { batsmenList ->
                if (batsmenList.isNotEmpty()) {
                    val striker = batsmenList.firstOrNull { it.isStriker } ?: batsmenList.first()
                    val nonStriker = batsmenList.find { !it.isStriker }
                    
                    setTextViewText(R.id.batsman1_name, "${striker.name}*")
                    setTextViewText(R.id.batsman1_stats, "${striker.runs}(${striker.balls})")
                    nonStriker?.let { ns ->
                        setTextViewText(R.id.batsman2_name, ns.name)
                        setTextViewText(R.id.batsman2_stats, "${ns.runs}(${ns.balls})")
                        setViewVisibility(R.id.batsman2_container, android.view.View.VISIBLE)
                    } ?: run {
                        setViewVisibility(R.id.batsman2_container, android.view.View.GONE)
                    }
                    setViewVisibility(R.id.batsmen_container, android.view.View.VISIBLE)
                } else {
                    setViewVisibility(R.id.batsmen_container, android.view.View.GONE)
                }
            } ?: run {
                setViewVisibility(R.id.batsmen_container, android.view.View.GONE)
            }
            
            // Bowler stats (iOS Live Activity parity)
            bowler?.let { b ->
                setTextViewText(R.id.bowler_name, b.name)
                setTextViewText(R.id.bowler_stats, "${b.wickets}/${b.runs} (${b.overs})")
                setViewVisibility(R.id.bowler_container, android.view.View.VISIBLE)
            } ?: run {
                setViewVisibility(R.id.bowler_container, android.view.View.GONE)
            }
            
            // Recent balls strip (iOS Live Activity parity)
            recentBalls?.let { balls ->
                if (balls.isNotEmpty()) {
                    val recentBallsText = balls.takeLast(6).joinToString("  ")
                    setTextViewText(R.id.recent_balls_text, recentBallsText)
                    setViewVisibility(R.id.recent_balls_container, android.view.View.VISIBLE)
                } else {
                    setViewVisibility(R.id.recent_balls_container, android.view.View.GONE)
                }
            } ?: run {
                setViewVisibility(R.id.recent_balls_container, android.view.View.GONE)
            }
            
            // Last event highlight with accent colors
            lastEvent?.let { event ->
                setTextViewText(R.id.last_event_text, event.description)
                setTextColor(R.id.last_event_text, event.color)
                setInt(R.id.last_event_indicator, "setBackgroundColor", event.color)
                setViewVisibility(R.id.last_event_container, android.view.View.VISIBLE)
            } ?: run {
                setViewVisibility(R.id.last_event_container, android.view.View.GONE)
            }
        }
    }
    
    /**
     * Batsman info for notification display
     */
    data class BatsmanInfo(
        val name: String,
        val runs: Int,
        val balls: Int,
        val isStriker: Boolean = false
    )
    
    /**
     * Bowler info for notification display
     */
    data class BowlerInfo(
        val name: String,
        val overs: String,
        val runs: Int,
        val wickets: Int
    )
    
    /**
     * Shows a wicket alert notification
     */
    fun showWicketAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        dismissalType: String,
        runsScored: Int
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Showing wicket alert"
            level = SentryLevel.INFO
            setData("matchId", match.id)
            setData("batsmanName", batsmanName)
            setData("dismissalType", dismissalType)
        })
        
        showEventAlert(
            notificationId = NOTIFICATION_ID_WICKET,
            title = "WICKET!",
            text = "$batsmanName $dismissalType by $bowlerName ($runsScored runs)",
            color = COLOR_WICKET,
            match = match
        )
    }
    
    /**
     * Shows a boundary (six) alert notification
     */
    fun showSixAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String
    ) {
        showEventAlert(
            notificationId = NOTIFICATION_ID_BOUNDARY,
            title = "SIX!",
            text = "$batsmanName hits $bowlerName for six! $description",
            color = COLOR_SIX,
            match = match
        )
    }
    
    /**
     * Shows a boundary (four) alert notification
     */
    fun showFourAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String
    ) {
        showEventAlert(
            notificationId = NOTIFICATION_ID_BOUNDARY,
            title = "FOUR!",
            text = "$batsmanName hits $bowlerName for four! $description",
            color = COLOR_FOUR,
            match = match
        )
    }
    
    /**
     * Shows a milestone alert notification (50, 100, 5-wicket haul, etc.)
     */
    fun showMilestoneAlert(
        match: CricketMatch,
        playerName: String,
        milestone: String,
        description: String
    ) {
        showEventAlert(
            notificationId = NOTIFICATION_ID_MILESTONE,
            title = milestone,
            text = "$playerName - $description",
            color = COLOR_MILESTONE,
            match = match
        )
    }
    
    /**
     * Generic event alert notification
     */
    private fun showEventAlert(
        notificationId: Int,
        title: String,
        text: String,
        color: Int,
        match: CricketMatch
    ) {
        val intent = Intent(context, BouncerActivity::class.java).apply {
            try {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("match_id", match.id)
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        
        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Handle missing notification permission gracefully
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.notification"
                message = "Missing notification permission for event alert"
                level = SentryLevel.WARNING
                setData("notificationId", notificationId)
                setData("title", title)
            })
        }
    }
    
    /**
     * Dismisses the live match notification
     */
    fun dismissLiveNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_LIVE)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Dismisses all notifications
     */
    fun dismissAllNotifications() {
        try {
            notificationManager.cancelAll()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Builds score text from match data
     */
    private fun buildScoreText(match: CricketMatch): String {
        try {
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            val team1Score = team1?.score ?: "-"
            val team2Score = team2?.score
        
            return if (team2Score != null && team2Score != "-" && team2Score.isNotEmpty()) {
                "$team1Score • $team2Score"
            } else {
                team1Score
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Creates a win probability progress bitmap for notification
     */
    fun createWinProbabilityBitmap(probability: Float, width: Int, height: Int): Bitmap {
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
            // Background
            paint.color = 0xFF1A1A1A.toInt()
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 8f, 8f, paint)
        
            // Team 1 progress (Gold gradient)
            val team1Width = width * probability
            paint.shader = LinearGradient(
                0f, 0f, team1Width, 0f,
                intArrayOf(0xFFB39800.toInt(), 0xFFFFD700.toInt(), 0xFFFFF4C4.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, team1Width, height.toFloat()), 8f, 8f, paint)
        
            return bitmap
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Represents a match event for notification display
     */
    data class MatchEvent(
        val type: EventType,
        val description: String,
        val color: Int = when (type) {
            EventType.WICKET -> COLOR_WICKET
            EventType.SIX -> COLOR_SIX
            EventType.FOUR -> COLOR_FOUR
            EventType.MILESTONE -> COLOR_MILESTONE
            EventType.OTHER -> 0xFFFFFFFF.toInt()
        }
    )
    
    enum class EventType {
        WICKET, SIX, FOUR, MILESTONE, OTHER
    }
}
