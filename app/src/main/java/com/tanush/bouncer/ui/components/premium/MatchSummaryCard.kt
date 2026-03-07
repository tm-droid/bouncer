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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.BoundaryFour
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match Summary Card
 *
 * TODO: Integration Requirements:
 * - Connect to live match data WebSocket for real-time updates
 * - Fetch team logos from team data service/CDN
 * - Calculate and display required run rate dynamically
 * - Add match result prediction probability
 * - Show innings comparison when applicable
 * - Add share match summary functionality
 * - Navigate to detailed scorecard on tap
 * - Handle Test match day/session context
 * - Add loading skeleton while fetching match data
 * - Cache recent match summaries for offline viewing
 */

data class MatchSummaryTeam(
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val score: String,
    val overs: String,
    val runRate: Double,
)

data class TopPerformer(
    val name: String,
    val stat: String,
    val category: String, // "Batting", "Bowling"
    val imageUrl: String?,
)

@Composable

fun MatchSummaryCard(
    matchTitle: String,
    matchResult: String,
    winningTeam: String?,
    winMargin: String,
    team1: MatchSummaryTeam,
    team2: MatchSummaryTeam,
    topPerformers: List<TopPerformer>,
    venue: String,
    matchDate: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "summary")

    val trophyRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trophy",
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
        ),
        label = "shimmer",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(matchResult) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface1),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
    ) {
        Box {
            // Capture colors for Canvas scopes
            val goldPrimaryColor = colors.goldPrimary
            val goldDarkColor = colors.goldDark

            // Victory gradient background
            if (winningTeam != null) {
                Canvas(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                goldPrimaryColor.copy(alpha = 0.15f * animatedProgress.value),
                                Color.Transparent,
                            ),
                        ),
                    )

                    // Shimmer effect
                    val shimmerOffset = shimmerProgress * size.width * 2 - size.width
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                goldDarkColor.copy(alpha = 0.1f),
                                Color.Transparent,
                            ),
                            start = Offset(shimmerOffset, 0f),
                            end = Offset(shimmerOffset + 200f, size.height),
                        ),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = "MATCH COMPLETED",
                            color = colors.statusSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = matchTitle,
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = venue,
                            color = colors.textTertiary,
                            fontSize = 12.sp,
                        )
                    }

                    if (winningTeam != null) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .graphicsLayer {
                                    rotationZ = trophyRotation
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = colors.goldDark,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Teams comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Team 1
                    TeamSummaryColumn(
                        team = team1,
                        isWinner = team1.name == winningTeam,
                        progress = animatedProgress.value,
                    )

                    // VS
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "VS",
                            color = colors.goldDark,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Team 2
                    TeamSummaryColumn(
                        team = team2,
                        isWinner = team2.name == winningTeam,
                        progress = animatedProgress.value,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Result banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.3f),
                                    colors.goldDark.copy(alpha = 0.2f),
                                    colors.goldPrimary.copy(alpha = 0.3f),
                                ),
                            ),
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = matchResult,
                            color = colors.goldDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        if (winMargin.isNotEmpty()) {
                            Text(
                                text = winMargin,
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Top performers
                if (topPerformers.isNotEmpty()) {
                    Text(
                        text = "TOP PERFORMERS",
                        color = colors.goldDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        topPerformers.take(2).forEach { performer ->
                            TopPerformerCard(
                                performer = performer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Match date
                Text(
                    text = matchDate,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun TeamSummaryColumn(
    team: MatchSummaryTeam,
    isWinner: Boolean,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    val goldDark = colors.goldDark
    val goldPrimary = colors.goldPrimary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            alpha = progress
            scaleX = 0.8f + 0.2f * progress
            scaleY = 0.8f + 0.2f * progress
        },
    ) {
        // Logo with winner glow
        Box(
            modifier = Modifier.size(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isWinner) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                goldDark.copy(alpha = 0.5f),
                                Color.Transparent,
                            ),
                        ),
                        radius = size.minDimension / 2 + 10.dp.toPx(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        if (isWinner) {
                            Brush.linearGradient(
                                colors = listOf(goldPrimary, goldDark),
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.1f),
                                    Color.White.copy(alpha = 0.05f),
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (team.logoUrl != null) {
                    CoilImage(
                        model = team.logoUrl,
                        contentDescription = team.name,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Text(
                        text = team.shortName.take(3),
                        color = if (isWinner) Color.Black else colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = team.shortName,
            color = if (isWinner) colors.goldDark else colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = team.score,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "(${team.overs})",
            color = colors.textTertiary,
            fontSize = 12.sp,
        )

        if (isWinner) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.statusSuccess.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "WINNER",
                    color = colors.statusSuccess,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TopPerformerCard(
    performer: TopPerformer,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .padding(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (performer.category == "Batting") {
                                    listOf(colors.statusInfo, colors.statusWarning)
                                } else {
                                    listOf(colors.statusInfo, colors.statusSuccess)
                                },
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (performer.imageUrl != null) {
                        CoilImage(
                            model = performer.imageUrl,
                            contentDescription = performer.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = performer.name.take(1),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = performer.name.split(" ").lastOrNull() ?: performer.name,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = performer.category,
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = performer.stat,
                color = colors.goldDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
