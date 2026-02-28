package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Bowling Change Card
 *
 * TODO: Integration Requirements:
 * - Track bowling changes from live match feed
 * - Analyze impact of each bowling change
 * - Show tactical reasoning based on match situation
 * - Display matchup stats for new bowler vs batsman
 * - Track effectiveness of bowling changes
 * - Add captain decision pattern analysis
 * - Show bowling end preferences
 * - Predict likely next bowling change
 * - Navigate to bowler comparison on tap
 * - Cache change history for match review
 */

data class BowlingChangeData(
    val overNumber: Float,
    val previousBowler: String,
    val newBowler: String,
    val reason: String, // "End of Spell", "Tactical", "Injury", "Ineffective"
    val previousBowlerFigures: String,
    val expectedImpact: String,
)

@Composable
fun BowlingChangeCard(
    changes: List<BowlingChangeData>,
    currentBowler: String,
    currentBowlerStats: String,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "change")

    val arrowPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrow",
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        WicketRed.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * shimmerOffset, 0f),
                    end = Offset(size.width * (shimmerOffset + 0.3f), size.height),
                )
                drawRect(brush = shimmerBrush)
            }

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
                                        colors = listOf(WicketRed, WicketRed.copy(alpha = 0.7f)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "BOWLING CHANGE",
                                color = WicketRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = "${changes.size} changes this innings",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Current bowler
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    SuccessGreen.copy(alpha = 0.2f),
                                    SuccessGreen.copy(alpha = 0.1f),
                                ),
                            ),
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "NOW BOWLING",
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = currentBowler,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Text(
                            text = currentBowlerStats,
                            color = SuccessGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (changes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "RECENT CHANGES",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    changes.takeLast(3).reversed().forEach { change ->
                        BowlingChangeRow(
                            change = change,
                            arrowOffset = arrowPulse,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BowlingChangeRow(
    change: BowlingChangeData,
    arrowOffset: Float,
    modifier: Modifier = Modifier,
) {
    val reasonColor = when (change.reason) {
        "Tactical" -> ShinyGoldMid
        "Injury" -> WicketRed
        "Ineffective" -> WarningYellow
        else -> ChartBlue
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Over number
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${change.overNumber.toInt()}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Bowler swap
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = change.previousBowler,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Text(
                    text = change.previousBowlerFigures,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "→",
                color = ShinyGoldMid,
                fontSize = 18.sp,
                modifier = Modifier.offset(x = arrowOffset.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = change.newBowler,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Reason badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(reasonColor.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = change.reason,
                color = reasonColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
