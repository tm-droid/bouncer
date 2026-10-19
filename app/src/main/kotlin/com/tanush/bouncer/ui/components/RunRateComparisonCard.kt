package com.tanush.bouncer.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

/**
 * Run rate data for each over
 */
data class OverRunRate(
    val over: Int,
    val team1Runs: Int,
    val team2Runs: Int?,
    val team1Wickets: Int = 0,
    val team2Wickets: Int? = null
)

/**
 * The manhattan: runs scored in each over, one bar per innings side by side.
 *
 * This used to plot each side's cumulative run rate, which is the slope of the worm drawn directly
 * above it — the same data a second time, under a glow stroke, gradient fills, a gold icon tile and
 * a tracked-caps title. Runs per over is what the worm cannot show: a 22-run over in over 15
 * barely bends a cumulative line.
 */
@Composable
fun RunRateComparisonCard(
    overData: List<OverRunRate>,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    modifier: Modifier = Modifier,
    requiredRunRate: Double? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val hasSecond = overData.any { it.team2Runs != null }
    val required = requiredRunRate?.takeIf { it > 0.0 }

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.runs_per_over_title),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                LegendSwatch(team1Color, team1Name)
                if (hasSecond) LegendSwatch(team2Color, team2Name)
                if (required != null) {
                    Text(
                        text = stringResource(R.string.runs_per_over_required, String.format(Locale.US, "%.2f", required)),
                        style = typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
            ManhattanPlate(overData, team1Color, team2Color, hasSecond, required)
            if (overData.isNotEmpty()) OverRange(overData.first().over, overData.last().over)
        }
    }
}

@Composable
private fun ManhattanPlate(
    overData: List<OverRunRate>,
    team1Color: Color,
    team2Color: Color,
    hasSecond: Boolean,
    required: Double?,
) {
    val colors = BouncerThemeV2.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .clip(BouncerThemeV2.shapes.small)
            .background(colors.surfaceVariant),
    ) {
        val grid = colors.divider
        val requiredInk = colors.textSecondary
        Canvas(modifier = Modifier.fillMaxSize().padding(BouncerThemeV2.spacing.md)) {
            val maxRuns = maxOf(
                overData.maxOfOrNull { maxOf(it.team1Runs, it.team2Runs ?: 0) } ?: 0,
                required?.toInt()?.plus(1) ?: 0,
                MIN_SCALE,
            )
            for (i in 0..GRID_LINES) {
                val gy = size.height * i / GRID_LINES
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            drawBars(overData, maxRuns, hasSecond, team1Color, team2Color)
            if (required != null) {
                val ry = size.height - size.height * (required / maxRuns).toFloat()
                drawLine(
                    color = requiredInk,
                    start = Offset(0f, ry),
                    end = Offset(size.width, ry),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
        }
    }
}

@Composable
private fun OverRange(first: Int, last: Int) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.winprob_over_label, first.toString()),
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.winprob_over_label, last.toString()),
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
    }
}

private fun DrawScope.drawBars(
    overData: List<OverRunRate>,
    maxRuns: Int,
    hasSecond: Boolean,
    team1Color: Color,
    team2Color: Color,
) {
    val slot = size.width / overData.size.coerceAtLeast(1)
    val bars = if (hasSecond) 2 else 1
    val barWidth = (slot * BAR_FILL / bars).coerceAtLeast(1f)
    overData.forEachIndexed { index, over ->
        val left = slot * index + slot * (1 - BAR_FILL) / 2
        drawBar(left, barWidth, over.team1Runs, maxRuns, team1Color)
        over.team2Runs?.let { drawBar(left + barWidth, barWidth, it, maxRuns, team2Color) }
    }
}

private fun DrawScope.drawBar(
    left: Float,
    width: Float,
    runs: Int,
    maxRuns: Int,
    color: Color,
) {
    if (runs <= 0) return
    val barHeight = size.height * runs / maxRuns
    drawRect(color = color, topLeft = Offset(left, size.height - barHeight), size = Size(width, barHeight))
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = BouncerThemeV2.typography.labelMedium,
            color = BouncerThemeV2.colors.onSurface,
        )
    }
}

private val CHART_HEIGHT = 160.dp
private const val GRID_LINES = 4
private const val BAR_FILL = 0.8f
/** A run-starved innings still scales to a full over of singles, so one-run bars are not full height. */
private const val MIN_SCALE = 6
