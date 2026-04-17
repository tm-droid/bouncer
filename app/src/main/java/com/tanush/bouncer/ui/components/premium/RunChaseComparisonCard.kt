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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.abs
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Run chase comparison showing both innings
 *
 * TODO: Integration Requirements:
 * - Track both innings progress in real-time
 * - Show comparative graph with both scoring curves
 * - Highlight where chasing team is ahead/behind
 * - Add vertical markers for wickets
 * - Show run rate comparison at each over
 * - Display key moments affecting the chase
 * - Add projected outcome based on trends
 * - Animate second innings progression
 * - Support touch for detailed over comparison
 * - Cache comparison data for match analysis
 */

data class InningsChasePoint(
    val over: Float,
    val runs: Int,
    val wickets: Int,
)

data class ChaseData(
    val team1Name: String,
    val team1Points: List<InningsChasePoint>,
    val team1Final: Int,
    val team2Name: String,
    val team2Points: List<InningsChasePoint>,
    val target: Int,
    val currentOver: Float,
)

@Composable
fun RunChaseComparisonCard(
    chaseData: ChaseData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(chaseData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val currentTeam2Runs = chaseData.team2Points.lastOrNull()?.runs ?: 0
    val runsNeeded = (chaseData.target - currentTeam2Runs).coerceAtLeast(0)
    val oversRemaining = (20f - chaseData.currentOver).coerceAtLeast(0f)
    val requiredRate = if (oversRemaining > 0) runsNeeded / oversRemaining else 0f

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
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "RUN CHASE",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "Innings Comparison",
                            color = Color.Gray,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Target
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Target",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = "${chaseData.target}",
                        color = hierarchy.secondary.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chase status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ChaseStatColumn(
                    value = "$runsNeeded",
                    label = "Runs Needed",
                    color = if (runsNeeded > 60) colors.statusLive else if (runsNeeded > 30) colors.statusWarning else colors.statusSuccess,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(Color(0xFF3D3D55)),
                )

                ChaseStatColumn(
                    value = String.format("%.1f", oversRemaining),
                    label = "Overs Left",
                    color = colors.textPrimary,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(Color(0xFF3D3D55)),
                )

                ChaseStatColumn(
                    value = String.format("%.2f", requiredRate),
                    label = "Req. Rate",
                    color = when {
                        requiredRate > 12 -> colors.statusLive
                        requiredRate > 9 -> colors.statusWarning
                        else -> colors.statusSuccess
                    },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Capture color for Canvas scope
            val goldDark = hierarchy.secondary.primary
            val statusInfoColor = colors.statusInfo

            // Comparison chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(16.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val maxRuns = maxOf(chaseData.target, chaseData.team1Final, currentTeam2Runs) + 20
                    val maxOvers = 20f

                    // Draw grid
                    for (i in 0..4) {
                        val y = height * (1 - i / 4f)
                        drawLine(
                            color = Color(0xFF3D3D55),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f,
                        )
                    }

                    // Draw target line
                    val targetY = height * (1 - chaseData.target.toFloat() / maxRuns)
                    drawLine(
                        color = goldDark.copy(alpha = 0.6f),
                        start = Offset(0f, targetY),
                        end = Offset(width, targetY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)),
                    )

                    // Draw Team 1 (first innings) line
                    if (chaseData.team1Points.size >= 2) {
                        val team1Path = Path()
                        chaseData.team1Points.forEachIndexed { index, point ->
                            val x = (point.over / maxOvers) * width
                            val y = height * (1 - point.runs.toFloat() / maxRuns)
                            if (index == 0) {
                                team1Path.moveTo(x * animatedProgress.value, y)
                            } else {
                                team1Path.lineTo(x * animatedProgress.value, y)
                            }
                        }

                        // Fill
                        val fillPath = Path().apply {
                            addPath(team1Path)
                            val lastPoint = chaseData.team1Points.last()
                            val lastX = (lastPoint.over / maxOvers) * width * animatedProgress.value
                            lineTo(lastX, height)
                            lineTo(0f, height)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(statusInfoColor.copy(alpha = 0.3f), Color.Transparent),
                            ),
                        )

                        drawPath(
                            path = team1Path,
                            color = statusInfoColor,
                            style = Stroke(width = 3f, cap = StrokeCap.Round),
                        )
                    }

                    // Draw Team 2 (chasing) line
                    if (chaseData.team2Points.size >= 2) {
                        val team2Path = Path()
                        chaseData.team2Points.forEachIndexed { index, point ->
                            val x = (point.over / maxOvers) * width
                            val y = height * (1 - point.runs.toFloat() / maxRuns)
                            if (index == 0) {
                                team2Path.moveTo(x * animatedProgress.value, y)
                            } else {
                                team2Path.lineTo(x * animatedProgress.value, y)
                            }
                        }

                        // Fill
                        val fillPath = Path().apply {
                            addPath(team2Path)
                            val lastPoint = chaseData.team2Points.last()
                            val lastX = (lastPoint.over / maxOvers) * width * animatedProgress.value
                            lineTo(lastX, height)
                            lineTo(0f, height)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(colors.statusSuccess.copy(alpha = 0.3f), Color.Transparent),
                            ),
                        )

                        drawPath(
                            path = team2Path,
                            color = colors.statusSuccess,
                            style = Stroke(width = 3f, cap = StrokeCap.Round),
                        )

                        // Current position dot
                        val lastPoint = chaseData.team2Points.last()
                        val lastX = (lastPoint.over / maxOvers) * width * animatedProgress.value
                        val lastY = height * (1 - lastPoint.runs.toFloat() / maxRuns)
                        drawCircle(
                            color = colors.statusSuccess,
                            radius = 8f,
                            center = Offset(lastX, lastY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(lastX, lastY),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(0, 5, 10, 15, 20).forEach { over ->
                    Text(
                        text = "$over",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.statusInfo),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${chaseData.team1Name} (${chaseData.team1Final})",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.statusSuccess),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${chaseData.team2Name} ($currentTeam2Runs*)",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(hierarchy.secondary.primary),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Target",
                        color = hierarchy.secondary.primary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stage comparison
            val team1AtCurrentOver = chaseData.team1Points.lastOrNull { it.over <= chaseData.currentOver }?.runs ?: 0
            val difference = currentTeam2Runs - team1AtCurrentOver

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (difference >= 0) {
                            colors.statusSuccess.copy(alpha = 0.15f)
                        } else {
                            colors.statusLive.copy(alpha = 0.15f)
                        },
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "At this stage (${String.format("%.1f", chaseData.currentOver)} overs)",
                    color = Color.Gray,
                    fontSize = 12.sp,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (difference >= 0) "Ahead by " else "Behind by ",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "${abs(difference)} runs",
                        color = if (difference >= 0) colors.statusSuccess else colors.statusLive,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChaseStatColumn(
    value: String,
    label: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun RunChaseComparisonCardPreview() {
    val colors = BouncerDesignSystem.colors
    val team1Points = listOf(
        InningsChasePoint(0f, 0, 0),
        InningsChasePoint(4f, 38, 1),
        InningsChasePoint(8f, 68, 2),
        InningsChasePoint(12f, 102, 3),
        InningsChasePoint(16f, 145, 5),
        InningsChasePoint(20f, 185, 7),
    )

    val team2Points = listOf(
        InningsChasePoint(0f, 0, 0),
        InningsChasePoint(4f, 42, 0),
        InningsChasePoint(8f, 78, 1),
        InningsChasePoint(12f, 115, 2),
        InningsChasePoint(14.3f, 138, 3),
    )

    RunChaseComparisonCard(
        chaseData = ChaseData(
            team1Name = "Australia",
            team1Points = team1Points,
            team1Final = 185,
            team2Name = "India",
            team2Points = team2Points,
            target = 186,
            currentOver = 14.3f,
        ),
    )
}
