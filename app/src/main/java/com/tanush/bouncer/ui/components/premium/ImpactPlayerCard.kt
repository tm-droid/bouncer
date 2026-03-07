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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Impact player card showing most impactful moments
 */

data class ImpactEvent(
    val overNumber: Float,
    val event: String,
    val playerName: String,
    val impactPoints: Float,
    val description: String,
    val isPositive: Boolean,
)

@Composable
fun ImpactPlayerCard(
    playerName: String,
    team: String,
    totalImpact: Float,
    impactEvents: List<ImpactEvent>,
    positiveImpact: Float,
    negativeImpact: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "impact")
    val impactPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(impactEvents) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val impactColor = when {
        totalImpact >= 80 -> colors.statusSuccess
        totalImpact >= 50 -> colors.statusInfo
        totalImpact >= 30 -> colors.statusWarning
        else -> Color.Gray
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
                                    colors = listOf(colors.goldDark, colors.goldPrimary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = impactPulse
                                    scaleY = impactPulse
                                },
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "IMPACT PLAYER",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "$playerName • $team",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Impact score with gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Impact gauge
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12f
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        // Background arc
                        drawArc(
                            color = Color(0xFF2D2D44),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        // Impact arc
                        val sweepAngle = 270f * (totalImpact / 100f) * animatedProgress.value
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(colors.statusLive, colors.statusWarning, colors.statusSuccess),
                                center = center,
                            ),
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(totalImpact * animatedProgress.value).toInt()}",
                            color = impactColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Impact",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                    }
                }

                // Impact breakdown
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImpactBreakdownRow(
                        label = "Positive",
                        value = positiveImpact,
                        color = colors.statusSuccess,
                        progress = animatedProgress.value,
                    )

                    ImpactBreakdownRow(
                        label = "Negative",
                        value = negativeImpact,
                        color = colors.statusLive,
                        progress = animatedProgress.value,
                    )

                    HorizontalDivider(color = Color(0xFF3D3D55), thickness = 1.dp)

                    Row(
                        modifier = Modifier.width(120.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Net",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${if (positiveImpact - negativeImpact >= 0) "+" else ""}${(positiveImpact - negativeImpact).toInt()}",
                            color = if (positiveImpact >= negativeImpact) colors.statusSuccess else colors.statusLive,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Impact timeline
            if (impactEvents.isNotEmpty()) {
                Text(
                    text = "IMPACT MOMENTS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Timeline visualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val centerY = height / 2
                        val maxOvers = 20f

                        // Draw timeline
                        drawLine(
                            color = Color(0xFF3D3D55),
                            start = Offset(0f, centerY),
                            end = Offset(width, centerY),
                            strokeWidth = 2f,
                        )

                        // Draw impact points
                        impactEvents.forEach { event ->
                            val x = (event.overNumber / maxOvers) * width * animatedProgress.value
                            val yOffset = if (event.isPositive) -20f else 20f
                            val pointColor = if (event.isPositive) colors.statusSuccess else colors.statusLive
                            val radius = (event.impactPoints / 20f).coerceIn(4f, 12f)

                            // Draw line to point
                            drawLine(
                                color = pointColor.copy(alpha = 0.5f),
                                start = Offset(x, centerY),
                                end = Offset(x, centerY + yOffset),
                                strokeWidth = 2f,
                            )

                            // Draw impact point
                            drawCircle(
                                color = pointColor,
                                radius = radius * animatedProgress.value,
                                center = Offset(x, centerY + yOffset),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0", color = Color.Gray, fontSize = 10.sp)
                    Text("Overs", color = Color.Gray, fontSize = 10.sp)
                    Text("20", color = Color.Gray, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Event list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    impactEvents.sortedByDescending { it.impactPoints }.take(4).forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (event.isPositive) {
                                            colors.statusSuccess.copy(alpha = 0.2f)
                                        } else {
                                            colors.statusLive.copy(alpha = 0.2f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (event.isPositive) "+" else "-",
                                    color = if (event.isPositive) colors.statusSuccess else colors.statusLive,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.event,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Over ${event.overNumber} • ${event.description}",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                )
                            }

                            Text(
                                text = "${if (event.isPositive) "+" else "-"}${event.impactPoints.toInt()}",
                                color = if (event.isPositive) colors.statusSuccess else colors.statusLive,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactBreakdownRow(
    label: String,
    value: Float,
    color: Color,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.width(120.dp),
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
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }

        Text(
            text = "+${(value * progress).toInt()}",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
