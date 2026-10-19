package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

data class LiveScoringData(
    val team1Name: String,
    val team2Name: String,
    val team1Scores: List<Int>, // Cumulative scores per over
    val team2Scores: List<Int>,
    val currentOver: Int,
    val projectedScore: Int,
    val totalOvers: Int = 20,
    /** The match's shared chart pair — see `rememberMatchChartColors`. */
    val team1Color: Color,
    val team2Color: Color,
    /** Overs in which the side still batting (the second line if there is one) lost a wicket. */
    val battingWicketOvers: List<Int> = emptyList(),
)

/**
 * The worm: cumulative runs by over for each innings, with the batting side's wickets marked.
 *
 * Flat lines on the chart plate. It used to fill under each line with a fading gradient, put a
 * radial glow behind the latest point, label its axis 0/6/10/15/20 whatever the format, and end
 * with a stats row — current over, current score, required rate — that the score header above
 * already states (and whose required rate took the first innings as the chase).
 */
@Composable
fun LiveScoringGraphCard(
    scoringData: LiveScoringData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val progress = remember { Animatable(0f) }
    LaunchedEffect(scoringData) {
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val maxOvers = maxOf(scoringData.team1Scores.size, scoringData.team2Scores.size, scoringData.totalOvers, 1)

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.live_run_progression_by_over),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                LegendItem(scoringData.team1Color, scoringData.team1Name, scoringData.team1Scores.lastOrNull())
                if (scoringData.team2Scores.isNotEmpty()) {
                    LegendItem(scoringData.team2Color, scoringData.team2Name, scoringData.team2Scores.last())
                }
            }
            Spacer(Modifier.height(spacing.md))
            WormPlate(scoringData, maxOvers, progress.value)
            WormAxis(maxOvers, showWicketKey = scoringData.battingWicketOvers.isNotEmpty())
        }
    }
}

@Composable
private fun WormPlate(scoringData: LiveScoringData, maxOvers: Int, progress: Float) {
    val colors = BouncerThemeV2.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .clip(BouncerThemeV2.shapes.small)
            .background(colors.surfaceVariant),
    ) {
        val inks = WormInks(grid = colors.divider, wicket = colors.onSurface, projection = colors.textSecondary)
        Canvas(modifier = Modifier.fillMaxSize().padding(BouncerThemeV2.spacing.md)) {
            drawWormChart(scoringData, maxOvers, progress, inks)
        }
        if (scoringData.projectedScore > 0) {
            Text(
                text = stringResource(R.string.live_projected_score, scoringData.projectedScore),
                style = BouncerThemeV2.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun WormAxis(maxOvers: Int, showWicketKey: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(text = "0", style = typography.numericSmall, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.winprob_over_label, maxOvers.toString()),
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
    }
    if (showWicketKey) {
        Spacer(Modifier.height(spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(colors.onSurface))
            Spacer(Modifier.width(spacing.xs))
            Text(
                text = stringResource(R.string.live_wicket_marker),
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

private class WormInks(val grid: Color, val wicket: Color, val projection: Color)

private fun DrawScope.drawWormChart(data: LiveScoringData, maxOvers: Int, progress: Float, inks: WormInks) {
    val maxScore = maxOf(
        data.team1Scores.maxOrNull() ?: 0,
        data.team2Scores.maxOrNull() ?: 0,
        data.projectedScore,
    ).coerceAtLeast(1)
    val x = { index: Int -> size.width * (index + 1) / maxOvers }
    val y = { score: Int -> size.height - size.height * score / maxScore }

    for (i in 0..GRID_LINES) {
        val gy = size.height * i / GRID_LINES
        drawLine(inks.grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
    }
    if (data.projectedScore > 0) {
        val py = y(data.projectedScore)
        drawLine(
            color = inks.projection,
            start = Offset(0f, py),
            end = Offset(size.width, py),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
    drawWorm(data.team1Scores, data.team1Color, progress, x, y)
    drawWorm(data.team2Scores, data.team2Color, progress, x, y)

    val batting = data.team2Scores.ifEmpty { data.team1Scores }
    val shown = (batting.size * progress).toInt()
    data.battingWicketOvers.distinct().forEach { over ->
        val index = over.coerceAtMost(batting.size - 1)
        if (index in 0 until shown) {
            drawCircle(inks.wicket, radius = WICKET_DOT.toPx(), center = Offset(x(index), y(batting[index])))
        }
    }
}

private fun DrawScope.drawWorm(
    scores: List<Int>,
    color: Color,
    progress: Float,
    x: (Int) -> Float,
    y: (Int) -> Float,
) {
    val shown = scores.take((scores.size * progress).toInt().coerceAtLeast(1))
    if (shown.size < 2) return
    val path = Path()
    shown.forEachIndexed { index, score ->
        if (index == 0) path.moveTo(x(index), y(score)) else path.lineTo(x(index), y(score))
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
private fun LegendItem(color: Color, label: String, score: Int?) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 3.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = typography.labelMedium, color = colors.onSurface)
        if (score != null) {
            Spacer(Modifier.width(4.dp))
            Text(text = score.toString(), style = typography.numericSmall, color = colors.textSecondary)
        }
    }
}

private val CHART_HEIGHT = 180.dp
private val WICKET_DOT = 3.dp
private const val GRID_LINES = 4
