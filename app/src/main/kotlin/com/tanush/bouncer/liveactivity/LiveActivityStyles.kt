package com.tanush.bouncer.liveactivity

import android.widget.RemoteViews
import com.tanush.bouncer.R

interface LiveActivityTemplate {
    val style: LiveActivityStyle
    val compactLayoutRes: Int
    val expandedLayoutRes: Int

    fun bindCompact(views: RemoteViews, state: MatchState, relativeTime: String)
    fun bindExpanded(views: RemoteViews, state: MatchState, relativeTime: String)
}

object CompactScoreStyle : LiveActivityTemplate {
    override val style: LiveActivityStyle = LiveActivityStyle.Compact
    override val compactLayoutRes: Int = R.layout.notification_live_activity_compact
    override val expandedLayoutRes: Int = R.layout.notification_live_activity_compact

    override fun bindCompact(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_matchup, "${state.team1Short} ${state.team1Score} vs ${state.team2Short} ${state.team2Score}")
        views.setTextViewText(R.id.tv_score, "${state.currentBatter} ${state.runs}*")
        views.setTextViewText(R.id.tv_status, "Live • $relativeTime")
        views.setBoolean(R.id.iv_live_dot, "setActivated", true)
    }

    override fun bindExpanded(views: RemoteViews, state: MatchState, relativeTime: String) =
        bindCompact(views, state, relativeTime)
}

object DetailedScoreStyle : LiveActivityTemplate {
    override val style: LiveActivityStyle = LiveActivityStyle.Detailed
    override val compactLayoutRes: Int = R.layout.notification_live_activity_compact
    override val expandedLayoutRes: Int = R.layout.notification_live_activity_detailed

    override fun bindCompact(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_matchup, "${state.team1Short} vs ${state.team2Short}")
        views.setTextViewText(R.id.tv_score, state.scoreLine)
        views.setTextViewText(R.id.tv_status, "Live • $relativeTime")
    }

    override fun bindExpanded(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_header, "${state.team1Short} ${state.team1Score}  •  ${state.team2Short} ${state.team2Score}")
        views.setTextViewText(R.id.tv_score, state.scoreLine)
        views.setTextViewText(R.id.tv_context, "${state.currentBatter} & ${state.nonStriker} • ${state.currentBowler}")
        views.setTextViewText(R.id.tv_last_ball, "Last ball: ${state.lastBall}")
        views.setTextViewText(R.id.tv_footer, "Need ${state.requiredRateLabel} • $relativeTime")
    }
}

object AnimatedLiveStyle : LiveActivityTemplate {
    override val style: LiveActivityStyle = LiveActivityStyle.Animated
    override val compactLayoutRes: Int = R.layout.notification_live_activity_compact
    override val expandedLayoutRes: Int = R.layout.notification_live_activity_animated

    override fun bindCompact(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_matchup, "${state.team1Short} vs ${state.team2Short}")
        views.setTextViewText(R.id.tv_score, state.scoreLine)
        views.setTextViewText(R.id.tv_status, "Live • $relativeTime")
        views.setBoolean(R.id.iv_live_dot, "setActivated", true)
    }

    override fun bindExpanded(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_header, "LIVE • ${state.leagueName}")
        views.setTextViewText(R.id.tv_score, state.scoreLine)
        views.setTextViewText(R.id.tv_context, "${state.team1Short} ${state.team1Score} vs ${state.team2Short} ${state.team2Score}")
        views.setTextViewText(R.id.tv_footer, "${state.currentBatter} batting • Updated $relativeTime")
    }
}

object StatisticsStyle : LiveActivityTemplate {
    override val style: LiveActivityStyle = LiveActivityStyle.Statistics
    override val compactLayoutRes: Int = R.layout.notification_live_activity_compact
    override val expandedLayoutRes: Int = R.layout.notification_live_activity_statistics

    override fun bindCompact(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_matchup, "${state.team1Short} vs ${state.team2Short}")
        views.setTextViewText(R.id.tv_score, state.scoreLine)
        views.setTextViewText(R.id.tv_status, "Data live • $relativeTime")
    }

    override fun bindExpanded(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_header, "${state.team1Short} ${state.team1Score} • ${state.team2Short} ${state.team2Score}")
        views.setTextViewText(R.id.tv_score, state.scoreLine)
        views.setTextViewText(R.id.tv_metric_1, state.currentRateLabel)
        views.setTextViewText(R.id.tv_metric_2, state.requiredRateLabel)
        views.setTextViewText(R.id.tv_metric_3, "Win ${state.winProbabilityLabel}")
        views.setProgressBar(R.id.pb_win_probability, 100, state.winProbabilityPercent ?: 50, false)
        views.setTextViewText(R.id.tv_footer, "Wkts left ${state.wicketsRemaining} • $relativeTime")
    }
}

object MinimalStyle : LiveActivityTemplate {
    override val style: LiveActivityStyle = LiveActivityStyle.Minimal
    override val compactLayoutRes: Int = R.layout.notification_live_activity_minimal
    override val expandedLayoutRes: Int = R.layout.notification_live_activity_minimal

    override fun bindCompact(views: RemoteViews, state: MatchState, relativeTime: String) {
        views.setTextViewText(R.id.tv_minimal_score, state.scoreLine)
        views.setTextViewText(R.id.tv_minimal_status, "Live • $relativeTime")
    }

    override fun bindExpanded(views: RemoteViews, state: MatchState, relativeTime: String) =
        bindCompact(views, state, relativeTime)
}

object LiveActivityStyleRegistry {
    private val templates: Map<LiveActivityStyle, LiveActivityTemplate> = listOf(
        CompactScoreStyle,
        DetailedScoreStyle,
        AnimatedLiveStyle,
        StatisticsStyle,
        MinimalStyle
    ).associateBy { it.style }

    fun templateFor(style: LiveActivityStyle): LiveActivityTemplate =
        templates[style] ?: CompactScoreStyle
}
