package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*

/**
 * Player Form Card - Technical Performance Analytics.
 * Updated to BouncerImportance.STANDARD to align with design hierarchy.
 */

data class BatterForm(
    val name: String,
    val recentScores: List<Int>, // Last 5-10 innings
    val average: Double,
    val strikeRate: Double,
    val formRating: Float, // 0.0 to 1.0
    val trend: String, // "Rising", "Declining", "Stable"
)

data class BowlerForm(
    val name: String,
    val recentFigures: List<String>, // Last 5-10 innings like "3/25"
    val economy: Double,
    val average: Double,
    val formRating: Float,
    val trend: String,
)

@Composable
fun PlayerFormCard(
    batters: List<BatterForm>,
    bowlers: List<BowlerForm>,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(batters, bowlers) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.STANDARD,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Technical
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = WarningOrange.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "PERFORMANCE FORM",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = "RECENT ANALYTICS",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab selector - Minimalist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(2.dp),
            ) {
                FormTabButton(
                    label = "BATTERS",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                )

                FormTabButton(
                    label = "BOWLERS",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on tab
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedTab == 0) {
                    batters.forEachIndexed { index, batter ->
                        BatterFormRow(
                            batter = batter,
                            progress = animatedProgress.value,
                            index = index,
                        )
                    }
                } else {
                    bowlers.forEachIndexed { index, bowler ->
                        BowlerFormRow(
                            bowler = bowler,
                            progress = animatedProgress.value,
                            index = index,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) TextPrimary else TextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun BatterFormRow(
    batter: BatterForm,
    progress: Float,
    index: Int,
) {
    val delayedProgress = ((progress - index * 0.1f) / 0.9f).coerceIn(0f, 1f)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = delayedProgress },
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Form indicator
            FormRatingRing(
                rating = batter.formRating,
                progress = delayedProgress,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Player info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = batter.name.uppercase(),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TrendBadge(trend = batter.trend)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Recent scores
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    batter.recentScores.take(5).forEach { score ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        score >= 50 -> SuccessGreen.copy(alpha = 0.15f)
                                        score >= 30 -> ShinyGoldDeep.copy(alpha = 0.15f)
                                        else -> Color.White.copy(alpha = 0.03f)
                                    },
                                )
                                .border(
                                    0.5.dp,
                                    when {
                                        score >= 50 -> SuccessGreen.copy(alpha = 0.3f)
                                        score >= 30 -> ShinyGoldDeep.copy(alpha = 0.3f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = score.toString(),
                                color = if (score >= 30) TextPrimary else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Stats
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "AVG ${String.format("%.1f", batter.average)}",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "SR ${String.format("%.1f", batter.strikeRate)}",
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BowlerFormRow(
    bowler: BowlerForm,
    progress: Float,
    index: Int,
) {
    val delayedProgress = ((progress - index * 0.1f) / 0.9f).coerceIn(0f, 1f)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = delayedProgress },
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Form indicator
            FormRatingRing(
                rating = bowler.formRating,
                progress = delayedProgress,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Player info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bowler.name.uppercase(),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TrendBadge(trend = bowler.trend)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Recent figures
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    bowler.recentFigures.take(5).forEach { figure ->
                        val wickets = figure.split("/").firstOrNull()?.toIntOrNull() ?: 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        wickets >= 3 -> SuccessGreen.copy(alpha = 0.15f)
                                        wickets >= 2 -> ChartBlue.copy(alpha = 0.15f)
                                        else -> Color.White.copy(alpha = 0.03f)
                                    },
                                )
                                .border(
                                    0.5.dp,
                                    when {
                                        wickets >= 3 -> SuccessGreen.copy(alpha = 0.3f)
                                        wickets >= 2 -> ChartBlue.copy(alpha = 0.3f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = figure,
                                color = if (wickets >= 2) TextPrimary else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Stats
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "ECON ${String.format("%.1f", bowler.economy)}",
                    color = ChartBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "AVG ${String.format("%.1f", bowler.average)}",
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FormRatingRing(
    rating: Float,
    progress: Float,
) {
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius,
                style = Stroke(width = strokeWidth),
            )

            // Progress
            val color = when {
                rating > 0.7f -> SuccessGreen
                rating > 0.4f -> ShinyGoldDeep
                else -> WicketRed
            }

            drawArc(
                color = color.copy(alpha = 0.7f),
                startAngle = -90f,
                sweepAngle = 360f * rating * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Text(
            text = "${(rating * 10).toInt()}",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TrendBadge(trend: String) {
    val (color, arrow) = when (trend) {
        "Rising" -> Pair(SuccessGreen, "↑")
        "Declining" -> Pair(WicketRed, "↓")
        else -> Pair(TextTertiary, "→")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = arrow,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = trend.uppercase(),
            color = color.copy(alpha = 0.8f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}
