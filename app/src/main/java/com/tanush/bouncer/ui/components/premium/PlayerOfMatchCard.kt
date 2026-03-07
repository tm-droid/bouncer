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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Player of the match prediction card
 *
 * TODO: Integration Requirements:
 * - Build POM prediction model based on performance metrics
 * - Calculate real-time POM probability percentages
 * - Update predictions as match progresses
 * - Show key contributions for each candidate
 * - Factor in match impact moments
 * - Add historical POM patterns for context
 * - Display official POM after match ends
 * - Navigate to player profile on tap
 * - Compare with fan votes/polls
 * - Cache prediction data for analysis
 */

data class PlayerPerformance(
    val name: String,
    val team: String,
    val role: String,
    val runs: Int?,
    val balls: Int?,
    val wickets: Int?,
    val overs: String?,
    val runsConceded: Int?,
    val catches: Int,
    val runOuts: Int,
    val impactScore: Float,
    val keyContributions: List<String>,
)

@Composable
fun PlayerOfMatchCard(
    candidates: List<PlayerPerformance>,
    matchStatus: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(candidates) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pom")
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotate",
    )

    val topCandidate = candidates.maxByOrNull { it.impactScore }
    val sortedCandidates = candidates.sortedByDescending { it.impactScore }

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
                            Icons.Default.Stars,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = starRotation },
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "PLAYER OF THE MATCH",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = matchStatus,
                            color = Color.Gray,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Top candidate showcase
            topCandidate?.let { player ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.3f),
                                    Color(0xFF2D2D44),
                                ),
                            ),
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Player avatar placeholder
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(colors.goldDark, colors.goldPrimary),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = player.name.split(" ").take(2).map { it.first() }.joinToString(""),
                                color = Color.Black,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LIKELY WINNER",
                                color = colors.goldDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = player.name,
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            Text(
                                text = "${player.team} • ${player.role}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Performance
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                player.runs?.let { runs ->
                                    Column {
                                        Text(
                                            text = "$runs",
                                            color = colors.textPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "runs",
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }

                                player.wickets?.let { wickets ->
                                    if (wickets > 0) {
                                        Column {
                                            Text(
                                                text = "$wickets/${player.runsConceded ?: 0}",
                                                color = colors.textPrimary,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = "wickets",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }

                                if (player.catches > 0 || player.runOuts > 0) {
                                    Column {
                                        Text(
                                            text = "${player.catches + player.runOuts}",
                                            color = colors.textPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "catches",
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }

                        // Impact score
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(60.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 6f
                                    val radius = (size.minDimension - strokeWidth) / 2
                                    val center = Offset(size.width / 2, size.height / 2)

                                    // Background arc
                                    drawArc(
                                        color = Color(0xFF3D3D55),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                    )

                                    // Progress arc
                                    val sweepAngle = 360f * (player.impactScore / 100f) * animatedProgress.value
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            colors = listOf(colors.goldDark, colors.goldPrimary, colors.goldDark),
                                            center = center,
                                        ),
                                        startAngle = -90f,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                    )
                                }

                                Text(
                                    text = "${(player.impactScore * animatedProgress.value).toInt()}",
                                    color = colors.goldDark,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Text(
                                text = "Impact",
                                color = Color.Gray,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                // Key contributions
                if (player.keyContributions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2D2D44))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "KEY CONTRIBUTIONS",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        player.keyContributions.forEach { contribution ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "★",
                                    color = colors.goldDark,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = contribution,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Other candidates
            if (sortedCandidates.size > 1) {
                Text(
                    text = "OTHER CONTENDERS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortedCandidates.drop(1).take(3).forEachIndexed { index, player ->
                        val rowAlpha = (animatedProgress.value * 4 - index).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = rowAlpha }
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2D2D44))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 2}",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp),
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = player.name,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )

                                Row {
                                    player.runs?.let { runs ->
                                        Text(
                                            text = "$runs runs",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    player.wickets?.let { wickets ->
                                        if (wickets > 0) {
                                            if (player.runs != null) {
                                                Text(" • ", color = Color.Gray, fontSize = 11.sp)
                                            }
                                            Text(
                                                text = "$wickets wkts",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "${player.impactScore.toInt()}",
                                color = colors.goldDark.copy(alpha = 0.7f),
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
fun PlayerOfMatchCardPreview() {
    val colors = BouncerDesignSystem.colors
    val candidates = listOf(
        PlayerPerformance(
            name = "Virat Kohli",
            team = "India",
            role = "Batsman",
            runs = 89,
            balls = 56,
            wickets = null,
            overs = null,
            runsConceded = null,
            catches = 1,
            runOuts = 0,
            impactScore = 92f,
            keyContributions = listOf(
                "Match-winning 89 off 56 balls",
                "Key partnership of 112 runs",
                "Hit winning boundary",
            ),
        ),
        PlayerPerformance(
            name = "Jasprit Bumrah",
            team = "India",
            role = "Bowler",
            runs = null,
            balls = null,
            wickets = 3,
            overs = "4",
            runsConceded = 22,
            catches = 0,
            runOuts = 0,
            impactScore = 78f,
            keyContributions = listOf("3/22 in crucial spell"),
        ),
        PlayerPerformance(
            name = "Rohit Sharma",
            team = "India",
            role = "Batsman",
            runs = 45,
            balls = 32,
            wickets = null,
            overs = null,
            runsConceded = null,
            catches = 2,
            runOuts = 0,
            impactScore = 65f,
            keyContributions = listOf(),
        ),
    )

    PlayerOfMatchCard(
        candidates = candidates,
        matchStatus = "India chasing 186",
    )
}
