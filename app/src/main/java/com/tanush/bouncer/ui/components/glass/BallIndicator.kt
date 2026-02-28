package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Ball event types for cricket scoring.
 */
enum class BallEvent {
    DOT,        // 0 runs
    SINGLE,     // 1 run
    DOUBLE,     // 2 runs
    THREE,      // 3 runs
    FOUR,       // 4 runs (boundary)
    SIX,        // 6 runs (over boundary)
    WICKET,     // W
    WIDE,       // Wd
    NO_BALL,    // Nb
    LEG_BYE,    // Lb
    BYE;        // B
    
    val displayText: String
        get() = when (this) {
            DOT -> "."
            SINGLE -> "1"
            DOUBLE -> "2"
            THREE -> "3"
            FOUR -> "4"
            SIX -> "6"
            WICKET -> "W"
            WIDE -> "wd"
            NO_BALL -> "nb"
            LEG_BYE -> "lb"
            BYE -> "b"
        }
}

/**
 * Ball indicator size variants.
 */
enum class BallIndicatorSize {
    COMPACT,    // Small for dense displays
    STANDARD,   // Normal size
    EXPANDED    // Large for featured displays
}

/**
 * Ball-by-ball indicator showing the result of a single delivery.
 * Based on mockup CSS for etched gold box styling.
 *
 * Color coding:
 * - Gold Pulse: Wicket
 * - Blue: Four
 * - Green: Six
 * - Grey: Dot ball (0)
 *
 * @param event The ball event to display
 * @param modifier Modifier for the indicator
 * @param size Size variant of the indicator
 */
@Composable
fun BallIndicator(
    event: BallEvent,
    modifier: Modifier = Modifier,
    size: BallIndicatorSize = BallIndicatorSize.STANDARD
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    // Wicket pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "wicket_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val (backgroundColor, borderColor, textColor, useGlow) = when (event) {
        BallEvent.DOT -> BallQuadruple(
            colors.ballDot.copy(alpha = 0.1f),
            colors.ballDot.copy(alpha = 0.2f),
            colors.textMuted,
            false
        )
        
        BallEvent.FOUR -> BallQuadruple(
            colors.statusInfo.copy(alpha = 0.15f), // India Blue
            colors.statusInfo.copy(alpha = 0.4f),
            colors.statusInfo,
            true
        )
        
        BallEvent.SIX -> BallQuadruple(
            colors.statusSuccess.copy(alpha = 0.15f), // SA Green (Green/Maroon spec)
            colors.statusSuccess.copy(alpha = 0.4f),
            colors.statusSuccess,
            true
        )
        
        BallEvent.WICKET -> BallQuadruple(
            colors.goldPrimary.copy(alpha = 0.2f), // Gold Pulse spec
            colors.goldPrimary.copy(alpha = 0.8f),
            colors.goldPrimary,
            true
        )
        
        else -> BallQuadruple(
            colors.ballRun.copy(alpha = 0.15f),
            colors.ballRun.copy(alpha = 0.3f),
            colors.textPrimary,
            false
        )
    }

    val boxSize = when (size) {
        BallIndicatorSize.COMPACT -> 28.dp
        BallIndicatorSize.STANDARD -> 36.dp // Matching w-9 (36px)
        BallIndicatorSize.EXPANDED -> 44.dp
    }
    
    val shape = if (event == BallEvent.WICKET || event == BallEvent.SIX || event == BallEvent.FOUR) 
        RoundedCornerShape(percent = 50) // Circle for boundaries/wickets
    else 
        RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(boxSize)
            .then(
                if (event == BallEvent.WICKET) Modifier.graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Pulse ring for wicket
        if (event == BallEvent.WICKET) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulseScale + (1f - pulseAlpha)
                        scaleY = pulseScale + (1f - pulseAlpha)
                        alpha = pulseAlpha
                    }
                    .border(2.dp, colors.goldPrimary, RoundedCornerShape(percent = 50))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(backgroundColor)
                .border(1.dp, borderColor, shape)
                .drawBehind {
                    if (useGlow) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    textColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                ),
                                center = Offset(this.size.width / 2, this.size.height / 2),
                                radius = this.size.width
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = event.displayText,
                style = when (size) {
                    BallIndicatorSize.COMPACT -> typography.labelSmall
                    BallIndicatorSize.STANDARD -> typography.labelMedium
                    BallIndicatorSize.EXPANDED -> typography.headingSmall
                },
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper to add 4th item to Triple
private fun <A, B, C, D> Triple<A, B, C>.addItem(item: D): BallQuadruple<A, B, C, D> {
    return BallQuadruple(first, second, third, item)
}

private data class BallQuadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Row of ball indicators showing the last N balls.
 *
 * @param events List of ball events to display
 * @param modifier Modifier for the row
 * @param size Size variant for all indicators
 * @param spacing Space between indicators
 * @param maxBalls Maximum number of balls to show (default 6)
 */
@Composable
fun BallsRow(
    events: List<BallEvent>,
    modifier: Modifier = Modifier,
    size: BallIndicatorSize = BallIndicatorSize.STANDARD,
    spacing: Dp = 4.dp,
    maxBalls: Int = 6
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        events.takeLast(maxBalls).forEach { event ->
            BallIndicator(event = event, size = size)
        }
        
        // Fill remaining slots with placeholders if needed
        val remaining = maxBalls - events.size.coerceAtMost(maxBalls)
        repeat(remaining) {
            BallPlaceholder(size = size)
        }
    }
}

/**
 * Shows all balls in a single over with over number label.
 *
 * @param overNumber The over number (e.g., 12)
 * @param balls List of ball events in this over
 * @param modifier Modifier for the component
 * @param size Size variant for indicators
 */
@Composable
fun OverBalls(
    overNumber: Int,
    balls: List<BallEvent>,
    modifier: Modifier = Modifier,
    size: BallIndicatorSize = BallIndicatorSize.STANDARD
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Over number label
        Text(
            text = "Over $overNumber",
            style = typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.width(56.dp)
        )
        
        // Ball indicators
        BallsRow(
            events = balls,
            size = size,
            maxBalls = 6
        )
        
        // Over summary (runs scored)
        val runsInOver = balls.fold(0) { acc, event ->
            acc + when (event) {
                BallEvent.DOT -> 0
                BallEvent.SINGLE -> 1
                BallEvent.DOUBLE -> 2
                BallEvent.THREE -> 3
                BallEvent.FOUR -> 4
                BallEvent.SIX -> 6
                BallEvent.WIDE -> 1
                BallEvent.NO_BALL -> 1
                else -> 0
            }
        }
        
        Text(
            text = "$runsInOver",
            style = typography.labelMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Placeholder for empty ball slot.
 */
@Composable
private fun BallPlaceholder(
    size: BallIndicatorSize = BallIndicatorSize.STANDARD
) {
    val boxSize = when (size) {
        BallIndicatorSize.COMPACT -> 24.dp
        BallIndicatorSize.STANDARD -> 32.dp
        BallIndicatorSize.EXPANDED -> 40.dp
    }
    
    val cornerRadius = when (size) {
        BallIndicatorSize.COMPACT -> 4.dp
        BallIndicatorSize.STANDARD -> 6.dp
        BallIndicatorSize.EXPANDED -> 8.dp
    }
    
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerRadius))
    )
}

/**
 * Current over display with animated ball indicators.
 * Shows real-time ball-by-ball updates during live matches.
 *
 * @param balls List of ball events in the current over
 * @param overNumber Current over number
 * @param modifier Modifier for the component
 */
@Composable
fun CurrentOverDisplay(
    balls: List<BallEvent>,
    overNumber: Int,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    GlassCardSmall(
        modifier = modifier,
        borderOpacity = 0.12f
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Over",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                Text(
                    text = "${overNumber}.${balls.size}",
                    style = typography.overCount,
                    color = colors.textPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Ball indicators
            BallsRow(
                events = balls,
                size = BallIndicatorSize.EXPANDED,
                spacing = 6.dp
            )
        }
    }
}

/**
 * Overs timeline showing multiple overs.
 *
 * @param overs Map of over number to list of ball events
 * @param modifier Modifier for the component
 */
@Composable
fun OversTimeline(
    overs: Map<Int, List<BallEvent>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        overs.entries.sortedByDescending { it.key }.forEach { (overNumber, balls) ->
            OverBalls(
                overNumber = overNumber,
                balls = balls,
                size = BallIndicatorSize.COMPACT
            )
        }
    }
}

/**
 * Calculates runs from a ball event.
 */
fun BallEvent.runs(): Int = when (this) {
    BallEvent.DOT -> 0
    BallEvent.SINGLE -> 1
    BallEvent.DOUBLE -> 2
    BallEvent.THREE -> 3
    BallEvent.FOUR -> 4
    BallEvent.SIX -> 6
    BallEvent.WICKET -> 0
    BallEvent.WIDE -> 1
    BallEvent.NO_BALL -> 1
    BallEvent.LEG_BYE -> 1
    BallEvent.BYE -> 1
}

/**
 * Checks if ball event is a boundary.
 */
fun BallEvent.isBoundary(): Boolean = this == BallEvent.FOUR || this == BallEvent.SIX

/**
 * Checks if ball event is a wicket.
 */
fun BallEvent.isWicket(): Boolean = this == BallEvent.WICKET

/**
 * Checks if ball event is an extra.
 */
fun BallEvent.isExtra(): Boolean = this in listOf(
    BallEvent.WIDE,
    BallEvent.NO_BALL,
    BallEvent.LEG_BYE,
    BallEvent.BYE
)
