package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Security
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.MatchPredictionInfo
import com.tanush.bouncer.ui.components.PredictionFactor
import com.tanush.bouncer.ui.components.PredictionTrend
import com.tanush.bouncer.ui.components.premium.MomentumEvent
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.theme.StatusInfo
import com.tanush.bouncer.ui.theme.StatusLive
import com.tanush.bouncer.ui.theme.StatusSuccess
import java.util.Locale

// Shared, pure helpers + player cards for OverviewTabV2 sections.

/**
 * Bowler economy, preferring the scorecard's own figure.
 *
 * Over notation is not a decimal: "19.5" is 19 overs and 5 balls (119 balls), not 19.5 overs.
 * Dividing by the decimal reported 5.13 where the true figure was 5.04.
 */
internal fun computeEconomy(overs: String, runs: String, declared: String): String? =
    declared.takeIf { it.isNotBlank() } ?: run {
        val balls = CricketConstants.ballsFromOvers(overs) ?: return@run null
        val r = runs.toIntOrNull() ?: return@run null
        // Locale.US: cricket rates are dot-decimal by convention, and a comma-decimal locale
        // would render "8,33".
        MatchProjections.currentRunRate(r, balls)?.let { String.format(Locale.US, "%.2f", it) }
    }

internal fun momentumEventFor(overLabel: String, ball: String): MomentumEvent? = when (ball.uppercase()) {
    "W" -> MomentumEvent(overLabel, "WICKET", "Fall of wicket", "W", Icons.Default.HeartBroken, StatusLive)
    "6" -> MomentumEvent(overLabel, "SIX", "Boundary — 6 runs", "6", Icons.Default.Flare, StatusSuccess)
    "4" -> MomentumEvent(overLabel, "FOUR", "Boundary — 4 runs", "4", Icons.Default.Flare, Gold400)
    "0" -> MomentumEvent(overLabel, "DOT BALL", "Pressure on batting side", "·", Icons.Default.Security, StatusInfo)
    else -> null
}

internal fun predictionTrendFor(delta: Float): PredictionTrend = when {
    delta > 0.05f -> PredictionTrend.TEAM1_RISING
    delta < -0.05f -> PredictionTrend.TEAM2_RISING
    else -> PredictionTrend.STABLE
}

internal fun runRatePredictionFactors(currentRunRate: String?, requiredRunRate: String?): List<PredictionFactor> {
    val crr = currentRunRate?.toFloatOrNull()
    val rrr = requiredRunRate?.toFloatOrNull()
    return if (crr != null && rrr != null && rrr > 0f) {
        listOf(PredictionFactor(
            name = "Run Rate",
            impact = ((crr / rrr) - 1f).coerceIn(-1f, 1f),
            description = "CRR $crr vs RRR $rrr",
        ))
    } else {
        emptyList()
    }
}

internal fun predictionInfoFor(
    match: CricketMatch,
    probabilityHistory: List<WinProbabilityPoint>,
    currentRunRate: String?,
    requiredRunRate: String?,
): MatchPredictionInfo {
    val latestProb = probabilityHistory.last()
    val prevProb = probabilityHistory[probabilityHistory.size - 2]
    val probDelta = latestProb.team1Probability - prevProb.team1Probability
    return MatchPredictionInfo(
        team1Name = match.teams[0].name,
        team2Name = match.teams[1].name,
        team1WinProbability = latestProb.team1Probability,
        team2WinProbability = latestProb.team2Probability,
        predictionTrend = predictionTrendFor(probDelta),
        predictedWinner = if (latestProb.team1Probability >= 0.5f) match.teams[0].name else match.teams[1].name,
        predictionFactors = runRatePredictionFactors(currentRunRate, requiredRunRate),
    )
}

internal fun commentaryPausedMessage(match: CricketMatch, isCompleted: Boolean): String = when {
    match.currentStatus.lowercase().contains("stumps") -> "Commentary paused — Stumps"
    match.currentStatus.lowercase().contains("break") -> "Commentary paused — Session break"
    match.currentStatus.lowercase().contains("rain") -> "Commentary paused — Rain delay"
    isCompleted -> "Match completed"
    else -> "Waiting for commentary..."
}

internal fun cleanCommentary(line: String): String =
    line.replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim()

@Composable
internal fun BatsmanCreaseCard(bat: ScrapedBatsman) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bat.name + if (bat.isStriker) " *" else "",
                    style = typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = if (bat.isStriker) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AnimatedScoreText(
                    text = "${bat.runs} (${bat.balls})",
                    style = typography.scoreMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "SR: ${bat.strikeRate} • 4s: ${bat.fours} • 6s: ${bat.sixes}",
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun FallbackBatsmanCard(bat: BatsmanStats) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bat.name,
                    style = typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${bat.runs} (${bat.balls})",
                    style = typography.scoreMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            val details = buildString {
                append("SR: ${bat.strikeRate}")
                if (bat.fours != "0") append(" • 4s: ${bat.fours}")
                if (bat.sixes != "0") append(" • 6s: ${bat.sixes}")
                if (bat.dismissal.isNotBlank() && !bat.dismissal.lowercase().contains("not out")) {
                    append(" • ${bat.dismissal}")
                }
            }
            Text(
                text = details,
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
