package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

// ── Shimmer effect ──────────────────────────────────────────────────────────

/**
 * Creates a shimmer gradient [Brush] that sweeps from left to right repeatedly.
 * Apply it as a [Modifier.background] on placeholder shapes.
 */
@Composable
fun bouncerShimmerBrush(): Brush {
    val colors = BouncerDesignSystem.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslateX"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.03f),
            Color.White.copy(alpha = 0.08f),
            colors.goldPrimary.copy(alpha = 0.12f), // Premium gold-tinted highlight
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.03f)
        ),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 400f, 400f) // Angled shimmer
    )
}

/**
 * A single shimmer placeholder box with the given dimensions and corner rounding.
 */
@Composable
private fun ShimmerBox(
    width: Dp = Dp.Unspecified,
    height: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BouncerDesignSystem.dimens.radius.xs
) {
    val baseModifier = if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(
        modifier = modifier
            .then(baseModifier)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bouncerShimmerBrush())
    )
}

// ── Match card skeleton ─────────────────────────────────────────────────────

/**
 * Skeleton loading placeholder that mirrors [MatchCard] layout.
 */
@Composable
fun BouncerMatchCardSkeleton(modifier: Modifier = Modifier) {
    val dimens = BouncerDesignSystem.dimens
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = BouncerDesignSystem.shapes.matchCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.md)
        ) {
            // Header — small pill + long text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
            ) {
                ShimmerBox(
                    width = 48.dp, 
                    height = 20.dp, 
                    cornerRadius = BouncerDesignSystem.dimens.radius.pill
                )
                ShimmerBox(width = 140.dp, height = 14.dp)
            }

            Spacer(modifier = Modifier.height(dimens.spacing.md))

            // Team row 1: circle + name bar + score bar
            TeamRowSkeleton()

            Spacer(modifier = Modifier.height(dimens.spacing.sm))

            // Team row 2
            TeamRowSkeleton()

            Spacer(modifier = Modifier.height(dimens.spacing.md))

            // Status text
            ShimmerBox(width = 180.dp, height = 14.dp)
        }
    }
}

@Composable
private fun TeamRowSkeleton() {
    val dimens = BouncerDesignSystem.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo circle
        Box(
            modifier = Modifier
                .size(dimens.iconSize.jumbo)
                .clip(CircleShape)
                .background(bouncerShimmerBrush())
        )

        Spacer(modifier = Modifier.width(dimens.spacing.md))

        // Name bar
        ShimmerBox(width = 100.dp, height = 16.dp, modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.width(dimens.spacing.sm))

        // Score bar
        ShimmerBox(width = 60.dp, height = 18.dp)
    }
}

// ── News Item Skeleton ───────────────────────────────────────────────────

/**
 * Skeleton for news articles.
 */
@Composable
fun BouncerNewsSkeleton(modifier: Modifier = Modifier) {
    val dimens = BouncerDesignSystem.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(width = 200.dp, height = 16.dp)
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            ShimmerBox(width = 150.dp, height = 12.dp)
        }
        Spacer(modifier = Modifier.width(dimens.spacing.md))
        ShimmerBox(width = 80.dp, height = 60.dp, cornerRadius = dimens.radius.sm)
    }
}

// ── Stat Grid Skeleton ────────────────────────────────────────────────────

/**
 * Skeleton for a grid of player/match stats.
 */
@Composable
fun BouncerStatGridSkeleton(modifier: Modifier = Modifier) {
    val dimens = BouncerDesignSystem.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clip(BouncerDesignSystem.shapes.glassCard)
                        .background(bouncerShimmerBrush())
                )
            }
        }
    }
}

// ── Match list skeleton ─────────────────────────────────────────────────────

/**
 * Stacked skeleton cards simulating a loading match list.
 *
 * @param count Number of skeleton cards to render (default 5).
 */
@Composable
fun MatchListSkeleton(
    count: Int = 5,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.cardSpacing)
    ) {
        repeat(count) {
            BouncerMatchCardSkeleton()
        }
    }
}
