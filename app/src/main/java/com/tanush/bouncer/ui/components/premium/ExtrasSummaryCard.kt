package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Extras Summary Card
 *
 * TODO: Integration Requirements:
 * - Track extras in real-time from match feed
 * - Show extras by bowler breakdown
 * - Calculate extras as percentage of total score
 * - Compare against venue/format averages
 * - Track penalty runs separately
 * - Show impact of extras on match result
 * - Add by-bowler extras leaderboard
 * - Highlight disciplinary issues (excessive wides)
 * - Display historical extras trends for team
 * - Cache extras data for match statistics
 */

data class DetailedExtrasBreakdown(
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,
    val penalties: Int = 0,
)

@Composable
fun ExtrasSummaryCard(
    bowlingTeam: String,
    extras: DetailedExtrasBreakdown,
    totalExtras: Int,
    totalRuns: Int,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(extras) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    val extrasPercentage = if (totalRuns > 0) (totalExtras.toFloat() / totalRuns * 100) else 0f
    val isHighExtras = extrasPercentage > 5f

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
                                    colors = listOf(
                                        if (isHighExtras) colors.statusLive else colors.statusWarning,
                                        if (isHighExtras) colors.statusLive.copy(alpha = 0.6f) else colors.statusWarning.copy(alpha = 0.6f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "EXTRAS ANALYSIS",
                            color = colors.goldDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "$bowlingTeam Bowling",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Total extras
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalExtras",
                        color = if (isHighExtras) colors.statusLive else colors.goldDark,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${String.format("%.1f", extrasPercentage)}% of total",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Pie chart visualization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pie chart
                ExtrasPieChart(
                    extras = extras,
                    progress = animatedProgress.value,
                    modifier = Modifier.size(120.dp),
                )

                // Legend
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExtrasLegendItem(
                        color = colors.statusLive,
                        label = "Wides",
                        value = extras.wides,
                        totalExtras = totalExtras,
                        progress = animatedProgress.value,
                    )

                    ExtrasLegendItem(
                        color = colors.statusWarning,
                        label = "No Balls",
                        value = extras.noBalls,
                        totalExtras = totalExtras,
                        progress = animatedProgress.value,
                    )

                    ExtrasLegendItem(
                        color = colors.statusInfo,
                        label = "Byes",
                        value = extras.byes,
                        totalExtras = totalExtras,
                        progress = animatedProgress.value,
                    )

                    ExtrasLegendItem(
                        color = colors.statusSuccess,
                        label = "Leg Byes",
                        value = extras.legByes,
                        totalExtras = totalExtras,
                        progress = animatedProgress.value,
                    )

                    if (extras.penalties > 0) {
                        ExtrasLegendItem(
                            color = Color.Magenta,
                            label = "Penalties",
                            value = extras.penalties,
                            totalExtras = totalExtras,
                            progress = animatedProgress.value,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bar chart breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(16.dp),
            ) {
                Text(
                    text = "Breakdown by Type",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val maxExtra = maxOf(extras.wides, extras.noBalls, extras.byes, extras.legByes).coerceAtLeast(1)

                ExtrasBarRow(
                    label = "Wides",
                    value = extras.wides,
                    maxValue = maxExtra,
                    color = colors.statusLive,
                    progress = animatedProgress.value,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExtrasBarRow(
                    label = "No Balls",
                    value = extras.noBalls,
                    maxValue = maxExtra,
                    color = colors.statusWarning,
                    progress = animatedProgress.value,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExtrasBarRow(
                    label = "Byes",
                    value = extras.byes,
                    maxValue = maxExtra,
                    color = colors.statusInfo,
                    progress = animatedProgress.value,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExtrasBarRow(
                    label = "Leg Byes",
                    value = extras.legByes,
                    maxValue = maxExtra,
                    color = colors.statusSuccess,
                    progress = animatedProgress.value,
                )
            }

            // Impact message
            if (isHighExtras) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.statusLive.copy(alpha = 0.15f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = colors.statusLive,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "High extras percentage! This is affecting bowling economy.",
                        color = colors.statusLive,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtrasPieChart(
    extras: DetailedExtrasBreakdown,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val statusLiveC = colors.statusLive
    val statusWarningC = colors.statusWarning
    val statusInfoC = colors.statusInfo
    val statusSuccessC = colors.statusSuccess
    Canvas(modifier = modifier) {
        val total = extras.wides + extras.noBalls + extras.byes + extras.legByes + extras.penalties
        if (total == 0) return@Canvas

        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 8.dp.toPx()

        var startAngle = -90f

        val segments = listOf(
            Triple(extras.wides, statusLiveC, "Wides"),
            Triple(extras.noBalls, statusWarningC, "No Balls"),
            Triple(extras.byes, statusInfoC, "Byes"),
            Triple(extras.legByes, statusSuccessC, "Leg Byes"),
            Triple(extras.penalties, Color.Magenta, "Penalties"),
        ).filter { it.first > 0 }

        segments.forEach { (value, color, _) ->
            val sweepAngle = (value.toFloat() / total * 360f) * progress

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
            )

            // Separator line
            drawArc(
                color = Color(0xFF1A1A2E),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx()),
            )

            startAngle += sweepAngle
        }

        // Center hole
        drawCircle(
            color = Color(0xFF1A1A2E),
            radius = radius * 0.5f,
            center = Offset(centerX, centerY),
        )
    }
}

@Composable
private fun ExtrasLegendItem(
    color: Color,
    label: String,
    value: Int,
    totalExtras: Int,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    val percentage = if (totalExtras > 0) (value.toFloat() / totalExtras * 100) else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer { alpha = progress },
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.width(60.dp),
        )

        Text(
            text = "$value",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )

        Text(
            text = "(${String.format("%.0f", percentage)}%)",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ExtrasBarRow(
    label: String,
    value: Int,
    maxValue: Int,
    color: Color,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.width(60.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (value.toFloat() / maxValue * progress))
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color, color.copy(alpha = 0.6f)),
                        ),
                    ),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value.toString(),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )
    }
}
