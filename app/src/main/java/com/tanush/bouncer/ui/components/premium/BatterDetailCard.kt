package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * Batter Detail Card
 *
 * TODO: Integration Requirements:
 * - Fetch live batting statistics from match data service
 * - Load player image from player database CDN
 * - Show real-time strike rate calculation
 * - Add wagon wheel integration for shot placement
 * - Show vs bowler matchup statistics
 * - Display milestone tracking (approaching 50/100)
 * - Add career stats comparison toggle
 * - Navigate to full player profile on tap
 * - Add ball-by-ball breakdown option
 * - Cache player stats for quick access
 */

data class DetailedPlayerStats(
    val runs: Int,
    val balls: Int,
    val strikeRate: Double,
    val fours: Int,
    val sixes: Int,
    val dotBalls: Int,
    val minutes: Int,
    val controlPercentage: Float,
)

@Composable
fun BatterDetailCard(
    playerName: String,
    playerImageUrl: String?,
    teamName: String,
    stats: DetailedPlayerStats,
    isOnStrike: Boolean = false,
    recentBalls: List<String> = emptyList(), // "0", "1", "2", "4", "6", "W", etc.
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "batter")

    val strikeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "strike",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(stats) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // On strike glow
            if (isOnStrike) {
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ShinyGoldMid.copy(alpha = 0.2f * strikeGlow),
                                Color.Transparent,
                            ),
                            center = Offset(100f, 100f),
                            radius = 300f,
                        ),
                        center = Offset(100f, 100f),
                        radius = 300f,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Player header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Player image
                    Box(
                        modifier = Modifier.size(70.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isOnStrike) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ShinyGoldMid.copy(alpha = strikeGlow * 0.5f),
                                            Color.Transparent,
                                        ),
                                    ),
                                    radius = size.minDimension / 2 + 10f,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.2f),
                                            Color.White.copy(alpha = 0.1f),
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playerImageUrl != null) {
                                CoilImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(playerImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = playerName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Player info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playerName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            if (isOnStrike) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ShinyGoldMid),
                                )
                            }
                        }

                        Text(
                            text = teamName,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }

                    // Main score
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${stats.runs}",
                            color = when {
                                stats.runs >= 100 -> ShinyGoldMid
                                stats.runs >= 50 -> SuccessGreen
                                else -> Color.White
                            },
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "(${stats.balls})",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recent balls
                if (recentBalls.isNotEmpty()) {
                    Text(
                        text = "RECENT",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentBalls.takeLast(8).forEach { ball ->
                            RecentBallChip(ball = ball)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Stats grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatterStatItem(
                        label = "Strike Rate",
                        value = String.format("%.1f", stats.strikeRate),
                        color = if (stats.strikeRate > 150) SuccessGreen else if (stats.strikeRate < 100) WicketRed else ShinyGoldMid,
                    )

                    BatterStatItem(
                        label = "4s",
                        value = "${stats.fours}",
                        color = BoundaryFour,
                    )

                    BatterStatItem(
                        label = "6s",
                        value = "${stats.sixes}",
                        color = BoundarySix,
                    )

                    BatterStatItem(
                        label = "Dots",
                        value = "${stats.dotBalls}",
                        color = Color.Gray,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Control & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Control percentage
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Control: ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )

                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = stats.controlPercentage * animatedProgress.value)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            stats.controlPercentage > 0.8f -> SuccessGreen
                                            stats.controlPercentage > 0.6f -> ShinyGoldMid
                                            else -> WicketRed
                                        },
                                    ),
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${(stats.controlPercentage * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // Time at crease
                    Text(
                        text = "${stats.minutes} mins",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentBallChip(ball: String) {
    val (bgColor, textColor) = when (ball) {
        "6" -> Pair(BoundarySix, Color.White)
        "4" -> Pair(BoundaryFour, Color.White)
        "W" -> Pair(WicketRed, Color.White)
        "0" -> Pair(Color.Gray.copy(alpha = 0.3f), Color.White.copy(alpha = 0.6f))
        "Wd", "Nb" -> Pair(WarningYellow.copy(alpha = 0.3f), WarningYellow)
        else -> Pair(ChartBlue.copy(alpha = 0.3f), ChartBlue)
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (ball == "0") "•" else ball,
            color = textColor,
            fontSize = if (ball == "0") 16.sp else 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BatterStatItem(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
    }
}
