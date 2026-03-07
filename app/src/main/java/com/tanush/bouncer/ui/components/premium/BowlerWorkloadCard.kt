package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsKabaddi
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
 * Bowler workload card showing overs bowled and fitness
 *
 * TODO: Integration Requirements:
 * - Track cumulative overs per bowler in real-time
 * - Calculate remaining overs in limited-overs formats
 * - Add fatigue prediction based on speed drops
 * - Show historical workload for Test matches
 * - Track rest periods between spells
 * - Add injury risk indicators for high workloads
 * - Compare current workload vs season average
 * - Suggest bowling changes based on workload
 * - Alert when bowler approaching over limit
 * - Cache workload data for session analysis
 */

data class BowlerWorkloadEntry(
    val bowlerName: String,
    val oversBowled: Float,
    val maxOvers: Float,
    val runsConceded: Int,
    val wickets: Int,
    val dots: Int,
    val maidens: Int,
    val lastOverRuns: Int,
    val isCurrentBowler: Boolean = false,
)

@Composable
fun BowlerWorkloadCard(
    bowlers: List<BowlerWorkloadEntry>,
    format: String = "T20",
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(bowlers) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    val totalOversBowled = bowlers.sumOf { it.oversBowled.toDouble() }.toFloat()
    val totalMaxOvers = when (format) {
        "T20" -> 20f
        "ODI" -> 50f
        "TEST" -> Float.MAX_VALUE
        else -> 20f
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
                            Icons.Default.SportsKabaddi,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "BOWLER WORKLOAD",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = format,
                            color = Color.Gray,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Overs remaining
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.1f", totalOversBowled),
                        color = colors.goldDark,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "overs bowled",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bowler workload bars
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                bowlers.sortedByDescending { it.oversBowled }.forEach { bowler ->
                    BowlerWorkloadRow(
                        bowler = bowler,
                        animatedProgress = animatedProgress.value,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val totalWickets = bowlers.sumOf { it.wickets }
                val totalRuns = bowlers.sumOf { it.runsConceded }
                val economy = if (totalOversBowled > 0) totalRuns / totalOversBowled else 0f
                val totalDots = bowlers.sumOf { it.dots }

                WorkloadStatColumn(
                    value = "$totalWickets",
                    label = "Wickets",
                    color = colors.statusLive,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color(0xFF3D3D55)),
                )

                WorkloadStatColumn(
                    value = String.format("%.1f", economy),
                    label = "Economy",
                    color = when {
                        economy <= 6f -> colors.statusSuccess
                        economy <= 8f -> colors.statusWarning
                        else -> colors.statusLive
                    },
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color(0xFF3D3D55)),
                )

                WorkloadStatColumn(
                    value = "$totalDots",
                    label = "Dot Balls",
                    color = colors.statusInfo,
                )
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
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.statusSuccess),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fresh",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.statusWarning),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Moderate",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.statusLive),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Heavy",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BowlerWorkloadRow(
    bowler: BowlerWorkloadEntry,
    animatedProgress: Float,
) {
    val colors = BouncerDesignSystem.colors
    val workloadPercentage = bowler.oversBowled / bowler.maxOvers
    val workloadColor = when {
        workloadPercentage <= 0.5f -> colors.statusSuccess
        workloadPercentage <= 0.75f -> colors.statusWarning
        else -> colors.statusLive
    }

    val economy = if (bowler.oversBowled > 0) bowler.runsConceded / bowler.oversBowled else 0f

    val infiniteTransition = rememberInfiniteTransition(label = "current")
    val currentGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (bowler.isCurrentBowler) {
                    colors.statusInfo.copy(alpha = 0.1f * currentGlow)
                } else {
                    Color(0xFF2D2D44)
                },
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bowler.isCurrentBowler) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.statusInfo),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = bowler.bowlerName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (bowler.isCurrentBowler) FontWeight.Bold else FontWeight.Medium,
                )

                if (bowler.isCurrentBowler) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BOWLING",
                        color = colors.statusInfo,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${bowler.wickets}/${bowler.runsConceded}",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = String.format("%.1f ov", bowler.oversBowled),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Workload bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF3D3D55)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((workloadPercentage * animatedProgress).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(workloadColor.copy(alpha = 0.7f), workloadColor),
                        ),
                    ),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Econ: ${String.format("%.1f", economy)}",
                    color = when {
                        economy <= 6f -> colors.statusSuccess
                        economy <= 8f -> colors.statusWarning
                        else -> colors.statusLive
                    },
                    fontSize = 10.sp,
                )

                Text(
                    text = "Dots: ${bowler.dots}",
                    color = Color.Gray,
                    fontSize = 10.sp,
                )

                if (bowler.maidens > 0) {
                    Text(
                        text = "Maidens: ${bowler.maidens}",
                        color = colors.statusSuccess,
                        fontSize = 10.sp,
                    )
                }
            }

            Text(
                text = "${String.format("%.0f", workloadPercentage * 100)}% used",
                color = workloadColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun WorkloadStatColumn(
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
            fontSize = 20.sp,
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
fun BowlerWorkloadCardPreview() {
    val colors = BouncerDesignSystem.colors
    val bowlers = listOf(
        BowlerWorkloadEntry("Jasprit Bumrah", 4f, 4f, 24, 2, 12, 0, 8, true),
        BowlerWorkloadEntry("Mohammed Shami", 3f, 4f, 28, 1, 8, 0, 12, false),
        BowlerWorkloadEntry("Hardik Pandya", 2f, 4f, 18, 1, 6, 0, 4, false),
        BowlerWorkloadEntry("Ravindra Jadeja", 3f, 4f, 22, 0, 10, 1, 6, false),
        BowlerWorkloadEntry("Kuldeep Yadav", 2f, 4f, 16, 2, 8, 0, 5, false),
    )

    BowlerWorkloadCard(
        bowlers = bowlers,
        format = "T20",
    )
}
