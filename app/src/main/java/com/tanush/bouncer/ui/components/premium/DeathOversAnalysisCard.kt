package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Death Overs Analysis Card
 *
 * TODO: Integration Requirements:
 * - Fetch ball-by-ball data for overs 16-20 (or match context)
 * - Calculate death over economy and strike rates
 * - Show bowler effectiveness in death overs
 * - Add historical death over performance stats
 * - Track yorker success rate
 * - Display boundary percentage in death
 * - Compare team's death over performance vs average
 * - Show optimal bowler recommendations
 * - Add wide/no-ball tracking under pressure
 * - Cache death over analysis for post-match review
 */

data class DeathOversBall(
    val ballNumber: Int,
    val runs: Int,
    val isWicket: Boolean,
    val isBoundary: Boolean,
    val isSix: Boolean,
    val isDot: Boolean,
    val isWide: Boolean = false,
    val isNoBall: Boolean = false,
)

data class DeathOversData(
    val over: Int,
    val balls: List<DeathOversBall>,
    val totalRuns: Int,
    val wickets: Int,
)

@Composable
fun DeathOversAnalysisCard(
    battingTeam: String,
    bowlingTeam: String,
    deathOvers: List<DeathOversData>,
    totalDeathRuns: Int,
    totalDeathWickets: Int,
    deathRunRate: Double,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "death")

    val warningPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "warning",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(deathOvers) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Critical phase background glow
            Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WicketRed.copy(alpha = 0.15f * warningPulse),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.8f, size.height * 0.2f),
                        radius = size.width * 0.5f,
                    ),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width * 0.5f,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(WicketRed, WicketRed.copy(alpha = 0.6f)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CrisisAlert,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "DEATH OVERS",
                                color = WicketRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = "Overs 16-20 Analysis",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Death phase stats
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$totalDeathRuns/$totalDeathWickets",
                            color = ShinyGoldMid,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "RR: ${String.format("%.2f", deathRunRate)}",
                            color = if (deathRunRate > 10) SuccessGreen else WicketRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Over-by-over visualization
                deathOvers.forEachIndexed { index, overData ->
                    DeathOverRow(
                        overData = overData,
                        progress = animatedProgress.value,
                        index = index,
                    )

                    if (index < deathOvers.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Summary stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DeathStatItem(
                        value = deathOvers.sumOf { over -> over.balls.count { it.isBoundary } }.toString(),
                        label = "Fours",
                        color = BoundaryFour,
                    )

                    DeathStatItem(
                        value = deathOvers.sumOf { over -> over.balls.count { it.isSix } }.toString(),
                        label = "Sixes",
                        color = BoundarySix,
                    )

                    DeathStatItem(
                        value = deathOvers.sumOf { over -> over.balls.count { it.isDot } }.toString(),
                        label = "Dots",
                        color = Color.Gray,
                    )

                    DeathStatItem(
                        value = totalDeathWickets.toString(),
                        label = "Wickets",
                        color = WicketRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeathOverRow(
    overData: DeathOversData,
    progress: Float,
    index: Int,
) {
    val delayedProgress = ((progress - index * 0.15f) / 0.85f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = delayedProgress
                translationX = (1 - delayedProgress) * 50f
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Over number
        Box(
            modifier = Modifier
                .width(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Over ${overData.over}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Ball-by-ball
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            overData.balls.forEach { ball ->
                BallIndicator(ball = ball)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Over total
        Text(
            text = "${overData.totalRuns}/${overData.wickets}",
            color = when {
                overData.wickets >= 2 -> WicketRed
                overData.totalRuns >= 15 -> SuccessGreen
                else -> ShinyGoldMid
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun BallIndicator(ball: DeathOversBall) {
    val backgroundColor = when {
        ball.isWicket -> WicketRed
        ball.isSix -> BoundarySix
        ball.isBoundary -> BoundaryFour
        ball.isDot -> Color.Gray.copy(alpha = 0.4f)
        ball.isWide || ball.isNoBall -> WarningYellow.copy(alpha = 0.6f)
        else -> ChartBlue.copy(alpha = 0.5f)
    }

    val text = when {
        ball.isWicket -> "W"
        ball.isSix -> "6"
        ball.isBoundary -> "4"
        ball.isWide -> "Wd"
        ball.isNoBall -> "Nb"
        ball.isDot -> "•"
        else -> ball.runs.toString()
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (ball.isDot) 16.sp else 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DeathStatItem(
    value: String,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
        )
    }
}
