package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlin.math.abs

data class MatchPredictionInfo(
    val team1Name: String,
    val team2Name: String,
    val team1WinProbability: Float,
    val team2WinProbability: Float,
    val tieDrawProbability: Float = 0f,
    val predictionFactors: List<PredictionFactor> = emptyList(),
    val predictedWinner: String = "",
    val predictionTrend: PredictionTrend = PredictionTrend.STABLE
)

data class PredictionFactor(
    val name: String,
    val impact: Float, // -1 to 1, negative favors team2, positive favors team1
    val description: String = ""
)

enum class PredictionTrend {
    TEAM1_RISING,
    TEAM2_RISING,
    STABLE
}

/**
 * The factors behind the current reading, and which way it is moving.
 *
 * The hero above already shows the percentages, so this card no longer repeats them. It used to,
 * under a brain icon and a blue "scan line" canvas that a removed animation left parked across the
 * top edge — a third rendering of one number dressed as a separate AI. Nothing to say (no factors,
 * no trend) draws nothing.
 */
@Composable
fun MatchPredictionCard(
    predictionInfo: MatchPredictionInfo,
    modifier: Modifier = Modifier
) {
    val factors = predictionInfo.predictionFactors.take(MAX_FACTORS)
    if (factors.isEmpty() && predictionInfo.predictionTrend == PredictionTrend.STABLE) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.match_key_factors),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            TrendLine(predictionInfo)
            factors.forEachIndexed { index, factor ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                PredictionFactorRow(factor, predictionInfo.team1Name, predictionInfo.team2Name)
            }
        }
    }
}

@Composable
private fun TrendLine(info: MatchPredictionInfo) {
    val rising = when (info.predictionTrend) {
        PredictionTrend.TEAM1_RISING -> info.team1Name
        PredictionTrend.TEAM2_RISING -> info.team2Name
        PredictionTrend.STABLE -> return
    }
    Spacer(Modifier.height(BouncerThemeV2.spacing.xs))
    Text(
        text = stringResource(R.string.match_trend_rising, rising),
        style = BouncerThemeV2.typography.bodyMedium,
        color = rememberTeamInk(rising),
        fontWeight = FontWeight.Medium,
    )
}

/**
 * One factor: its name, a bar growing from the centre toward the side it favours, and that side's
 * code in its colour.
 *
 * The bar is a fraction fill, not a weight pair: Compose requires every weight to be strictly
 * positive, and `impact` legitimately reaches 0f and +/-1f. Each of those left one side of the old
 * split at zero and threw `IllegalArgumentException: invalid weight` on the Overview tab (ANDROID-4K).
 */
@Composable
private fun PredictionFactorRow(
    factor: PredictionFactor,
    team1Name: String,
    team2Name: String
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val favouring = if (factor.impact >= 0) team1Name else team2Name
    val ink = rememberTeamInk(favouring)
    // Clamped, not trusted: `fillMaxWidth` rejects a fraction outside 0..1.
    val strength = abs(factor.impact).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BouncerThemeV2.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BouncerThemeV2.spacing.sm),
    ) {
        Text(
            text = factor.name,
            style = typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(modifier = Modifier.width(BAR_WIDTH).height(BAR_HEIGHT)) {
            // Left half fills toward the second side, right half toward the first.
            HalfBar(fill = if (factor.impact < 0) strength else 0f, alignEnd = true, color = ink)
            HalfBar(fill = if (factor.impact > 0) strength else 0f, alignEnd = false, color = ink)
        }
        Text(
            text = TeamAcronymManager.getAcronym(favouring),
            style = typography.labelMedium,
            color = ink,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(CODE_WIDTH),
        )
    }
}

@Composable
private fun RowScope.HalfBar(fill: Float, alignEnd: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(2.dp))
            .background(BouncerThemeV2.colors.surfaceVariant),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (fill > 0f) Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(color))
    }
}

private const val MAX_FACTORS = 4
private val BAR_WIDTH = 72.dp
private val BAR_HEIGHT = 6.dp
private val CODE_WIDTH = 36.dp

// ── Previews ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MatchPredictionCardPreview() {
    com.tanush.bouncer.ui.theme.BouncerPreviewTheme {
        MatchPredictionCard(
            predictionInfo = MatchPredictionInfo(
                team1Name = "India",
                team2Name = "Australia",
                team1WinProbability = 62f,
                team2WinProbability = 38f,
                predictionFactors = listOf(
                    PredictionFactor("Teams", 0.4f),
                    PredictionFactor("Ground", -0.2f),
                ),
                predictionTrend = PredictionTrend.TEAM1_RISING
            )
        )
    }
}
