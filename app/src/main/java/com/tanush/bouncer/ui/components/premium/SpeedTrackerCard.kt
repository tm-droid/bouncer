package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Speed Tracker Card
 *
 * TODO: Integration Requirements:
 * - Connect to speed gun data feed (when available)
 * - Track ball speeds in real-time
 * - Show speed trend throughout spell
 * - Compare with bowler's historical speeds
 * - Detect fatigue through speed drops
 * - Add speed distribution chart
 * - Show fastest deliveries in match
 * - Animate speedometer on each delivery
 * - Convert km/h to mph based on settings
 * - Cache speed data for spell analysis
 */

data class BowlingSpeedData(
    val currentSpeed: Float, // in km/h
    val maxSpeed: Float,
    val avgSpeed: Float,
    val minSpeed: Float,
    val speedByOver: List<Float>,
    val bowlerName: String,
)

@Composable
fun SpeedTrackerCard(
    speedData: BowlingSpeedData,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speed")

    val needlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "needle",
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    val animatedSpeed = remember { Animatable(0f) }

    LaunchedEffect(speedData.currentSpeed) {
        animatedSpeed.animateTo(
            targetValue = speedData.currentSpeed,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    // Speed categories
    val speedColor = when {
        speedData.currentSpeed >= 145 -> WicketRed
        speedData.currentSpeed >= 135 -> ShinyGoldMid
        speedData.currentSpeed >= 125 -> ChartBlue
        else -> SuccessGreen
    }

    val speedCategory = when {
        speedData.currentSpeed >= 145 -> "EXPRESS"
        speedData.currentSpeed >= 135 -> "FAST"
        speedData.currentSpeed >= 125 -> "FAST-MEDIUM"
        speedData.currentSpeed >= 115 -> "MEDIUM"
        else -> "SLOW"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        speedColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * shimmerOffset, 0f),
                    end = Offset(size.width * (shimmerOffset + 0.3f), size.height),
                )
                drawRect(brush = shimmerBrush)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                                        colors = listOf(speedColor, speedColor.copy(alpha = 0.7f)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "SPEED TRACKER",
                                color = speedColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = speedData.bowlerName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Speed category badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(speedColor.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = speedCategory,
                            color = speedColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Speedometer
                Box(
                    modifier = Modifier
                        .size(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = size.minDimension / 2 - 20

                        // Draw gauge arc
                        drawArc(
                            color = Color.White.copy(alpha = 0.1f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 15f, cap = StrokeCap.Round),
                            topLeft = Offset(centerX - radius, centerY - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        )

                        // Speed color gradient
                        val speedProgress = (animatedSpeed.value - 80f) / (160f - 80f)
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    SuccessGreen,
                                    ChartBlue,
                                    ShinyGoldMid,
                                    WicketRed,
                                ),
                                center = Offset(centerX, centerY),
                            ),
                            startAngle = 135f,
                            sweepAngle = 270f * speedProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(width = 15f, cap = StrokeCap.Round),
                            topLeft = Offset(centerX - radius, centerY - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        )

                        // Draw tick marks
                        for (i in 0..8) {
                            val tickAngle = ((135 + i * 33.75) * (PI / 180)).toFloat()
                            val startRadius = radius - 25
                            val endRadius = radius - 15

                            drawLine(
                                color = Color.White.copy(alpha = 0.3f),
                                start = Offset(
                                    centerX + cos(tickAngle.toDouble()).toFloat() * startRadius,
                                    centerY + sin(tickAngle.toDouble()).toFloat() * startRadius,
                                ),
                                end = Offset(
                                    centerX + cos(tickAngle.toDouble()).toFloat() * endRadius,
                                    centerY + sin(tickAngle.toDouble()).toFloat() * endRadius,
                                ),
                                strokeWidth = 2f,
                            )
                        }

                        // Draw needle
                        val needleAngle = ((135 + 270 * speedProgress.coerceIn(0f, 1f)) * (PI / 180)).toFloat()
                        val needleLength = radius - 40

                        drawLine(
                            color = speedColor,
                            start = Offset(centerX, centerY),
                            end = Offset(
                                centerX + cos(needleAngle.toDouble()).toFloat() * needleLength * needlePulse,
                                centerY + sin(needleAngle.toDouble()).toFloat() * needleLength * needlePulse,
                            ),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round,
                        )

                        // Center circle
                        drawCircle(
                            color = speedColor,
                            radius = 10f,
                            center = Offset(centerX, centerY),
                        )
                    }

                    // Speed display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = 30.dp),
                    ) {
                        Text(
                            text = "${animatedSpeed.value.toInt()}",
                            color = speedColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "km/h",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Speed stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SpeedStatItem(
                        label = "Max",
                        value = "${speedData.maxSpeed.toInt()} km/h",
                        color = WicketRed,
                    )

                    SpeedStatItem(
                        label = "Avg",
                        value = "${speedData.avgSpeed.toInt()} km/h",
                        color = ShinyGoldMid,
                    )

                    SpeedStatItem(
                        label = "Min",
                        value = "${speedData.minSpeed.toInt()} km/h",
                        color = ChartBlue,
                    )
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
