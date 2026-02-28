package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.BallDelivery
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*

/**
 * BallByBallTimelineCard - Elite Technical Edition (v25.0).
 * Precision telemetry for recent deliveries. Layered, sleek, and high-fidelity.
 */
@Composable
fun BallByBallTimelineCard(
    deliveries: List<BallDelivery>,
    currentOver: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(deliveries) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    val overGroups: Map<Int, List<BallDelivery>> = deliveries.groupBy { it.overNumber.toInt() }
    val recentOvers = overGroups.entries.sortedByDescending { it.key }.take(3)

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Technical Data Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ShinyGoldDeep.copy(alpha = 0.1f), CircleShape)
                            .border(0.5.dp, GoldHairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = ShinyGoldDeep,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "LIVE TELEMETRY".uppercase(),
                            color = ShinyGoldDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = "RECENT DELIVERIES",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Overs Badge - Precision Box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", currentOver),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "OVERS",
                            color = TextTertiary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Overs breakdown - Layered Technical Blocks
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                recentOvers.forEach { entry ->
                    OverBlock(
                        overNum = entry.key,
                        balls = entry.value,
                        progress = animatedProgress.value
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Legend - Minimalist & Linear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BallLegendItem(color = DotBall, text = "DOT")
                BallLegendItem(color = SuccessGreen, text = "RUN")
                BallLegendItem(color = SuccessGreenBright, text = "4S")
                BallLegendItem(color = ShinyGoldDeep, text = "6S")
                BallLegendItem(color = ErrorRed, text = "WKT")
            }

            // AI Commentary Section - Integrated Sleek Surface
            deliveries.lastOrNull()?.commentary?.let { comm ->
                Spacer(modifier = Modifier.height(20.dp))
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 8.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(ShinyGoldDeep, CircleShape))
                            Text(
                                text = "AI MATCH ANALYSIS",
                                color = ShinyGoldDeep,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = comm,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverBlock(
    overNum: Int,
    balls: List<BallDelivery>,
    progress: Float
) {
    val overRuns = balls.sumOf { it.runs }
    val wicketsInOver = balls.count { it.isWicket }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OVER ${overNum + 1}",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = balls.firstOrNull()?.bowlerName?.uppercase() ?: "ACTIVE",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (wicketsInOver > 0) {
                    Text(text = "$wicketsInOver WKT", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "$overRuns RUNS", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Ball by ball - Technical Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            balls.take(6).forEachIndexed { index, ball ->
                val ballAlpha = (progress * 6f - index.toFloat()).coerceIn(0f, 1f)
                BallTile(
                    ball = ball,
                    alpha = ballAlpha,
                    modifier = Modifier.weight(1f)
                )
            }

            repeat((6 - balls.size).coerceAtLeast(0)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.02f)),
                )
            }
        }
    }
}

@Composable
private fun BallTile(
    ball: BallDelivery,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        ball.isWicket -> ErrorRed
        ball.isSix -> ShinyGoldDeep
        ball.isBoundary -> SuccessGreenBright
        ball.isExtra -> WarningYellow
        ball.runs == 0 -> DotBall
        else -> SuccessGreen
    }

    val displayText = when {
        ball.isWicket -> "W"
        ball.isSix -> "6"
        ball.isBoundary -> "4"
        ball.isExtra && ball.extraType != null -> ball.extraType.take(1).uppercase()
        else -> "${ball.runs}"
    }

    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor.copy(alpha = if (ball.runs == 0 && !ball.isWicket) 0.3f else 1f))
            .then(
                if (ball.isSix || ball.isWicket) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            color = if (ball.isSix) Color.Black else if (ball.runs == 0 && !ball.isWicket) TextTertiary else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun BallLegendItem(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(1.5.dp)).background(color))
        Text(text = text, color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}
