package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Swing and seam movement analysis card
 *
 * TODO: Integration Requirements:
 * - Integrate with ball-tracking data for movement
 * - Measure swing angle and seam deviation
 * - Track movement throughout the innings
 * - Show new ball vs old ball comparison
 * - Analyze conditions impact on movement
 * - Compare bowler's movement variations
 * - Animate ball trajectory visualization
 * - Show wickets from swing/seam deliveries
 * - Track reverse swing detection
 * - Cache movement data for bowler analysis
 */

data class BallMovementData(
    val deliveryNumber: Int,
    val swingDegree: Float, // negative = inswing, positive = outswing
    val seamMovement: Float,
    val speed: Float,
    val result: String,
)

@Composable
fun SwingSeamAnalysisCard(
    bowlerName: String,
    movementData: List<BallMovementData>,
    averageSwing: Float,
    averageSeam: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(movementData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val inswingCount = movementData.count { it.swingDegree < -0.5f }
    val outswingCount = movementData.count { it.swingDegree > 0.5f }
    val straightCount = movementData.count { abs(it.swingDegree) <= 0.5f }

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
                                    colors = listOf(colors.goldDark, colors.goldPrimary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.SportsHandball,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SWING & SEAM",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = bowlerName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Ball count
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${movementData.size}",
                        color = colors.goldDark,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "deliveries",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Capture color for Canvas scope
            val goldDark = colors.goldDark
            val statusInfoColor = colors.statusInfo
            val statusLiveColor = colors.statusLive

            // Movement visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    val width = this.size.width
                    val height = this.size.height
                    val centerY = height / 2

                    // Draw center line (straight)
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                    )

                    // Draw zone labels positions
                    val topZone = height * 0.2f
                    val bottomZone = height * 0.8f

                    // Draw zone lines
                    drawLine(
                        color = statusInfoColor.copy(alpha = 0.3f),
                        start = Offset(0f, topZone),
                        end = Offset(width, topZone),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                    )
                    drawLine(
                        color = statusLiveColor.copy(alpha = 0.3f),
                        start = Offset(0f, bottomZone),
                        end = Offset(width, bottomZone),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                    )

                    // Plot movement points
                    val maxSwing = 3f // degrees
                    val pointsToShow = movementData.takeLast(15)

                    if (pointsToShow.isNotEmpty()) {
                        val path = Path()

                        pointsToShow.forEachIndexed { index, ball ->
                            val x = (index.toFloat() / (pointsToShow.size - 1).coerceAtLeast(1)) * width
                            val normalizedSwing = (ball.swingDegree / maxSwing).coerceIn(-1f, 1f)
                            val y = centerY - (normalizedSwing * (height / 2 - 20f)) * animatedProgress.value

                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }

                            // Draw ball point
                            val pointColor = when {
                                ball.swingDegree < -0.5f -> statusInfoColor // Inswing
                                ball.swingDegree > 0.5f -> statusLiveColor // Outswing
                                else -> colors.statusSuccess // Straight
                            }

                            drawCircle(
                                color = pointColor,
                                radius = 6f * animatedProgress.value,
                                center = Offset(x, y),
                            )
                        }

                        // Draw connecting line
                        drawPath(
                            path = path,
                            color = goldDark.copy(alpha = 0.5f),
                            style = Stroke(width = 2f, cap = StrokeCap.Round),
                        )
                    }
                }

                // Zone labels
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Away", color = colors.statusInfo.copy(alpha = 0.7f), fontSize = 9.sp)
                    Text("Straight", color = Color.Gray, fontSize = 9.sp)
                    Text("Into", color = colors.statusLive.copy(alpha = 0.7f), fontSize = 9.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Movement stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MovementStatColumn(
                    value = "$outswingCount",
                    label = "Outswing",
                    color = colors.statusInfo,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(Color(0xFF3D3D55)),
                )

                MovementStatColumn(
                    value = "$straightCount",
                    label = "Straight",
                    color = colors.statusSuccess,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(Color(0xFF3D3D55)),
                )

                MovementStatColumn(
                    value = "$inswingCount",
                    label = "Inswing",
                    color = colors.statusLive,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Average movement
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
            ) {
                Text(
                    text = "MOVEMENT ANALYSIS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Average Swing",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format("%.1f", abs(averageSwing)),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "°",
                                color = Color.Gray,
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (averageSwing < 0) "into" else "away",
                                color = if (averageSwing < 0) colors.statusLive else colors.statusInfo,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Seam Movement",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format("%.1f", averageSeam),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = " cm",
                                color = Color.Gray,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Swing meter
                Column {
                    Text(
                        text = "Swing Tendency",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            val total = (inswingCount + outswingCount + straightCount).coerceAtLeast(1)

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight((outswingCount.toFloat() / total * animatedProgress.value).coerceAtLeast(0.01f))
                                    .background(colors.statusInfo),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight((straightCount.toFloat() / total * animatedProgress.value).coerceAtLeast(0.01f))
                                    .background(colors.statusSuccess),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight((inswingCount.toFloat() / total * animatedProgress.value).coerceAtLeast(0.01f))
                                    .background(colors.statusLive),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementStatColumn(
    value: String,
    label: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun SwingSeamAnalysisCardPreview() {
    val colors = BouncerDesignSystem.colors
    val data = listOf(
        BallMovementData(1, 1.2f, 2.1f, 142f, "Dot"),
        BallMovementData(2, -0.8f, 1.5f, 138f, "Single"),
        BallMovementData(3, 1.5f, 2.3f, 145f, "Dot"),
        BallMovementData(4, -1.8f, 1.8f, 140f, "Wicket"),
        BallMovementData(5, 0.3f, 1.2f, 143f, "Dot"),
        BallMovementData(6, 2.1f, 2.5f, 144f, "Four"),
        BallMovementData(7, -1.2f, 1.9f, 139f, "Dot"),
        BallMovementData(8, 0.1f, 1.0f, 141f, "Single"),
    )

    SwingSeamAnalysisCard(
        bowlerName = "James Anderson",
        movementData = data,
        averageSwing = 0.8f,
        averageSeam = 1.8f,
    )
}
