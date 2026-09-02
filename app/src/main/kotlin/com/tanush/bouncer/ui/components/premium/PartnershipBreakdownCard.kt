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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

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
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    // No ambient loop. Motion in this app reports a state change (a score tick, a
    // toggle); a permanent breathing glow reports nothing and is the kind of
    // decoration this design system spends its budget away from.
    val activeGlow = 0.5f

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(partnerships) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    // coerceAtLeast, not `?: 1`: the elvis only fires on an empty list, but scraped partnership
    // runs are `toIntOrNull() ?: 0`, so a non-empty list of unparseable rows yields a max of 0 and
    // the bar fraction below becomes 0f/0 = NaN. fillMaxWidth(NaN) throws "Cannot round NaN value".
    val maxPartnership = (partnerships.maxOfOrNull { it.totalRuns } ?: 0).coerceAtLeast(1)
    val totalRuns = partnerships.sumOf { it.totalRuns }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BouncerThemeV2.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                                    colors = listOf(colors.statusInfo, colors.statusSuccess),
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
                            text = stringResource(R.string.partnership_partnerships),
                            color = hierarchy.secondary.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = teamName,
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalRuns",
                        color = hierarchy.secondary.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Total from ${partnerships.size} partnerships",
                        color = colors.textSecondary,
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
                        .background(hierarchy.secondary.primary.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.partnership_best_partnership),
                            color = hierarchy.secondary.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "${best.batter1Name} & ${best.batter2Name}",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${best.totalRuns} (${best.totalBalls})",
                            color = hierarchy.secondary.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "RR: ${String.format(Locale.US, "%.2f", best.runRate)}",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bar width as a fraction of the row: always finite, always within `0f..1f`.
 *
 * `maxRuns` is derived from scraped strings via `toIntOrNull() ?: 0`, so a partnership list none of
 * whose rows parsed yields a max of 0 and an unguarded `totalRuns / maxRuns` of `0f/0` = NaN.
 * `Modifier.fillMaxWidth(fraction = NaN)` does not degrade — it throws
 * `IllegalArgumentException: Cannot round NaN value`, taking down the Scorecard tab.
 */
internal fun partnershipBarFraction(totalRuns: Int, maxRuns: Int, progress: Float): Float =
    if (maxRuns <= 0) 0f else (totalRuns.toFloat() / maxRuns * progress).coerceIn(0f, 1f)

@Composable
private fun PartnershipRow(
    partnership: PartnershipEntry,
    maxRuns: Int,
    progress: Float,
    activeGlow: Float,
    index: Int,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val delayedProgress = ((progress - index * 0.08f) / 0.92f).coerceIn(0f, 1f)
    val barWidth = partnershipBarFraction(partnership.totalRuns, maxRuns, delayedProgress)

    // Calculate contribution percentages
    // A scraped contribution can exceed the partnership total; an unclamped fraction > 1f
    // overflows the bar's layout.
    val batter1Pct = if (partnership.totalRuns > 0) {
        (partnership.batter1Runs.toFloat() / partnership.totalRuns).coerceIn(0f, 1f)
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
                            if (partnership.isActive) hierarchy.secondary.primary else colors.glassBorder,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${partnership.wicketNumber}",
                        color = if (partnership.isActive) Color.Black else colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${partnership.batter1Name.split(" ").last()} (${partnership.batter1Runs})",
                    color = colors.statusInfo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = " & ",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )

                Text(
                    text = "${partnership.batter2Name.split(" ").last()} (${partnership.batter2Runs})",
                    color = colors.statusSuccess,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = "${partnership.totalRuns} (${partnership.totalBalls})",
                color = if (partnership.isActive) hierarchy.secondary.primary else colors.textPrimary,
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
                .background(colors.glassSurface),
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
                                    hierarchy.secondary.primary.copy(alpha = activeGlow * 0.3f),
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
                                colors = listOf(colors.statusInfo, colors.statusInfo.copy(alpha = 0.7f)),
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
                                colors = listOf(colors.statusSuccess.copy(alpha = 0.7f), colors.statusSuccess),
                            ),
                        ),
                )
            }
        }
    }
}
