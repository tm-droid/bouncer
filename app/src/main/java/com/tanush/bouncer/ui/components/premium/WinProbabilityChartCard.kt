package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.BlackSurface
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.GoldHairline
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.TextTertiary
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Premium Sleek Win Probability Chart.
 * High-fidelity financial-grade visualization.
 * Updated to BouncerImportance.CRITICAL as it is a flagship metric.
 */

@Composable
fun WinProbabilityChartCard(
    team1Name: String,
    team2Name: String,
    team1Color: Color = BouncerDesignSystem.colors.statusInfo,
    team2Color: Color = BouncerDesignSystem.colors.statusLive,
    probabilityHistory: List<WinProbabilityPoint>,
    currentTeam1Probability: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(probabilityHistory) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.CRITICAL, // CRITICAL: This is the heart of the analytics
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Premium but focused
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(hierarchy.secondary.primary.copy(alpha = 0.1f), CircleShape)
                            .border(0.5.dp, GoldHairline.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = hierarchy.secondary.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "WIN PROBABILITY".uppercase(),
                            color = hierarchy.secondary.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "LIVE STRATEGIC ANALYTICS",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Current probabilities - Sharp Typography
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProbabilityScore(name = team1Name, percent = currentTeam1Probability, color = team1Color, progress = animatedProgress.value)
                ProbabilityScore(name = team2Name, percent = 100 - currentTeam1Probability, color = team2Color, progress = animatedProgress.value, isRightAligned = true)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Probability bar - Precision line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight((currentTeam1Probability * animatedProgress.value).coerceAtLeast(0.1f))
                            .background(team1Color),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(((100 - currentTeam1Probability) * animatedProgress.value).coerceAtLeast(0.1f))
                            .background(team2Color),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Capture color for Canvas scope
            val goldPrimary = hierarchy.secondary.primary

            // Probability chart - Technical Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val points = probabilityHistory
                    val maxOver = points.maxOfOrNull { it.over } ?: 20f

                    // Draw subtle technical grid (dashed)
                    val probGridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    for (i in 0..4) {
                        val y = height * (i / 4f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 0.5f,
                            pathEffect = probGridDash,
                        )
                    }

                    // 50% Baseline - Dotted Technical
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(0f, height / 2),
                        end = Offset(width, height / 2),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                    )

                    if (points.size >= 2) {
                        val team1Path = Path()
                        points.forEachIndexed { index, point ->
                            val x = (point.over / maxOver) * width * animatedProgress.value
                            val y1 = height - (point.team1Probability / 100f) * height
                            if (index == 0) team1Path.moveTo(x, y1) else team1Path.lineTo(x, y1)
                        }

                        // Glow layer for the line
                        drawPath(
                            path = team1Path,
                            color = team1Color.copy(alpha = 0.2f),
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                        )

                        // Sharp Line
                        drawPath(
                            path = team1Path,
                            color = team1Color,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                        )

                        // Key moment markers
                        points.filter { it.keyEvent != null }.forEach { point ->
                            val x = (point.over / maxOver) * width * animatedProgress.value
                            val y = height - (point.team1Probability / 100f) * height
                            
                            drawCircle(color = goldPrimary, radius = 4.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = BlackSurface, radius = 2.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Key moments - Minimalist Technical List
            val keyEvents = probabilityHistory.filter { it.keyEvent != null }.takeLast(2)
            if (keyEvents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "KEY STRATEGIC MOMENTS",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    keyEvents.forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(goldPrimary))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "OV ${event.over}", 
                                color = TextTertiary, 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Black, 
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                text = event.keyEvent ?: "", 
                                color = TextPrimary, 
                                fontSize = 12.sp, 
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbabilityScore(name: String, percent: Float, color: Color, progress: Float, isRightAligned: Boolean = false) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start) {
        Text(text = name, color = color.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        Text(
            text = "${(percent * progress).toInt()}%", 
            color = TextPrimary, 
            fontSize = 30.sp, 
            fontWeight = FontWeight.Black, 
            letterSpacing = (-1).sp
        )
    }
}
