package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Score
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Scoring summary card
 *
 * TODO: Integration Requirements:
 * - Fetch live match score from match data service
 * - Update score in real-time via WebSocket
 * - Show match status with context
 * - Add quick stats breakdown
 * - Display run rate comparison
 * - Show wickets and overs remaining
 * - Add match result for completed matches
 * - Navigate to detailed scorecard on tap
 * - Support all match formats
 * - Cache score data for quick display
 */

data class ScoringSummaryData(
    val team1Name: String,
    val team2Name: String,
    val team1Score: String,
    val team2Score: String,
    val matchStatus: String,
    val scoringBreakdown: List<ScoringBreakdownItem>,
    val extras: ExtrasBreakdown,
    val runRate: Float,
    val topScorer: String,
    val topWicketTaker: String,
)

data class ScoringBreakdownItem(
    val label: String,
    val team1Value: Int,
    val team2Value: Int,
)

data class ExtrasBreakdown(
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,
)

@Composable
fun ScoringSummaryCard(
    summaryData: ScoringSummaryData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "scoring")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(summaryData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
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
                                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Score,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SCORING SUMMARY",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = summaryData.matchStatus,
                            color = Color.White.copy(alpha = pulseAlpha),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scores display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamScoreColumn(
                    teamName = summaryData.team1Name,
                    score = summaryData.team1Score,
                    isLeading = true,
                )

                Text(
                    text = "VS",
                    color = hierarchy.secondary.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )

                TeamScoreColumn(
                    teamName = summaryData.team2Name,
                    score = summaryData.team2Score,
                    isLeading = false,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scoring breakdown comparison
            Text(
                text = "SCORING BREAKDOWN",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                summaryData.scoringBreakdown.forEach { item ->
                    ScoringComparisonBar(
                        item = item,
                        progress = animatedProgress.value,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extras breakdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = "EXTRAS",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ExtrasItem(
                            label = "Wides",
                            value = summaryData.extras.wides,
                            color = colors.statusWarning,
                        )
                        ExtrasItem(
                            label = "No Balls",
                            value = summaryData.extras.noBalls,
                            color = colors.statusLive,
                        )
                        ExtrasItem(
                            label = "Byes",
                            value = summaryData.extras.byes,
                            color = colors.statusInfo,
                        )
                        ExtrasItem(
                            label = "Leg Byes",
                            value = summaryData.extras.legByes,
                            color = colors.statusSuccess,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Total extras bar
                    val totalExtras = with(summaryData.extras) { wides + noBalls + byes + legByes }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total: $totalExtras runs",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "${String.format("%.1f", (totalExtras / summaryData.runRate) * 100 / 100)}% of scoring",
                            color = Color.Gray,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Top performers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopPerformerChip(
                    title = "Top Scorer",
                    name = summaryData.topScorer,
                    icon = Icons.Outlined.SportsCricket,
                    modifier = Modifier.weight(1f),
                )

                TopPerformerChip(
                    title = "Top Wicket Taker",
                    name = summaryData.topWicketTaker,
                    icon = Icons.Outlined.EmojiEvents,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TeamScoreColumn(
    teamName: String,
    score: String,
    isLeading: Boolean,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = teamName,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = score,
            color = if (isLeading) hierarchy.secondary.primary else colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ScoringComparisonBar(
    item: ScoringBreakdownItem,
    progress: Float,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val total = (item.team1Value + item.team2Value).coerceAtLeast(1)
    val team1Ratio = item.team1Value.toFloat() / total

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${item.team1Value}",
                color = colors.statusInfo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.label,
                color = Color.Gray,
                fontSize = 11.sp,
            )
            Text(
                text = "${item.team2Value}",
                color = hierarchy.secondary.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((team1Ratio * progress).coerceAtLeast(0.05f))
                    .background(colors.statusInfo),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(((1f - team1Ratio) * progress).coerceAtLeast(0.05f))
                    .background(hierarchy.secondary.primary),
            )
        }
    }
}

@Composable
private fun ExtrasItem(
    label: String,
    value: Int,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun TopPerformerChip(
    title: String,
    name: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2D2D44))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 9.sp,
            )
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ScoringSummaryCardPreview() {
    val colors = BouncerDesignSystem.colors
    ScoringSummaryCard(
        summaryData = ScoringSummaryData(
            team1Name = "India",
            team2Name = "Australia",
            team1Score = "186/4 (20)",
            team2Score = "178/9 (20)",
            matchStatus = "India won by 8 runs",
            scoringBreakdown = listOf(
                ScoringBreakdownItem("Singles", 48, 52),
                ScoringBreakdownItem("Twos", 24, 18),
                ScoringBreakdownItem("Threes", 6, 3),
                ScoringBreakdownItem("Fours", 56, 44),
                ScoringBreakdownItem("Sixes", 42, 48),
            ),
            extras = ExtrasBreakdown(
                wides = 5,
                noBalls = 2,
                byes = 3,
                legByes = 2,
            ),
            runRate = 9.3f,
            topScorer = "Kohli 82(56)",
            topWicketTaker = "Bumrah 3/24",
        ),
    )
}
