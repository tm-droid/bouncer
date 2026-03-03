package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Fielding positions card showing field placements
 *
 * TODO: Integration Requirements:
 * - Fetch real-time field position data from match feed
 * - Animate position changes between deliveries
 * - Show fielder names on hover/tap
 * - Track field changes for each batsman
 * - Add historical field position patterns
 * - Show run-saving opportunities based on positioning
 * - Integrate with wagon wheel for gap analysis
 * - Display fielding weaknesses
 * - Add captain's field setting suggestions
 * - Cache field positions for analysis
 */

data class FieldingPositionsData(
    val battingTeam: String,
    val bowlingTeam: String,
    val bowlerName: String,
    val batsmanName: String,
    val positions: List<FielderPosition>,
    val strategy: String,
)

data class FielderPosition(
    val playerName: String,
    val positionName: String,
    val angle: Float, // 0-360 degrees from wicketkeeper
    val distance: Float, // 0-1, where 1 is boundary
    val isSlip: Boolean = false,
    val isClose: Boolean = false,
)

@Composable
fun FieldingPositionsCard(
    fieldingData: FieldingPositionsData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "fielding")
    val ballPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ball",
    )

    LaunchedEffect(fieldingData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
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
                                    colors = listOf(colors.statusSuccess, Color(0xFF4CAF50)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.GroupWork,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "FIELDING POSITIONS",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "${fieldingData.bowlerName} to ${fieldingData.batsmanName}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.statusInfo.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = fieldingData.strategy,
                        color = colors.statusInfo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Field diagram
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val statusWarningC = colors.statusWarning
                val statusLiveC = colors.statusLive
                val statusInfoC = colors.statusInfo
                val goldDarkC = colors.goldDark
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val boundaryRadius = minOf(size.width, size.height) / 2 * 0.95f
                    val innerCircleRadius = boundaryRadius * 0.3f
                    val pitchWidth = 15.dp.toPx()
                    val pitchHeight = boundaryRadius * 0.25f

                    // Outfield gradient
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1B5E20), // Dark green center
                                Color(0xFF2E7D32), // Mid green
                                Color(0xFF388E3C), // Lighter green edge
                            ),
                            center = Offset(centerX, centerY),
                            radius = boundaryRadius,
                        ),
                        radius = boundaryRadius,
                        center = Offset(centerX, centerY),
                    )

                    // Boundary rope
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = boundaryRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 3.dp.toPx()),
                    )

                    // Inner circle (30-yard)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.4f),
                        radius = innerCircleRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    // Pitch
                    drawRect(
                        color = Color(0xFFD7CCC8), // Pitch color
                        topLeft = Offset(centerX - pitchWidth / 2, centerY - pitchHeight / 2),
                        size = Size(pitchWidth, pitchHeight),
                    )

                    // Crease lines
                    val creaseWidth = 25.dp.toPx()
                    // Batting crease
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX - creaseWidth / 2, centerY + pitchHeight / 2 - 10.dp.toPx()),
                        end = Offset(centerX + creaseWidth / 2, centerY + pitchHeight / 2 - 10.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                    // Bowling crease
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX - creaseWidth / 2, centerY - pitchHeight / 2 + 10.dp.toPx()),
                        end = Offset(centerX + creaseWidth / 2, centerY - pitchHeight / 2 + 10.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )

                    // Draw fielders
                    fieldingData.positions.forEach { fielder ->
                        val angle = (fielder.angle - 90) * PI / 180 // Adjust so 0 is at top
                        val distance = fielder.distance * (boundaryRadius - 20.dp.toPx())
                        val x = centerX + (cos(angle) * distance).toFloat()
                        val y = centerY + (sin(angle) * distance).toFloat()

                        // Fielder position indicator
                        val fielderColor = when {
                            fielder.isSlip -> statusWarningC
                            fielder.isClose -> statusLiveC
                            fielder.distance < 0.4f -> statusInfoC
                            else -> Color.White
                        }

                        // Outer glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    fielderColor.copy(alpha = 0.4f * animatedProgress.value),
                                    fielderColor.copy(alpha = 0f),
                                ),
                                center = Offset(x, y),
                                radius = 20.dp.toPx(),
                            ),
                            center = Offset(x, y),
                            radius = 20.dp.toPx(),
                        )

                        // Fielder dot
                        drawCircle(
                            color = fielderColor,
                            radius = 8.dp.toPx() * animatedProgress.value,
                            center = Offset(x, y),
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }

                    // Batsman at crease
                    drawCircle(
                        color = goldDarkC,
                        radius = 10.dp.toPx() * ballPulse,
                        center = Offset(centerX, centerY + pitchHeight / 2 - 15.dp.toPx()),
                    )

                    // Bowler
                    drawCircle(
                        color = statusLiveC,
                        radius = 8.dp.toPx(),
                        center = Offset(centerX, centerY - pitchHeight / 2 + 15.dp.toPx()),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendItem(color = Color.White, label = "Boundary")
                LegendItem(color = colors.statusInfo, label = "Inner")
                LegendItem(color = colors.statusWarning, label = "Slips")
                LegendItem(color = colors.statusLive, label = "Close")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fielder list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(12.dp),
            ) {
                Text(
                    text = "KEY POSITIONS",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fieldingData.positions.take(4).forEach { fielder ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF3D3D55))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = fielder.positionName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = fielder.playerName.split(" ").last(),
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
        )
    }
}

@Composable
fun FieldingPositionsCardPreview() {
    val colors = BouncerDesignSystem.colors
    FieldingPositionsCard(
        fieldingData = FieldingPositionsData(
            battingTeam = "India",
            bowlingTeam = "Australia",
            bowlerName = "Pat Cummins",
            batsmanName = "Virat Kohli",
            positions = listOf(
                FielderPosition("Warner", "Point", 90f, 0.4f),
                FielderPosition("Smith", "Cover", 70f, 0.5f),
                FielderPosition("Labuschagne", "Mid-off", 30f, 0.35f),
                FielderPosition("Green", "Mid-on", 330f, 0.35f),
                FielderPosition("Head", "Square Leg", 270f, 0.45f),
                FielderPosition("Marsh", "Fine Leg", 240f, 0.9f, isClose = false),
                FielderPosition("Hazlewood", "Third Man", 120f, 0.9f),
                FielderPosition("Starc", "Long-on", 350f, 0.85f),
                FielderPosition("Lyon", "1st Slip", 135f, 0.15f, isSlip = true),
                FielderPosition("Carey", "Wicketkeeper", 180f, 0.1f),
            ),
            strategy = "Attacking",
        ),
    )
}
