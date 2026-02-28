package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
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
import androidx.compose.ui.graphics.Path
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
 * Spin analysis card for spin bowlers
 *
 * TODO: Integration Requirements:
 * - Integrate with ball-tracking data for spin metrics
 * - Track revolutions per minute (RPM)
 * - Show drift and turn analysis
 * - Measure bounce variation
 * - Compare variations (arm ball, doosra, etc.)
 * - Add pitch conditions impact on spin
 * - Show spin vs batsman weakness correlation
 * - Animate spin trajectory visualization
 * - Track spin effectiveness over time
 * - Cache spin data for bowler analysis
 */

data class SpinAnalysisData(
    val bowlerName: String,
    val spinType: SpinType,
    val revolutions: Float, // RPM
    val driftAngle: Float, // degrees
    val turnAmount: Float, // inches
    val bounceHeight: Float, // percentage above average
    val deliveryBreakdown: List<SpinDeliveryType>,
)

enum class SpinType {
    OFF_SPIN, LEG_SPIN, LEFT_ARM_ORTHODOX, LEFT_ARM_CHINAMAN, CARROM_BALL
}

data class SpinDeliveryType(
    val name: String,
    val count: Int,
    val wickets: Int,
    val economy: Float,
)

@Composable
fun SpinAnalysisCard(
    spinData: SpinAnalysisData,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    LaunchedEffect(spinData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    val spinTypeColor = when (spinData.spinType) {
        SpinType.OFF_SPIN -> ChartBlue
        SpinType.LEG_SPIN -> Color(0xFFE91E63)
        SpinType.LEFT_ARM_ORTHODOX -> SuccessGreen
        SpinType.LEFT_ARM_CHINAMAN -> Color(0xFF9C27B0)
        SpinType.CARROM_BALL -> WarningYellow
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
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(spinTypeColor, spinTypeColor.copy(alpha = 0.6f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Air,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SPIN ANALYSIS",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = spinData.bowlerName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(spinTypeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = spinData.spinType.name.replace("_", " "),
                        color = spinTypeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Spin visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2D2D44)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val ballRadius = 35.dp.toPx()

                    // Spin trail
                    val trailPath = Path()
                    repeat(8) { i ->
                        val angle = (spinRotation + i * 45) * PI / 180
                        val radius = ballRadius + 15.dp.toPx() + i * 5.dp.toPx()
                        val x = centerX + (cos(angle) * radius).toFloat()
                        val y = centerY + (sin(angle) * radius).toFloat()

                        drawCircle(
                            color = spinTypeColor.copy(alpha = 0.3f - i * 0.03f),
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }

                    // Ball
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                WicketRed.copy(alpha = 0.9f),
                                WicketRed.copy(alpha = 0.6f),
                            ),
                            center = Offset(centerX - 10.dp.toPx(), centerY - 10.dp.toPx()),
                            radius = ballRadius,
                        ),
                        radius = ballRadius,
                        center = Offset(centerX, centerY),
                    )

                    // Seam line
                    val seamAngle = spinRotation * PI / 180
                    drawArc(
                        color = Color.White,
                        startAngle = spinRotation,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(centerX - ballRadius + 5.dp.toPx(), centerY - ballRadius + 5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(
                            (ballRadius - 5.dp.toPx()) * 2,
                            (ballRadius - 5.dp.toPx()) * 2,
                        ),
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    // RPM indicator
                    val rpmNormalized = (spinData.revolutions / 3000f).coerceIn(0f, 1f)
                    val indicatorRadius = ballRadius + 25.dp.toPx()
                    val indicatorSweep = 270f * rpmNormalized * animatedProgress.value

                    drawArc(
                        color = Color.White.copy(alpha = 0.2f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(centerX - indicatorRadius, centerY - indicatorRadius),
                        size = androidx.compose.ui.geometry.Size(indicatorRadius * 2, indicatorRadius * 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    )

                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(spinTypeColor, spinTypeColor.copy(alpha = 0.3f)),
                        ),
                        startAngle = 135f,
                        sweepAngle = indicatorSweep,
                        useCenter = false,
                        topLeft = Offset(centerX - indicatorRadius, centerY - indicatorRadius),
                        size = androidx.compose.ui.geometry.Size(indicatorRadius * 2, indicatorRadius * 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // RPM text
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${spinData.revolutions.toInt()}",
                        color = spinTypeColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "RPM",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spin metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SpinMetricItem(
                    label = "Drift",
                    value = "${spinData.driftAngle.toInt()}°",
                    progress = animatedProgress.value,
                )

                SpinMetricItem(
                    label = "Turn",
                    value = "${spinData.turnAmount.toInt()}\"",
                    progress = animatedProgress.value,
                )

                SpinMetricItem(
                    label = "Bounce",
                    value = "+${spinData.bounceHeight.toInt()}%",
                    progress = animatedProgress.value,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delivery breakdown
            Text(
                text = "DELIVERY TYPES",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                spinData.deliveryBreakdown.forEach { delivery ->
                    SpinDeliveryRow(
                        delivery = delivery,
                        color = spinTypeColor,
                        progress = animatedProgress.value,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpinMetricItem(
    label: String,
    value: String,
    progress: Float,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2D2D44))
            .padding(12.dp),
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SpinDeliveryRow(
    delivery: SpinDeliveryType,
    color: Color,
    progress: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2D2D44))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = delivery.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${delivery.count}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "balls",
                    color = Color.Gray,
                    fontSize = 9.sp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${delivery.wickets}",
                    color = if (delivery.wickets > 0) WicketRed else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "wkts",
                    color = Color.Gray,
                    fontSize = 9.sp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.1f", delivery.economy),
                    color = if (delivery.economy < 6f) {
                        SuccessGreen
                    } else if (delivery.economy > 8f) {
                        WicketRed
                    } else {
                        Color.White
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "econ",
                    color = Color.Gray,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
fun SpinAnalysisCardPreview() {
    SpinAnalysisCard(
        spinData = SpinAnalysisData(
            bowlerName = "R Ashwin",
            spinType = SpinType.OFF_SPIN,
            revolutions = 2450f,
            driftAngle = 8f,
            turnAmount = 4.5f,
            bounceHeight = 12f,
            deliveryBreakdown = listOf(
                SpinDeliveryType("Off Break", 24, 2, 5.2f),
                SpinDeliveryType("Carrom Ball", 8, 1, 6.8f),
                SpinDeliveryType("Arm Ball", 6, 0, 4.0f),
                SpinDeliveryType("Top Spinner", 4, 0, 7.5f),
            ),
        ),
    )
}
