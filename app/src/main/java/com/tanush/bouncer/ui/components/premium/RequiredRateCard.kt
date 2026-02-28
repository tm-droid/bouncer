package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Required rate tracker card
 *
 * TODO: Integration Requirements:
 * - Calculate required rate in real-time
 * - Update after each ball with new projections
 * - Show historical success at this required rate
 * - Add scenario calculator (if X wickets fall)
 * - Display required boundaries analysis
 * - Show RRR trend over last few overs
 * - Compare with achievable rates in death overs
 * - Animate rate gauge changes
 * - Push alerts when rate crosses thresholds
 * - Cache rate data for match review
 */

data class RequiredRateData(
    val target: Int,
    val currentScore: Int,
    val currentOver: Float,
    val totalOvers: Int,
    val requiredRate: Float,
    val currentRate: Float,
    val projectedScore: Int,
    val rateHistory: List<Float>, // Required rate at each over
    val isAchievable: Boolean,
)

@Composable
fun RequiredRateCard(
    rateData: RequiredRateData,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "rate")
    val urgencyPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (rateData.requiredRate > 12f) 400 else if (rateData.requiredRate > 10f) 600 else 1000,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "urgency",
    )

    LaunchedEffect(rateData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val rateColor = when {
        rateData.requiredRate > 15f -> WicketRed
        rateData.requiredRate > 12f -> Color(0xFFFF5722)
        rateData.requiredRate > 10f -> WarningYellow
        rateData.requiredRate > 8f -> ChartBlue
        else -> SuccessGreen
    }

    val diffRate = rateData.requiredRate - rateData.currentRate
    val diffColor = when {
        diffRate > 4f -> WicketRed
        diffRate > 2f -> WarningYellow
        diffRate > 0f -> ChartBlue
        else -> SuccessGreen
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
                            .size(44.dp * urgencyPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(rateColor, rateColor.copy(alpha = 0.6f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "REQUIRED RATE",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "Target: ${rateData.target}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (rateData.isAchievable) {
                                SuccessGreen.copy(alpha = 0.2f)
                            } else {
                                WicketRed.copy(alpha = 0.2f)
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (rateData.isAchievable) "ACHIEVABLE" else "DIFFICULT",
                        color = if (rateData.isAchievable) SuccessGreen else WicketRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main required rate display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Required rate gauge
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        // Background arc
                        drawArc(
                            color = Color(0xFF3D3D55),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        // Progress arc (max RR display = 18)
                        val normalizedRate = (rateData.requiredRate / 18f).coerceIn(0f, 1f)
                        val sweepAngle = 270f * normalizedRate * animatedProgress.value

                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    SuccessGreen,
                                    ChartBlue,
                                    WarningYellow,
                                    WicketRed,
                                ),
                            ),
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        // Glow effect
                        val endAngle = (135f + sweepAngle) * PI / 180
                        val glowX = center.x + (cos(endAngle) * radius).toFloat()
                        val glowY = center.y + (sin(endAngle) * radius).toFloat()

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    rateColor.copy(alpha = 0.6f * urgencyPulse),
                                    rateColor.copy(alpha = 0f),
                                ),
                                center = Offset(glowX, glowY),
                                radius = 20.dp.toPx() * urgencyPulse,
                            ),
                            center = Offset(glowX, glowY),
                            radius = 20.dp.toPx() * urgencyPulse,
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.2f", rateData.requiredRate * animatedProgress.value),
                            color = rateColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "RRR",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Stats column
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RateStatItem(
                        label = "Current Rate",
                        value = String.format("%.2f", rateData.currentRate),
                        color = if (rateData.currentRate >= rateData.requiredRate) SuccessGreen else Color.White,
                    )

                    RateStatItem(
                        label = "Difference",
                        value = "${if (diffRate > 0) "+" else ""}${String.format("%.2f", diffRate)}",
                        color = diffColor,
                    )

                    RateStatItem(
                        label = "Need",
                        value = "${rateData.target - rateData.currentScore}",
                        color = Color.White,
                    )

                    RateStatItem(
                        label = "From",
                        value = "${(rateData.totalOvers - rateData.currentOver.toInt())} overs",
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rate progression graph
            if (rateData.rateHistory.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(12.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val maxRate = rateData.rateHistory.maxOrNull() ?: 15f
                        val minRate = rateData.rateHistory.minOrNull() ?: 5f
                        val range = (maxRate - minRate).coerceAtLeast(1f)

                        // Grid lines
                        listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                            val y = height * ratio
                            drawLine(
                                color = Color.White.copy(alpha = 0.1f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        // Rate line
                        val animatedHistory = rateData.rateHistory.take(
                            (rateData.rateHistory.size * animatedProgress.value).toInt().coerceAtLeast(1),
                        )

                        val path = Path().apply {
                            animatedHistory.forEachIndexed { index, rate ->
                                val x = width * index / (rateData.rateHistory.size - 1).coerceAtLeast(1)
                                val normalizedRate = ((rate - minRate) / range).coerceIn(0f, 1f)
                                val y = height * (1f - normalizedRate)

                                if (index == 0) {
                                    moveTo(x, y)
                                } else {
                                    lineTo(x, y)
                                }
                            }
                        }

                        // Fill under curve
                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(width * (animatedHistory.size - 1) / (rateData.rateHistory.size - 1).coerceAtLeast(1), height)
                            lineTo(0f, height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    rateColor.copy(alpha = 0.4f),
                                    rateColor.copy(alpha = 0f),
                                ),
                            ),
                        )

                        drawPath(
                            path = path,
                            color = rateColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )

                        // Current point
                        if (animatedHistory.isNotEmpty()) {
                            val lastX = width * (animatedHistory.size - 1) / (rateData.rateHistory.size - 1).coerceAtLeast(1)
                            val lastRate = animatedHistory.last()
                            val normalizedLastRate = ((lastRate - minRate) / range).coerceIn(0f, 1f)
                            val lastY = height * (1f - normalizedLastRate)

                            drawCircle(
                                color = rateColor,
                                radius = 5.dp.toPx(),
                                center = Offset(lastX, lastY),
                            )
                        }
                    }

                    // Label
                    Text(
                        text = "Rate progression",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Projection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (rateData.projectedScore >= rateData.target) {
                            SuccessGreen.copy(alpha = 0.15f)
                        } else {
                            WicketRed.copy(alpha = 0.15f)
                        },
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (rateData.projectedScore >= rateData.target) "📈" else "📉",
                        fontSize = 18.sp,
                    )
                    Column {
                        Text(
                            text = "Projected Score: ${rateData.projectedScore}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (rateData.projectedScore >= rateData.target) {
                                "On track to win by ${rateData.projectedScore - rateData.target + 1} runs"
                            } else {
                                "Short by ${rateData.target - rateData.projectedScore} runs at current rate"
                            },
                            color = Color.Gray,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RateStatItem(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier.width(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun RequiredRateCardPreview() {
    RequiredRateCard(
        rateData = RequiredRateData(
            target = 186,
            currentScore = 112,
            currentOver = 13.4f,
            totalOvers = 20,
            requiredRate = 11.54f,
            currentRate = 8.18f,
            projectedScore = 164,
            rateHistory = listOf(9.3f, 9.5f, 9.8f, 10.2f, 10.5f, 10.8f, 11.0f, 11.2f, 11.3f, 11.4f, 11.5f, 11.54f),
            isAchievable = true,
        ),
    )
}
