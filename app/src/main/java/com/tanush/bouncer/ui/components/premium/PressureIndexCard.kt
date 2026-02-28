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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ShowChart
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pressure index card showing match pressure on batsmen/bowlers
 *
 * TODO: Integration Requirements:
 * - Build pressure calculation model (RRR, wickets, overs left)
 * - Update pressure index in real-time
 * - Track pressure history throughout innings
 * - Factor in match context (knockout, final, etc.)
 * - Add player-specific pressure handling stats
 * - Show historical high-pressure performance
 * - Animate pressure gauge changes
 * - Compare current pressure with historical chases
 * - Predict pressure changes for scenarios
 * - Cache pressure data for analysis
 */

data class PressureIndexData(
    val currentPressure: Float, // 0-100
    val pressureHistory: List<Float>,
    val requiredRate: Float,
    val currentRate: Float,
    val wicketsInHand: Int,
    val ballsRemaining: Int,
    val pressureFactors: List<PressureFactor>,
)

data class PressureFactor(
    val name: String,
    val impact: Float, // -50 to +50
    val description: String,
)

@Composable
fun PressureIndexCard(
    data: PressureIndexData,
    battingTeam: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesign.colors
    val dimens = BouncerDesign.dimens
    val typography = BouncerDesign.typography

    val animatedPressure = remember { Animatable(0f) }
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pressure")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1000 - (data.currentPressure * 5).toInt()).coerceAtLeast(300),
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(data.currentPressure) {
        animatedPressure.animateTo(
            targetValue = data.currentPressure,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        )
    }

    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val pressureColor = when {
        data.currentPressure <= 30 -> colors.statusSuccess
        data.currentPressure <= 50 -> colors.statusInfo
        data.currentPressure <= 70 -> colors.statusWarning
        else -> colors.statusLive
    }

    val pressureLevel = when {
        data.currentPressure <= 30 -> "LOW"
        data.currentPressure <= 50 -> "MODERATE"
        data.currentPressure <= 70 -> "HIGH"
        else -> "EXTREME"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radius.xl),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.elevation.level3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.lg),
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
                            .size(dimens.iconSize.hero)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(colors.goldPrimary, colors.goldLight),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(dimens.iconSize.lg),
                        )
                    }

                    Spacer(modifier = Modifier.width(dimens.spacing.md))

                    Column {
                        Text(
                            text = "PRESSURE INDEX",
                            color = colors.goldPrimary,
                            style = typography.labelLarge,
                        )
                        Text(
                            text = battingTeam,
                            color = colors.textPrimary,
                            style = typography.headingSmall,
                        )
                    }
                }

                // Pressure level badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimens.radius.sm))
                        .background(pressureColor.copy(alpha = 0.2f))
                        .border(dimens.borderWidth, pressureColor, RoundedCornerShape(dimens.radius.sm))
                        .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs),
                ) {
                    Text(
                        text = pressureLevel,
                        color = pressureColor,
                        style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacing.lg))

            // Main pressure gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Circular pressure gauge
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14f
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        // Background arc
                        drawArc(
                            color = Color(0xFF2D2D44),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        // Pressure arc
                        val sweepAngle = 270f * (animatedPressure.value / 100f)
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(SuccessGreen, WarningYellow, WicketRed),
                                center = center,
                            ),
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        // Needle indicator
                        val needleAngle = Math.toRadians((135.0 + sweepAngle).toDouble())
                        val needleLength = radius - 20f
                        val needleEnd = Offset(
                            center.x + (needleLength * cos(needleAngle)).toFloat(),
                            center.y + (needleLength * sin(needleAngle)).toFloat(),
                        )

                        drawCircle(
                            color = pressureColor,
                            radius = 8f * pulseScale,
                            center = needleEnd,
                        )
                    }

                    // Center text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${animatedPressure.value.toInt()}",
                            color = pressureColor,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "/ 100",
                            color = Color.Gray,
                            fontSize = 14.sp,
                        )
                    }
                }

                // Stats column
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PressureStatRow(
                        label = "Required RR",
                        value = String.format("%.2f", data.requiredRate),
                        color = if (data.requiredRate > 10) WicketRed else if (data.requiredRate > 7) WarningYellow else Color.White,
                    )
                    PressureStatRow(
                        label = "Current RR",
                        value = String.format("%.2f", data.currentRate),
                        color = if (data.currentRate >= data.requiredRate) SuccessGreen else WarningYellow,
                    )
                    PressureStatRow(
                        label = "Wickets Left",
                        value = "${data.wicketsInHand}",
                        color = if (data.wicketsInHand <= 3) WicketRed else Color.White,
                    )
                    PressureStatRow(
                        label = "Balls Left",
                        value = "${data.ballsRemaining}",
                        color = if (data.ballsRemaining <= 30) WarningYellow else Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Pressure trend line
            if (data.pressureHistory.isNotEmpty()) {
                Column {
                    Text(
                        text = "PRESSURE TREND",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2D2D44)),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            val width = size.width
                            val height = size.height
                            val points = data.pressureHistory.take(20)
                            val maxPressure = 100f

                            if (points.size >= 2) {
                                val path = Path()
                                val step = width / (points.size - 1)

                                points.forEachIndexed { index, pressure ->
                                    val x = index * step
                                    val y = height - (pressure / maxPressure * height) * animatedProgress.value

                                    if (index == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }

                                // Draw gradient fill
                                val fillPath = Path().apply {
                                    addPath(path)
                                    lineTo(width, height)
                                    lineTo(0f, height)
                                    close()
                                }

                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            pressureColor.copy(alpha = 0.4f),
                                            Color.Transparent,
                                        ),
                                    ),
                                )

                                // Draw line
                                drawPath(
                                    path = path,
                                    color = pressureColor,
                                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                                )

                                // Draw current point
                                val lastX = (points.size - 1) * step
                                val lastY = height - (points.last() / maxPressure * height) * animatedProgress.value
                                drawCircle(
                                    color = pressureColor,
                                    radius = 5f,
                                    center = Offset(lastX, lastY),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pressure factors
            if (data.pressureFactors.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "PRESSURE FACTORS",
                        color = ShinyGoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    data.pressureFactors.forEach { factor ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = factor.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = factor.description,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val impactColor = if (factor.impact > 0) WicketRed else SuccessGreen
                                val impactText = if (factor.impact > 0) "+${factor.impact.toInt()}" else "${factor.impact.toInt()}"

                                Text(
                                    text = impactText,
                                    color = impactColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
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
private fun PressureStatRow(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(130.dp),
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PressureIndexCardPreview() {
    PressureIndexCard(
        data = PressureIndexData(
            currentPressure = 72f,
            pressureHistory = listOf(45f, 48f, 52f, 55f, 60f, 65f, 62f, 68f, 72f),
            requiredRate = 9.5f,
            currentRate = 7.2f,
            wicketsInHand = 4,
            ballsRemaining = 42,
            pressureFactors = listOf(
                PressureFactor("High Required Rate", 15f, "Need 9.5 per over"),
                PressureFactor("New Batsman", 10f, "Just came to crease"),
                PressureFactor("Set Batsman", -8f, "Experienced player on 45"),
            ),
        ),
        battingTeam = "India",
    )
}
