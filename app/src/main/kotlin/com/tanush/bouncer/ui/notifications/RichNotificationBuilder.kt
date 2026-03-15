package com.tanush.bouncer.ui.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Sentry

/**
 * RichNotificationBuilder - Enhanced Cricket Notification System
 * 
 * Provides rich, interactive notifications with:
 * - Multiple notification channels for different alert types
 * - Expanded scorecard views with custom layouts
 * - Quick action buttons for common operations
 * - Progress notifications for overs remaining
 * - Grouped notifications for tournaments
 * - Summary notifications with highlights
 */
object RichNotificationBuilder {
    
    // ===========================================
    // NOTIFICATION CHANNELS & GROUPS
    // ===========================================
    
    object CricketNotificationChannel {
        
        // Channel Group ID
        const val GROUP_CRICKET = "cricket_notifications"
        
        // Channel IDs
        const val CHANNEL_LIVE_SCORE = "live_score_updates"
        const val CHANNEL_WICKETS = "wicket_alerts"
        const val CHANNEL_BOUNDARIES = "boundary_alerts"
        const val CHANNEL_MILESTONES = "milestone_alerts"
        const val CHANNEL_MATCH_EVENTS = "match_events"
        const val CHANNEL_MATCH_START = "match_start"
        const val CHANNEL_MATCH_END = "match_end"
        const val CHANNEL_SUMMARY = "match_summary"
        
        // Notification IDs
        private var notificationIdCounter = 3000
        fun getNextNotificationId(): Int = notificationIdCounter++
        
        const val NOTIFICATION_ID_LIVE = 1001
        const val NOTIFICATION_ID_WICKET_BASE = 2000
        const val NOTIFICATION_ID_BOUNDARY_BASE = 2100
        const val NOTIFICATION_ID_MILESTONE_BASE = 2200
        const val NOTIFICATION_ID_SUMMARY_BASE = 2300
        const val NOTIFICATION_ID_GROUP = 2999
        
        // Colors matching app theme
        const val COLOR_GOLD = 0xFFFFD700.toInt()
        const val COLOR_GOLD_DIM = 0xFFB39800.toInt()
        const val COLOR_BLACK = 0xFF050505.toInt()
        const val COLOR_SURFACE = 0xFF0F0F0F.toInt()
        const val COLOR_WICKET = 0xFFFF3B30.toInt()
        const val COLOR_SIX = 0xFF8B5CF6.toInt()
        const val COLOR_FOUR = 0xFF22C55E.toInt()
        const val COLOR_MILESTONE = 0xFFFFD700.toInt()
        const val COLOR_SUCCESS = 0xFF22C55E.toInt()
        const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        const val COLOR_TEXT_SECONDARY = 0xFF94A3B8.toInt()
        
        /**
         * Creates all notification channels for the cricket app
         * Call this in Application.onCreate() or BouncerActivity
         */
        fun createNotificationChannels(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                    // Create channel group
                    val channelGroup = NotificationChannelGroup(
                        GROUP_CRICKET,
                        "Cricket Notifications"
                    )
                    notificationManager.createNotificationChannelGroup(channelGroup)
                
                    // Live Score Channel - Low priority for persistent display
                    val liveScoreChannel = NotificationChannel(
                        CHANNEL_LIVE_SCORE,
                        "Live Score Updates",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Ongoing live match score updates"
                        group = GROUP_CRICKET
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(false)
                        setSound(null, null)
                    }
                
                    // Wicket Alerts - High priority with vibration
                    val wicketChannel = NotificationChannel(
                        CHANNEL_WICKETS,
                        "Wicket Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerts when a wicket falls"
                        group = GROUP_CRICKET
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 300, 100, 300)
                        enableLights(true)
                        lightColor = COLOR_WICKET
                    }
                
                    // Boundary Alerts - Default priority
                    val boundaryChannel = NotificationChannel(
                        CHANNEL_BOUNDARIES,
                        "Boundary Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Alerts for fours and sixes"
                        group = GROUP_CRICKET
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 200)
                    }
                
                    // Milestone Alerts - Default priority
                    val milestoneChannel = NotificationChannel(
                        CHANNEL_MILESTONES,
                        "Milestone Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Player milestones (50s, 100s, 5-wicket hauls)"
                        group = GROUP_CRICKET
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableLights(true)
                        lightColor = COLOR_GOLD
                    }
                
                    // Match Events - Low priority
                    val matchEventsChannel = NotificationChannel(
                        CHANNEL_MATCH_EVENTS,
                        "Match Events",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "General match events (innings break, powerplay, etc.)"
                        group = GROUP_CRICKET
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                
                    // Match Start - High priority
                    val matchStartChannel = NotificationChannel(
                        CHANNEL_MATCH_START,
                        "Match Start Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications when a match is about to start"
                        group = GROUP_CRICKET
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(true)
                    }
                
                    // Match End - Default priority
                    val matchEndChannel = NotificationChannel(
                        CHANNEL_MATCH_END,
                        "Match End Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifications when a match ends"
                        group = GROUP_CRICKET
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                
                    // Summary Channel - Low priority
                    val summaryChannel = NotificationChannel(
                        CHANNEL_SUMMARY,
                        "Match Summaries",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Daily and tournament summaries"
                        group = GROUP_CRICKET
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                
                    // Create all channels
                    notificationManager.createNotificationChannels(
                        listOf(
                            liveScoreChannel,
                            wicketChannel,
                            boundaryChannel,
                            milestoneChannel,
                            matchEventsChannel,
                            matchStartChannel,
                            matchEndChannel,
                            summaryChannel
                        )
                    )
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
    
    // ===========================================
    // EXPANDED SCORECARD NOTIFICATION
    // ===========================================
    
    /**
     * Creates an expanded scorecard notification with big content view
     */
    fun createExpandedScorecardNotification(
        context: Context,
        match: CricketMatch,
        team1Score: String,
        team2Score: String?,
        status: String,
        winProbability: Float? = null,
        batsmen: List<BatsmanData>? = null,
        bowler: BowlerData? = null,
        recentBalls: List<String>? = null,
        lastEvent: String? = null
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        
        val builder = NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${match.teams.getOrNull(0)?.name} vs ${match.teams.getOrNull(1)?.name}")
            .setContentText(buildScoreText(team1Score, team2Score))
            .setSubText(match.matchType)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        
        // Add custom big content view
        builder.setCustomBigContentView(
            createExpandedScorecardView(
                context = context,
                match = match,
                team1Score = team1Score,
                team2Score = team2Score,
                status = status,
                winProbability = winProbability,
                batsmen = batsmen,
                bowler = bowler,
                recentBalls = recentBalls,
                lastEvent = lastEvent
            )
        )
        
        return builder.build()
    }
    
    /**
     * Creates the expanded view RemoteViews for scorecard
     */
    private fun createExpandedScorecardView(
        context: Context,
        match: CricketMatch,
        team1Score: String,
        team2Score: String?,
        status: String,
        winProbability: Float?,
        batsmen: List<BatsmanData>?,
        bowler: BowlerData?,
        recentBalls: List<String>?,
        lastEvent: String?
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_expanded).apply {
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            
            // Team names and scores
            setTextViewText(R.id.team1_name, team1?.name ?: "Team 1")
            setTextViewText(R.id.team2_name, team2?.name ?: "Team 2")
            setTextViewText(R.id.team1_score, team1Score)
            setTextViewText(R.id.team2_score, team2Score ?: "-")
            setTextViewText(R.id.match_status, status)
            setTextViewText(R.id.match_info, match.matchType)
            
            // Win probability
            winProbability?.let { prob ->
                setTextViewText(R.id.win_prob_team1, "${(prob * 100).toInt()}%")
                setTextViewText(R.id.win_prob_team2, "${((1 - prob) * 100).toInt()}%")
            }
        }
    }
    
    // ===========================================
    // QUICK ACTION NOTIFICATION
    // ===========================================
    
    /**
     * Creates a notification with quick action buttons
     */
    fun createQuickActionNotification(
        context: Context,
        match: CricketMatch,
        title: String,
        message: String,
        channelId: String = CricketNotificationChannel.CHANNEL_MATCH_EVENTS,
        accentColor: Int = CricketNotificationChannel.COLOR_GOLD,
        actions: List<NotificationAction> = listOf()
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("${match.teams.getOrNull(0)?.name} vs ${match.teams.getOrNull(1)?.name}")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(accentColor)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Add action buttons
        actions.forEach { action ->
            val actionIntent = Intent(context, BouncerActivity::class.java).apply {
                putExtra("match_id", match.id)
                putExtra("action", action.actionId)
            }
            val actionPendingIntent = PendingIntent.getActivity(
                context,
                action.actionId.hashCode(),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(
                NotificationCompat.Action.Builder(
                    action.icon,
                    action.title,
                    actionPendingIntent
                ).build()
            )
        }
        
        return builder.build()
    }
    
    data class NotificationAction(
        val actionId: String,
        val title: String,
        @param:DrawableRes val icon: Int = R.drawable.ic_launcher_foreground
    )
    
    // ===========================================
    // PROGRESS NOTIFICATION
    // ===========================================
    
    /**
     * Creates a progress notification showing overs remaining
     */
    fun createProgressNotification(
        context: Context,
        match: CricketMatch,
        currentOvers: Float,
        totalOvers: Float,
        runsRequired: Int? = null,
        runRate: Float? = null
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        val progress = if (totalOvers > 0) ((currentOvers / totalOvers) * 100).toInt() else 0
        
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        var contentText = "Overs: ${String.format("%.1f", currentOvers)}/${"%.0f".format(totalOvers)}"
        runsRequired?.let {
            contentText += " • Need $it runs"
        }
        runRate?.let {
            contentText += " @ ${String.format("%.2f", it)} RR"
        }
        
        val builder = NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText(contentText)
            .setSubText(match.matchType)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_GOLD)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        // Add overs remaining as big content
        val oversRemaining = totalOvers - currentOvers
        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("$contentText\n${String.format("%.1f", oversRemaining)} overs remaining")
        )
        
        return builder.build()
    }
    
    // ===========================================
    // GROUPED MATCH NOTIFICATIONS
    // ===========================================
    
    /**
     * Creates grouped notifications for multiple matches (tournament view)
     */
    fun createGroupedMatchNotifications(
        context: Context,
        matches: List<MatchSummaryData>,
        groupTitle: String = "Live Matches",
        groupId: String = "cricket_group"
    ): List<Notification> {
        val notifications = mutableListOf<Notification>()
        
        // Create summary notification (for grouping)
        val summaryNotification = NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(groupTitle)
            .setContentText("${matches.size} live matches")
            .setGroup(groupId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setColor(CricketNotificationChannel.COLOR_GOLD)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle(groupTitle)
                    .setSummaryText("${matches.size} matches")
                    .also { style ->
                        matches.take(5).forEach { match ->
                            style.addLine("${match.team1} ${match.score1} vs ${match.team2} ${match.score2 ?: ""}")
                        }
                    }
            )
            .build()
        
        notifications.add(summaryNotification)
        
        // Create individual match notifications
        matches.forEach { match ->
            val matchNotification = NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_LIVE_SCORE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${match.team1} vs ${match.team2}")
                .setContentText(buildScoreText(match.score1, match.score2))
                .setSubText(match.status)
                .setGroup(groupId)
                .setAutoCancel(true)
                .setColor(CricketNotificationChannel.COLOR_GOLD)
                .build()
            
            notifications.add(matchNotification)
        }
        
        return notifications
    }
    
    data class MatchSummaryData(
        val matchId: String,
        val team1: String,
        val team2: String,
        val score1: String,
        val score2: String?,
        val status: String,
        val matchType: String
    )
    
    // ===========================================
    // SUMMARY NOTIFICATION
    // ===========================================
    
    /**
     * Creates a summary notification with match highlights
     */
    fun createSummaryNotification(
        context: Context,
        match: CricketMatch,
        result: String,
        highlights: List<HighlightItem>,
        topPerformers: List<PerformerData>? = null
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        val builder = NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_MATCH_END)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText(result)
            .setSubText("Match Completed")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Build expanded content
        val expandedContent = StringBuilder()
        expandedContent.append(result)
        expandedContent.append("\n\n")
        
        // Add top performers
        topPerformers?.take(3)?.forEach { performer ->
            expandedContent.append("${performer.emoji} ${performer.name}: ${performer.performance}\n")
        }
        
        if (highlights.isNotEmpty()) {
            expandedContent.append("\n📊 Highlights:\n")
            highlights.take(4).forEach { highlight ->
                expandedContent.append("• ${highlight.text}\n")
            }
        }
        
        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(expandedContent.toString().trim())
        )
        
        // Add share action
        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "Share Result",
            pendingIntent
        )
        
        return builder.build()
    }
    
    data class HighlightItem(
        val text: String,
        val type: HighlightType = HighlightType.GENERAL
    )
    
    enum class HighlightType {
        GENERAL, MILESTONE, RECORD, TURNING_POINT
    }
    
    data class PerformerData(
        val name: String,
        val performance: String,
        val emoji: String = "⭐"
    )
    
    // ===========================================
    // EVENT-SPECIFIC NOTIFICATIONS
    // ===========================================
    
    /**
     * Creates a wicket alert notification
     */
    fun createWicketNotification(
        context: Context,
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        dismissalType: String,
        runsScored: Int,
        ballsFaced: Int,
        teamScore: String
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        return NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_WICKETS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("WICKET! 🔴")
            .setContentText("$batsmanName $dismissalType by $bowlerName")
            .setSubText("${team1?.name} vs ${team2?.name} • $teamScore")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_WICKET)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$batsmanName $dismissalType by $bowlerName\n$runsScored runs off $ballsFaced balls\nTeam score: $teamScore")
            )
            .build()
    }
    
    /**
     * Creates a six alert notification
     */
    fun createSixNotification(
        context: Context,
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String,
        totalSixes: Int
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_BOUNDARIES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SIX! 💜")
            .setContentText("$batsmanName hits $bowlerName for six!")
            .setSubText(description)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_SIX)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
    
    /**
     * Creates a four alert notification
     */
    fun createFourNotification(
        context: Context,
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_BOUNDARIES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FOUR! 💚")
            .setContentText("$batsmanName hits $bowlerName for four!")
            .setSubText(description)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_FOUR)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Creates a milestone alert notification (50, 100, etc.)
     */
    fun createMilestoneNotification(
        context: Context,
        match: CricketMatch,
        playerName: String,
        milestone: String,
        stats: String,
        milestoneType: MilestoneType
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        
        val (emoji, title) = when (milestoneType) {
            MilestoneType.FIFTY -> "🏅" to "FIFTY!"
            MilestoneType.CENTURY -> "💯" to "CENTURY!"
            MilestoneType.DOUBLE_CENTURY -> "🌟" to "DOUBLE CENTURY!"
            MilestoneType.FIVE_WICKETS -> "🎯" to "FIFER!"
            MilestoneType.TEN_WICKETS -> "🔥" to "TEN WICKETS!"
            MilestoneType.HAT_TRICK -> "🎩" to "HAT-TRICK!"
            MilestoneType.FASTEST_FIFTY -> "⚡" to "FASTEST FIFTY!"
            MilestoneType.FASTEST_CENTURY -> "🚀" to "FASTEST CENTURY!"
        }
        
        return NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_MILESTONES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$emoji $title")
            .setContentText("$playerName reaches $milestone!")
            .setSubText(stats)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_MILESTONE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$playerName reaches $milestone!\n$stats")
            )
            .build()
    }
    
    enum class MilestoneType {
        FIFTY, CENTURY, DOUBLE_CENTURY,
        FIVE_WICKETS, TEN_WICKETS, HAT_TRICK,
        FASTEST_FIFTY, FASTEST_CENTURY
    }
    
    // ===========================================
    // MATCH START/END NOTIFICATIONS
    // ===========================================
    
    /**
     * Creates a match starting soon notification
     */
    fun createMatchStartingSoonNotification(
        context: Context,
        match: CricketMatch,
        minutesUntilStart: Int
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        return NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_MATCH_START)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText("Match starting in $minutesUntilStart minutes!")
            .setSubText(match.matchType)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Watch",
                pendingIntent
            )
            .build()
    }
    
    /**
     * Creates a toss notification
     */
    fun createTossNotification(
        context: Context,
        match: CricketMatch,
        tossWinner: String,
        decision: String
    ): Notification {
        val pendingIntent = createMatchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        return NotificationCompat.Builder(context, CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Toss - ${team1?.name} vs ${team2?.name}")
            .setContentText("$tossWinner won the toss and elected to $decision")
            .setSubText(match.matchType)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setColor(CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    // ===========================================
    // HELPER FUNCTIONS
    // ===========================================
    
    /**
     * Creates a PendingIntent to open match details
     */
    private fun createMatchPendingIntent(context: Context, matchId: String): PendingIntent {
        try {
            val intent = Intent(context, BouncerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("match_id", matchId)
            }
            return PendingIntent.getActivity(
                context,
                matchId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Builds score text from team scores
     */
    private fun buildScoreText(score1: String, score2: String?): String {
        try {
            return if (score2 != null && score2 != "-" && score2.isNotEmpty()) {
                "$score1 • $score2"
            } else {
                score1
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Batsman data for notification display
     */
    data class BatsmanData(
        val name: String,
        val runs: Int,
        val balls: Int,
        val fours: Int,
        val sixes: Int,
        val isStriker: Boolean = false
    ) {
        val strikeRate: Double
            get() = if (balls > 0) (runs.toDouble() / balls * 100) else 0.0
    }
    
    /**
     * Bowler data for notification display
     */
    data class BowlerData(
        val name: String,
        val overs: String,
        val maidens: Int,
        val runs: Int,
        val wickets: Int
    ) {
        val economy: Double
            get() {
                val oversValue = overs.toDoubleOrNull() ?: 0.0
                return if (oversValue > 0) runs / oversValue else 0.0
            }
    }
}

// ===========================================
// NOTIFICATION STYLING HELPERS
// ===========================================

/**
 * Helper functions for notification styling
 */
object NotificationStyling {
    
    /**
     * Creates a win probability progress bitmap
     */
    fun createWinProbabilityBitmap(
        probability: Float,
        width: Int,
        height: Int,
        team1Color: Int = RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD,
        team2Color: Int = RichNotificationBuilder.CricketNotificationChannel.COLOR_TEXT_SECONDARY
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val cornerRadius = height / 2f
        val team1Width = width * probability
        
        // Team 2 background (full width)
        paint.color = team2Color
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            cornerRadius, cornerRadius, paint
        )
        
        // Team 1 foreground (probability width)
        paint.shader = LinearGradient(
            0f, 0f, team1Width, 0f,
            intArrayOf(
                (team1Color and 0x00FFFFFF) or 0x99000000.toInt(),
                team1Color
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(0f, 0f, team1Width, height.toFloat()),
            cornerRadius, cornerRadius, paint
        )
        
        return bitmap
    }
    
    /**
     * Creates a recent balls strip bitmap
     */
    fun createRecentBallsBitmap(
        balls: List<String>,
        ballSize: Int = 24,
        spacing: Int = 4
    ): Bitmap {
        val maxBalls = 6
        val displayBalls = balls.takeLast(maxBalls)
        val width = (maxBalls * ballSize) + ((maxBalls - 1) * spacing)
        val height = ballSize
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = ballSize * 0.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        
        displayBalls.forEachIndexed { index, ball ->
            val x = index * (ballSize + spacing)
            val (bgColor, textColor) = getBallColors(ball)
            
            // Draw ball circle
            paint.color = bgColor
            canvas.drawCircle(
                x + ballSize / 2f,
                ballSize / 2f,
                ballSize / 2f,
                paint
            )
            
            // Draw ball text
            textPaint.color = textColor
            canvas.drawText(
                formatBallText(ball),
                x + ballSize / 2f,
                ballSize / 2f + textPaint.textSize / 3,
                textPaint
            )
        }
        
        return bitmap
    }
    
    private fun getBallColors(ball: String): Pair<Int, Int> {
        try {
            return when (ball.uppercase().trim()) {
                "W" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_WICKET to Color.WHITE
                "6" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_SIX to Color.WHITE
                "4" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_FOUR to Color.WHITE
                "0", "•" -> 0xFF454545.toInt() to 0xFF94A3B8.toInt()
                "NB", "WD", "LB", "B" -> 0xFFFACC15.toInt() to Color.BLACK
                else -> 0xFF1A1A1A.toInt() to Color.WHITE
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    private fun formatBallText(ball: String): String {
        try {
            return when (ball.uppercase().trim()) {
                "0" -> "•"
                "NB" -> "nb"
                "WD" -> "wd"
                else -> ball
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Gets accent color for event type
     */
    fun getEventColor(eventType: String): Int {
        try {
            return when (eventType.lowercase()) {
                "wicket", "w" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_WICKET
                "six", "6" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_SIX
                "four", "4" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_FOUR
                "milestone", "50", "100" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_MILESTONE
                "success", "win" -> RichNotificationBuilder.CricketNotificationChannel.COLOR_SUCCESS
                else -> RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Formats overs display string
     */
    fun formatOvers(overs: Float): String {
        try {
            val wholeOvers = overs.toInt()
            val balls = ((overs - wholeOvers) * 10).toInt()
            return "$wholeOvers.$balls"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Calculates required run rate
     */
    fun calculateRequiredRunRate(runsRequired: Int, oversRemaining: Float): Float {
        try {
            return if (oversRemaining > 0) runsRequired / oversRemaining else 0f
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ===========================================
// NOTIFICATION TEMPLATES
// ===========================================

/**
 * Pre-built notification templates for common scenarios
 */
object NotificationTemplates {
    
    /**
     * Quick score update notification
     */
    fun quickScoreUpdate(
        context: Context,
        match: CricketMatch,
        team1Score: String,
        team2Score: String?
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText(buildScoreText(team1Score, team2Score))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Innings break notification
     */
    fun inningsBreak(
        context: Context,
        match: CricketMatch,
        firstInningsScore: String,
        target: Int,
        breakDuration: Int = 20
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Innings Break - ${team1?.name} vs ${team2?.name}")
            .setContentText("$firstInningsScore. Target: $target")
            .setSubText("Break: $breakDuration mins")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .build()
    }
    
    /**
     * Drinks break notification
     */
    fun drinksBreak(
        context: Context,
        match: CricketMatch,
        currentScore: String,
        overs: String
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Drinks Break")
            .setContentText("${team1?.name} vs ${team2?.name}: $currentScore ($overs)")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Strategic timeout notification (IPL style)
     */
    fun strategicTimeout(
        context: Context,
        match: CricketMatch,
        teamName: String,
        currentScore: String
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Strategic Timeout")
            .setContentText("$teamName $currentScore")
            .setSubText("${team1?.name} vs ${team2?.name}")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Review notification (DRS)
     */
    fun reviewDecision(
        context: Context,
        match: CricketMatch,
        teamName: String,
        playerName: String,
        decision: String,
        isSuccessful: Boolean
    ): Notification {
        val pendingIntent = createPendingIntent(context, match.id)
        
        val emoji = if (isSuccessful) "✅" else "❌"
        val resultText = if (isSuccessful) "Review Successful" else "Review Unsuccessful"
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$emoji DRS Review - $resultText")
            .setContentText("$teamName reviewed $playerName's $decision")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(
                if (isSuccessful) RichNotificationBuilder.CricketNotificationChannel.COLOR_SUCCESS
                else RichNotificationBuilder.CricketNotificationChannel.COLOR_WICKET
            )
            .build()
    }
    
    /**
     * Rain delay notification
     */
    fun rainDelay(
        context: Context,
        match: CricketMatch,
        currentScore: String,
        estimatedResume: String? = null
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        val contentText = if (estimatedResume != null) {
            "Rain stopped play. Resume: $estimatedResume"
        } else {
            "Rain stopped play. $currentScore"
        }
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🌧️ Rain Delay - ${team1?.name} vs ${team2?.name}")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF60A5FA.toInt())
            .build()
    }
    
    /**
     * Bad light notification
     */
    fun badLight(
        context: Context,
        match: CricketMatch,
        currentScore: String
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🌑 Bad Light - Play Suspended")
            .setContentText("${team1?.name} vs ${team2?.name}: $currentScore")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF6B7280.toInt())
            .build()
    }
    
    /**
     * Super over notification
     */
    fun superOver(
        context: Context,
        match: CricketMatch,
        team1SuperOverScore: String? = null,
        team2SuperOverScore: String? = null
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        val contentText = if (team1SuperOverScore != null && team2SuperOverScore != null) {
            "${team1?.name} $team1SuperOverScore vs ${team2?.name} $team2SuperOverScore"
        } else {
            "Match tied! Super Over to decide the winner"
        }
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚡ SUPER OVER!")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
    
    private fun createPendingIntent(context: Context, matchId: String): PendingIntent {
        try {
            val intent = Intent(context, BouncerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("match_id", matchId)
            }
            return PendingIntent.getActivity(
                context,
                matchId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    private fun buildScoreText(score1: String, score2: String?): String {
        try {
            return if (score2 != null && score2 != "-" && score2.isNotEmpty()) {
                "$score1 • $score2"
            } else {
                score1
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ===========================================
// NOTIFICATION MANAGER EXTENSIONS
// ===========================================

/**
 * Extension functions for notification management
 */
object NotificationManagerExtensions {
    
    /**
     * Shows a notification with auto-generated ID
     */
    fun NotificationManagerCompat.showCricketNotification(
        context: Context,
        notification: Notification,
        tag: String? = null
    ): Int {
        val id = RichNotificationBuilder.CricketNotificationChannel.getNextNotificationId()
        if (tag != null) {
            try {
                notify(tag, id, notification)
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        } else {
            notify(id, notification)
        }
        return id
    }
    
    /**
     * Cancels all cricket notifications
     */
    fun NotificationManagerCompat.cancelAllCricketNotifications() {
        try {
            cancelAll()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Cancels notifications for a specific match
     */
    fun NotificationManagerCompat.cancelMatchNotifications(matchId: String) {
        try {
            cancel(matchId, matchId.hashCode())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ===========================================
// CUSTOM SOUND OPTIONS
// ===========================================

/**
 * Custom notification sounds for different events
 */
object NotificationSounds {
    
    /**
     * Sound preference keys
     */
    const val PREF_WICKET_SOUND = "notification_sound_wicket"
    const val PREF_BOUNDARY_SOUND = "notification_sound_boundary"
    const val PREF_MILESTONE_SOUND = "notification_sound_milestone"
    const val PREF_MATCH_START_SOUND = "notification_sound_match_start"
    
    enum class SoundType {
        DEFAULT,
        CROWD_CHEER,
        STADIUM_HORN,
        WICKET_STUMPS,
        SILENT
    }
    
    /**
     * Gets the appropriate sound URI based on event type and user preferences
     */
    fun getSoundUri(context: Context, eventType: String): android.net.Uri? {
        try {
            val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        
            val soundPreference = when (eventType.lowercase()) {
                "wicket" -> prefs.getString(PREF_WICKET_SOUND, "default")
                "boundary", "four", "six" -> prefs.getString(PREF_BOUNDARY_SOUND, "default")
                "milestone" -> prefs.getString(PREF_MILESTONE_SOUND, "default")
                "match_start" -> prefs.getString(PREF_MATCH_START_SOUND, "default")
                else -> "default"
            }
        
            return when (soundPreference) {
                "silent" -> null
                "default" -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                else -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if notifications are muted for a specific match
     */
    fun isMatchMuted(context: Context, matchId: String): Boolean {
        try {
            val prefs = context.getSharedPreferences("notification_mutes", Context.MODE_PRIVATE)
            val mutedMatches = prefs.getStringSet("muted_matches", emptySet())
            return mutedMatches?.contains(matchId) == true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Mute/unmute notifications for a match
     */
    fun setMatchMuted(context: Context, matchId: String, muted: Boolean) {
        try {
            val prefs = context.getSharedPreferences("notification_mutes", Context.MODE_PRIVATE)
            val mutedMatches = prefs.getStringSet("muted_matches", mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
        
            if (muted) {
                mutedMatches.add(matchId)
            } else {
                mutedMatches.remove(matchId)
            }
        
            prefs.edit().putStringSet("muted_matches", mutedMatches).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ===========================================
// NOTIFICATION PREFERENCE MANAGER
// ===========================================

/**
 * Manages notification preferences
 */
object NotificationPreferences {
    
    private const val PREFS_NAME = "notification_preferences"
    
    // Preference keys
    const val KEY_WICKET_ALERTS = "pref_wicket_alerts"
    const val KEY_BOUNDARY_ALERTS = "pref_boundary_alerts"
    const val KEY_MILESTONE_ALERTS = "pref_milestone_alerts"
    const val KEY_MATCH_START_ALERTS = "pref_match_start_alerts"
    const val KEY_MATCH_END_ALERTS = "pref_match_end_alerts"
    const val KEY_SCORE_UPDATES = "pref_score_updates"
    const val KEY_ONLY_FAVORITES = "pref_only_favorites"
    const val KEY_QUIET_HOURS_ENABLED = "pref_quiet_hours"
    const val KEY_QUIET_HOURS_START = "pref_quiet_start"
    const val KEY_QUIET_HOURS_END = "pref_quiet_end"
    
    /**
     * Check if a specific notification type is enabled
     */
    fun isNotificationEnabled(context: Context, preferenceKey: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(preferenceKey, true)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Enable/disable a notification type
     */
    fun setNotificationEnabled(context: Context, preferenceKey: String, enabled: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(preferenceKey, enabled).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if quiet hours are active
     */
    fun isQuietHoursActive(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
            if (!prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)) {
                return false
            }
        
            val startHour = prefs.getInt(KEY_QUIET_HOURS_START, 22)
            val endHour = prefs.getInt(KEY_QUIET_HOURS_END, 7)
        
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
            return if (startHour < endHour) {
                currentHour in startHour until endHour
            } else {
                currentHour >= startHour || currentHour < endHour
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if only favorite matches should trigger notifications
     */
    fun onlyFavorites(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ONLY_FAVORITES, false)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if match is a favorite
     */
    fun isMatchFavorite(context: Context, matchId: String): Boolean {
        try {
            val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            val favorites = prefs.getStringSet("favorite_matches", emptySet())
            return favorites?.contains(matchId) == true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Should show notification based on all preferences
     */
    fun shouldShowNotification(
        context: Context,
        matchId: String,
        notificationType: String
    ): Boolean {
        // Check if notification type is enabled
        val typeKey = when (notificationType.lowercase()) {
            "wicket" -> KEY_WICKET_ALERTS
            "boundary", "four", "six" -> KEY_BOUNDARY_ALERTS
            "milestone" -> KEY_MILESTONE_ALERTS
            "match_start" -> KEY_MATCH_START_ALERTS
            "match_end" -> KEY_MATCH_END_ALERTS
            "score" -> KEY_SCORE_UPDATES
            else -> return true
        }
        
        if (!isNotificationEnabled(context, typeKey)) {
            return false
        }
        
        // Check quiet hours
        if (isQuietHoursActive(context)) {
            return false
        }
        
        // Check match mute status
        if (NotificationSounds.isMatchMuted(context, matchId)) {
            return false
        }
        
        // Check favorites preference
        if (onlyFavorites(context) && !isMatchFavorite(context, matchId)) {
            return false
        }
        
        return true
    }
}

// ===========================================
// RICH MEDIA NOTIFICATIONS
// ===========================================

/**
 * Creates notifications with rich media content
 */
object RichMediaNotifications {
    
    /**
     * Creates a notification with a large image (match moment)
     */
    fun createImageNotification(
        context: Context,
        match: CricketMatch,
        title: String,
        message: String,
        imageBitmap: Bitmap
    ): Notification {
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setLargeIcon(imageBitmap)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .bigLargeIcon(null as Bitmap?)
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .build()
    }
    
    /**
     * Creates a player card notification (for milestones)
     */
    fun createPlayerCardNotification(
        context: Context,
        match: CricketMatch,
        playerName: String,
        achievement: String,
        stats: String,
        playerPhotoBitmap: Bitmap? = null
    ): Notification {
        val pendingIntent = createPendingIntent(context, match.id)
        
        val builder = NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MILESTONES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🌟 $playerName - $achievement")
            .setContentText(stats)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_MILESTONE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        playerPhotoBitmap?.let {
            builder.setLargeIcon(it)
        }
        
        return builder.build()
    }
    
    /**
     * Creates a scorecard snapshot notification
     */
    fun createScorecardSnapshotNotification(
        context: Context,
        match: CricketMatch,
        scorecardBitmap: Bitmap
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scorecard - ${team1?.name} vs ${team2?.name}")
            .setContentText("Current match status")
            .setLargeIcon(scorecardBitmap)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(scorecardBitmap)
                    .bigLargeIcon(null as Bitmap?)
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .build()
    }
    
    private fun createPendingIntent(context: Context, matchId: String): PendingIntent {
        try {
            val intent = Intent(context, BouncerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("match_id", matchId)
            }
            return PendingIntent.getActivity(
                context,
                matchId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ===========================================
// CONVERSATION-STYLE NOTIFICATIONS
// ===========================================

/**
 * Creates messaging-style notifications for match commentary
 */
object CommentaryNotifications {
    
    /**
     * Creates a messaging-style notification showing recent commentary
     */
    fun createCommentaryNotification(
        context: Context,
        match: CricketMatch,
        commentaryItems: List<CommentaryItem>
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val pendingIntent = createPendingIntent(context, match.id)
        
        val messagingStyle = NotificationCompat.MessagingStyle(
            androidx.core.app.Person.Builder()
                .setName("Commentary")
                .build()
        ).setConversationTitle("${team1?.name} vs ${team2?.name}")
        
        commentaryItems.takeLast(5).forEach { item ->
            val person = androidx.core.app.Person.Builder()
                .setName(item.source)
                .build()
            messagingStyle.addMessage(item.text, item.timestamp, person)
        }
        
        return NotificationCompat.Builder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(messagingStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setColor(RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    data class CommentaryItem(
        val text: String,
        val source: String = "Commentary",
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private fun createPendingIntent(context: Context, matchId: String): PendingIntent {
        try {
            val intent = Intent(context, BouncerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("match_id", matchId)
            }
            return PendingIntent.getActivity(
                context,
                matchId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
