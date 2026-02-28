package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.tanush.bouncer.ui.theme.*

// ==========================================
// BOUNCER – Skeleton Loader System
// Structured shimmer placeholders that match
// final content grid layout. No text placeholders.
// ==========================================

/**
 * Shared shimmer brush — single animated gradient
 * reused across all skeleton composables.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.shimmer,
                easing = MaterialEasing.Standard
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.08f).copy(alpha = 0.6f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.08f).copy(alpha = 0.6f),
            Color.White.copy(alpha = 0.08f),
        ),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 400f, 0f)
    )
}

/**
 * Base skeleton shape — a rounded rectangle with shimmer.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CornerRadius.xs
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
    )
}

/**
 * Circular skeleton — for avatars and team logos.
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = CardSize.logoMedium
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

// ─── Match Card Skeleton ───────────────────

/**
 * Skeleton for a featured match card.
 * Matches the layout of the real FeaturedMatchCard:
 * [Logo]  Score vs Score  [Logo]
 *         Status text
 */
@Composable
fun MatchCardSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(Spacing.md)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Top row: series name
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(16.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonCircle(size = CardSize.logoSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonBox(Modifier.width(64.dp).height(14.dp))
                        SkeletonBox(Modifier.width(48.dp).height(20.dp))
                    }
                }

                // VS divider
                SkeletonBox(
                    modifier = Modifier.size(24.dp),
                    cornerRadius = CornerRadius.circular
                )

                // Team 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SkeletonBox(Modifier.width(64.dp).height(14.dp))
                        SkeletonBox(Modifier.width(48.dp).height(20.dp))
                    }
                    SkeletonCircle(size = CardSize.logoSmall)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Status row
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * Skeleton for a compact match card in a horizontal carousel.
 */
@Composable
fun MatchCardCompactSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 280.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(Spacing.sm)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SkeletonBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
            Spacer(modifier = Modifier.height(4.dp))

            // Team rows
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonCircle(size = 32.dp)
                        SkeletonBox(Modifier.width(56.dp).height(14.dp))
                    }
                    SkeletonBox(Modifier.width(40.dp).height(16.dp))
                }
                if (it == 0) Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.6f).height(12.dp))
        }
    }
}

// ─── Scorecard Skeleton ────────────────────

/**
 * Skeleton for a scorecard table row.
 */
@Composable
fun ScorecardRowSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(Modifier.width(120.dp).height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(4) {
                SkeletonBox(Modifier.width(32.dp).height(14.dp))
            }
        }
    }
}

/**
 * Full scorecard skeleton — header + rows.
 */
@Composable
fun ScorecardSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 6
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(Spacing.sm)
    ) {
        // Header
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(18.dp)
                .padding(bottom = Spacing.xs)
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        repeat(rowCount) {
            ScorecardRowSkeleton()
            if (it < rowCount - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ─── Stats / Insights Skeleton ─────────────

/**
 * Skeleton for a stat card (used in insights section).
 */
@Composable
fun StatCardSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(Spacing.sm)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SkeletonBox(Modifier.fillMaxWidth(0.6f).height(12.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.9f).height(32.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
        }
    }
}

// ─── Home Screen Full Skeleton ─────────────

/**
 * Full-page skeleton for HomeScreen initial load.
 * Mimics: filter chips → featured card → carousel → section.
 */
@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
    ) {
        // Filter chips row
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            repeat(4) {
                SkeletonBox(
                    modifier = Modifier.width(72.dp).height(32.dp),
                    cornerRadius = CornerRadius.pill
                )
            }
        }

        // Featured match card
        MatchCardSkeleton()

        // Section header
        SkeletonBox(Modifier.width(96.dp).height(16.dp))

        // Horizontal carousel
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            repeat(2) {
                MatchCardCompactSkeleton()
            }
        }

        // Insights section header
        SkeletonBox(Modifier.width(80.dp).height(16.dp))

        // Stat cards row
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            repeat(2) {
                StatCardSkeleton(Modifier.weight(1f))
            }
        }
    }
}
