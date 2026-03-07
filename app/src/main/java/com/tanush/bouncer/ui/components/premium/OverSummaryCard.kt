package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BoundaryFour
import com.tanush.bouncer.ui.theme.BoundarySix
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Over summary card showing ball-by-ball for a specific over
 *
 * TODO: Integration Requirements:
 * - Fetch ball-by-ball data for each over
 * - Animate each delivery in over sequence
 * - Show delivery types (dot, run, boundary, wicket)
 * - Add over navigation (previous/next)
 * - Display bowler stats for the over
 * - Show running commentary for each ball
 * - Link to video replay for each delivery
 * - Track extras in over
 * - Compare with average over performance
 * - Cache over data for match review
 */

data class OverSummaryData(
    val overNumber: Int,
    val bowlerName: String,
    val deliveries: List<DeliveryOutcome>,
    val runsInOver: Int,
    val wicketsInOver: Int,
)

data class DeliveryOutcome(
    val ballNumber: Int,
    val runs: Int,
    val isWicket: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
    val isBye: Boolean,
    val isLegBye: Boolean,
    val isSix: Boolean,
    val isFour: Boolean,
    val batsman: String,
)

@Composable
fun OverSummaryCard(
    overData: OverSummaryData,
    previousOverData: OverSummaryData?,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(overData) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }

    val overQuality = when {
        overData.wicketsInOver > 0 -> "WICKET OVER"
        overData.runsInOver <= 4 -> "ECONOMICAL"
        overData.runsInOver >= 15 -> "EXPENSIVE"
        else -> "REGULAR"
    }

    val qualityColor = when {
        overData.wicketsInOver > 0 -> colors.statusLive
        overData.runsInOver <= 4 -> colors.statusSuccess
        overData.runsInOver >= 15 -> colors.statusLive
        else -> colors.statusInfo
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
                            .background(qualityColor.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${overData.overNumber}",
                            color = qualityColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "OVER ${overData.overNumber}",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = overData.bowlerName,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(qualityColor.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = overQuality,
                        color = qualityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ball-by-ball display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                overData.deliveries.forEachIndexed { index, delivery ->
                    val isVisible = index < (overData.deliveries.size * animatedProgress.value).toInt() + 1

                    if (isVisible) {
                        DeliveryBall(
                            delivery = delivery,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }

                // Fill remaining balls if under 6
                repeat(6 - overData.deliveries.size.coerceAtMost(6)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2D2D44)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "•",
                            color = Color.Gray,
                            fontSize = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Over summary stats
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    OverStatItem(
                        label = "Runs",
                        value = "${overData.runsInOver}",
                        color = if (overData.runsInOver >= 15) {
                            colors.statusLive
                        } else if (overData.runsInOver <= 4) {
                            colors.statusSuccess
                        } else {
                            colors.textPrimary
                        },
                    )

                    OverStatItem(
                        label = "Wickets",
                        value = "${overData.wicketsInOver}",
                        color = if (overData.wicketsInOver > 0) colors.statusLive else Color.Gray,
                    )

                    OverStatItem(
                        label = "Extras",
                        value = "${overData.deliveries.count { it.isWide || it.isNoBall }}",
                        color = if (overData.deliveries.any { it.isWide || it.isNoBall }) colors.statusWarning else Color.Gray,
                    )

                    OverStatItem(
                        label = "Boundaries",
                        value = "${overData.deliveries.count { it.isFour || it.isSix }}",
                        color = if (overData.deliveries.any { it.isSix }) {
                            colors.statusWarning
                        } else if (overData.deliveries.any { it.isFour }) {
                            colors.statusWarning
                        } else {
                            Color.Gray
                        },
                    )
                }
            }

            // Comparison with previous over
            if (previousOverData != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val runDiff = overData.runsInOver - previousOverData.runsInOver
                val diffText = when {
                    runDiff > 0 -> "+$runDiff more than last over"
                    runDiff < 0 -> "${abs(runDiff)} less than last over"
                    else -> "Same as last over"
                }
                val diffColor = when {
                    runDiff < 0 -> colors.statusSuccess
                    runDiff > 0 -> colors.statusLive
                    else -> Color.Gray
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (runDiff < 0) Icons.Default.TrendingDown else if (runDiff > 0) Icons.Default.TrendingUp else Icons.Default.TrendingFlat,
                        contentDescription = "Trend",
                        tint = diffColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = diffText,
                        color = diffColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryBall(
    delivery: DeliveryOutcome,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val backgroundColor = when {
        delivery.isWicket -> colors.statusLive
        delivery.isSix -> colors.statusWarning
        delivery.isFour -> colors.statusWarning
        delivery.isWide || delivery.isNoBall -> colors.statusWarning
        delivery.runs == 0 -> Color(0xFF3D3D55)
        else -> colors.statusInfo
    }

    val displayText = when {
        delivery.isWicket -> "W"
        delivery.isSix -> "6"
        delivery.isFour -> "4"
        delivery.isWide -> "Wd"
        delivery.isNoBall -> "Nb"
        delivery.isBye -> "${delivery.runs}B"
        delivery.isLegBye -> "${delivery.runs}Lb"
        else -> "${delivery.runs}"
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            color = if (delivery.isWide || delivery.isNoBall) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OverStatItem(
    label: String,
    value: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
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
fun OverSummaryCardPreview() {
    val colors = BouncerDesignSystem.colors
    val deliveries = listOf(
        DeliveryOutcome(1, 1, false, false, false, false, false, false, false, "Kohli"),
        DeliveryOutcome(2, 6, false, false, false, false, false, true, false, "Kohli"),
        DeliveryOutcome(3, 0, false, false, false, false, false, false, false, "Kohli"),
        DeliveryOutcome(4, 4, false, false, false, false, false, false, true, "Kohli"),
        DeliveryOutcome(5, 0, true, false, false, false, false, false, false, "Sharma"),
        DeliveryOutcome(6, 2, false, false, false, false, false, false, false, "Pant"),
    )

    OverSummaryCard(
        overData = OverSummaryData(
            overNumber = 18,
            bowlerName = "Pat Cummins",
            deliveries = deliveries,
            runsInOver = 13,
            wicketsInOver = 1,
        ),
        previousOverData = OverSummaryData(
            overNumber = 17,
            bowlerName = "Mitchell Starc",
            deliveries = emptyList(),
            runsInOver = 8,
            wicketsInOver = 0,
        ),
    )
}
