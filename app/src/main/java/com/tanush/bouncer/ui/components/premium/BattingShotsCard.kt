package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Batting shots analysis card
 *
 * TODO: Integration Requirements:
 * - Fetch shot data from ball-tracking/Hawk-Eye service
 * - Map shots to field regions dynamically
 * - Add filter by shot type (drives, cuts, pulls, etc.)
 * - Show shot percentage by region
 * - Add comparison with player's historical shot patterns
 * - Animate shot placement on the wagon wheel
 * - Show scoring efficiency per shot type
 * - Add dismissal analysis overlay
 * - Integrate with video highlights for shot replays
 * - Cache shot data for offline analysis
 */

data class BattingShotsData(
    val batsmanName: String,
    val totalRuns: Int,
    val shots: List<ShotRegion>,
)

data class ShotRegion(
    val region: String,
    val runs: Int,
    val percentage: Float,
    val boundaries: Int,
    val angleStart: Float,
    val angleEnd: Float,
)

@Composable
fun BattingShotsCard(
    shotsData: BattingShotsData,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "shots")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    LaunchedEffect(shotsData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val maxRuns = shotsData.shots.maxOfOrNull { it.runs } ?: 1

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
                            Icons.Default.SportsHandball,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "BATTING SHOTS",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = shotsData.batsmanName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${shotsData.totalRuns}",
                        color = ShinyGoldMid,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "runs",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Wagon wheel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = minOf(size.width, size.height) / 2 * 0.9f

                    // Field background
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1B5E20),
                                Color(0xFF2E7D32),
                                Color(0xFF388E3C),
                            ),
                            center = Offset(centerX, centerY),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(centerX, centerY),
                    )

                    // Draw each region
                    shotsData.shots.forEach { shot ->
                        val intensity = (shot.runs.toFloat() / maxRuns).coerceIn(0.1f, 1f)
                        val color = when {
                            shot.boundaries > 3 -> BoundarySix
                            shot.boundaries > 0 -> BoundaryFour
                            shot.percentage > 15 -> ChartBlue
                            else -> Color.White.copy(alpha = 0.3f)
                        }

                        val path = Path().apply {
                            moveTo(centerX, centerY)
                            arcTo(
                                rect = androidx.compose.ui.geometry.Rect(
                                    centerX - radius,
                                    centerY - radius,
                                    centerX + radius,
                                    centerY + radius,
                                ),
                                startAngleDegrees = shot.angleStart - 90,
                                sweepAngleDegrees = (shot.angleEnd - shot.angleStart) * animatedProgress.value,
                                forceMoveTo = false,
                            )
                            close()
                        }

                        // Fill
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = intensity * 0.6f * animatedProgress.value),
                                    color.copy(alpha = intensity * 0.2f * animatedProgress.value),
                                ),
                                center = Offset(centerX, centerY),
                                radius = radius,
                            ),
                        )

                        // Border
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.8f * animatedProgress.value),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )

                        // Region label
                        val midAngle = ((shot.angleStart + shot.angleEnd) / 2 - 90) * PI / 180
                        val labelRadius = radius * 0.7f
                        val labelX = centerX + (cos(midAngle) * labelRadius).toFloat()
                        val labelY = centerY + (sin(midAngle) * labelRadius).toFloat()

                        // Background for label
                        if (shot.runs > 0) {
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.7f),
                                radius = 18.dp.toPx(),
                                center = Offset(labelX, labelY),
                            )
                        }
                    }

                    // Inner circle (pitch area)
                    drawCircle(
                        color = Color(0xFF8D6E63),
                        radius = 15.dp.toPx(),
                        center = Offset(centerX, centerY),
                    )

                    // Batsman position
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ShinyGoldMid.copy(alpha = glowPulse),
                                ShinyGoldMid.copy(alpha = 0f),
                            ),
                            center = Offset(centerX, centerY),
                            radius = 25.dp.toPx() * glowPulse,
                        ),
                        center = Offset(centerX, centerY),
                        radius = 25.dp.toPx() * glowPulse,
                    )

                    drawCircle(
                        color = ShinyGoldMid,
                        radius = 8.dp.toPx(),
                        center = Offset(centerX, centerY),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shot breakdown list
            Text(
                text = "SHOT BREAKDOWN",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shotsData.shots.sortedByDescending { it.runs }.take(4).forEach { shot ->
                    ShotBreakdownRow(
                        shot = shot,
                        maxRuns = maxRuns,
                        progress = animatedProgress.value,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotBreakdownRow(
    shot: ShotRegion,
    maxRuns: Int,
    progress: Float,
) {
    val barColor = when {
        shot.boundaries > 3 -> BoundarySix
        shot.boundaries > 0 -> BoundaryFour
        else -> ChartBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2D2D44))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shot.region,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF3D3D55)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((shot.runs.toFloat() / maxRuns * progress).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${shot.runs}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (shot.boundaries > 0) {
                Text(
                    text = "${shot.boundaries} bounds",
                    color = barColor,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
fun BattingShotsCardPreview() {
    BattingShotsCard(
        shotsData = BattingShotsData(
            batsmanName = "Virat Kohli",
            totalRuns = 82,
            shots = listOf(
                ShotRegion("Cover", 24, 29.3f, 4, 45f, 90f),
                ShotRegion("Point", 8, 9.8f, 1, 90f, 135f),
                ShotRegion("Third Man", 6, 7.3f, 1, 135f, 180f),
                ShotRegion("Fine Leg", 12, 14.6f, 2, 180f, 225f),
                ShotRegion("Square Leg", 10, 12.2f, 1, 225f, 270f),
                ShotRegion("Mid-Wicket", 14, 17.1f, 2, 270f, 315f),
                ShotRegion("Mid-On", 4, 4.9f, 0, 315f, 360f),
                ShotRegion("Mid-Off", 4, 4.9f, 0, 0f, 45f),
            ),
        ),
    )
}
