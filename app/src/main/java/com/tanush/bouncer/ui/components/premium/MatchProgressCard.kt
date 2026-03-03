package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match Progress Card
 *
 * TODO: Integration Requirements:
 * - Connect to match timing/clock service
 * - Track session timings for Test matches
 * - Show real-time countdown to breaks
 * - Display estimated finish time
 * - Handle rain delays and DLS scenarios
 * - Track overs remaining in each format
 * - Add schedule for next sessions
 * - Show light/weather impact on timing
 * - Push notifications for session changes
 * - Cache timing data for display continuity
 */

data class MatchBreakInfo(
    val breakType: String, // "Innings Break", "Drinks", "Rain Delay", "Strategic Timeout"
    val breakDuration: String,
    val afterOver: String,
    val scoreAtBreak: String,
    val breakActive: Boolean = false,
)

data class MatchProgressInfo(
    val currentSessionTime: String,
    val totalMatchTime: String,
    val oversRemaining: Int,
    val estimatedTimeRemaining: String,
    val nextBreak: String?,
)

@Composable
fun MatchProgressCard(
    progressInfo: MatchProgressInfo,
    breaks: List<MatchBreakInfo>,
    oversBowled: Float,
    totalOvers: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "progress")

    val clockPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "clock",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(oversBowled) {
        animatedProgress.animateTo(
            targetValue = oversBowled / totalOvers,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
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
                                    colors = listOf(colors.statusInfo, colors.statusInfo.copy(alpha = 0.6f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = clockPulse
                                    scaleY = clockPulse
                                },
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "MATCH PROGRESS",
                            color = colors.goldDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = progressInfo.totalMatchTime,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Time remaining
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = progressInfo.estimatedTimeRemaining,
                        color = colors.goldDark,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Est. remaining",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress visualization
            MatchProgressVisualization(
                progress = animatedProgress.value,
                totalOvers = totalOvers.toInt(),
                oversBowled = oversBowled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Overs info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Overs: ${oversBowled.toInt()} / ${totalOvers.toInt()}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                Text(
                    text = "${progressInfo.oversRemaining} overs remaining",
                    color = colors.statusInfo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Session stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TimeStatColumn(
                    label = "Session Time",
                    value = progressInfo.currentSessionTime,
                    color = colors.statusInfo,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.2f)),
                )

                TimeStatColumn(
                    label = "Total Time",
                    value = progressInfo.totalMatchTime,
                    color = colors.goldDark,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.2f)),
                )

                TimeStatColumn(
                    label = "Next Break",
                    value = progressInfo.nextBreak ?: "None",
                    color = colors.statusSuccess,
                )
            }

            // Breaks timeline
            if (breaks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "BREAKS",
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
                    breaks.take(3).forEach { breakInfo ->
                        BreakChip(
                            breakInfo = breakInfo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchProgressVisualization(
    progress: Float,
    totalOvers: Int,
    oversBowled: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val statusInfoC = colors.statusInfo
    val goldDarkC = colors.goldDark
    val goldPrimaryC = colors.goldPrimary
    val statusSuccessC = colors.statusSuccess
    Canvas(modifier = modifier) {
        val barHeight = 20.dp.toPx()
        val barY = (size.height - barHeight) / 2

        // Background bar
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = Offset(0f, barY),
            size = androidx.compose.ui.geometry.Size(size.width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
        )

        // Progress bar
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(statusInfoC, goldDarkC, statusSuccessC),
            ),
            topLeft = Offset(0f, barY),
            size = androidx.compose.ui.geometry.Size(size.width * progress, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
        )

        // Over markers
        val markerCount = totalOvers / 5
        repeat(markerCount) { i ->
            val x = size.width * ((i + 1) * 5f / totalOvers)

            // Marker line
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(x, barY - 5.dp.toPx()),
                end = Offset(x, barY + barHeight + 5.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Current position indicator
        val currentX = size.width * progress
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldDarkC, goldPrimaryC),
            ),
            radius = 10.dp.toPx(),
            center = Offset(currentX, barY + barHeight / 2),
        )

        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = Offset(currentX, barY + barHeight / 2),
        )
    }
}

@Composable
private fun TimeStatColumn(
    label: String,
    value: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun BreakChip(
    breakInfo: MatchBreakInfo,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (breakInfo.breakActive) {
                    Brush.horizontalGradient(
                        colors = listOf(colors.goldDark.copy(alpha = 0.2f), colors.goldPrimary.copy(alpha = 0.2f)),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                    )
                },
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = breakInfo.breakType,
                color = if (breakInfo.breakActive) colors.goldDark else Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = breakInfo.breakDuration,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
            )
        }
    }
}
