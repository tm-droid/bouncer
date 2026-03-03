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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match Awards Card
 *
 * TODO: Integration Requirements:
 * - Fetch official match awards from match API
 * - Show Player of the Match with stats
 * - Add series/tournament awards
 * - Load player images from CDN
 * - Add celebration animations for awards
 * - Show award history for player
 * - Navigate to player profile on tap
 * - Add social sharing for awards
 * - Display sponsor branding (if required)
 * - Cache awards data after match completion
 */

data class MatchAward(
    val title: String,
    val playerName: String,
    val playerTeam: String,
    val playerImage: String,
    val achievement: String,
    val stats: String,
)

@Composable
fun MatchAwardsCard(
    playerOfMatch: MatchAward,
    otherAwards: List<MatchAward> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "awards")

    val trophyScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trophy",
    )

    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkle",
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
            val goldDarkC = colors.goldDark
            val goldPrimaryC = colors.goldPrimary
            // Premium shimmer background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        goldDarkC.copy(alpha = 0.15f),
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
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(trophyScale),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "MATCH AWARDS",
                        color = colors.goldDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(trophyScale),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Player of the Match - Main Award
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.3f),
                                    colors.goldDark.copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Stars decoration
                        Row {
                            repeat(3) { index ->
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = colors.goldDark.copy(alpha = if (index == 1) sparkleAlpha else 0.5f),
                                    modifier = Modifier.size(if (index == 1) 24.dp else 16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = playerOfMatch.title,
                            color = colors.goldDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Player image with golden ring
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.goldDark.copy(alpha = 0.5f),
                                                colors.goldPrimary.copy(alpha = 0.3f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                CoilImage(
                                    model = playerOfMatch.playerImage,
                                    contentDescription = playerOfMatch.playerName,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape),
                                )
                            }

                            // Trophy badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(colors.goldDark, colors.goldPrimary),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = playerOfMatch.playerName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = playerOfMatch.playerTeam,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats highlight
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            colors.goldPrimary.copy(alpha = 0.4f),
                                            colors.goldDark.copy(alpha = 0.4f),
                                        ),
                                    ),
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = playerOfMatch.stats,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = playerOfMatch.achievement,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }

                // Other awards
                if (otherAwards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "OTHER AWARDS",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    otherAwards.forEach { award ->
                        OtherAwardRow(award = award)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherAwardRow(
    award: MatchAward,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            CoilImage(
                model = award.playerImage,
                contentDescription = award.playerName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = award.title,
                color = colors.statusInfo,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = award.playerName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = award.playerTeam,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = award.stats,
                color = colors.goldDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = award.achievement,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }
    }
}
