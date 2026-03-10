package com.tanush.bouncer.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.tanush.bouncer.MainActivity
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.LiveActivityData
import com.tanush.bouncer.ui.components.LiveActivityStyle
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * LiveActivityService - Foreground service for persistent live match notifications
 * 
 * Features:
 * - Creates persistent foreground notification for live matches
 * - Uses custom RemoteViews for notification layout
 * - Updates notification content when match data changes
 * - Supports multiple notification styles matching LiveActivityStyle enum
 * - Stops automatically when match ends
 */
class LiveActivityService : Service() {
    
    companion object {
        const val CHANNEL_ID = "bouncer_live_activity"
        const val NOTIFICATION_ID = 3001
        
        const val ACTION_START = "com.tanush.bouncer.START_LIVE_ACTIVITY"
        const val ACTION_UPDATE = "com.tanush.bouncer.UPDATE_LIVE_ACTIVITY"
        const val ACTION_STOP = "com.tanush.bouncer.STOP_LIVE_ACTIVITY"
        
        const val EXTRA_TEAM1_NAME = "team1_name"
        const val EXTRA_TEAM2_NAME = "team2_name"
        const val EXTRA_TEAM1_ABBR = "team1_abbr"
        const val EXTRA_TEAM2_ABBR = "team2_abbr"
        const val EXTRA_TEAM1_SCORE = "team1_score"
        const val EXTRA_TEAM2_SCORE = "team2_score"
        const val EXTRA_TEAM1_OVERS = "team1_overs"
        const val EXTRA_TEAM2_OVERS = "team2_overs"
        const val EXTRA_STATUS = "status"
        const val EXTRA_BATSMAN1 = "batsman1"
        const val EXTRA_BATSMAN2 = "batsman2"
        const val EXTRA_BOWLER = "bowler"
        const val EXTRA_RRR = "rrr"
        const val EXTRA_CRR = "crr"
        const val EXTRA_LAST_BALLS = "last_balls"
        const val EXTRA_IS_LIVE = "is_live"
        const val EXTRA_STYLE = "style"
        
        fun startLiveActivity(
            context: Context,
            matchData: LiveActivityData,
            style: LiveActivityStyle = LiveActivityStyle.EXPANDED
        ) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_START
                putExtras(matchData.toBundle(style))
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun updateLiveActivity(context: Context, matchData: LiveActivityData) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_UPDATE
                putExtras(matchData.toBundle(LiveActivityStyle.EXPANDED))
            }
            context.startService(intent)
        }
        
        fun stopLiveActivity(context: Context) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
        
        private fun LiveActivityData.toBundle(style: LiveActivityStyle) = android.os.Bundle().apply {
            putString(EXTRA_TEAM1_NAME, team1Name)
            putString(EXTRA_TEAM2_NAME, team2Name)
            putString(EXTRA_TEAM1_ABBR, team1Abbreviation)
            putString(EXTRA_TEAM2_ABBR, team2Abbreviation)
            putString(EXTRA_TEAM1_SCORE, team1Score)
            putString(EXTRA_TEAM2_SCORE, team2Score)
            putString(EXTRA_TEAM1_OVERS, team1Overs)
            putString(EXTRA_TEAM2_OVERS, team2Overs)
            putString(EXTRA_STATUS, status)
            putString(EXTRA_BATSMAN1, currentBatsman1)
            putString(EXTRA_BATSMAN2, currentBatsman2)
            putString(EXTRA_BOWLER, currentBowler)
            putString(EXTRA_RRR, requiredRunRate)
            putString(EXTRA_CRR, currentRunRate)
            putStringArray(EXTRA_LAST_BALLS, lastSixBalls.toTypedArray())
            putBoolean(EXTRA_IS_LIVE, isLive)
            putString(EXTRA_STYLE, style.name)
        }
    }
    
    private var currentStyle: LiveActivityStyle = LiveActivityStyle.EXPANDED
    private var currentMatchData: LiveActivityData? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.live_activity"
            message = "LiveActivityService created"
            level = SentryLevel.INFO
        })
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    val matchData = extractMatchData(intent)
                    val style = LiveActivityStyle.valueOf(
                        intent.getStringExtra(EXTRA_STYLE) ?: LiveActivityStyle.EXPANDED.name
                    )
                    
                    currentMatchData = matchData
                    currentStyle = style
                    
                    startForeground(NOTIFICATION_ID, buildNotification(matchData, style))
                    
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.live_activity"
                        message = "Live activity started"
                        level = SentryLevel.INFO
                        setData("style", style.name)
                        setData("isLive", matchData.isLive)
                    })
                }
                
                ACTION_UPDATE -> {
                    val matchData = extractMatchData(intent)
                    currentMatchData = matchData
                    
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(matchData, currentStyle))
                    
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.live_activity"
                        message = "Live activity updated"
                        level = SentryLevel.INFO
                    })
                }
                
                ACTION_STOP -> {
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.live_activity"
                        message = "Live activity stopped"
                        level = SentryLevel.INFO
                    })
                    
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Match Activity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent live match score updates"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(matchData: LiveActivityData, style: LiveActivityStyle): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val dismissIntent = Intent(this, LiveActivityService::class.java).apply {
            action = ACTION_STOP
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentView = createRemoteViews(matchData, style, isExpanded = false)
        val bigContentView = createRemoteViews(matchData, style, isExpanded = true)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${matchData.team1Abbreviation} vs ${matchData.team2Abbreviation}")
            .setContentText("${matchData.team1Score} • ${matchData.team2Score}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setCustomContentView(contentView)
            .setCustomBigContentView(bigContentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Open Match",
                openPendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Dismiss",
                dismissPendingIntent
            )
            .build()
    }
    
    private fun createRemoteViews(
        matchData: LiveActivityData,
        style: LiveActivityStyle,
        isExpanded: Boolean
    ): RemoteViews {
        return when {
            !isExpanded -> createCompactRemoteViews(matchData)
            style == LiveActivityStyle.COMPACT -> createCompactRemoteViews(matchData)
            style == LiveActivityStyle.EXPANDED -> createExpandedRemoteViews(matchData)
            style == LiveActivityStyle.DETAILED -> createDetailedRemoteViews(matchData)
            style == LiveActivityStyle.MINIMAL_PILL -> createMinimalRemoteViews(matchData)
            style == LiveActivityStyle.BROADCAST -> createBroadcastRemoteViews(matchData)
            else -> createExpandedRemoteViews(matchData)
        }
    }
    
    private fun createCompactRemoteViews(matchData: LiveActivityData): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_live_compact).apply {
            setTextViewText(R.id.team1_name, matchData.team1Abbreviation)
            setTextViewText(R.id.team2_name, matchData.team2Abbreviation)
            setTextViewText(R.id.score_text, "${matchData.team1Score} • ${matchData.team2Score}")
            setTextViewText(R.id.status_text, matchData.status)
            
            if (matchData.isLive) {
                setViewVisibility(R.id.live_indicator, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.live_indicator, android.view.View.GONE)
            }
        }
    }
    
    private fun createExpandedRemoteViews(matchData: LiveActivityData): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_live_expanded).apply {
            setTextViewText(R.id.team1_name, matchData.team1Name)
            setTextViewText(R.id.team2_name, matchData.team2Name)
            setTextViewText(R.id.team1_score, matchData.team1Score)
            setTextViewText(R.id.team2_score, matchData.team2Score)
            setTextViewText(R.id.team1_overs, matchData.team1Overs)
            setTextViewText(R.id.team2_overs, matchData.team2Overs)
            setTextViewText(R.id.match_status, matchData.status)
            
            if (matchData.isLive) {
                setViewVisibility(R.id.live_indicator, android.view.View.VISIBLE)
                setTextViewText(R.id.live_text, "LIVE")
            } else {
                setViewVisibility(R.id.live_indicator, android.view.View.GONE)
            }
        }
    }
    
    private fun createDetailedRemoteViews(matchData: LiveActivityData): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_live_expanded).apply {
            setTextViewText(R.id.team1_name, matchData.team1Name)
            setTextViewText(R.id.team2_name, matchData.team2Name)
            setTextViewText(R.id.team1_score, "${matchData.team1Score} ${matchData.team1Overs}")
            setTextViewText(R.id.team2_score, "${matchData.team2Score} ${matchData.team2Overs}")
            
            val detailsText = buildString {
                if (matchData.currentBatsman1.isNotEmpty()) {
                    append("${matchData.currentBatsman1}* | ")
                }
                if (matchData.currentBatsman2.isNotEmpty()) {
                    append("${matchData.currentBatsman2} | ")
                }
                if (matchData.currentBowler.isNotEmpty()) {
                    append("${matchData.currentBowler}")
                }
            }
            
            setTextViewText(R.id.match_status, detailsText.ifEmpty { matchData.status })
            
            if (matchData.isLive) {
                setViewVisibility(R.id.live_indicator, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.live_indicator, android.view.View.GONE)
            }
            
            if (matchData.requiredRunRate.isNotEmpty() || matchData.currentRunRate.isNotEmpty()) {
                val rateText = buildString {
                    if (matchData.requiredRunRate.isNotEmpty()) {
                        append("RRR: ${matchData.requiredRunRate}")
                    }
                    if (matchData.currentRunRate.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append("CRR: ${matchData.currentRunRate}")
                    }
                }
                
                if (rateText.isNotEmpty()) {
                    setTextViewText(R.id.match_info, rateText)
                    setViewVisibility(R.id.match_info, android.view.View.VISIBLE)
                } else {
                    setViewVisibility(R.id.match_info, android.view.View.GONE)
                }
            }
            
            if (matchData.lastSixBalls.isNotEmpty()) {
                val ballsText = "Last 6: ${matchData.lastSixBalls.takeLast(6).joinToString(" ")}"
                setTextViewText(R.id.recent_balls_text, ballsText)
                setViewVisibility(R.id.recent_balls_container, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.recent_balls_container, android.view.View.GONE)
            }
        }
    }
    
    private fun createMinimalRemoteViews(matchData: LiveActivityData): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_live_compact).apply {
            setTextViewText(
                R.id.score_text,
                "${matchData.team1Abbreviation} ${matchData.team1Score} vs ${matchData.team2Abbreviation} ${matchData.team2Score}"
            )
            setTextViewText(R.id.status_text, "")
            
            if (matchData.isLive) {
                setViewVisibility(R.id.live_indicator, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.live_indicator, android.view.View.GONE)
            }
        }
    }
    
    private fun createBroadcastRemoteViews(matchData: LiveActivityData): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_live_expanded).apply {
            setTextViewText(R.id.team1_name, matchData.team1Name)
            setTextViewText(R.id.team2_name, matchData.team2Name)
            setTextViewText(R.id.team1_score, matchData.team1Score)
            setTextViewText(R.id.team2_score, matchData.team2Score)
            setTextViewText(R.id.team1_overs, matchData.team1Overs)
            setTextViewText(R.id.team2_overs, matchData.team2Overs)
            setTextViewText(R.id.match_status, matchData.status)
            setTextViewText(R.id.match_info, "T20 INTERNATIONAL")
            
            if (matchData.isLive) {
                setViewVisibility(R.id.live_indicator, android.view.View.VISIBLE)
                setTextViewText(R.id.live_text, "LIVE")
            } else {
                setViewVisibility(R.id.live_indicator, android.view.View.GONE)
            }
        }
    }
    
    private fun extractMatchData(intent: Intent): LiveActivityData {
        return LiveActivityData(
            team1Name = intent.getStringExtra(EXTRA_TEAM1_NAME) ?: "",
            team2Name = intent.getStringExtra(EXTRA_TEAM2_NAME) ?: "",
            team1Abbreviation = intent.getStringExtra(EXTRA_TEAM1_ABBR) ?: "",
            team2Abbreviation = intent.getStringExtra(EXTRA_TEAM2_ABBR) ?: "",
            team1Score = intent.getStringExtra(EXTRA_TEAM1_SCORE) ?: "",
            team2Score = intent.getStringExtra(EXTRA_TEAM2_SCORE) ?: "",
            team1Overs = intent.getStringExtra(EXTRA_TEAM1_OVERS) ?: "",
            team2Overs = intent.getStringExtra(EXTRA_TEAM2_OVERS) ?: "",
            status = intent.getStringExtra(EXTRA_STATUS) ?: "",
            currentBatsman1 = intent.getStringExtra(EXTRA_BATSMAN1) ?: "",
            currentBatsman2 = intent.getStringExtra(EXTRA_BATSMAN2) ?: "",
            currentBowler = intent.getStringExtra(EXTRA_BOWLER) ?: "",
            requiredRunRate = intent.getStringExtra(EXTRA_RRR) ?: "",
            currentRunRate = intent.getStringExtra(EXTRA_CRR) ?: "",
            lastSixBalls = intent.getStringArrayExtra(EXTRA_LAST_BALLS)?.toList() ?: emptyList(),
            isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false)
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.live_activity"
            message = "LiveActivityService destroyed"
            level = SentryLevel.INFO
        })
    }
}
