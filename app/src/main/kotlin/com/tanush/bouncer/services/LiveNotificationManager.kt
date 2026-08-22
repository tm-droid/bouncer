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
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.getTeamNotificationColor
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

        const val GROUP_KEY_MATCHES = "com.tanush.bouncer.LIVE_MATCHES"

        const val ACTION_TRACK_MATCH = "com.tanush.bouncer.ACTION_TRACK_MATCH"
        const val ACTION_MUTE_MATCH = "com.tanush.bouncer.ACTION_MUTE_MATCH"
        const val ACTION_SHARE_SCORE = "com.tanush.bouncer.ACTION_SHARE_SCORE"
        const val EXTRA_MATCH_ID = "match_id"

        // Event Colors (matching iOS Live Activity colors)
        const val COLOR_WICKET = 0xFFFF3B30.toInt()
        const val COLOR_SIX = 0xFF8B5CF6.toInt()
        const val COLOR_FOUR = 0xFFFACC15.toInt()
        const val COLOR_MILESTONE = 0xFFFFD700.toInt()
        const val COLOR_WIN_PROB_HIGH = 0xFF22C55E.toInt()
        const val COLOR_WIN_PROB_LOW = 0xFFEF4444.toInt()

        const val SCORECARD_WIDTH = 720
        const val SCORECARD_HEIGHT = 360
    }

    enum class MatchPhase {
        POWERPLAY,
        MIDDLE_OVERS,
        DEATH_OVERS,
        STRATEGIC_TIMEOUT,
        INNINGS_BREAK
    }

    private val activeLiveMatchIds = mutableSetOf<Int>()

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    // Wraps a block, captures any exception to Sentry, then rethrows so callers
    // see identical behavior to the original try/catch + captureException + throw.
    private inline fun <T> reportingErrors(block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun breadcrumb(
        message: String,
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any?> = emptyMap()
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.notification"
            this.message = message
            this.level = level
            data.forEach { (k, v) -> setData(k, v ?: "UNKNOWN") }
        })
    }

    // Stable, non-zero, bounded suffix for per-match notification IDs.
    private fun matchIdSuffix(matchId: String): Int =
        matchId.hashCode().let { if (it == 0) 1 else kotlin.math.abs(it) % 1000 }

    private fun createNotificationChannels() = reportingErrors {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@reportingErrors

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val liveChannel = NotificationChannel(
            CHANNEL_ID_LIVE,
            "Live Match Updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing live match score updates"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alertsChannel = highImportanceChannel(
            CHANNEL_ID_ALERTS,
            "Match Alerts",
            "Wickets, boundaries, and milestone alerts",
            longArrayOf(0, 200, 100, 200)
        )

        val wicketsChannel = highImportanceChannel(
            CHANNEL_ID_WICKETS,
            "Wicket Alerts",
            "Wicket fall notifications with distinctive sound",
            longArrayOf(0, 300, 150, 300)
        )

        val boundariesChannel = highImportanceChannel(
            CHANNEL_ID_BOUNDARIES,
            "Boundary Alerts",
            "Four and Six notifications with distinctive sound",
            longArrayOf(0, 150, 100, 150)
        )

        val milestonesChannel = highImportanceChannel(
            CHANNEL_ID_MILESTONES,
            "Milestone Alerts",
            "Century, half-century, and achievement notifications",
            longArrayOf(0, 200, 100, 200, 100, 200)
        )

        listOf(liveChannel, alertsChannel, wicketsChannel, boundariesChannel, milestonesChannel)
            .forEach(systemManager::createNotificationChannel)
    }

    private fun highImportanceChannel(
        id: String,
        name: String,
        desc: String,
        vibration: LongArray
    ): NotificationChannel = NotificationChannel(
        id, name, NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = desc
        enableVibration(true)
        vibrationPattern = vibration
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }

    /**
     * Shows/updates the ongoing live match notification
     */
    fun showLiveMatchNotification(
        match: CricketMatch,
        winProbability: Float? = null,
        lastEvent: MatchEvent? = null,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null,
        recentBalls: List<String>? = null,
        matchPhase: MatchPhase? = null,
        requiredRate: String? = null
    ) {
        // Respect per-match notification preferences
        val perMatchPrefs = context.getSharedPreferences("bouncer_match_notif_prefs", Context.MODE_PRIVATE)
        if (!perMatchPrefs.getBoolean("notif_${match.id}_scoreUpdates", true)) return
        val mutedMatches = context.getSharedPreferences("notification_state", Context.MODE_PRIVATE)
            .getStringSet("muted_matches", emptySet()) ?: emptySet()
        if (match.id in mutedMatches) return

        breadcrumb(
            "Showing live match notification",
            data = mapOf(
                "matchId" to match.id,
                "hasWinProbability" to (winProbability != null),
                "hasLastEvent" to (lastEvent != null),
                "matchPhase" to (matchPhase?.name ?: "UNKNOWN")
            )
        )

        val notificationId = NOTIFICATION_ID_LIVE + matchIdSuffix(match.id)

        val pendingIntent = matchActivityPendingIntent(match.id, notificationId)

        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)

        val priority = matchPhase?.let { phase ->
            getNotificationPriority(phase, lastEvent?.type ?: EventType.OTHER)
        } ?: NotificationCompat.PRIORITY_LOW

        val teamAccentColor = getTeamNotificationColor(team1?.name ?: "")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"}")
            .setContentText(buildScoreText(match))
            .setSubText(match.matchType)
            .setColor(teamAccentColor)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(createCompactView(match, winProbability, teamAccentColor))
            .setCustomBigContentView(createExpandedView(match, winProbability, lastEvent, batsmen, bowler, recentBalls, requiredRate, teamAccentColor))
            .setGroup(GROUP_KEY_MATCHES)

        addQuickActions(builder, match, notificationId)

        safelyNotify(notificationId, builder, "matchId" to match.id) {
            activeLiveMatchIds.add(notificationId)
            if (activeLiveMatchIds.size > 1) {
                showGroupSummaryNotification()
            }
        }
    }

    // Builds the standard "open match in app" PendingIntent shared by live & event notifications.
    private fun matchActivityPendingIntent(matchId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, BouncerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("match_id", matchId)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Centralizes notify() + SecurityException handling so call sites stay flat.
    private inline fun safelyNotify(
        notificationId: Int,
        builder: NotificationCompat.Builder,
        vararg breadcrumbData: Pair<String, Any?>,
        onSuccess: () -> Unit = {}
    ) {
        try {
            notificationManager.notify(notificationId, builder.build())
            onSuccess()
        } catch (e: SecurityException) {
            breadcrumb(
                "Missing notification permission",
                level = SentryLevel.WARNING,
                data = breadcrumbData.toMap() + ("cause" to e.message.orEmpty())
            )
        }
    }

    private fun addQuickActions(builder: NotificationCompat.Builder, match: CricketMatch, notificationId: Int) {
        val trackIntent = Intent(context, BouncerActivity::class.java).apply {
            action = ACTION_TRACK_MATCH
            putExtra(EXTRA_MATCH_ID, match.id)
        }
        val trackPendingIntent = PendingIntent.getActivity(
            context, notificationId + 100, trackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(R.drawable.ic_launcher_foreground, "Track Match", trackPendingIntent)

        val muteIntent = Intent(context, BouncerActivity::class.java).apply {
            action = ACTION_MUTE_MATCH
            putExtra(EXTRA_MATCH_ID, match.id)
            putExtra("mute_duration_minutes", 30)
        }
        val mutePendingIntent = PendingIntent.getActivity(
            context, notificationId + 200, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(R.drawable.ic_launcher_foreground, "Mute 30 min", mutePendingIntent)

        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareScoreText(match))
            putExtra(Intent.EXTRA_SUBJECT, "${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"} - Live Score")
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context, notificationId + 300,
            Intent.createChooser(shareIntent, "Share Score"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(R.drawable.ic_launcher_foreground, "Share Score", sharePendingIntent)
    }

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

    private fun showGroupSummaryNotification() {
        breadcrumb(
            "Showing group summary notification",
            data = mapOf("activeMatches" to activeLiveMatchIds.size)
        )

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

        safelyNotify(NOTIFICATION_ID_GROUP_SUMMARY, summaryBuilder)
    }

    fun showScorecardNotification(
        match: CricketMatch,
        winProbability: Float? = null,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null
    ) {
        breadcrumb(
            "Showing scorecard notification",
            data = mapOf("matchId" to match.id)
        )

        val notificationId = NOTIFICATION_ID_SCORECARD + matchIdSuffix(match.id)
        val pendingIntent = matchActivityPendingIntent(match.id, notificationId)

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

        addQuickActions(builder, match, notificationId)

        safelyNotify(notificationId, builder, "matchId" to match.id)
    }

    private fun createScorecardBitmap(
        match: CricketMatch,
        winProbability: Float?,
        batsmen: List<BatsmanInfo>?,
        bowler: BowlerInfo?
    ): Bitmap = reportingErrors {
        val bitmap = Bitmap.createBitmap(SCORECARD_WIDTH, SCORECARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, SCORECARD_WIDTH.toFloat(), SCORECARD_HEIGHT.toFloat(),
                intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, SCORECARD_WIDTH.toFloat(), SCORECARD_HEIGHT.toFloat(), backgroundPaint)

        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)

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

        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2D4A7C.toInt() }
        canvas.drawRoundRect(RectF(20f, 60f, 350f, 160f), 12f, 12f, scorePaint)
        canvas.drawRoundRect(RectF(370f, 60f, 700f, 160f), 12f, 12f, scorePaint)

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

        winProbability?.let { prob ->
            val barY = 180f
            val barHeight = 20f
            val barWidth = SCORECARD_WIDTH - 40f

            val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
            canvas.drawRoundRect(
                RectF(20f, barY, 20f + barWidth, barY + barHeight),
                10f, 10f, barBgPaint
            )

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

            val probPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16f
            }
            canvas.drawText("${(prob * 100).toInt()}%", 25f, barY + 35f, probPaint)
            probPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${((1 - prob) * 100).toInt()}%", SCORECARD_WIDTH - 25f, barY + 35f, probPaint)
        }

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

        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MILESTONE
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(match.status, SCORECARD_WIDTH / 2f, SCORECARD_HEIGHT - 20f, statusPaint)

        bitmap
    }

    fun getNotificationPriority(matchPhase: MatchPhase, eventType: EventType): Int {
        breadcrumb(
            "Calculating notification priority",
            level = SentryLevel.DEBUG,
            data = mapOf("matchPhase" to matchPhase.name, "eventType" to eventType.name)
        )

        val eventBoost = when (eventType) {
            EventType.WICKET -> 2
            EventType.MILESTONE -> 2
            EventType.SIX -> 1
            EventType.FOUR -> 0
            EventType.OTHER -> 0
        }

        val basePriority = when (matchPhase) {
            MatchPhase.DEATH_OVERS -> NotificationCompat.PRIORITY_HIGH
            MatchPhase.POWERPLAY -> NotificationCompat.PRIORITY_HIGH
            MatchPhase.MIDDLE_OVERS -> NotificationCompat.PRIORITY_DEFAULT
            MatchPhase.STRATEGIC_TIMEOUT -> NotificationCompat.PRIORITY_LOW
            MatchPhase.INNINGS_BREAK -> NotificationCompat.PRIORITY_MIN
        }

        return minOf(basePriority + eventBoost, NotificationCompat.PRIORITY_MAX)
    }

    private fun createCompactView(match: CricketMatch, winProbability: Float?, teamAccentColor: Int): RemoteViews = reportingErrors {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)

        RemoteViews(context.packageName, R.layout.notification_live_compact).apply {
            setInt(R.id.team_accent_bar, "setBackgroundColor", teamAccentColor)
            setTextViewText(R.id.team1_name, team1?.acronym?.ifEmpty { team1.name.take(3) } ?: "T1")
            setTextViewText(R.id.team2_name, team2?.acronym?.ifEmpty { team2.name.take(3) } ?: "T2")
            setTextViewText(R.id.score_text, buildScoreText(match))
            setTextViewText(R.id.status_text, match.status)

            winProbability?.let { prob ->
                setTextViewText(R.id.win_prob_text, "${(prob * 100).toInt()}%")
                val color = if (prob > 0.5f) COLOR_WIN_PROB_HIGH else COLOR_WIN_PROB_LOW
                setTextColor(R.id.win_prob_text, color)
            }
        }
    }

    private fun createExpandedView(
        match: CricketMatch,
        winProbability: Float?,
        lastEvent: MatchEvent?,
        batsmen: List<BatsmanInfo>? = null,
        bowler: BowlerInfo? = null,
        recentBalls: List<String>? = null,
        requiredRate: String? = null,
        teamAccentColor: Int = COLOR_MILESTONE
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

            winProbability?.let { prob ->
                setTextViewText(R.id.win_prob_team1, "${(prob * 100).toInt()}%")
                setTextViewText(R.id.win_prob_team2, "${((1 - prob) * 100).toInt()}%")
                setImageViewBitmap(R.id.win_prob_bitmap, createWinProbabilityBitmap(prob, 600, 16))
            }

            // Batsmen container: visible only when at least one batsman is supplied.
            val batsmenList = batsmen?.takeIf { it.isNotEmpty() }
            if (batsmenList != null) {
                val striker = batsmenList.firstOrNull { it.isStriker } ?: batsmenList.first()
                val nonStriker = batsmenList.find { !it.isStriker }

                setTextViewText(R.id.batsman1_name, "${striker.name}*")
                setTextViewText(R.id.batsman1_stats, "${striker.runs}(${striker.balls})")
                if (nonStriker != null) {
                    setTextViewText(R.id.batsman2_name, nonStriker.name)
                    setTextViewText(R.id.batsman2_stats, "${nonStriker.runs}(${nonStriker.balls})")
                    setViewVisibility(R.id.batsman2_container, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.batsman2_container, View.GONE)
                }
                setViewVisibility(R.id.batsmen_container, View.VISIBLE)
            } else {
                setViewVisibility(R.id.batsmen_container, View.GONE)
            }

            if (bowler != null) {
                setTextViewText(R.id.bowler_name, bowler.name)
                setTextViewText(R.id.bowler_stats, "${bowler.wickets}/${bowler.runs} (${bowler.overs})")
                setViewVisibility(R.id.bowler_container, View.VISIBLE)
            } else {
                setViewVisibility(R.id.bowler_container, View.GONE)
            }

            if (!requiredRate.isNullOrBlank()) {
                setTextViewText(R.id.required_rate_text, "$requiredRate rpo")
                setViewVisibility(R.id.required_rate_container, View.VISIBLE)
            } else {
                setViewVisibility(R.id.required_rate_container, View.GONE)
            }

            val balls = recentBalls?.takeIf { it.isNotEmpty() }
            if (balls != null) {
                setTextViewText(R.id.recent_balls_text, balls.takeLast(6).joinToString("  "))
                setViewVisibility(R.id.recent_balls_container, View.VISIBLE)
            } else {
                setViewVisibility(R.id.recent_balls_container, View.GONE)
            }

            if (lastEvent != null) {
                setTextViewText(R.id.last_event_text, lastEvent.description)
                setTextColor(R.id.last_event_text, lastEvent.color)
                setInt(R.id.last_event_indicator, "setBackgroundColor", lastEvent.color)
                setViewVisibility(R.id.last_event_container, View.VISIBLE)
            } else {
                setViewVisibility(R.id.last_event_container, View.GONE)
            }
        }
    }

    data class BatsmanInfo(
        val name: String,
        val runs: Int,
        val balls: Int,
        val isStriker: Boolean = false
    )

    data class BowlerInfo(
        val name: String,
        val overs: String,
        val runs: Int,
        val wickets: Int
    )

    fun showWicketAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        dismissalType: String,
        runsScored: Int,
        matchPhase: MatchPhase? = null
    ) {
        breadcrumb(
            "Showing wicket alert",
            data = mapOf(
                "matchId" to match.id,
                "batsmanName" to batsmanName,
                "dismissalType" to dismissalType
            )
        )

        showEventAlert(
            notificationId = NOTIFICATION_ID_WICKET,
            channelId = CHANNEL_ID_WICKETS,
            title = "WICKET! 🔴",
            text = "$batsmanName $dismissalType by $bowlerName ($runsScored runs)",
            color = COLOR_WICKET,
            match = match,
            priority = priorityFor(matchPhase, EventType.WICKET)
        )
    }

    fun showSixAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String,
        matchPhase: MatchPhase? = null
    ) {
        showEventAlert(
            notificationId = NOTIFICATION_ID_BOUNDARY,
            channelId = CHANNEL_ID_BOUNDARIES,
            title = "SIX! 💜",
            text = "$batsmanName hits $bowlerName for six! $description",
            color = COLOR_SIX,
            match = match,
            priority = priorityFor(matchPhase, EventType.SIX)
        )
    }

    fun showFourAlert(
        match: CricketMatch,
        batsmanName: String,
        bowlerName: String,
        description: String,
        matchPhase: MatchPhase? = null
    ) {
        showEventAlert(
            notificationId = NOTIFICATION_ID_BOUNDARY,
            channelId = CHANNEL_ID_BOUNDARIES,
            title = "FOUR! 💛",
            text = "$batsmanName hits $bowlerName for four! $description",
            color = COLOR_FOUR,
            match = match,
            priority = priorityFor(matchPhase, EventType.FOUR)
        )
    }

    fun showMilestoneAlert(
        match: CricketMatch,
        playerName: String,
        milestone: String,
        description: String,
        matchPhase: MatchPhase? = null
    ) {
        showEventAlert(
            notificationId = NOTIFICATION_ID_MILESTONE,
            channelId = CHANNEL_ID_MILESTONES,
            title = "$milestone 🏆",
            text = "$playerName - $description",
            color = COLOR_MILESTONE,
            match = match,
            priority = priorityFor(matchPhase, EventType.MILESTONE)
        )
    }

    private fun priorityFor(matchPhase: MatchPhase?, eventType: EventType): Int =
        matchPhase?.let { getNotificationPriority(it, eventType) } ?: NotificationCompat.PRIORITY_HIGH

    private fun showEventAlert(
        notificationId: Int,
        channelId: String = CHANNEL_ID_ALERTS,
        title: String,
        text: String,
        color: Int,
        match: CricketMatch,
        priority: Int = NotificationCompat.PRIORITY_HIGH
    ) {
        val pendingIntent = matchActivityPendingIntent(match.id, notificationId)

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

        addQuickActions(builder, match, notificationId)

        safelyNotify(
            notificationId,
            builder,
            "notificationId" to notificationId,
            "title" to title
        )
    }

    fun dismissLiveNotification(matchId: String? = null) = reportingErrors {
        if (matchId != null) {
            val notificationId = NOTIFICATION_ID_LIVE + matchIdSuffix(matchId)
            notificationManager.cancel(notificationId)
            activeLiveMatchIds.remove(notificationId)

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
    }

    fun dismissAllNotifications() = reportingErrors {
        notificationManager.cancelAll()
        activeLiveMatchIds.clear()
    }

    fun getActiveLiveMatchCount(): Int = activeLiveMatchIds.size

    private fun buildScoreText(match: CricketMatch): String = reportingErrors {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        val team1Score = team1?.score ?: "-"
        val team2Score = team2?.score

        if (team2Score != null && team2Score != "-" && team2Score.isNotEmpty()) {
            "$team1Score • $team2Score"
        } else {
            team1Score
        }
    }

    fun createWinProbabilityBitmap(probability: Float, width: Int, height: Int): Bitmap = reportingErrors {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFF1A1A1A.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 8f, 8f, paint)

        val team1Width = width * probability
        paint.shader = LinearGradient(
            0f, 0f, team1Width, 0f,
            intArrayOf(0xFFB39800.toInt(), 0xFFFFD700.toInt(), 0xFFFFF4C4.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, team1Width, height.toFloat()), 8f, 8f, paint)

        bitmap
    }

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
