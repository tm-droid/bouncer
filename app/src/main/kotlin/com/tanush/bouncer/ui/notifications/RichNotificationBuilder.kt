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

internal fun matchPendingIntent(context: Context, matchId: String): PendingIntent {
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

internal fun scoreText(score1: String, score2: String?): String {
    try {
        return if (!score2.isNullOrEmpty() && score2 != "-") "$score1 • $score2" else score1
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

internal fun baseBuilder(
    context: Context,
    channelId: String,
    color: Int = RichNotificationBuilder.CricketNotificationChannel.COLOR_GOLD
): NotificationCompat.Builder =
    NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setColor(color)

object RichNotificationBuilder {

    object CricketNotificationChannel {

        const val GROUP_CRICKET = "cricket_notifications"

        const val CHANNEL_LIVE_SCORE = "live_score_updates"
        const val CHANNEL_WICKETS = "wicket_alerts"
        const val CHANNEL_BOUNDARIES = "boundary_alerts"
        const val CHANNEL_MILESTONES = "milestone_alerts"
        const val CHANNEL_MATCH_EVENTS = "match_events"
        const val CHANNEL_MATCH_START = "match_start"
        const val CHANNEL_MATCH_END = "match_end"
        const val CHANNEL_SUMMARY = "match_summary"

        private var notificationIdCounter = 3000
        fun getNextNotificationId(): Int = notificationIdCounter++

        const val NOTIFICATION_ID_LIVE = 1001
        const val NOTIFICATION_ID_WICKET_BASE = 2000
        const val NOTIFICATION_ID_BOUNDARY_BASE = 2100
        const val NOTIFICATION_ID_MILESTONE_BASE = 2200
        const val NOTIFICATION_ID_SUMMARY_BASE = 2300
        const val NOTIFICATION_ID_GROUP = 2999

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

        fun createNotificationChannels(context: Context) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_CRICKET, "Cricket Notifications"))

                fun channel(
                    id: String,
                    name: String,
                    importance: Int,
                    desc: String,
                    badge: Boolean = true,
                    configure: NotificationChannel.() -> Unit = {}
                ) = NotificationChannel(id, name, importance).apply {
                    description = desc
                    group = GROUP_CRICKET
                    setShowBadge(badge)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    configure()
                }

                nm.createNotificationChannels(
                    listOf(
                        channel(CHANNEL_LIVE_SCORE, "Live Score Updates", NotificationManager.IMPORTANCE_LOW,
                            "Ongoing live match score updates", badge = false) {
                            enableVibration(false)
                            setSound(null, null)
                        },
                        channel(CHANNEL_WICKETS, "Wicket Alerts", NotificationManager.IMPORTANCE_HIGH,
                            "Alerts when a wicket falls") {
                            enableVibration(true)
                            vibrationPattern = longArrayOf(0, 300, 100, 300)
                            enableLights(true)
                            lightColor = COLOR_WICKET
                        },
                        channel(CHANNEL_BOUNDARIES, "Boundary Alerts", NotificationManager.IMPORTANCE_DEFAULT,
                            "Alerts for fours and sixes") {
                            enableVibration(true)
                            vibrationPattern = longArrayOf(0, 200)
                        },
                        channel(CHANNEL_MILESTONES, "Milestone Alerts", NotificationManager.IMPORTANCE_DEFAULT,
                            "Player milestones (50s, 100s, 5-wicket hauls)") {
                            enableLights(true)
                            lightColor = COLOR_GOLD
                        },
                        channel(CHANNEL_MATCH_EVENTS, "Match Events", NotificationManager.IMPORTANCE_LOW,
                            "General match events (innings break, powerplay, etc.)", badge = false),
                        channel(CHANNEL_MATCH_START, "Match Start Alerts", NotificationManager.IMPORTANCE_HIGH,
                            "Notifications when a match is about to start") { enableVibration(true) },
                        channel(CHANNEL_MATCH_END, "Match End Alerts", NotificationManager.IMPORTANCE_DEFAULT,
                            "Notifications when a match ends"),
                        channel(CHANNEL_SUMMARY, "Match Summaries", NotificationManager.IMPORTANCE_LOW,
                            "Daily and tournament summaries", badge = false)
                    )
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

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
        val pendingIntent = matchPendingIntent(context, match.id)
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setContentTitle("${match.teams.getOrNull(0)?.name} vs ${match.teams.getOrNull(1)?.name}")
            .setContentText(scoreText(team1Score, team2Score))
            .setSubText(match.matchType)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(
                createExpandedScorecardView(
                    context, match, team1Score, team2Score, status,
                    winProbability, batsmen, bowler, recentBalls, lastEvent
                )
            )
            .build()
    }

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
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_live_expanded).apply {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        setTextViewText(R.id.team1_name, team1?.name ?: "Team 1")
        setTextViewText(R.id.team2_name, team2?.name ?: "Team 2")
        setTextViewText(R.id.team1_score, team1Score)
        setTextViewText(R.id.team2_score, team2Score ?: "-")
        setTextViewText(R.id.match_status, status)
        setTextViewText(R.id.match_info, match.matchType)
        winProbability?.let { prob ->
            setTextViewText(R.id.win_prob_team1, "${(prob * 100).toInt()}%")
            setTextViewText(R.id.win_prob_team2, "${((1 - prob) * 100).toInt()}%")
        }
    }

    fun createQuickActionNotification(
        context: Context,
        match: CricketMatch,
        title: String,
        message: String,
        channelId: String = CricketNotificationChannel.CHANNEL_MATCH_EVENTS,
        accentColor: Int = CricketNotificationChannel.COLOR_GOLD,
        actions: List<NotificationAction> = listOf()
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        val builder = baseBuilder(context, channelId, accentColor)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("${match.teams.getOrNull(0)?.name} vs ${match.teams.getOrNull(1)?.name}")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

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
                NotificationCompat.Action.Builder(action.icon, action.title, actionPendingIntent).build()
            )
        }
        return builder.build()
    }

    data class NotificationAction(
        val actionId: String,
        val title: String,
        @param:DrawableRes val icon: Int = R.drawable.ic_launcher_foreground
    )

    fun createProgressNotification(
        context: Context,
        match: CricketMatch,
        currentOvers: Float,
        totalOvers: Float,
        runsRequired: Int? = null,
        runRate: Float? = null
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        val progress = if (totalOvers > 0) ((currentOvers / totalOvers) * 100).toInt() else 0
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)

        var contentText = "Overs: ${String.format("%.1f", currentOvers)}/${"%.0f".format(totalOvers)}"
        runsRequired?.let { contentText += " • Need $it runs" }
        runRate?.let { contentText += " @ ${String.format("%.2f", it)} RR" }

        val oversRemaining = totalOvers - currentOvers
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText(contentText)
            .setSubText(match.matchType)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n${String.format("%.1f", oversRemaining)} overs remaining")
            )
            .build()
    }

    fun createGroupedMatchNotifications(
        context: Context,
        matches: List<MatchSummaryData>,
        groupTitle: String = "Live Matches",
        groupId: String = "cricket_group"
    ): List<Notification> {
        val notifications = mutableListOf<Notification>()

        val summaryNotification = baseBuilder(context, CricketNotificationChannel.CHANNEL_SUMMARY)
            .setContentTitle(groupTitle)
            .setContentText("${matches.size} live matches")
            .setGroup(groupId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle(groupTitle)
                    .setSummaryText("${matches.size} matches")
                    .also { style ->
                        matches.take(5).forEach { m ->
                            style.addLine("${m.team1} ${m.score1} vs ${m.team2} ${m.score2 ?: ""}")
                        }
                    }
            )
            .build()
        notifications.add(summaryNotification)

        matches.forEach { m ->
            notifications.add(
                baseBuilder(context, CricketNotificationChannel.CHANNEL_LIVE_SCORE)
                    .setContentTitle("${m.team1} vs ${m.team2}")
                    .setContentText(scoreText(m.score1, m.score2))
                    .setSubText(m.status)
                    .setGroup(groupId)
                    .setAutoCancel(true)
                    .build()
            )
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

    fun createSummaryNotification(
        context: Context,
        match: CricketMatch,
        result: String,
        highlights: List<HighlightItem>,
        topPerformers: List<PerformerData>? = null
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)

        val expandedContent = StringBuilder()
        expandedContent.append(result).append("\n\n")
        topPerformers?.take(3)?.forEach { p ->
            expandedContent.append("${p.emoji} ${p.name}: ${p.performance}\n")
        }
        if (highlights.isNotEmpty()) {
            expandedContent.append("\n📊 Highlights:\n")
            highlights.take(4).forEach { h -> expandedContent.append("• ${h.text}\n") }
        }

        return baseBuilder(context, CricketNotificationChannel.CHANNEL_MATCH_END)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText(result)
            .setSubText("Match Completed")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent.toString().trim()))
            .addAction(R.drawable.ic_launcher_foreground, "Share Result", pendingIntent)
            .build()
    }

    data class HighlightItem(
        val text: String,
        val type: HighlightType = HighlightType.GENERAL
    )

    enum class HighlightType { GENERAL, MILESTONE, RECORD, TURNING_POINT }

    data class PerformerData(
        val name: String,
        val performance: String,
        val emoji: String = "⭐"
    )

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
        val pendingIntent = matchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_WICKETS, CricketNotificationChannel.COLOR_WICKET)
            .setContentTitle("WICKET! 🔴")
            .setContentText("$batsmanName $dismissalType by $bowlerName")
            .setSubText("${team1?.name} vs ${team2?.name} • $teamScore")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$batsmanName $dismissalType by $bowlerName\n$runsScored runs off $ballsFaced balls\nTeam score: $teamScore")
            )
            .build()
    }

    fun createSixNotification(
        context: Context,
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String,
        totalSixes: Int
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_BOUNDARIES, CricketNotificationChannel.COLOR_SIX)
            .setContentTitle("SIX! 💜")
            .setContentText("$batsmanName hits $bowlerName for six!")
            .setSubText(description)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun createFourNotification(
        context: Context,
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_BOUNDARIES, CricketNotificationChannel.COLOR_FOUR)
            .setContentTitle("FOUR! 💚")
            .setContentText("$batsmanName hits $bowlerName for four!")
            .setSubText(description)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun createMilestoneNotification(
        context: Context,
        match: CricketMatch,
        playerName: String,
        milestone: String,
        stats: String,
        milestoneType: MilestoneType
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
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
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_MILESTONES, CricketNotificationChannel.COLOR_MILESTONE)
            .setContentTitle("$emoji $title")
            .setContentText("$playerName reaches $milestone!")
            .setSubText(stats)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$playerName reaches $milestone!\n$stats"))
            .build()
    }

    enum class MilestoneType {
        FIFTY, CENTURY, DOUBLE_CENTURY,
        FIVE_WICKETS, TEN_WICKETS, HAT_TRICK,
        FASTEST_FIFTY, FASTEST_CENTURY
    }

    fun createMatchStartingSoonNotification(
        context: Context,
        match: CricketMatch,
        minutesUntilStart: Int
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_MATCH_START)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText("Match starting in $minutesUntilStart minutes!")
            .setSubText(match.matchType)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher_foreground, "Watch", pendingIntent)
            .build()
    }

    fun createTossNotification(
        context: Context,
        match: CricketMatch,
        tossWinner: String,
        decision: String
    ): Notification {
        val pendingIntent = matchPendingIntent(context, match.id)
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setContentTitle("Toss - ${team1?.name} vs ${team2?.name}")
            .setContentText("$tossWinner won the toss and elected to $decision")
            .setSubText(match.matchType)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

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

object NotificationStyling {

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

        paint.color = team2Color
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, paint)

        paint.shader = LinearGradient(
            0f, 0f, team1Width, 0f,
            intArrayOf((team1Color and 0x00FFFFFF) or 0x99000000.toInt(), team1Color),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, team1Width, height.toFloat()), cornerRadius, cornerRadius, paint)
        return bitmap
    }

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
            paint.color = bgColor
            canvas.drawCircle(x + ballSize / 2f, ballSize / 2f, ballSize / 2f, paint)
            textPaint.color = textColor
            canvas.drawText(formatBallText(ball), x + ballSize / 2f, ballSize / 2f + textPaint.textSize / 3, textPaint)
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

    fun calculateRequiredRunRate(runsRequired: Int, oversRemaining: Float): Float {
        try {
            return if (oversRemaining > 0) runsRequired / oversRemaining else 0f
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object NotificationTemplates {

    fun quickScoreUpdate(
        context: Context,
        match: CricketMatch,
        team1Score: String,
        team2Score: String?
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setContentTitle("${team1?.name} vs ${team2?.name}")
            .setContentText(scoreText(team1Score, team2Score))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun inningsBreak(
        context: Context,
        match: CricketMatch,
        firstInningsScore: String,
        target: Int,
        breakDuration: Int = 20
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setContentTitle("Innings Break - ${team1?.name} vs ${team2?.name}")
            .setContentText("$firstInningsScore. Target: $target")
            .setSubText("Break: $breakDuration mins")
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .build()
    }

    fun drinksBreak(
        context: Context,
        match: CricketMatch,
        currentScore: String,
        overs: String
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setContentTitle("Drinks Break")
            .setContentText("${team1?.name} vs ${team2?.name}: $currentScore ($overs)")
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun strategicTimeout(
        context: Context,
        match: CricketMatch,
        teamName: String,
        currentScore: String
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setContentTitle("Strategic Timeout")
            .setContentText("$teamName $currentScore")
            .setSubText("${team1?.name} vs ${team2?.name}")
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun reviewDecision(
        context: Context,
        match: CricketMatch,
        teamName: String,
        playerName: String,
        decision: String,
        isSuccessful: Boolean
    ): Notification {
        val emoji = if (isSuccessful) "✅" else "❌"
        val resultText = if (isSuccessful) "Review Successful" else "Review Unsuccessful"
        val color = if (isSuccessful) RichNotificationBuilder.CricketNotificationChannel.COLOR_SUCCESS
                    else RichNotificationBuilder.CricketNotificationChannel.COLOR_WICKET
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS, color)
            .setContentTitle("$emoji DRS Review - $resultText")
            .setContentText("$teamName reviewed $playerName's $decision")
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .build()
    }

    fun rainDelay(
        context: Context,
        match: CricketMatch,
        currentScore: String,
        estimatedResume: String? = null
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val contentText = if (estimatedResume != null) "Rain stopped play. Resume: $estimatedResume"
                          else "Rain stopped play. $currentScore"
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS, 0xFF60A5FA.toInt())
            .setContentTitle("🌧️ Rain Delay - ${team1?.name} vs ${team2?.name}")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .build()
    }

    fun badLight(
        context: Context,
        match: CricketMatch,
        currentScore: String
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS, 0xFF6B7280.toInt())
            .setContentTitle("🌑 Bad Light - Play Suspended")
            .setContentText("${team1?.name} vs ${team2?.name}: $currentScore")
            .setOngoing(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .build()
    }

    fun superOver(
        context: Context,
        match: CricketMatch,
        team1SuperOverScore: String? = null,
        team2SuperOverScore: String? = null
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val contentText = if (team1SuperOverScore != null && team2SuperOverScore != null)
            "${team1?.name} $team1SuperOverScore vs ${team2?.name} $team2SuperOverScore"
        else "Match tied! Super Over to decide the winner"
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
            .setContentTitle("⚡ SUPER OVER!")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}

object NotificationManagerExtensions {

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

    fun NotificationManagerCompat.cancelAllCricketNotifications() {
        try {
            cancelAll()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun NotificationManagerCompat.cancelMatchNotifications(matchId: String) {
        try {
            cancel(matchId, matchId.hashCode())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object NotificationSounds {

    const val PREF_WICKET_SOUND = "notification_sound_wicket"
    const val PREF_BOUNDARY_SOUND = "notification_sound_boundary"
    const val PREF_MILESTONE_SOUND = "notification_sound_milestone"
    const val PREF_MATCH_START_SOUND = "notification_sound_match_start"

    enum class SoundType { DEFAULT, CROWD_CHEER, STADIUM_HORN, WICKET_STUMPS, SILENT }

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
                else -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun isMatchMuted(context: Context, matchId: String): Boolean {
        try {
            val prefs = context.getSharedPreferences("notification_mutes", Context.MODE_PRIVATE)
            return prefs.getStringSet("muted_matches", emptySet())?.contains(matchId) == true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setMatchMuted(context: Context, matchId: String, muted: Boolean) {
        try {
            val prefs = context.getSharedPreferences("notification_mutes", Context.MODE_PRIVATE)
            val mutedMatches = prefs.getStringSet("muted_matches", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (muted) mutedMatches.add(matchId) else mutedMatches.remove(matchId)
            prefs.edit().putStringSet("muted_matches", mutedMatches).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object NotificationPreferences {

    private const val PREFS_NAME = "notification_preferences"

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

    fun isNotificationEnabled(context: Context, preferenceKey: String): Boolean {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(preferenceKey, true)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun setNotificationEnabled(context: Context, preferenceKey: String, enabled: Boolean) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(preferenceKey, enabled).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun isQuietHoursActive(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)) return false
            val startHour = prefs.getInt(KEY_QUIET_HOURS_START, 22)
            val endHour = prefs.getInt(KEY_QUIET_HOURS_END, 7)
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return if (startHour < endHour) currentHour in startHour until endHour
                   else currentHour >= startHour || currentHour < endHour
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun onlyFavorites(context: Context): Boolean {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ONLY_FAVORITES, false)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun isMatchFavorite(context: Context, matchId: String): Boolean {
        try {
            val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            return prefs.getStringSet("favorite_matches", emptySet())?.contains(matchId) == true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun shouldShowNotification(
        context: Context,
        matchId: String,
        notificationType: String
    ): Boolean {
        val typeKey = when (notificationType.lowercase()) {
            "wicket" -> KEY_WICKET_ALERTS
            "boundary", "four", "six" -> KEY_BOUNDARY_ALERTS
            "milestone" -> KEY_MILESTONE_ALERTS
            "match_start" -> KEY_MATCH_START_ALERTS
            "match_end" -> KEY_MATCH_END_ALERTS
            "score" -> KEY_SCORE_UPDATES
            else -> return true
        }
        if (!isNotificationEnabled(context, typeKey)) return false
        if (isQuietHoursActive(context)) return false
        if (NotificationSounds.isMatchMuted(context, matchId)) return false
        if (onlyFavorites(context) && !isMatchFavorite(context, matchId)) return false
        return true
    }
}

object RichMediaNotifications {

    fun createImageNotification(
        context: Context,
        match: CricketMatch,
        title: String,
        message: String,
        imageBitmap: Bitmap
    ): Notification = baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MATCH_EVENTS)
        .setContentTitle(title)
        .setContentText(message)
        .setLargeIcon(imageBitmap)
        .setStyle(NotificationCompat.BigPictureStyle().bigPicture(imageBitmap).bigLargeIcon(null as Bitmap?))
        .setAutoCancel(true)
        .setContentIntent(matchPendingIntent(context, match.id))
        .build()

    fun createPlayerCardNotification(
        context: Context,
        match: CricketMatch,
        playerName: String,
        achievement: String,
        stats: String,
        playerPhotoBitmap: Bitmap? = null
    ): Notification {
        val builder = baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_MILESTONES, RichNotificationBuilder.CricketNotificationChannel.COLOR_MILESTONE)
            .setContentTitle("🌟 $playerName - $achievement")
            .setContentText(stats)
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        playerPhotoBitmap?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    fun createScorecardSnapshotNotification(
        context: Context,
        match: CricketMatch,
        scorecardBitmap: Bitmap
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_SUMMARY)
            .setContentTitle("Scorecard - ${team1?.name} vs ${team2?.name}")
            .setContentText("Current match status")
            .setLargeIcon(scorecardBitmap)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(scorecardBitmap).bigLargeIcon(null as Bitmap?))
            .setAutoCancel(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .build()
    }
}

object CommentaryNotifications {

    fun createCommentaryNotification(
        context: Context,
        match: CricketMatch,
        commentaryItems: List<CommentaryItem>
    ): Notification {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val messagingStyle = NotificationCompat.MessagingStyle(
            androidx.core.app.Person.Builder().setName("Commentary").build()
        ).setConversationTitle("${team1?.name} vs ${team2?.name}")

        commentaryItems.takeLast(5).forEach { item ->
            val person = androidx.core.app.Person.Builder().setName(item.source).build()
            messagingStyle.addMessage(item.text, item.timestamp, person)
        }

        return baseBuilder(context, RichNotificationBuilder.CricketNotificationChannel.CHANNEL_LIVE_SCORE)
            .setStyle(messagingStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(matchPendingIntent(context, match.id))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    data class CommentaryItem(
        val text: String,
        val source: String = "Commentary",
        val timestamp: Long = System.currentTimeMillis()
    )
}
