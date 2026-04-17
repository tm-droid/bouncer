package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Fantasy points breakdown card
 *
 * TODO: API Integration
 * - Connect to fantasy sports API (Dream11, MPL, My11Circle) for real-time fantasy points
 * - Implement user's fantasy team integration and selection persistence
 * - Add real-time point calculation service based on live match events
 * - Implement captain/vice-captain bonus multiplier logic
 *
 * TODO: Features
 * - Add player comparison for fantasy selection
 * - Implement projected points based on player form and matchups
 * - Add notification for significant point changes
 * - Cache fantasy data for offline viewing
 *
 * TODO: UI/UX
 * - Add loading skeleton while data fetches
 * - Implement error state with retry option
 * - Add pull-to-refresh for manual updates
 */

data class FantasyPointsData(
    val playerName: String,
    val teamName: String,
    val totalPoints: Int,
    val breakdown: List<FantasyPointBreakdown>,
    val rank: Int,
    val selectionPercentage: Float,
    val captainPercentage: Float,
    val isCaptain: Boolean,
    val isViceCaptain: Boolean,
)

data class FantasyPointBreakdown(
    val category: String,
    val points: Int,
    val description: String,
)

@Composable
fun FantasyPointsCard(
    pointsData: FantasyPointsData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }
    val animatedPoints = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "fantasy")
    val starRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "star",
    )

    LaunchedEffect(pointsData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
        animatedPoints.animateTo(
            targetValue = pointsData.totalPoints.toFloat(),
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
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
                                    colors = listOf(
                                        Color(0xFF9C27B0),
                                        Color(0xFF673AB7),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "FANTASY POINTS",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = pointsData.playerName,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (pointsData.isCaptain) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(hierarchy.secondary.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "C",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                } else if (pointsData.isViceCaptain) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.glassSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "VC",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Total points display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF9C27B0).copy(alpha = 0.3f),
                                Color(0xFF673AB7).copy(alpha = 0.2f),
                            ),
                        ),
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = animatedPoints.value.toInt().toString(),
                        color = colors.textPrimary,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                    )

                    if (pointsData.isCaptain) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "2x Captain bonus",
                                color = hierarchy.secondary.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "(${animatedPoints.value.toInt() / 2} base)",
                                color = Color.Gray,
                                fontSize = 11.sp,
                            )
                        }
                    } else if (pointsData.isViceCaptain) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "1.5x Vice Captain bonus",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (pointsData.rank <= 3) {
                                    hierarchy.secondary.primary.copy(alpha = 0.3f)
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Rank #${pointsData.rank}",
                            color = if (pointsData.rank <= 3) hierarchy.secondary.secondary else colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Points breakdown
            Text(
                text = "POINTS BREAKDOWN",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pointsData.breakdown.forEach { item ->
                    PointsBreakdownRow(
                        breakdown = item,
                        progress = animatedProgress.value,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Spacer(modifier = Modifier.height(14.dp))

            // Selection stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectionStatItem(
                    label = "Selected By",
                    value = "${(pointsData.selectionPercentage * 100).toInt()}%",
                    icon = "👥",
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.1f)),
                )

                SelectionStatItem(
                    label = "Captain By",
                    value = "${(pointsData.captainPercentage * 100).toInt()}%",
                    icon = "👑",
                )
            }
        }
    }
}

@Composable
private fun PointsBreakdownRow(
    breakdown: FantasyPointBreakdown,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2D2D44))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = breakdown.category,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = breakdown.description,
                color = Color.Gray,
                fontSize = 11.sp,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (breakdown.points > 0) {
                        colors.statusSuccess.copy(alpha = 0.2f)
                    } else {
                        colors.statusLive.copy(alpha = 0.2f)
                    },
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (breakdown.points > 0) {
                    "+${(breakdown.points * progress).toInt()}"
                } else {
                    "${(breakdown.points * progress).toInt()}"
                },
                color = if (breakdown.points > 0) colors.statusSuccess else colors.statusLive,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SelectionStatItem(
    label: String,
    value: String,
    icon: String,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = icon,
            fontSize = 20.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
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
fun FantasyPointsCardPreview() {
    val colors = BouncerDesignSystem.colors
    FantasyPointsCard(
        pointsData = FantasyPointsData(
            playerName = "Virat Kohli",
            teamName = "India",
            totalPoints = 142,
            breakdown = listOf(
                FantasyPointBreakdown("Runs", 82, "82 runs scored"),
                FantasyPointBreakdown("Boundaries", 28, "7 fours, 2 sixes"),
                FantasyPointBreakdown("Strike Rate", 16, "SR > 170"),
                FantasyPointBreakdown("50 Bonus", 8, "Half century"),
                FantasyPointBreakdown("Fielding", 8, "1 catch"),
            ),
            rank = 2,
            selectionPercentage = 0.76f,
            captainPercentage = 0.24f,
            isCaptain = true,
            isViceCaptain = false,
        ),
    )
}
