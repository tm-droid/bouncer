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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Fan Engagement Card
 *
 * TODO: Integration Requirements:
 * - Connect to Firebase Realtime Database for live fan counts
 * - Integrate social media APIs for reactions/shares tracking
 * - Add real-time team support percentage updates
 * - Implement WebSocket for instant engagement updates
 * - Add share to social media functionality
 * - Show trending hashtags related to match
 * - Track engagement spikes during key moments
 * - Add regional fan distribution map
 * - Implement fan reactions during live events
 * - Cache engagement data with short TTL
 */

data class FanEngagementData(
    val team1Name: String,
    val team2Name: String,
    val team1Support: Float, // 0.0 - 1.0
    val team2Support: Float,
    val totalFans: Int,
    val reactions: Int,
    val shares: Int,
    val comments: Int,
    val trendingMoment: String,
    val fanSentiment: String, // "electric", "intense", "balanced"
)

@Composable
fun FanEngagementCard(
    engagement: FanEngagementData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "engagement")

    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heart",
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave",
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    val sentimentColor = when (engagement.fanSentiment) {
        "electric" -> colors.goldDark
        "intense" -> colors.statusLive
        else -> colors.statusInfo
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Animated wave background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        sentimentColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * shimmerOffset, 0f),
                    end = Offset(size.width * (shimmerOffset + 0.3f), size.height),
                )
                drawRect(brush = shimmerBrush)

                // Wave effect
                val waveHeight = 10f
                val points = mutableListOf<Offset>()
                for (x in 0..size.width.toInt() step 5) {
                    val angle = (x / size.width * 360 + wavePhase) * (Math.PI / 180).toFloat()
                    val y = size.height - 30 + kotlin.math.sin(angle) * waveHeight
                    points.add(Offset(x.toFloat(), y))
                }

                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = sentimentColor.copy(alpha = 0.1f),
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 2f,
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
                                        colors = listOf(sentimentColor, sentimentColor.copy(alpha = 0.7f)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "FAN ENGAGEMENT",
                                color = sentimentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = "${formatNumber(engagement.totalFans)} fans watching",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    // Sentiment badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(sentimentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡",
                                fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = engagement.fanSentiment.uppercase(),
                                color = sentimentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Team support battle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = engagement.team1Name,
                            color = colors.statusInfo,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${(engagement.team1Support * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = colors.statusLive,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(heartScale),
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = engagement.team2Name,
                            color = colors.statusLive,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${(engagement.team2Support * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Support bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(engagement.team1Support.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(colors.statusInfo, colors.statusInfo.copy(alpha = 0.7f)),
                                ),
                            ),
                    )

                    Box(
                        modifier = Modifier
                            .weight(engagement.team2Support.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(colors.statusLive.copy(alpha = 0.7f), colors.statusLive),
                                ),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Engagement stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    EngagementStatItem(
                        icon = Icons.Default.ThumbUp,
                        value = formatNumber(engagement.reactions),
                        label = "Reactions",
                        color = colors.statusSuccess,
                    )

                    EngagementStatItem(
                        icon = Icons.Default.Share,
                        value = formatNumber(engagement.shares),
                        label = "Shares",
                        color = colors.statusInfo,
                    )

                    EngagementStatItem(
                        icon = null,
                        emojiIcon = "💬",
                        value = formatNumber(engagement.comments),
                        label = "Comments",
                        color = colors.goldDark,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trending moment
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    sentimentColor.copy(alpha = 0.15f),
                                    sentimentColor.copy(alpha = 0.05f),
                                ),
                            ),
                        )
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = sentimentColor,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "TRENDING NOW",
                                color = sentimentColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = engagement.trendingMoment,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngagementStatItem(
    icon: ImageVector? = null,
    emojiIcon: String? = null,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        } else if (emojiIcon != null) {
            Text(
                text = emojiIcon,
                fontSize = 18.sp,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000f)
        number >= 1_000 -> String.format("%.1fK", number / 1_000f)
        else -> number.toString()
    }
}
