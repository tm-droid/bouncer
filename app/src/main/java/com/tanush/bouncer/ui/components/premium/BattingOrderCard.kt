package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
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
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Team batting order card showing remaining batsmen
 *
 * TODO: Integration Requirements:
 * - Fetch batting lineup from team/match data service
 * - Update status in real-time (batting/out/yet to bat)
 * - Show player roles and batting positions
 * - Highlight night watchman or promoted batters
 * - Add player mini-profiles on expand
 * - Track impact player substitutions (IPL)
 * - Show average batting position stats
 * - Navigate to player detail on tap
 * - Add injury/unavailable status indicators
 * - Cache lineup for offline reference
 */

data class BattingOrderEntry(
    val position: Int,
    val name: String,
    val role: String,
    val status: BattingStatus,
    val runs: Int? = null,
    val balls: Int? = null,
    val isOut: Boolean = false,
    val dismissalInfo: String? = null,
    val strikeRate: Float? = null,
)

enum class BattingStatus {
    YET_TO_BAT, ON_STRIKE, NON_STRIKER, OUT, RETIRED_HURT
}

@Composable
fun BattingOrderCard(
    teamName: String,
    battingOrder: List<BattingOrderEntry>,
    wicketsFallen: Int,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(battingOrder) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
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
                            Icons.Default.Leaderboard,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "BATTING ORDER",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = teamName,
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Wickets remaining
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${10 - wicketsFallen}",
                        color = if (wicketsFallen >= 7) colors.statusLive else hierarchy.secondary.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "wickets left",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Batting order list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                battingOrder.forEachIndexed { index, entry ->
                    val entryAlpha = (animatedProgress.value * battingOrder.size - index).coerceIn(0f, 1f)

                    BattingOrderRow(
                        entry = entry,
                        alpha = entryAlpha,
                    )

                    if (index < battingOrder.size - 1) {
                        HorizontalDivider(color = Color(0xFF3D3D55), thickness = 1.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatusLegendItem(color = colors.statusInfo, text = "On Strike")
                StatusLegendItem(color = colors.statusSuccess, text = "Non-Striker")
                StatusLegendItem(color = colors.statusLive, text = "Out")
                StatusLegendItem(color = Color.Gray, text = "Yet to Bat")
            }
        }
    }
}

@Composable
private fun BattingOrderRow(
    entry: BattingOrderEntry,
    alpha: Float,
) {
    val colors = BouncerDesignSystem.colors
    val statusColor = when (entry.status) {
        BattingStatus.ON_STRIKE -> colors.statusInfo
        BattingStatus.NON_STRIKER -> colors.statusSuccess
        BattingStatus.OUT -> colors.statusLive
        BattingStatus.YET_TO_BAT -> Color.Gray
        BattingStatus.RETIRED_HURT -> colors.statusWarning
    }

    val infiniteTransition = rememberInfiniteTransition(label = "batting")
    val strikeGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (entry.status == BattingStatus.ON_STRIKE) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.statusInfo.copy(alpha = 0.15f * strikeGlow))
                        .padding(8.dp)
                } else {
                    Modifier.padding(8.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Position number
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.2f))
                .then(
                    if (entry.status == BattingStatus.ON_STRIKE || entry.status == BattingStatus.NON_STRIKER) {
                        Modifier.border(2.dp, statusColor, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${entry.position}",
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and role
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    color = if (entry.status == BattingStatus.OUT) Color.Gray else colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )

                if (entry.status == BattingStatus.ON_STRIKE) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "*",
                        color = colors.statusInfo,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = when (entry.status) {
                    BattingStatus.OUT -> entry.dismissalInfo ?: "Out"
                    BattingStatus.RETIRED_HURT -> "Retired Hurt"
                    else -> entry.role
                },
                color = if (entry.status == BattingStatus.OUT) colors.statusLive.copy(alpha = 0.7f) else Color.Gray,
                fontSize = 11.sp,
            )
        }

        // Score
        if (entry.runs != null) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${entry.runs}",
                        color = when (entry.status) {
                            BattingStatus.ON_STRIKE, BattingStatus.NON_STRIKER -> colors.textPrimary
                            else -> Color.Gray
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (entry.balls != null) {
                        Text(
                            text = " (${entry.balls})",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                    }
                }

                entry.strikeRate?.let { sr ->
                    Text(
                        text = "SR: ${String.format("%.1f", sr)}",
                        color = when {
                            sr >= 150 -> colors.statusSuccess
                            sr >= 100 -> colors.textPrimary
                            else -> colors.statusWarning
                        },
                        fontSize = 10.sp,
                    )
                }
            }
        } else if (entry.status == BattingStatus.YET_TO_BAT) {
            Text(
                text = "DNB",
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatusLegendItem(
    color: Color,
    text: String,
) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = Color.Gray,
            fontSize = 9.sp,
        )
    }
}

@Composable
fun BattingOrderCardPreview() {
    val colors = BouncerDesignSystem.colors
    val battingOrder = listOf(
        BattingOrderEntry(1, "Rohit Sharma", "Opener", BattingStatus.OUT, 45, 32, true, "c Smith b Starc", 140.6f),
        BattingOrderEntry(2, "Shubman Gill", "Opener", BattingStatus.OUT, 23, 18, true, "b Hazlewood", 127.8f),
        BattingOrderEntry(3, "Virat Kohli", "Top Order", BattingStatus.ON_STRIKE, 67, 48, false, null, 139.6f),
        BattingOrderEntry(4, "Suryakumar Yadav", "Top Order", BattingStatus.NON_STRIKER, 34, 22, false, null, 154.5f),
        BattingOrderEntry(5, "Rishabh Pant", "Middle Order", BattingStatus.YET_TO_BAT),
        BattingOrderEntry(6, "Hardik Pandya", "All-rounder", BattingStatus.YET_TO_BAT),
        BattingOrderEntry(7, "Ravindra Jadeja", "All-rounder", BattingStatus.YET_TO_BAT),
    )

    BattingOrderCard(
        teamName = "India",
        battingOrder = battingOrder,
        wicketsFallen = 2,
    )
}
