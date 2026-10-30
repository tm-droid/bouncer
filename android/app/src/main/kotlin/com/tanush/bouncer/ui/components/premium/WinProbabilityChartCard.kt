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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.displayOvers

/**
 * How the win probability moved over the match.
 *
 * The hero directly above already states the current split, so this card is only the history: one
 * line, the area above 50% washed in the first side's colour and below it in the second's, with the
 * sides named at the ends of the axis they lead toward. It used to repeat the hero's percentages and
 * split bar, and wear a glow stroke, a black plate (a grey slab in light mode) and tracked caps.
 */
@Composable
fun WinProbabilityChartCard(
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    probabilityHistory: List<WinProbabilityPoint>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val progress = remember { Animatable(0f) }
    LaunchedEffect(probabilityHistory) {
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.common_win_probability),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .clip(BouncerThemeV2.shapes.small)
                    .background(colors.surfaceVariant),
            ) {
                val midline = colors.textSecondary
                val line = colors.onSurface
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm, vertical = spacing.md)) {
                    drawMidline(midline)
                    if (probabilityHistory.size >= 2) {
                        drawProbability(probabilityHistory, team1Color, team2Color, line, progress.value)
                    }
                }
                AxisLabel(team1Name, team1Color, Modifier.align(Alignment.TopStart))
                AxisLabel("50%", colors.textSecondary, Modifier.align(Alignment.CenterEnd))
                AxisLabel(team2Name, team2Color, Modifier.align(Alignment.BottomStart))
            }
            OverAxis(probabilityHistory)
            KeyMomentsList(probabilityHistory.filter { it.keyEvent != null }.takeLast(KEY_MOMENTS))
        }
    }
}

@Composable
private fun AxisLabel(text: String, color: Color, modifier: Modifier) {
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun OverAxis(points: List<WinProbabilityPoint>) {
    if (points.size < 2) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.winprob_over_label, displayOvers(points.first().over.toString())),
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.winprob_over_label, displayOvers(points.last().over.toString())),
            style = typography.numericSmall,
            color = colors.textSecondary,
        )
    }
}

private fun DrawScope.drawMidline(color: Color) {
    drawLine(
        color = color.copy(alpha = 0.5f),
        start = Offset(0f, size.height / 2),
        end = Offset(size.width, size.height / 2),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
    )
}

private fun DrawScope.drawProbability(
    points: List<WinProbabilityPoint>,
    team1Color: Color,
    team2Color: Color,
    lineColor: Color,
    progress: Float,
) {
    val first = points.first().over
    val span = (points.last().over - first).coerceAtLeast(0.1f)
    fun x(over: Float) = ((over - first) / span) * size.width * progress
    fun y(probability: Float) = size.height - (probability.coerceIn(0f, 100f) / 100f) * size.height

    val line = Path()
    points.forEachIndexed { index, p ->
        if (index == 0) line.moveTo(x(p.over), y(p.team1Probability)) else line.lineTo(x(p.over), y(p.team1Probability))
    }
    // The area between the line and 50%, closed along the midline.
    val area = Path().apply {
        addPath(line)
        lineTo(x(points.last().over), size.height / 2)
        lineTo(x(first), size.height / 2)
        close()
    }
    clipRect(bottom = size.height / 2) { drawPath(area, team1Color.copy(alpha = FILL_ALPHA)) }
    clipRect(top = size.height / 2) { drawPath(area, team2Color.copy(alpha = FILL_ALPHA)) }
    drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

    points.filter { it.keyEvent != null }.forEach { p ->
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x(p.over), y(p.team1Probability)))
    }
}

@Composable
private fun KeyMomentsList(keyEvents: List<WinProbabilityPoint>) {
    if (keyEvents.isEmpty()) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Spacer(Modifier.height(spacing.md))
    Text(text = stringResource(R.string.overview_key_moments), style = typography.titleSmall, color = colors.onSurface)
    Spacer(Modifier.height(spacing.xs))
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        keyEvents.forEach { event ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.onSurface))
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = displayOvers(event.over.toString()),
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.width(OVER_COLUMN),
                )
                Text(
                    text = event.keyEvent.orEmpty(),
                    style = typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val CHART_HEIGHT = 180.dp
private val OVER_COLUMN = 44.dp
private const val FILL_ALPHA = 0.22f
private const val KEY_MOMENTS = 3
