package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Celebration
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Milestone celebration card for player achievements
 *
 * TODO: Integration Requirements:
 * - Track approaching milestones (50, 100, 5-fer, etc.)
 * - Trigger celebration animation on milestone
 * - Load player images from CDN
 * - Add confetti/fireworks animations
 * - Show career milestone context
 * - Add share milestone functionality
 * - Push notification for followed players
 * - Display fastest/youngest records if applicable
 * - Navigate to player profile on tap
 * - Cache milestone data for history
 */

data class MilestoneData(
    val playerName: String,
    val teamName: String,
    val playerImageUrl: String?,
    val milestoneType: MilestoneType,
    val milestoneValue: String,
    val achievedTime: String,
    val context: String,
    val previousRecord: String?,
    val isRecord: Boolean,
)

enum class MilestoneType {
    FIFTY, CENTURY, DOUBLE_CENTURY, TRIPLE_CENTURY,
    FIVE_WICKETS, TEN_WICKETS,
    FASTEST_FIFTY, FASTEST_CENTURY,
    HUNDRED_PARTNERSHIP, TWO_HUNDRED_PARTNERSHIP,
    CAREER_RUNS, CAREER_WICKETS,
    CATCH_RECORD, MOST_SIXES
}

@Composable
fun MilestoneCelebrationCard(
    milestone: MilestoneData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "milestone")

    val celebrationPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkle",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(milestone) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val milestoneColor = when (milestone.milestoneType) {
        MilestoneType.CENTURY, MilestoneType.DOUBLE_CENTURY, MilestoneType.TRIPLE_CENTURY -> colors.goldDark
        MilestoneType.FIFTY -> Color(0xFFC0C0C0) // Silver
        MilestoneType.FIVE_WICKETS, MilestoneType.TEN_WICKETS -> Color(0xFF00BCD4)
        MilestoneType.FASTEST_FIFTY, MilestoneType.FASTEST_CENTURY -> colors.statusLive
        else -> colors.goldLight
    }

    val milestoneLabel = when (milestone.milestoneType) {
        MilestoneType.FIFTY -> "50"
        MilestoneType.CENTURY -> "100"
        MilestoneType.DOUBLE_CENTURY -> "200"
        MilestoneType.TRIPLE_CENTURY -> "300"
        MilestoneType.FIVE_WICKETS -> "5W"
        MilestoneType.TEN_WICKETS -> "10W"
        MilestoneType.FASTEST_FIFTY -> "FAST 50"
        MilestoneType.FASTEST_CENTURY -> "FAST 100"
        MilestoneType.HUNDRED_PARTNERSHIP -> "P100"
        MilestoneType.TWO_HUNDRED_PARTNERSHIP -> "P200"
        MilestoneType.CAREER_RUNS -> "STATS"
        MilestoneType.CAREER_WICKETS -> "TROPHY"
        MilestoneType.CATCH_RECORD -> "CATCH"
        MilestoneType.MOST_SIXES -> "6S"
    }

    val milestoneEmoji = when (milestone.milestoneType) {
        MilestoneType.FIFTY -> "★"
        MilestoneType.CENTURY -> "◆"
        MilestoneType.DOUBLE_CENTURY -> "◇◆"
        MilestoneType.TRIPLE_CENTURY -> "◆◆◆"
        MilestoneType.FIVE_WICKETS -> "▼"
        MilestoneType.TEN_WICKETS -> "▼▼"
        MilestoneType.FASTEST_FIFTY -> "★"
        MilestoneType.FASTEST_CENTURY -> "◆"
        MilestoneType.HUNDRED_PARTNERSHIP -> "∞"
        MilestoneType.TWO_HUNDRED_PARTNERSHIP -> "∞∞"
        MilestoneType.CAREER_RUNS -> "◈"
        MilestoneType.CAREER_WICKETS -> "◉"
        MilestoneType.CATCH_RECORD -> "○"
        MilestoneType.MOST_SIXES -> "⬡"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Background celebration effect
            Canvas(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                val centerX = size.width / 2
                val centerY = size.height / 2

                // Draw sparkles
                repeat(12) { i ->
                    val angle = (sparkleRotation + i * 30) * PI / 180
                    val radius = 80.dp.toPx() + (i % 3) * 30.dp.toPx()
                    val x = centerX + (cos(angle) * radius).toFloat()
                    val y = centerY + (sin(angle) * radius).toFloat()

                    drawCircle(
                        color = milestoneColor.copy(alpha = 0.3f * animatedProgress.value),
                        radius = (4.dp.toPx() * (1 + (i % 3) * 0.3f)),
                        center = Offset(x, y),
                    )
                }

                // Radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            milestoneColor.copy(alpha = 0.3f * celebrationPulse),
                            milestoneColor.copy(alpha = 0f),
                        ),
                        center = Offset(centerX, centerY),
                        radius = 150.dp.toPx() * celebrationPulse,
                    ),
                    center = Offset(centerX, centerY),
                    radius = 150.dp.toPx() * celebrationPulse,
                )
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
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(milestoneColor, milestoneColor.copy(alpha = 0.6f)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Celebration,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "MILESTONE",
                                color = colors.goldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = milestone.milestoneType.name.replace("_", " "),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    if (milestone.isRecord) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.statusLive.copy(alpha = 0.3f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "🏆 RECORD",
                                color = colors.statusLive,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Player and milestone display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Player image
                    Box(
                        modifier = Modifier
                            .size(80.dp * celebrationPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(milestoneColor, milestoneColor.copy(alpha = 0.3f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2D2D44)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (milestone.playerImageUrl != null) {
                                CoilImage(
                                    model = milestone.playerImageUrl,
                                    contentDescription = milestone.playerName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = milestone.playerName.take(2).uppercase(),
                                    color = milestoneColor,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Milestone value
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = milestoneEmoji,
                        fontSize = 32.sp,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = milestone.milestoneValue,
                        color = milestoneColor,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Text(
                        text = milestone.playerName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = milestone.teamName,
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Context info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = milestone.context,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = milestone.achievedTime,
                            color = Color.Gray,
                            fontSize = 11.sp,
                        )

                        if (milestone.previousRecord != null && milestone.isRecord) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Previous: ${milestone.previousRecord}",
                                color = Color.Gray.copy(alpha = 0.7f),
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
fun MilestoneCelebrationCardPreview() {
    val colors = BouncerDesignSystem.colors
    MilestoneCelebrationCard(
        milestone = MilestoneData(
            playerName = "Virat Kohli",
            teamName = "India",
            playerImageUrl = null,
            milestoneType = MilestoneType.CENTURY,
            milestoneValue = "100*",
            achievedTime = "Off 58 balls",
            context = "27th T20I century, fastest ever against Australia",
            previousRecord = "63 balls by Rohit Sharma",
            isRecord = true,
        ),
    )
}
