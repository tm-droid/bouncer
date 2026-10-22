package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.WagonWheel
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.res.pluralStringResource

/** Octants in a wheel, as the server validates them. */
internal const val WAGON_ZONES = 8
private const val ZONE_SWEEP = 360f / WAGON_ZONES

// Compose measures an arc clockwise from three o'clock; the zones run clockwise from the top.
private const val TOP = -90f
private const val LABEL_RADIUS = 0.68f
private const val MIN_FILL = 0.06f
private const val MAX_FILL = 0.42f
private val WHEEL_SIZE = 132.dp
private val CARD_WIDTH = 164.dp

/**
 * The wheels to show for one innings: that innings' batters who scored, in the order the server
 * holds them. A wheel whose zones are not the eight the drawing expects, or do not add up to the
 * runs it claims to cover, is left out — it would be a picture of something else.
 */
internal fun wagonWheelsFor(wagon: List<WagonWheel>, inningsNumber: Int): List<WagonWheel> =
    wagon.filter { w ->
        w.innings == inningsNumber && w.runs > 0 && w.name.isNotBlank() &&
            w.zones.size == WAGON_ZONES && w.zones.all { it >= 0 } && w.zones.sum() == w.runs
    }

/** How strongly each zone is filled, 0..1 against the batter's own busiest zone. */
internal fun zoneWeights(zones: List<Int>): List<Float> {
    val most = zones.maxOrNull()?.takeIf { it > 0 } ?: return zones.map { 0f }
    return zones.map { it.toFloat() / most }
}

/** Where a zone sits, in words, for a reader who cannot see the wheel. Index 0 is zone 1. */
private val ZONE_WORDS = listOf(
    "behind the wicket, right", "square, right", "wide of straight, right", "straight, right",
    "straight, left", "wide of straight, left", "square, left", "behind the wicket, left",
)

/** One wheel per batter, side by side, left and right as the ground lies with the bowler's end at the bottom. */
@Composable
internal fun WagonWheelRow(wheels: List<WagonWheel>, modifier: Modifier = Modifier, edgePadding: Dp? = null) {
    val spacing = BouncerThemeV2.spacing
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    // The row scrolls to the screen edge on Scorecard; on Graphs its list already pads the page.
    val edge = edgePadding ?: spacing.lg
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            itemsIndexed(wheels, key = { index, wheel -> "wagon_${index}_${wheel.innings}" }) { _, wheel ->
                WagonWheelCard(wheel)
            }
        }
        Text(
            text = stringResource(R.string.scorecard_wagon_source),
            style = typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = edge, vertical = spacing.xs),
        )
    }
}

@Composable
private fun WagonWheelCard(wheel: WagonWheel) {
    val spacing = BouncerThemeV2.spacing
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val measurer = rememberTextMeasurer()
    val weights = zoneWeights(wheel.zones)
    val busiest = wheel.zones.indices.maxByOrNull { wheel.zones[it] } ?: 0
    val description = stringResource(R.string.scorecard_wagon_description, wheel.name, wheel.zones[busiest], ZONE_WORDS[busiest])
    val ink = colors.onSurface
    val line = colors.divider
    val numberStyle = typography.numericSmall

    // No card: these are same-kind things side by side, and the section header already groups them.
    run {
        Column(modifier = Modifier.width(CARD_WIDTH).padding(vertical = spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(wheel.name, style = typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // What the wheel covers — the snapshot's own figures, not the scorecard's.
            Text(
                text = stringResource(
                    R.string.scorecard_wagon_covers,
                    pluralStringResource(R.plurals.scorecard_wagon_runs, wheel.runs, wheel.runs),
                    pluralStringResource(R.plurals.scorecard_wagon_balls, wheel.balls, wheel.balls),
                ),
                style = typography.labelSmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.xs))
            Canvas(modifier = Modifier.size(WHEEL_SIZE).semantics { contentDescription = description }) {
                val radius = size.minDimension / 2f
                val centre = Offset(size.width / 2f, size.height / 2f)
                val box = Size(radius * 2f, radius * 2f)
                val corner = Offset(centre.x - radius, centre.y - radius)
                wheel.zones.forEachIndexed { index, runs ->
                    val start = TOP + index * ZONE_SWEEP
                    if (runs > 0) {
                        drawArc(
                            color = ink.copy(alpha = MIN_FILL + (MAX_FILL - MIN_FILL) * weights[index]),
                            startAngle = start, sweepAngle = ZONE_SWEEP, useCenter = true, topLeft = corner, size = box,
                        )
                        val mid = Math.toRadians((start + ZONE_SWEEP / 2f).toDouble())
                        val label = measurer.measure(runs.toString(), numberStyle)
                        drawText(
                            textLayoutResult = label,
                            color = ink,
                            topLeft = Offset(
                                centre.x + (radius * LABEL_RADIUS * cos(mid)).toFloat() - label.size.width / 2f,
                                centre.y + (radius * LABEL_RADIUS * sin(mid)).toFloat() - label.size.height / 2f,
                            ),
                        )
                    }
                    val edge = Math.toRadians(start.toDouble())
                    val rim = Offset(centre.x + (radius * cos(edge)).toFloat(), centre.y + (radius * sin(edge)).toFloat())
                    drawLine(line, centre, rim, 1.dp.toPx())
                }
                drawCircle(color = line, radius = radius, center = centre, style = Stroke(width = 1.dp.toPx()))
            }
            Spacer(Modifier.height(spacing.xs))
            Text(stringResource(R.string.scorecard_wagon_bowler_end), style = typography.labelSmall, color = colors.textSecondary)
        }
    }
}
