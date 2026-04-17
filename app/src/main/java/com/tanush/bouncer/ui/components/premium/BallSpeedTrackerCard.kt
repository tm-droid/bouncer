package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
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
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Ball speed tracker showing bowling speed over time
 *
 * TODO: Integration Requirements:
 * - Connect to speed gun data feed when available
 * - Show real-time speed for each delivery
 * - Track speed trends throughout the spell
 * - Compare against bowler's historical speeds
 * - Add speed distribution chart
 * - Show fastest/slowest delivery highlights
 * - Add delivery type classification (fast/medium/slow)
 * - Convert between km/h and mph based on user preference
 * - Animate speedometer on new delivery
 * - Cache speed data for spell analysis
 */

data class BallSpeedData(
    val ballNumber: Int,
    val speed: Float,
    val bowlerName: String,
    val deliveryType: String,
    val result: String,
)

@Composable
fun BallSpeedTrackerCard(
    speedData: List<BallSpeedData>,
    currentBowler: String,
    averageSpeed: Float,
    maxSpeed: Float,
    minSpeed: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val goldDarkC = hierarchy.secondary.primary
    val statusSuccessC = colors.statusSuccess
    val statusWarningC = colors.statusWarning
    val statusLiveC = colors.statusLive
    val statusInfoC = colors.statusInfo
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(speedData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "speed")
    val speedPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

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
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "BALL SPEED TRACKER",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = currentBowler,
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Current speed display (speedometer style)
            val currentSpeed = speedData.lastOrNull()?.speed ?: 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Speedometer
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.minDimension / 2 - 10f

                        // Speed range: 80-160 km/h
                        val minSpeedRange = 80f
                        val maxSpeedRange = 160f
                        val speedNormalized = ((currentSpeed - minSpeedRange) / (maxSpeedRange - minSpeedRange)).coerceIn(0f, 1f)

                        // Background arc
                        drawArc(
                            color = Color(0xFF2D2D44),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 16f, cap = StrokeCap.Round),
                        )

                        // Speed gradient arc
                        val sweepAngle = 270f * speedNormalized * animatedProgress.value
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(statusSuccessC, statusWarningC, statusLiveC),
                                center = center,
                            ),
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 16f, cap = StrokeCap.Round),
                        )

                        // Needle
                        val needleAngle = Math.toRadians((135.0 + sweepAngle).toDouble())
                        val needleLength = radius - 25f
                        drawLine(
                            color = Color.White,
                            start = center,
                            end = Offset(
                                center.x + (needleLength * cos(needleAngle)).toFloat(),
                                center.y + (needleLength * sin(needleAngle)).toFloat(),
                            ),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round,
                        )

                        // Center dot
                        drawCircle(
                            color = goldDarkC,
                            radius = 8f,
                            center = center,
                        )
                    }

                    // Speed text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = 15.dp),
                    ) {
                        Text(
                            text = "${(currentSpeed * animatedProgress.value).toInt()}",
                            color = colors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer {
                                scaleX = if (currentSpeed > 140) speedPulse else 1f
                                scaleY = if (currentSpeed > 140) speedPulse else 1f
                            },
                        )
                        Text(
                            text = "km/h",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                    }
                }

                // Speed stats
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeedStatItem(
                        label = "Max",
                        value = "${maxSpeed.toInt()} km/h",
                        color = colors.statusLive,
                    )
                    SpeedStatItem(
                        label = "Average",
                        value = "${averageSpeed.toInt()} km/h",
                        color = colors.statusInfo,
                    )
                    SpeedStatItem(
                        label = "Min",
                        value = "${minSpeed.toInt()} km/h",
                        color = colors.statusSuccess,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Speed trend chart
            if (speedData.size >= 2) {
                Column {
                    Text(
                        text = "SPEED TREND (Last ${speedData.size} balls)",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2D2D44))
                            .padding(10.dp),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val data = speedData.takeLast(12)
                            val speedMin = 80f
                            val speedMax = 160f
                            val speedRange = speedMax - speedMin

                            // Draw reference lines
                            listOf(100f, 120f, 140f).forEach { speed ->
                                val y = height - ((speed - speedMin) / speedRange * height)
                                drawLine(
                                    color = Color(0xFF3D3D55),
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                                )
                            }

                            // Draw bars
                            val barWidth = width / data.size - 6f
                            data.forEachIndexed { index, ball ->
                                val x = index * (width / data.size) + 3f
                                val barHeight = ((ball.speed - speedMin) / speedRange * height * animatedProgress.value).coerceAtLeast(4f)
                                val y = height - barHeight

                                val barColor = when {
                                    ball.speed >= 145 -> statusLiveC
                                    ball.speed >= 135 -> statusWarningC
                                    else -> statusInfoC
                                }

                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent deliveries
            if (speedData.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "RECENT DELIVERIES",
                        color = hierarchy.secondary.secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        speedData.takeLast(6).reversed().forEach { ball ->
                            val bgColor = when {
                                ball.speed >= 145 -> colors.statusLive
                                ball.speed >= 135 -> colors.statusWarning
                                else -> colors.statusInfo
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor.copy(alpha = 0.2f))
                                    .padding(8.dp),
                            ) {
                                Text(
                                    text = "${ball.speed.toInt()}",
                                    color = bgColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = ball.deliveryType.take(3),
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedStatItem(
    label: String,
    value: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 11.sp,
            )
            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun BallSpeedTrackerCardPreview() {
    val colors = BouncerDesignSystem.colors
    val speedData = listOf(
        BallSpeedData(1, 142f, "Jasprit Bumrah", "Yorker", "Dot"),
        BallSpeedData(2, 145f, "Jasprit Bumrah", "Bouncer", "Wide"),
        BallSpeedData(3, 138f, "Jasprit Bumrah", "Outswinger", "Dot"),
        BallSpeedData(4, 148f, "Jasprit Bumrah", "Yorker", "Wicket"),
        BallSpeedData(5, 141f, "Jasprit Bumrah", "Length", "Single"),
        BallSpeedData(6, 146f, "Jasprit Bumrah", "Bouncer", "Dot"),
    )

    BallSpeedTrackerCard(
        speedData = speedData,
        currentBowler = "Jasprit Bumrah",
        averageSpeed = 143.3f,
        maxSpeed = 148f,
        minSpeed = 138f,
    )
}
