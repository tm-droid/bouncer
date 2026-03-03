package com.tanush.bouncer.ui.components.premium

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Player Achievements Card
 *
 * TODO: Integration Requirements:
 * - Fetch player career achievements from stats API
 * - Track milestones achieved in current match
 * - Load player images from CDN
 * - Show career context (e.g., 45th century)
 * - Add celebration animations for achievements
 * - Compare with all-time records
 * - Show format-specific achievements
 * - Navigate to player profile on tap
 * - Add share achievement functionality
 * - Cache achievements for offline viewing
 */

data class PlayerMilestone(
    val playerName: String,
    val playerImage: String,
    val teamName: String,
    val milestoneType: String, // "century", "half_century", "five_wicket", "hat_trick"
    val achievement: String,
    val stats: String,
    val careerNumber: Int, // e.g., 45th century
    val matchContext: String,
)

@Composable
fun PlayerAchievementsCard(
    achievements: List<PlayerMilestone>,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "achievements")

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val sparkRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spark",
    )

    val colors = BouncerDesignSystem.colors
    val goldDarkC = colors.goldDark

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Animated background sparkles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2

                for (i in 0..5) {
                    val angle = (sparkRotation + i * 60) * (Math.PI / 180).toFloat()
                    val radius = 100f + i * 20f
                    val x = centerX + kotlin.math.cos(angle) * radius
                    val y = centerY + kotlin.math.sin(angle) * radius

                    drawCircle(
                        color = goldDarkC.copy(alpha = 0.1f * glowPulse),
                        radius = 3f,
                        center = Offset(x, y),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "MILESTONES",
                        color = colors.goldDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                achievements.forEachIndexed { index, achievement ->
                    AchievementRow(
                        achievement = achievement,
                        glowPulse = glowPulse,
                    )

                    if (index < achievements.lastIndex) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(
    achievement: PlayerMilestone,
    glowPulse: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val milestoneColor = when (achievement.milestoneType) {
        "century" -> colors.goldDark
        "half_century" -> colors.statusInfo
        "five_wicket" -> colors.statusLive
        "hat_trick" -> colors.statusSuccess
        else -> colors.goldDark
    }

    val milestoneEmoji = when (achievement.milestoneType) {
        "century" -> "💯"
        "half_century" -> "5️⃣0️⃣"
        "five_wicket" -> "🎯"
        "hat_trick" -> "🎩"
        else -> "⭐"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        milestoneColor.copy(alpha = 0.15f * glowPulse),
                        milestoneColor.copy(alpha = 0.05f),
                    ),
                ),
            ),
    ) {
        // Glow effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = milestoneColor.copy(alpha = 0.1f * glowPulse),
                radius = 80f,
                center = Offset(size.width * 0.9f, size.height / 2),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Player image with milestone badge
            Box {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(milestoneColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CoilImage(
                        model = achievement.playerImage,
                        contentDescription = achievement.playerName,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape),
                    )
                }

                // Milestone badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(milestoneColor, milestoneColor.copy(alpha = 0.8f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = milestoneEmoji,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Achievement details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.playerName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = achievement.teamName,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(milestoneColor.copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = achievement.achievement,
                            color = milestoneColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = achievement.matchContext,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }

            // Stats column
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = achievement.stats,
                    color = milestoneColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${achievement.careerNumber}${getOrdinalSuffix(achievement.careerNumber)} career",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

private fun getOrdinalSuffix(number: Int): String {
    return when {
        number % 100 in 11..13 -> "th"
        number % 10 == 1 -> "st"
        number % 10 == 2 -> "nd"
        number % 10 == 3 -> "rd"
        else -> "th"
    }
}
