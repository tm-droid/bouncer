package com.tanush.bouncer.liveactivity

import androidx.annotation.StringRes
import com.tanush.bouncer.R
import java.util.Locale

sealed class LiveMatchUpdate {
    data class ScoreUpdate(
        val team1: String,
        val team2: String,
        val currentScore: String,
        val overs: String,
        val wickets: Int,
        val runs: Int
    ) : LiveMatchUpdate()

    data class WicketUpdate(
        val batsman: String,
        val bowler: String,
        val fieldsman: String,
        val mode: String
    ) : LiveMatchUpdate()

    data class MilestoneUpdate(
        val player: String,
        val milestone: String,
        val runs: Int
    ) : LiveMatchUpdate()

    data class RunRateUpdate(
        val currentRate: Float,
        val requiredRate: Float
    ) : LiveMatchUpdate()
}

enum class LiveActivityStyle(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val batteryScore: Int,
    val visibilityScore: Int
) {
    Compact(
        R.string.live_activity_style_compact_title,
        R.string.live_activity_style_compact_subtitle,
        batteryScore = 5,
        visibilityScore = 3
    ),
    Detailed(
        R.string.live_activity_style_detailed_title,
        R.string.live_activity_style_detailed_subtitle,
        batteryScore = 3,
        visibilityScore = 5
    ),
    Minimal(
        R.string.live_activity_style_minimal_title,
        R.string.live_activity_style_minimal_subtitle,
        batteryScore = 5,
        visibilityScore = 2
    ),
    Animated(
        R.string.live_activity_style_animated_title,
        R.string.live_activity_style_animated_subtitle,
        batteryScore = 2,
        visibilityScore = 5
    ),
    Statistics(
        R.string.live_activity_style_statistics_title,
        R.string.live_activity_style_statistics_subtitle,
        batteryScore = 4,
        visibilityScore = 4
    )
}

enum class UpdateFrequency {
    EVERY_RUN,
    EVERY_OVER,
    EVERY_WICKET
}

data class MatchState(
    val matchId: String,
    val leagueName: String,
    val team1Short: String,
    val team2Short: String,
    val team1Score: String,
    val team2Score: String,
    val currentScore: String,
    val overs: String,
    val wickets: Int,
    val runs: Int,
    val currentBatter: String,
    val nonStriker: String,
    val currentBowler: String,
    val lastBall: String,
    val liveSinceEpochMs: Long,
    val lastUpdateEpochMs: Long,
    val currentRunRate: Float,
    val requiredRunRate: Float,
    val wicketsRemaining: Int,
    val winProbabilityPercent: Int?,
    val statusText: String,
    val isLive: Boolean,
    val isCompleted: Boolean
) {
    val chipTitle: String
        get() = "$team1Short $team1Score vs $team2Short $team2Score"

    val scoreLine: String
        get() = "$runs-$wickets ($overs ov)"

    val requiredRateLabel: String
        get() = "RRR ${requiredRunRate.asRate()}"

    val currentRateLabel: String
        get() = "CRR ${currentRunRate.asRate()}"

    val winProbabilityLabel: String
        get() = winProbabilityPercent?.let { "$it%" } ?: "--"

    val liveAgeMinutes: Int
        get() = ((lastUpdateEpochMs - liveSinceEpochMs).coerceAtLeast(0L) / 60_000L).toInt()
}

data class QueuedUpdate(
    val update: LiveMatchUpdate,
    val enqueueTimeMs: Long
)

/** Cricket rates are dot-decimal by convention; Locale.US keeps that on a comma-decimal device. */
fun Float.asRate(): String = String.format(Locale.US, "%.2f", this)
