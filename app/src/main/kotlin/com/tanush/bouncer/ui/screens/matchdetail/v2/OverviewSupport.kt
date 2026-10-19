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
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.models.WinProbFactor
import com.tanush.bouncer.ui.components.PredictionFactor
import kotlin.math.roundToInt
import com.tanush.bouncer.ui.components.MatchPredictionInfo
import com.tanush.bouncer.ui.components.PredictionTrend
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

// Shared, pure helpers + player cards for OverviewTabV2 sections.

/**
 * Bowler economy, preferring the scorecard's own figure.
 *
 * Over notation is not a decimal: "19.5" is 19 overs and 5 balls (119 balls), not 19.5 overs.
 * Dividing by the decimal reported 5.13 where the true figure was 5.04.
 */
/**
 * A required run rate worth showing, or null.
 *
 * The server sends `"0"` whenever a required rate does not exist — a first innings, a Test that is
 * not a fourth-innings chase, a completed chase — and `"RRR: 0"` is not a true statement about any
 * of them. Null is: there is no target to chase at this rate.
 */
internal fun meaningfulRequiredRunRate(raw: String?): String? =
    raw?.takeIf { it.isNotBlank() && (it.trim().toDoubleOrNull() ?: 0.0) > 0.0 }

internal fun computeEconomy(overs: String, runs: String, declared: String): String? =
    declared.takeIf { it.isNotBlank() } ?: run {
        val balls = CricketConstants.ballsFromOvers(overs) ?: return@run null
        val r = runs.toIntOrNull() ?: return@run null
        // Locale.US: cricket rates are dot-decimal by convention, and a comma-decimal locale
        // would render "8,33".
        MatchProjections.currentRunRate(r, balls)?.let { String.format(Locale.US, "%.2f", it) }
    }

internal fun predictionTrendFor(delta: Float): PredictionTrend = when {
    delta > 0.05f -> PredictionTrend.TEAM1_RISING
    delta < -0.05f -> PredictionTrend.TEAM2_RISING
    else -> PredictionTrend.STABLE
}

/** A factor of this size fills the card's bar. The model's largest groups move it about this far. */
private const val FULL_FACTOR = 0.25

/**
 * The model's own account of what moved the latest reading, as rows for the prediction card.
 *
 * [labels] maps the server's keys to words; a key with no label is one this build predates, and it
 * is dropped rather than shown under a name the app made up.
 */
internal fun predictionFactorsFor(
    factors: List<WinProbFactor>,
    labels: Map<String, String>,
): List<PredictionFactor> = factors.mapNotNull { factor ->
    val name = labels[factor.k] ?: return@mapNotNull null
    PredictionFactor(
        name = name,
        impact = (factor.d / FULL_FACTOR).toFloat().coerceIn(-1f, 1f),
        description = String.format(Locale.US, "%+d pts", (factor.d * PERCENT).roundToInt()),
    )
}

private const val PERCENT = 100

internal fun predictionInfoFor(
    match: CricketMatch,
    probabilityHistory: List<WinProbabilityPoint>,
    factorLabels: Map<String, String> = emptyMap(),
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
        predictionFactors = predictionFactorsFor(latestProb.factors, factorLabels),
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

/**
 * One batter at the crease, as a row — the caller groups them into a single container.
 *
 * This used to be a card per batter, so "At the crease" rendered as two floating tiles for what
 * is one two-line fact. Two people batting together belong in one box.
 */
@Composable
internal fun BatsmanCreaseRow(bat: ScrapedBatsman) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
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
            text = "SR ${bat.strikeRate} • 4s ${bat.fours} • 6s ${bat.sixes}",
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
    }
}

/** Scorecard-sourced batter row, used when live crease data is unavailable. */
@Composable
internal fun FallbackBatsmanRow(bat: BatsmanStats) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
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
            // Same shape as the live crease row above, so the two read as one kind of line.
            append("SR ${bat.strikeRate}")
            if (bat.fours != "0") append(" • 4s ${bat.fours}")
            if (bat.sixes != "0") append(" • 6s ${bat.sixes}")
            if (bat.dismissal.isNotBlank() && !bat.dismissal.lowercase().contains("not out")) {
                append(" • ${bat.dismissal}")
            }
        }
        Text(
            text = details,
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
    }
}
