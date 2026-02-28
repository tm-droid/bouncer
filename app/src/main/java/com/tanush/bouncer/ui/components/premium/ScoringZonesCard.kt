package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Scoring zones visualization - shows where batsman is scoring runs
 *
 * TODO: Integration Requirements:
 * - Integrate with ball-tracking data for shot placement
 * - Map runs to field zones dynamically
 * - Show zone-wise run distribution
 * - Add shot type analysis per zone
 * - Compare with player's typical scoring zones
 * - Highlight scoring vs dismissal zones
 * - Animate zones as runs are scored
 * - Add filter by bowler or spell
 * - Show boundary vs rotation zones
 * - Cache zone data for innings analysis
 */

data class ScoringZone(
    val zoneName: String,
    val runs: Int,
    val balls: Int,
    val boundaries: Int,
    val startAngle: Float,
    val sweepAngle: Float,
)

@Composable
fun ScoringZonesCard(
    batsmanName: String,
    zones: List<ScoringZone>,
    totalRuns: Int,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(zones) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val textMeasurer = rememberTextMeasurer()

    // Zone colors
    val zoneColors = listOf(
        Color(0xFF2196F3), // Blue - Third man/Fine leg
        Color(0xFF4CAF50), // Green - Point/Square leg
        Color(0xFFFF9800), // Orange - Cover/Mid-wicket
        Color(0xFFE91E63), // Pink - Mid-off/Mid-on
        Color(0xFF9C27B0), // Purple - Extra cover/Long-on
        Color(0xFF00BCD4), // Cyan - Long-off/Deep
        Color(0xFFFFEB3B), // Yellow - Straight
        Color(0xFF795548), // Brown - Behind wicket
    )

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
                                    colors = listOf(ShinyGoldMid, ShinyGoldDeep),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SCORING ZONES",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = batsmanName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Total runs
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalRuns",
                        color = ShinyGoldMid,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Total Runs",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Wagon Wheel Visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier.size(240.dp),
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val outerRadius = size.minDimension / 2 - 10f
                    val innerRadius = outerRadius * 0.25f

                    // Draw outer circle (field boundary)
                    drawCircle(
                        color = Color(0xFF2D2D44),
                        radius = outerRadius,
                        center = center,
                        style = Fill,
                    )

                    // Draw field rings
                    for (i in 1..4) {
                        val ringRadius = outerRadius * (i / 4f)
                        drawCircle(
                            color = Color(0xFF3D3D55).copy(alpha = 0.5f),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 1f),
                        )
                    }

                    // Draw scoring zones as pie segments
                    zones.forEachIndexed { index, zone ->
                        val percentage = if (totalRuns > 0) zone.runs.toFloat() / totalRuns else 0f
                        val segmentRadius = innerRadius + (outerRadius - innerRadius) * percentage * animatedProgress.value
                        val color = zoneColors[index % zoneColors.size]

                        // Draw zone segment
                        drawArc(
                            color = color.copy(alpha = 0.7f),
                            startAngle = zone.startAngle - 90f, // Offset to start from top
                            sweepAngle = zone.sweepAngle * animatedProgress.value,
                            useCenter = true,
                            topLeft = Offset(center.x - segmentRadius, center.y - segmentRadius),
                            size = Size(segmentRadius * 2, segmentRadius * 2),
                        )

                        // Draw zone arc outline
                        drawArc(
                            color = color,
                            startAngle = zone.startAngle - 90f,
                            sweepAngle = zone.sweepAngle * animatedProgress.value,
                            useCenter = true,
                            topLeft = Offset(center.x - segmentRadius, center.y - segmentRadius),
                            size = Size(segmentRadius * 2, segmentRadius * 2),
                            style = Stroke(width = 2f),
                        )
                    }

                    // Draw pitch in center
                    val pitchWidth = 20f
                    val pitchHeight = 50f
                    drawRoundRect(
                        color = Color(0xFFD4A574),
                        topLeft = Offset(center.x - pitchWidth / 2, center.y - pitchHeight / 2),
                        size = Size(pitchWidth, pitchHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    )

                    // Draw batsman position
                    drawCircle(
                        color = Color.White,
                        radius = 6f,
                        center = Offset(center.x, center.y + pitchHeight / 4),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Zone Legend
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                zones.chunked(2).forEach { rowZones ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowZones.forEachIndexed { index, zone ->
                            val colorIndex = zones.indexOf(zone)
                            val color = zoneColors[colorIndex % zoneColors.size]
                            val percentage = if (totalRuns > 0) (zone.runs.toFloat() / totalRuns * 100) else 0f

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2D2D44))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = zone.zoneName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${zone.runs} runs (${zone.boundaries} 4s/6s)",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                    )
                                }

                                Text(
                                    text = "${percentage.toInt()}%",
                                    color = color,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // Fill empty space if odd number
                        if (rowZones.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Strongest zone callout
            val strongestZone = zones.maxByOrNull { it.runs }
            strongestZone?.let { zone ->
                val strikeRate = if (zone.balls > 0) (zone.runs.toFloat() / zone.balls * 100) else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = 0.3f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "STRONGEST ZONE",
                            color = ShinyGoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = zone.zoneName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${zone.runs}",
                                color = ShinyGoldMid,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Runs",
                                color = Color.Gray,
                                fontSize = 10.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${strikeRate.toInt()}",
                                color = SuccessGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "SR",
                                color = Color.Gray,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoringZonesCardPreview() {
    val zones = listOf(
        ScoringZone("Third Man", 28, 12, 4, 0f, 45f),
        ScoringZone("Point", 35, 18, 5, 45f, 45f),
        ScoringZone("Cover", 42, 22, 6, 90f, 45f),
        ScoringZone("Mid-off", 18, 15, 2, 135f, 45f),
        ScoringZone("Mid-on", 22, 14, 3, 180f, 45f),
        ScoringZone("Mid-wicket", 38, 20, 5, 225f, 45f),
        ScoringZone("Square Leg", 30, 16, 4, 270f, 45f),
        ScoringZone("Fine Leg", 25, 13, 3, 315f, 45f),
    )

    ScoringZonesCard(
        batsmanName = "Virat Kohli",
        zones = zones,
        totalRuns = 238,
    )
}
