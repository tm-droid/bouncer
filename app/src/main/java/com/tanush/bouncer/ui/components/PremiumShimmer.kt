package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ══════════════════════════════════════════════════════════════════════════════
// Premium Shimmer System — Elite loading states for Bouncer
// ══════════════════════════════════════════════════════════════════════════════

// Color constants
private val SurfaceColor = Color(0xFF1A1D23)
private val ShimmerHighlight = Color(0xFF2A2D35)
private val GoldTint = Color(0x15D4AF37)
private val CardBackground = Color(0xFF141720)

/**
 * Creates an animated shimmer [Brush] with a 20° angled sweep.
 * Premium gold-tinted highlight for sophisticated loading states.
 */
@Composable
fun ShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    // 20-degree angle calculation
    val angleRad = Math.toRadians(20.0)
    val cos20 = cos(angleRad).toFloat()
    val sin20 = sin(angleRad).toFloat()

    return Brush.linearGradient(
        colors = listOf(
            SurfaceColor,
            ShimmerHighlight,
            ShimmerHighlight.copy(alpha = 0.5f),
            GoldTint,
            ShimmerHighlight.copy(alpha = 0.5f),
            ShimmerHighlight,
            SurfaceColor
        ),
        start = Offset(offset * cos20, offset * sin20),
        end = Offset((offset + 600f) * cos20, (offset + 600f) * sin20)
    )
}

/**
 * Applies shimmer effect as a background modifier.
 */
fun Modifier.shimmerEffect(cornerRadius: Dp = 8.dp): Modifier = this.then(
    Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(SurfaceColor)
)

// ══════════════════════════════════════════════════════════════════════════════
// Skeleton Components
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Match card skeleton placeholder.
 * Mirrors the structure of a live match card with team rows and scores.
 */
@Composable
fun ShimmerMatchCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header - match type
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShimmerBrush())
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Team 1 row
            TeamRowSkeleton()

            Spacer(modifier = Modifier.height(8.dp))

            // Team 2 row
            TeamRowSkeleton()

            Spacer(modifier = Modifier.height(8.dp))

            // Status bar
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShimmerBrush())
            )
        }
    }
}

@Composable
private fun TeamRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Team logo circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ShimmerBrush())
        )

        // Team name
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ShimmerBrush())
        )

        // Score
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ShimmerBrush())
        )
    }
}

/**
 * News card skeleton with image placeholder.
 */
@Composable
fun ShimmerNewsCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Image placeholder (16:9 ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(ShimmerBrush())
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShimmerBrush())
                )

                // Title line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShimmerBrush())
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Description line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ShimmerBrush())
                )

                // Description line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ShimmerBrush())
                )

                // Description line 3
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ShimmerBrush())
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Source + date row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ShimmerBrush())
                    )

                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ShimmerBrush())
                    )
                }
            }
        }
    }
}

/**
 * Scoreboard skeleton with table-like rows.
 */
@Composable
fun ShimmerScoreboard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShimmerBrush())
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShimmerBrush())
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShimmerBrush())
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShimmerBrush())
                )
            }

            // Data rows
            repeat(5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShimmerBrush())
                    )
                    Box(
                        modifier = Modifier
                            .width(35.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShimmerBrush())
                    )
                    Box(
                        modifier = Modifier
                            .width(35.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShimmerBrush())
                    )
                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShimmerBrush())
                    )
                }
            }
        }
    }
}

/**
 * Staggered list of skeleton cards with fade-in animation.
 * Each card appears with a 50ms delay.
 */
@Composable
fun SkeletonList(
    count: Int = 5,
    modifier: Modifier = Modifier,
    skeletonCard: @Composable () -> Unit = { ShimmerMatchCard() }
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(count) { index ->
            var visible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(index * 50L)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut()
            ) {
                skeletonCard()
            }
        }
    }
}

/**
 * Progressive content loader with crossfade transition.
 * Shows skeleton while loading, then crossfades to actual content.
 */
@Composable
fun ProgressiveContent(
    isLoading: Boolean,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            skeleton()
        }

        AnimatedVisibility(
            visible = !isLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut()
        ) {
            content()
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun PreviewShimmerMatchCard() {
    Box(modifier = Modifier.padding(16.dp)) {
        ShimmerMatchCard()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun PreviewShimmerNewsCard() {
    Box(modifier = Modifier.padding(16.dp)) {
        ShimmerNewsCard()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun PreviewShimmerScoreboard() {
    Box(modifier = Modifier.padding(16.dp)) {
        ShimmerScoreboard()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun PreviewSkeletonList() {
    Box(modifier = Modifier.padding(16.dp)) {
        SkeletonList(count = 3)
    }
}
