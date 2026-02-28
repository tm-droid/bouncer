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
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen

/**
 * Partnership Breakdown Card
 *
 * TODO: Integration Requirements:
 * - Track partnerships in real-time from match feed
 * - Show individual contribution percentages
 * - Add running between wickets analysis
 * - Display partnership run rate trends
 * - Compare with historical partnerships at venue
 * - Show strike rotation patterns
 * - Add wagon wheel for partnership
 * - Animate active partnership progress
 * - Navigate to batsman details on tap
 * - Cache partnership data for scorecard
 */

data class PartnershipEntry(
    val batter1Name: String,
    val batter1Runs: Int,
    val batter1Balls: Int,
    val batter2Name: String,
    val batter2Runs: Int,
    val batter2Balls: Int,
    val totalRuns: Int,
    val totalBalls: Int,
    val runRate: Double,
    val wicketNumber: Int,
    val isActive: Boolean = false,
)

@Composable
fun PartnershipBreakdownCard(
    teamName: String,
    partnerships: List<PartnershipEntry>,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "partnership")

    val activeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(partnerships) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    val maxPartnership = partnerships.maxOfOrNull { it.totalRuns } ?: 1
    val totalRuns = partnerships.sumOf { it.totalRuns }

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
                                    colors = listOf(ChartBlue, SuccessGreen),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "PARTNERSHIPS",
                            color = ShinyGoldMid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = teamName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalRuns",
                        color = ShinyGoldMid,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Total from ${partnerships.size} partnerships",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Partnership bars
            partnerships.forEachIndexed { index, partnership ->
                PartnershipRow(
                    partnership = partnership,
                    maxRuns = maxPartnership,
                    progress = animatedProgress.value,
                    activeGlow = if (partnership.isActive) activeGlow else 0f,
                    index = index,
                )

                if (index < partnerships.size - 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Best partnership highlight
            partnerships.maxByOrNull { it.totalRuns }?.let { best ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ShinyGoldMid.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "BEST PARTNERSHIP",
                            color = ShinyGoldMid,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "${best.batter1Name} & ${best.batter2Name}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${best.totalRuns} (${best.totalBalls})",
                            color = ShinyGoldMid,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "RR: ${String.format("%.2f", best.runRate)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnershipRow(
    partnership: PartnershipEntry,
    maxRuns: Int,
    progress: Float,
    activeGlow: Float,
    index: Int,
) {
    val delayedProgress = ((progress - index * 0.08f) / 0.92f).coerceIn(0f, 1f)
    val barWidth = (partnership.totalRuns.toFloat() / maxRuns * delayedProgress)

    // Calculate contribution percentages
    val batter1Pct = if (partnership.totalRuns > 0) {
        partnership.batter1Runs.toFloat() / partnership.totalRuns
    } else {
        0.5f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = delayedProgress
                translationX = (1 - delayedProgress) * 30f
            },
    ) {
        // Wicket number and batters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Wicket badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (partnership.isActive) ShinyGoldMid else Color.White.copy(alpha = 0.1f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${partnership.wicketNumber}",
                        color = if (partnership.isActive) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${partnership.batter1Name.split(" ").last()} (${partnership.batter1Runs})",
                    color = ChartBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = " & ",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )

                Text(
                    text = "${partnership.batter2Name.split(" ").last()} (${partnership.batter2Runs})",
                    color = SuccessGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = "${partnership.totalRuns} (${partnership.totalBalls})",
                color = if (partnership.isActive) ShinyGoldMid else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Partnership bar with split
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f)),
        ) {
            // Active glow
            if (partnership.isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = barWidth)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ShinyGoldMid.copy(alpha = activeGlow * 0.3f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(fraction = barWidth)) {
                // Batter 1 contribution
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = batter1Pct)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ChartBlue, ChartBlue.copy(alpha = 0.7f)),
                            ),
                        ),
                )

                // Batter 2 contribution
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(SuccessGreen.copy(alpha = 0.7f), SuccessGreen),
                            ),
                        ),
                )
            }
        }
    }
}
