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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Live scoring graph card
 */

data class LiveScoringData(
    val team1Name: String,
    val team2Name: String,
    val team1Scores: List<Int>, // Cumulative scores per over
    val team2Scores: List<Int>,
    val currentOver: Int,
    val projectedScore: Int,
    val totalOvers: Int = 20,
)

@Composable
fun LiveScoringGraphCard(
    scoringData: LiveScoringData,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "scoring")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(scoringData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        )
    }

    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val statusInfoC = colors.statusInfo
    val goldDarkC = hierarchy.secondary.primary
    val statusSuccessC = colors.statusSuccess

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
                                    colors = listOf(colors.statusInfo, Color(0xFF2196F3)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "LIVE SCORING GRAPH",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "Run progression by over",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Live indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp * livePulse)
                            .clip(CircleShape)
                            .background(colors.statusInfo),
                    )
                    Text(
                        text = "LIVE",
                        color = colors.statusInfo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LegendItem(
                    color = colors.statusInfo,
                    label = scoringData.team1Name,
                    score = scoringData.team1Scores.lastOrNull() ?: 0,
                )
                LegendItem(
                    color = hierarchy.secondary.primary,
                    label = scoringData.team2Name,
                    score = if (scoringData.team2Scores.isNotEmpty()) scoringData.team2Scores.last() else null,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(16.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    val maxScore = maxOf(
                        scoringData.team1Scores.maxOrNull() ?: 0,
                        scoringData.team2Scores.maxOrNull() ?: 0,
                        scoringData.projectedScore,
                    ).coerceAtLeast(1)

                    val maxOvers = maxOf(
                        scoringData.team1Scores.size,
                        scoringData.team2Scores.size,
                        20,
                    )

                    // Draw grid lines (dashed for subtlety)
                    val gridLines = 4
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    for (i in 0..gridLines) {
                        val y = height - (height * i / gridLines)
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 0.5.dp.toPx(),
                            pathEffect = gridDash,
                        )
                    }

                    // Draw vertical over markers
                    val overMarkers = listOf(0, 6, 10, 15, 20).filter { it <= maxOvers }
                    overMarkers.forEach { over ->
                        val x = width * over / maxOvers
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // Draw team 2 scores (if available)
                    if (scoringData.team2Scores.isNotEmpty()) {
                        val path2 = Path().apply {
                            scoringData.team2Scores.forEachIndexed { index, score ->
                                val x = width * index / maxOvers
                                val y = height - (height * score / maxScore)
                                if (index == 0) {
                                    moveTo(x, y)
                                } else {
                                    lineTo(x, y)
                                }
                            }
                        }

                        // Fill gradient
                        val fill2 = Path().apply {
                            addPath(path2)
                            lineTo(width * (scoringData.team2Scores.size - 1) / maxOvers, height)
                            lineTo(0f, height)
                            close()
                        }

                        drawPath(
                            path = fill2,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    goldDarkC.copy(alpha = 0.3f),
                                    goldDarkC.copy(alpha = 0f),
                                ),
                            ),
                        )

                        drawPath(
                            path = path2,
                            color = goldDarkC,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }

                    // Draw team 1 scores
                    if (scoringData.team1Scores.isNotEmpty()) {
                        val animatedScores = scoringData.team1Scores.take(
                            (scoringData.team1Scores.size * animatedProgress.value).toInt().coerceAtLeast(1),
                        )

                        val path1 = Path().apply {
                            animatedScores.forEachIndexed { index, score ->
                                val x = width * index / maxOvers
                                val y = height - (height * score / maxScore)
                                if (index == 0) {
                                    moveTo(x, y)
                                } else {
                                    lineTo(x, y)
                                }
                            }
                        }

                        // Fill gradient
                        val fill1 = Path().apply {
                            addPath(path1)
                            lineTo(width * (animatedScores.size - 1) / maxOvers, height)
                            lineTo(0f, height)
                            close()
                        }

                        drawPath(
                            path = fill1,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    statusInfoC.copy(alpha = 0.4f),
                                    statusInfoC.copy(alpha = 0f),
                                ),
                            ),
                        )

                        drawPath(
                            path = path1,
                            color = statusInfoC,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )

                        // Data-point markers at powerplay/milestone overs
                        val milestoneOvers = setOf(0, 5, 9, 14, 19)
                        animatedScores.forEachIndexed { index, score ->
                            if (index in milestoneOvers && index < animatedScores.size - 1) {
                                val mx = width * index / maxOvers
                                val my = height - (height * score / maxScore)
                                drawCircle(
                                    color = statusInfoC,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(mx, my),
                                )
                            }
                        }

                        // Current point indicator
                        if (animatedScores.isNotEmpty()) {
                            val lastIndex = animatedScores.size - 1
                            val lastX = width * lastIndex / maxOvers
                            val lastY = height - (height * animatedScores.last() / maxScore)

                            // Glow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        statusInfoC.copy(alpha = 0.5f * livePulse),
                                        statusInfoC.copy(alpha = 0f),
                                    ),
                                    center = Offset(lastX, lastY),
                                    radius = 20.dp.toPx() * livePulse,
                                ),
                                center = Offset(lastX, lastY),
                                radius = 20.dp.toPx() * livePulse,
                            )

                            drawCircle(
                                color = statusInfoC,
                                radius = 6.dp.toPx(),
                                center = Offset(lastX, lastY),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(lastX, lastY),
                            )
                        }
                    }

                    // Projected score line
                    if (scoringData.projectedScore > 0 && scoringData.team1Scores.isNotEmpty()) {
                        val projectedY = height - (height * scoringData.projectedScore / maxScore)

                        drawLine(
                            color = statusSuccessC.copy(alpha = 0.5f),
                            start = Offset(0f, projectedY),
                            end = Offset(width, projectedY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                        )
                    }
                }

                // Projected score label
                if (scoringData.projectedScore > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.statusSuccess.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Projected: ${scoringData.projectedScore}",
                            color = colors.statusSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Over markers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("0", "6", "10", "15", "20").forEach { over ->
                    Text(
                        text = over,
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScoringStatItem(
                    label = "Current Over",
                    value = "${scoringData.currentOver}",
                    subtext = "of ${scoringData.totalOvers}",
                )

                ScoringStatItem(
                    label = "Current Score",
                    value = "${scoringData.team1Scores.lastOrNull() ?: 0}",
                    subtext = scoringData.team1Name,
                )

                if (scoringData.team2Scores.isNotEmpty()) {
                    val target = scoringData.team2Scores.last()
                    val current = scoringData.team1Scores.lastOrNull() ?: 0
                    val required = target - current + 1
                    val oversRemaining = scoringData.totalOvers - scoringData.currentOver
                    val totalBalls = scoringData.totalOvers * CricketConstants.BALLS_PER_OVER
                    val ballsBowled = scoringData.currentOver * CricketConstants.BALLS_PER_OVER
                    val rrr = MatchProjections.requiredRunRate(target + 1, current, ballsBowled, totalBalls) ?: 0.0

                    ScoringStatItem(
                        label = "Required RR",
                        value = String.format("%.2f", rrr),
                        subtext = "$required from $oversRemaining",
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    score: Int?,
) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

        if (score != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($score)",
                color = Color.Gray,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ScoringStatItem(
    label: String,
    value: String,
    subtext: String,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtext,
            color = Color.Gray,
            fontSize = 10.sp,
        )
    }
}
