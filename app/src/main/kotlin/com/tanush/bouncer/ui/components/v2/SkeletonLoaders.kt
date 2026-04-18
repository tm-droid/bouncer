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
