package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Shimmer placeholder shapes for loading states.
 */

@Composable
private fun shimmerBrush(): Brush {
    val colors = BouncerThemeV2.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    return Brush.linearGradient(
        colors = listOf(colors.shimmer, colors.shimmerHighlight, colors.shimmer),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f),
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
) {
    val shapes = BouncerThemeV2.shapes
    Box(
        modifier = modifier
            .height(height)
            .background(shimmerBrush(), shapes.small)
    )
}

private const val SKELETON_ITEM_COUNT = 6

/** Shimmer placeholder for a ball-by-ball commentary list (ball circle + two text lines). */
@Composable
fun ShimmerCommentaryList(modifier: Modifier = Modifier) {
    val spacing = BouncerThemeV2.spacing
    Column(modifier = modifier.fillMaxWidth().padding(spacing.lg)) {
        repeat(SKELETON_ITEM_COUNT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(modifier = Modifier.width(28.dp), height = 28.dp)
                Spacer(Modifier.width(spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 12.dp)
                    Spacer(Modifier.height(spacing.xs))
                    ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 14.dp)
                }
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

/** Shimmer placeholder for a player profile header (avatar + name + meta lines). */
@Composable
fun ShimmerPlayerHeader(modifier: Modifier = Modifier) {
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(modifier = Modifier.width(64.dp), height = 64.dp)
            Spacer(Modifier.width(spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f), height = 20.dp)
                Spacer(Modifier.height(spacing.sm))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 12.dp)
                Spacer(Modifier.height(spacing.xs))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f), height = 12.dp)
            }
        }
    }
}

/** Shimmer placeholder for a 2-column stat grid. */
@Composable
fun ShimmerStatGrid(modifier: Modifier = Modifier, rows: Int = 4) {
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            repeat(rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ShimmerBox(modifier = Modifier.weight(1f), height = 16.dp)
                    Spacer(Modifier.width(spacing.lg))
                    ShimmerBox(modifier = Modifier.weight(1f), height = 16.dp)
                }
                Spacer(Modifier.height(spacing.md))
            }
        }
    }
}

/** Shimmer placeholder for tabular rows (points table, scorecards). */
@Composable
fun ShimmerTableRows(modifier: Modifier = Modifier) {
    val spacing = BouncerThemeV2.spacing
    Column(modifier = modifier.fillMaxWidth().padding(spacing.lg)) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 28.dp)
        Spacer(Modifier.height(spacing.md))
        repeat(SKELETON_ITEM_COUNT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(modifier = Modifier.width(24.dp), height = 16.dp)
                Spacer(Modifier.width(spacing.md))
                ShimmerBox(modifier = Modifier.weight(1f), height = 16.dp)
                Spacer(Modifier.width(spacing.lg))
                ShimmerBox(modifier = Modifier.width(80.dp), height = 16.dp)
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

/** Shimmer placeholder for a simple list (series list, generic rows). */
@Composable
fun ShimmerListItem(modifier: Modifier = Modifier) {
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 16.dp)
            Spacer(Modifier.height(spacing.sm))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 12.dp)
        }
    }
}

/** Generic stacked-card shimmer for match detail tab loading states. */
@Composable
fun ShimmerDetailTab(modifier: Modifier = Modifier) {
    val spacing = BouncerThemeV2.spacing
    Column(modifier = modifier.fillMaxWidth().padding(spacing.lg)) {
        ShimmerMatchCard()
        Spacer(Modifier.height(spacing.lg))
        ShimmerStatGrid(rows = 3)
        Spacer(Modifier.height(spacing.lg))
        ShimmerListItem()
    }
}

/** Shimmer placeholder that looks like a match card */
@Composable
fun ShimmerMatchCard(modifier: Modifier = Modifier) {
    val spacing = BouncerThemeV2.spacing

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f), height = 12.dp)
            Spacer(Modifier.height(spacing.md))
            Row {
                ShimmerBox(modifier = Modifier.width(24.dp), height = 24.dp)
                Spacer(Modifier.width(spacing.sm))
                ShimmerBox(modifier = Modifier.weight(1f), height = 20.dp)
            }
            Spacer(Modifier.height(spacing.sm))
            Row {
                ShimmerBox(modifier = Modifier.width(24.dp), height = 24.dp)
                Spacer(Modifier.width(spacing.sm))
                ShimmerBox(modifier = Modifier.weight(1f), height = 20.dp)
            }
            Spacer(Modifier.height(spacing.sm))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f), height = 12.dp)
        }
    }
}
