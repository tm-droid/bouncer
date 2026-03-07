package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Leaderboard Card
 */

data class LeaderboardEntry(
    val rank: Int,
    val playerName: String,
    val teamName: String,
    val value: String,
    val secondaryValue: String? = null,
    val change: Int = 0, // +1 up, -1 down, 0 same
    val imageUrl: String? = null,
)

@Composable
fun LeaderboardCard(
    title: String,
    category: String, // "Runs", "Wickets", "Economy", "Strike Rate"
    entries: List<LeaderboardEntry>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(entries) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GoldDark, GoldPrimary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title.uppercase(),
                            color = colors.goldDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = category,
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Leaderboard entries
            entries.forEachIndexed { index, entry ->
                LeaderboardEntryRow(
                    entry = entry,
                    progress = animatedProgress.value,
                    index = index,
                )

                if (index < entries.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun LeaderboardEntryRow(
    entry: LeaderboardEntry,
    progress: Float,
    index: Int,
) {
    val colors = BouncerDesignSystem.colors
    val delayedProgress = ((progress - index * 0.08f) / 0.92f).coerceIn(0f, 1f)

    val isTopThree = entry.rank <= 3
    val rankColor = when (entry.rank) {
        1 -> colors.goldDark
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> colors.textTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = delayedProgress
                translationX = (1 - delayedProgress) * 30f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isTopThree) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            rankColor.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.glassSurface,
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isTopThree) {
                        Brush.linearGradient(
                            colors = listOf(rankColor, rankColor.copy(alpha = 0.6f)),
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                colors.glassBorder,
                                colors.glassSurface,
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${entry.rank}",
                color = if (isTopThree) Color.Black else colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Player info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.playerName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Movement indicator
                if (entry.change != 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MovementIndicator(change = entry.change)
                }
            }

            Text(
                text = entry.teamName,
                color = colors.textTertiary,
                fontSize = 11.sp,
            )
        }

        // Value
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.value,
                color = if (isTopThree) rankColor else colors.goldDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            entry.secondaryValue?.let { secondary ->
                Text(
                    text = secondary,
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun MovementIndicator(change: Int) {
    val colors = BouncerDesignSystem.colors
    val (color, arrow) = when {
        change > 0 -> Pair(colors.statusSuccess, "▲")
        change < 0 -> Pair(colors.statusLive, "▼")
        else -> Pair(Color.Gray, "−")
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = arrow,
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${kotlin.math.abs(change)}",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
