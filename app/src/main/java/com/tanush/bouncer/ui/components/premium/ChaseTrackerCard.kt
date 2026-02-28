package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chase Tracker Card
 *
 * TODO: Integration Requirements:
 * - Connect to live match data for real-time chase progress
 * - Calculate required run rate dynamically
 * - Add milestone predictions based on current rate
 * - Show historical chase comparisons at this venue
 * - Integrate win probability model
 * - Add par score calculations for weather delays
 * - Show partnerships needed to achieve target
 * - Animate progress ring on score updates
 * - Display asking rate projections
 * - Cache chase data for match review
 */

data class TargetMilestone(
    val runs: Int,
    val description: String,
    val isAchieved: Boolean,
    val estimatedOver: String?,
)

@Composable
fun ChaseTrackerCard(
    battingTeam: String,
    targetRuns: Int,
    currentRuns: Int,
    wicketsLost: Int,
    oversRemaining: Float,
    requiredRate: Double,
    currentRate: Double,
    milestones: List<TargetMilestone>,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chase")

    val targetGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(currentRuns) {
        animatedProgress.animateTo(
            targetValue = currentRuns.toFloat() / targetRuns,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
        )
    }

    val runsNeeded = targetRuns - currentRuns
    val isOnTrack = currentRate >= requiredRate

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Target glow background
            Canvas(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                val centerX = size.width / 2
                val centerY = size.height * 0.4f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isOnTrack) {
                                SuccessGreen.copy(alpha = 0.2f * targetGlow)
                            } else {
                                WicketRed.copy(alpha = 0.2f * targetGlow)
                            },
                            Color.Transparent,
                        ),
                        center = Offset(centerX, centerY),
                        radius = size.width * 0.4f,
                    ),
                    center = Offset(centerX, centerY),
                    radius = size.width * 0.4f,
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
                                        colors = listOf(
                                            if (isOnTrack) SuccessGreen else WicketRed,
                                            if (isOnTrack) SuccessGreen.copy(alpha = 0.6f) else WicketRed.copy(alpha = 0.6f),
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "CHASE TRACKER",
                                color = ShinyGoldMid,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = battingTeam,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isOnTrack) {
                                    SuccessGreen.copy(alpha = 0.2f)
                                } else {
                                    WicketRed.copy(alpha = 0.2f)
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (isOnTrack) "ON TRACK" else "BEHIND",
                            color = if (isOnTrack) SuccessGreen else WicketRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Circular progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ChaseProgressRing(
                        progress = animatedProgress.value,
                        isOnTrack = isOnTrack,
                        modifier = Modifier.size(140.dp),
                    )

                    // Center info
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$runsNeeded",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "runs needed",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ChaseStatItem(
                        label = "Required RR",
                        value = String.format("%.2f", requiredRate),
                        color = if (requiredRate > 12) WicketRed else if (requiredRate > 8) WarningYellow else SuccessGreen,
                    )

                    ChaseStatItem(
                        label = "Current RR",
                        value = String.format("%.2f", currentRate),
                        color = if (currentRate >= requiredRate) SuccessGreen else WicketRed,
                    )

                    ChaseStatItem(
                        label = "Overs Left",
                        value = String.format("%.1f", oversRemaining),
                        color = ChartBlue,
                    )

                    ChaseStatItem(
                        label = "Wickets",
                        value = "${10 - wicketsLost}",
                        color = ShinyGoldMid,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Milestones
                Text(
                    text = "MILESTONES",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    milestones.take(4).forEach { milestone ->
                        MilestoneChip(
                            milestone = milestone,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaseProgressRing(
    progress: Float,
    isOnTrack: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Background ring
        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = radius,
            style = Stroke(width = strokeWidth),
        )

        // Progress arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = if (isOnTrack) {
                    listOf(SuccessGreen, ShinyGoldMid, SuccessGreen)
                } else {
                    listOf(WicketRed, WarningYellow, WicketRed)
                },
            ),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Target marker
        val targetAngle = Math.toRadians(-90.0)
        val targetX = center.x + radius * cos(targetAngle).toFloat()
        val targetY = center.y + radius * sin(targetAngle).toFloat()

        drawCircle(
            color = ShinyGoldMid,
            radius = 8.dp.toPx(),
            center = Offset(targetX, targetY),
        )
    }
}

@Composable
private fun ChaseStatItem(
    label: String,
    value: String,
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
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun MilestoneChip(
    milestone: TargetMilestone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (milestone.isAchieved) {
                    Brush.verticalGradient(
                        colors = listOf(
                            SuccessGreen.copy(alpha = 0.2f),
                            SuccessGreen.copy(alpha = 0.1f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (milestone.isAchieved) "✓" else "${milestone.runs}",
                color = if (milestone.isAchieved) SuccessGreen else Color.White,
                fontSize = if (milestone.isAchieved) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = milestone.description,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp,
            )
        }
    }
}
