package com.tanush.bouncer.services

import android.app.Notification
import android.app.NotificationChannel
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
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
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
 * - Notification grouping for multiple live matches
 * - Big picture scorecard notifications
 * - Quick reply actions (Track, Mute, Share)
 * - Custom sounds per event type
 * - Smart notification priority based on match phase
 */
class LiveNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID_LIVE = "bouncer_live_match"
        const val CHANNEL_ID_ALERTS = "bouncer_match_alerts"
        const val CHANNEL_ID_WICKETS = "bouncer_wicket_alerts"
        const val CHANNEL_ID_BOUNDARIES = "bouncer_boundary_alerts"
        const val CHANNEL_ID_MILESTONES = "bouncer_milestone_alerts"
        
        const val NOTIFICATION_ID_LIVE = 1001
        const val NOTIFICATION_ID_WICKET = 2001
        const val NOTIFICATION_ID_BOUNDARY = 2002
        const val NOTIFICATION_ID_MILESTONE = 2003
        const val NOTIFICATION_ID_GROUP_SUMMARY = 3000
        const val NOTIFICATION_ID_SCORECARD = 4000
        
        // Notification grouping key for multiple live matches
        const val GROUP_KEY_MATCHES = "com.tanush.bouncer.LIVE_MATCHES"
        
        // Action keys for quick reply actions
        const val ACTION_TRACK_MATCH = "com.tanush.bouncer.ACTION_TRACK_MATCH"
        const val ACTION_MUTE_MATCH = "com.tanush.bouncer.ACTION_MUTE_MATCH"
        const val ACTION_SHARE_SCORE = "com.tanush.bouncer.ACTION_SHARE_SCORE"
        const val EXTRA_MATCH_ID = "match_id"
        
        // Event Colors (matching iOS Live Activity colors)
        const val COLOR_WICKET = 0xFFFF3B30.toInt()      // Red
        const val COLOR_SIX = 0xFF8B5CF6.toInt()          // Purple
        const val COLOR_FOUR = 0xFFFACC15.toInt()         // Yellow/Gold
        const val COLOR_MILESTONE = 0xFFFFD700.toInt()    // Gold
        const val COLOR_WIN_PROB_HIGH = 0xFF22C55E.toInt() // Green
        const val COLOR_WIN_PROB_LOW = 0xFFEF4444.toInt()  // Red
        
        // Scorecard bitmap dimensions
        const val SCORECARD_WIDTH = 720
        const val SCORECARD_HEIGHT = 360
    }
    
    /**
     * Match phases for smart notification timing
     */
    enum class MatchPhase {
        POWERPLAY,          // First 6 overs - high action
        MIDDLE_OVERS,       // Overs 7-15 - moderate action
        DEATH_OVERS,        // Last 4-5 overs - high action
        STRATEGIC_TIMEOUT,  // Break in play
        INNINGS_BREAK       // Between innings - low priority
    }
    
    // Track active live match notification IDs for grouping
    private val activeLiveMatchIds = mutableSetOf<Int>()
    
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
                
                // Wickets Channel with custom sound
                val wicketsChannel = NotificationChannel(
                    CHANNEL_ID_WICKETS,
                    "Wicket Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Wicket fall notifications with distinctive sound"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    // Custom wicket sound - assumes R.raw.wicket_sound exists
                    // Uncomment when raw resource is available:
                    // setSound(
                    //     Uri.parse("android.resource://${context.packageName}/${R.raw.wicket_sound}"),
                    //     AudioAttributes.Builder()
                    //         .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    //         .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    //         .build()
                    // )
                }
                
                // Boundaries Channel with custom sound
                val boundariesChannel = NotificationChannel(
                    CHANNEL_ID_BOUNDARIES,
                    "Boundary Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Four and Six notifications with distinctive sound"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 150, 100, 150)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    // Custom boundary sound - assumes R.raw.boundary_sound exists
                    // Uncomment when raw resource is available:
                    // setSound(
                    //     Uri.parse("android.resource://${context.packageName}/${R.raw.boundary_sound}"),
                    //     AudioAttributes.Builder()
                    //         .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    //         .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    //         .build()
                    // )
                }
                
                // Milestones Channel with custom sound
                val milestonesChannel = NotificationChannel(
                    CHANNEL_ID_MILESTONES,
                    "Milestone Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Century, half-century, and achievement notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 200)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    // Custom milestone sound - assumes R.raw.milestone_sound exists
                    // Uncomment when raw resource is available:
                    // setSound(
                    //     Uri.parse("android.resource://${context.packageName}/${R.raw.milestone_sound}"),
                    //     AudioAttributes.Builder()
                    //         .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    //         .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    //         .build()
                    // )
                }
            
                systemManager.createNotificationChannel(liveChannel)
                systemManager.createNotificationChannel(alertsChannel)
                systemManager.createNotificationChannel(wicketsChannel)
                systemManager.createNotificationChannel(boundariesChannel)
                systemManager.createNotificationChannel(milestonesChannel)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Shows/updates the ongoing live match notification
     * Enhanced with iOS Live Activity parity: batsmen, bowler, recent balls
     * Supports notification grouping for multiple live matches
     */
    fun showLiveMatchNotification(
        match: CricketMatch,
        winProbability: Float? = null,
        lastEvent: MatchEvent? = null,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null,
        recentBalls: List<String>? = null,
        matchPhase: MatchPhase? = null
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Showing live match notification"
            level = SentryLevel.INFO
            setData("matchId", match.id)
            setData("hasWinProbability", winProbability != null)
            setData("hasLastEvent", lastEvent != null)
            setData("matchPhase", matchPhase?.name ?: "UNKNOWN")
        })
        
        val notificationId = NOTIFICATION_ID_LIVE + match.id.hashCode().let { 
            if (it == 0) 1 else kotlin.math.abs(it) % 1000 
        }
        
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
        
        // Build the notification with custom content
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        // Calculate priority based on match phase
        val priority = matchPhase?.let { phase ->
            getNotificationPriority(phase, lastEvent?.type ?: EventType.OTHER)
        } ?: NotificationCompat.PRIORITY_LOW
        
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
            .setPriority(priority)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(createCompactView(match, winProbability))
            .setCustomBigContentView(createExpandedView(match, winProbability, lastEvent, batsmen, bowler, recentBalls))
            .setGroup(GROUP_KEY_MATCHES)
        
        // Add quick action buttons
        addQuickActions(builder, match, notificationId)
        
        try {
            notificationManager.notify(notificationId, builder.build())
            
            // Track active match for grouping
            activeLiveMatchIds.add(notificationId)
            
            // Update group summary if multiple matches are live
            if (activeLiveMatchIds.size > 1) {
                showGroupSummaryNotification()
            }
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
     * Adds quick action buttons to notification
     */
    private fun addQuickActions(builder: NotificationCompat.Builder, match: CricketMatch, notificationId: Int) {
        // Track Match action
        val trackIntent = Intent(context, BouncerActivity::class.java).apply {
            action = ACTION_TRACK_MATCH
            putExtra(EXTRA_MATCH_ID, match.id)
        }
        val trackPendingIntent = PendingIntent.getActivity(
            context, notificationId + 100, trackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "Track Match",
            trackPendingIntent
        )
        
        // Mute for 30 min action
        val muteIntent = Intent(context, BouncerActivity::class.java).apply {
            action = ACTION_MUTE_MATCH
            putExtra(EXTRA_MATCH_ID, match.id)
            putExtra("mute_duration_minutes", 30)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 200, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "Mute 30 min",
            mutePendingIntent
        )
        
        // Share Score action
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val shareText = buildShareScoreText(match)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"} - Live Score")
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context, notificationId + 300, 
            Intent.createChooser(shareIntent, "Share Score"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "Share Score",
            sharePendingIntent
        )
    }
    
    /**
     * Builds shareable score text
     */
    private fun buildShareScoreText(match: CricketMatch): String {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val team1Name = team1?.name ?: "Team 1"
        val team2Name = team2?.name ?: "Team 2"
        val team1Score = team1?.score ?: "-"
        val team2Score = team2?.score ?: "-"
        
        return buildString {
            append("🏏 $team1Name vs $team2Name\n")
            append("$team1Name: $team1Score\n")
            if (team2Score != "-" && team2Score.isNotEmpty()) {
                append("$team2Name: $team2Score\n")
            }
            append("${match.status}\n")
            append("\nFollow live on Bouncer!")
        }
    }
    
    /**
     * Shows group summary notification when multiple matches are live
     */
    private fun showGroupSummaryNotification() {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Showing group summary notification"
            level = SentryLevel.INFO
            setData("activeMatches", activeLiveMatchIds.size)
        })
        
        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Live Matches")
            .setContentText("${activeLiveMatchIds.size} matches in progress")
            .setGroup(GROUP_KEY_MATCHES)
            .setGroupSummary(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.InboxStyle()
                .setBigContentTitle("${activeLiveMatchIds.size} Live Matches")
                .setSummaryText("Bouncer Live"))
        
        try {
            notificationManager.notify(NOTIFICATION_ID_GROUP_SUMMARY, summaryBuilder.build())
        } catch (e: SecurityException) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.notification"
                message = "Missing notification permission for group summary"
                level = SentryLevel.WARNING
            })
        }
    }
    
    /**
     * Shows a scorecard notification using BigPictureStyle
     * Displays team logos, scores, and key stats in a visual format
     */
    fun showScorecardNotification(
        match: CricketMatch,
        winProbability: Float? = null,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Showing scorecard notification"
            level = SentryLevel.INFO
            setData("matchId", match.id)
        })
        
        val notificationId = NOTIFICATION_ID_SCORECARD + match.id.hashCode().let { 
            if (it == 0) 1 else kotlin.math.abs(it) % 1000 
        }
        
        val intent = Intent(context, BouncerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("match_id", match.id)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Generate scorecard bitmap
        val scorecardBitmap = createScorecardBitmap(match, winProbability, batsmen, bowler)
        
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"}")
            .setContentText(buildScoreText(match))
            .setLargeIcon(scorecardBitmap)
            .setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(scorecardBitmap)
                .bigLargeIcon(null as Bitmap?)
                .setBigContentTitle("Match Scorecard")
                .setSummaryText(match.status))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_KEY_MATCHES)
        
        // Add quick actions
        addQuickActions(builder, match, notificationId)
        
        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.notification"
                message = "Missing notification permission for scorecard"
                level = SentryLevel.WARNING
                setData("matchId", match.id)
            })
        }
    }
    
    /**
     * Creates a scorecard bitmap for BigPictureStyle notification
     * Shows team logos, scores, and key stats
     */
    private fun createScorecardBitmap(
        match: CricketMatch,
        winProbability: Float?,
        batsmen: List<BatsmanInfo>?,
        bowler: BowlerInfo?
    ): Bitmap {
        try {
            val bitmap = Bitmap.createBitmap(SCORECARD_WIDTH, SCORECARD_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Background gradient
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            backgroundPaint.shader = LinearGradient(
                0f, 0f, SCORECARD_WIDTH.toFloat(), SCORECARD_HEIGHT.toFloat(),
                intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, SCORECARD_WIDTH.toFloat(), SCORECARD_HEIGHT.toFloat(), backgroundPaint)
            
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            
            // Title text
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"}",
                SCORECARD_WIDTH / 2f, 40f, titlePaint
            )
            
            // Score boxes
            val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF2D4A7C.toInt()
            }
            
            // Team 1 score box
            canvas.drawRoundRect(
                RectF(20f, 60f, 350f, 160f),
                12f, 12f, scorePaint
            )
            
            // Team 2 score box
            canvas.drawRoundRect(
                RectF(370f, 60f, 700f, 160f),
                12f, 12f, scorePaint
            )
            
            // Team names and scores
            val teamNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB0B0B0.toInt()
                textSize = 20f
                typeface = Typeface.DEFAULT
            }
            val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 36f
                typeface = Typeface.DEFAULT_BOLD
            }
            
            canvas.drawText(team1?.acronym?.ifEmpty { team1.name.take(3) } ?: "T1", 40f, 95f, teamNamePaint)
            canvas.drawText(team1?.score ?: "-", 40f, 140f, scoreTextPaint)
            
            canvas.drawText(team2?.acronym?.ifEmpty { team2.name.take(3) } ?: "T2", 390f, 95f, teamNamePaint)
            canvas.drawText(team2?.score ?: "-", 390f, 140f, scoreTextPaint)
            
            // Win probability bar
            winProbability?.let { prob ->
                val barY = 180f
                val barHeight = 20f
                val barWidth = SCORECARD_WIDTH - 40f
                
                // Background bar
                val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
                canvas.drawRoundRect(
                    RectF(20f, barY, 20f + barWidth, barY + barHeight),
                    10f, 10f, barBgPaint
                )
                
                // Team 1 probability
                val team1BarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        20f, 0f, 20f + barWidth * prob, 0f,
                        COLOR_WIN_PROB_HIGH, 0xFF15803D.toInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(
                    RectF(20f, barY, 20f + barWidth * prob, barY + barHeight),
                    10f, 10f, team1BarPaint
                )
                
                // Probability labels
                val probPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 16f
                }
                canvas.drawText("${(prob * 100).toInt()}%", 25f, barY + 35f, probPaint)
                probPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("${((1 - prob) * 100).toInt()}%", SCORECARD_WIDTH - 25f, barY + 35f, probPaint)
            }
            
            // Batsmen info
            batsmen?.let { batsmenList ->
                val batsmenY = 250f
                val batsmenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 18f
                }
                
                batsmenList.forEachIndexed { index, batsman ->
                    val yOffset = batsmenY + (index * 30f)
                    val strikerIndicator = if (batsman.isStriker) "*" else ""
                    canvas.drawText(
                        "${batsman.name}$strikerIndicator: ${batsman.runs}(${batsman.balls})",
                        40f, yOffset, batsmenPaint
                    )
                }
            }
            
            // Bowler info
            bowler?.let { b ->
                val bowlerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF90CAF9.toInt()
                    textSize = 18f
                }
                canvas.drawText(
                    "🎳 ${b.name}: ${b.wickets}/${b.runs} (${b.overs})",
                    400f, 250f, bowlerPaint
                )
            }
            
            // Match status
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MILESTONE
                textSize = 16f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(match.status, SCORECARD_WIDTH / 2f, SCORECARD_HEIGHT - 20f, statusPaint)
            
            return bitmap
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Determines notification priority based on match phase and event type
     * Higher priority during crucial moments (death overs, powerplay)
     * Lower priority during breaks
     */
    fun getNotificationPriority(matchPhase: MatchPhase, eventType: EventType): Int {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Calculating notification priority"
            level = SentryLevel.DEBUG
            setData("matchPhase", matchPhase.name)
            setData("eventType", eventType.name)
        })
        
        // Event type priority boost
        val eventBoost = when (eventType) {
            EventType.WICKET -> 2
            EventType.SIX -> 1
            EventType.FOUR -> 0
            EventType.MILESTONE -> 2
            EventType.OTHER -> 0
        }
        
        // Base priority by match phase
        val basePriority = when (matchPhase) {
            MatchPhase.DEATH_OVERS -> NotificationCompat.PRIORITY_HIGH
            MatchPhase.POWERPLAY -> NotificationCompat.PRIORITY_HIGH
            MatchPhase.MIDDLE_OVERS -> NotificationCompat.PRIORITY_DEFAULT
            MatchPhase.STRATEGIC_TIMEOUT -> NotificationCompat.PRIORITY_LOW
            MatchPhase.INNINGS_BREAK -> NotificationCompat.PRIORITY_MIN
        }
        
        // Calculate final priority (capped at MAX)
        return minOf(basePriority + eventBoost, NotificationCompat.PRIORITY_MAX)
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
     * Shows a wicket alert notification using dedicated channel with custom sound
     */
    fun showWicketAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        dismissalType: String,
        runsScored: Int,
        matchPhase: MatchPhase? = null
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            message = "Showing wicket alert"
            level = SentryLevel.INFO
            setData("matchId", match.id)
            setData("batsmanName", batsmanName)
            setData("dismissalType", dismissalType)
        })
        
        val priority = matchPhase?.let { 
            getNotificationPriority(it, EventType.WICKET) 
        } ?: NotificationCompat.PRIORITY_HIGH
        
        showEventAlert(
            notificationId = NOTIFICATION_ID_WICKET,
            channelId = CHANNEL_ID_WICKETS,
            title = "WICKET! 🔴",
            text = "$batsmanName $dismissalType by $bowlerName ($runsScored runs)",
            color = COLOR_WICKET,
            match = match,
            priority = priority
        )
    }
    
    /**
     * Shows a boundary (six) alert notification using dedicated channel
     */
    fun showSixAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String,
        matchPhase: MatchPhase? = null
    ) {
        val priority = matchPhase?.let { 
            getNotificationPriority(it, EventType.SIX) 
        } ?: NotificationCompat.PRIORITY_HIGH
        
        showEventAlert(
            notificationId = NOTIFICATION_ID_BOUNDARY,
            channelId = CHANNEL_ID_BOUNDARIES,
            title = "SIX! 💜",
            text = "$batsmanName hits $bowlerName for six! $description",
            color = COLOR_SIX,
            match = match,
            priority = priority
        )
    }
    
    /**
     * Shows a boundary (four) alert notification using dedicated channel
     */
    fun showFourAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String,
        matchPhase: MatchPhase? = null
    ) {
        val priority = matchPhase?.let { 
            getNotificationPriority(it, EventType.FOUR) 
        } ?: NotificationCompat.PRIORITY_HIGH
        
        showEventAlert(
            notificationId = NOTIFICATION_ID_BOUNDARY,
            channelId = CHANNEL_ID_BOUNDARIES,
            title = "FOUR! 💛",
            text = "$batsmanName hits $bowlerName for four! $description",
            color = COLOR_FOUR,
            match = match,
            priority = priority
        )
    }
    
    /**
     * Shows a milestone alert notification using dedicated channel (50, 100, 5-wicket haul, etc.)
     */
    fun showMilestoneAlert(
        match: CricketMatch,
        playerName: String,
        milestone: String,
        description: String,
        matchPhase: MatchPhase? = null
    ) {
        val priority = matchPhase?.let { 
            getNotificationPriority(it, EventType.MILESTONE) 
        } ?: NotificationCompat.PRIORITY_HIGH
        
        showEventAlert(
            notificationId = NOTIFICATION_ID_MILESTONE,
            channelId = CHANNEL_ID_MILESTONES,
            title = "$milestone 🏆",
            text = "$playerName - $description",
            color = COLOR_MILESTONE,
            match = match,
            priority = priority
        )
    }
    
    /**
     * Generic event alert notification with configurable channel and priority
     */
    private fun showEventAlert(
        notificationId: Int,
        channelId: String = CHANNEL_ID_ALERTS,
        title: String,
        text: String,
        color: Int,
        match: CricketMatch,
        priority: Int = NotificationCompat.PRIORITY_HIGH
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
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setGroup(GROUP_KEY_MATCHES)
        
        // Add quick actions
        addQuickActions(builder, match, notificationId)
        
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
     * Dismisses the live match notification and updates group
     */
    fun dismissLiveNotification(matchId: String? = null) {
        try {
            if (matchId != null) {
                val notificationId = NOTIFICATION_ID_LIVE + matchId.hashCode().let { 
                    if (it == 0) 1 else kotlin.math.abs(it) % 1000 
                }
                notificationManager.cancel(notificationId)
                activeLiveMatchIds.remove(notificationId)
                
                // Update or remove group summary
                if (activeLiveMatchIds.size <= 1) {
                    notificationManager.cancel(NOTIFICATION_ID_GROUP_SUMMARY)
                } else {
                    showGroupSummaryNotification()
                }
            } else {
                notificationManager.cancel(NOTIFICATION_ID_LIVE)
                activeLiveMatchIds.clear()
                notificationManager.cancel(NOTIFICATION_ID_GROUP_SUMMARY)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Dismisses all notifications and clears tracking
     */
    fun dismissAllNotifications() {
        try {
            notificationManager.cancelAll()
            activeLiveMatchIds.clear()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Gets the number of currently active live match notifications
     */
    fun getActiveLiveMatchCount(): Int = activeLiveMatchIds.size
    
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
