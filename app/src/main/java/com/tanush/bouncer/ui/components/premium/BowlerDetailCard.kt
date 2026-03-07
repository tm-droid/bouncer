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
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Bowler Detail Card
 *
 * TODO: Integration Requirements:
 * - Fetch live bowling statistics from match data service
 * - Load bowler image from player database CDN
 * - Show real-time economy rate calculation
 * - Add pitch map integration for bowling lengths
 * - Display vs batsman matchup statistics
 * - Show spell breakdown with rest periods
 * - Add career stats comparison toggle
 * - Navigate to full player profile on tap
 * - Track variations (slower balls, yorkers, bouncers)
 * - Cache player stats for quick access
 */

data class BowlerDetailStats(
    val overs: String,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Double,
    val wides: Int,
    val noBalls: Int,
    val dotBallPercentage: Float,
)

data class BowlerSpellInfo(
    val overStart: String,
    val overEnd: String,
    val figures: String,
    val isCurrentSpell: Boolean = false,
)

@Composable
fun BowlerDetailCard(
    bowlerName: String,
    bowlerImageUrl: String?,
    teamName: String,
    stats: BowlerDetailStats,
    bowlingType: String, // "Right-arm Fast", "Left-arm Spin", etc.
    currentOver: List<String> = emptyList(),
    spells: List<BowlerSpellInfo> = emptyList(),
    isBowling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "bowler")

    val bowlingGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bowling",
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
            // Bowling glow
            if (isBowling) {
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.statusInfo.copy(alpha = 0.2f * bowlingGlow),
                                Color.Transparent,
                            ),
                            center = Offset(size.width - 100f, 100f),
                            radius = 300f,
                        ),
                        center = Offset(size.width - 100f, 100f),
                        radius = 300f,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Bowler header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Bowler image
                    Box(
                        modifier = Modifier.size(70.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBowling) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.statusInfo.copy(alpha = bowlingGlow * 0.5f),
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
                                            colors.textTertiary,
                                            colors.glassBorder,
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bowlerImageUrl != null) {
                                CoilImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(bowlerImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = bowlerName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Bowler info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = bowlerName,
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            if (isBowling) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.statusInfo.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "BOWLING",
                                        color = colors.statusInfo,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Text(
                            text = bowlingType,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    // Figures
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${stats.wickets}/${stats.runs}",
                            color = when {
                                stats.wickets >= 5 -> colors.goldDark
                                stats.wickets >= 3 -> colors.statusSuccess
                                else -> colors.textPrimary
                            },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "(${stats.overs})",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current over
                if (currentOver.isNotEmpty()) {
                    Text(
                        text = "THIS OVER",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentOver.forEach { ball ->
                            BowlerBallChip(ball = ball)
                        }

                        // Empty slots for remaining balls
                        repeat(6 - currentOver.size) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.glassSurface),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Stats grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.glassSurface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BowlerStatItem(
                        label = "Economy",
                        value = String.format("%.1f", stats.economy),
                        color = when {
                            stats.economy < 6.0 -> colors.statusSuccess
                            stats.economy > 9.0 -> colors.statusLive
                            else -> colors.goldDark
                        },
                    )

                    BowlerStatItem(
                        label = "Maidens",
                        value = "${stats.maidens}",
                        color = colors.statusSuccess,
                    )

                    BowlerStatItem(
                        label = "Dots%",
                        value = "${(stats.dotBallPercentage * 100).toInt()}%",
                        color = colors.statusInfo,
                    )

                    BowlerStatItem(
                        label = "Extras",
                        value = "${stats.wides + stats.noBalls}",
                        color = if (stats.wides + stats.noBalls > 5) colors.statusLive else Color.Gray,
                    )
                }

                // Spells
                if (spells.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SPELLS",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        spells.forEach { spell ->
                            SpellChip(
                                spell = spell,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BowlerBallChip(ball: String) {
    val colors = BouncerDesignSystem.colors
    val (bgColor, textColor) = when (ball) {
        "W" -> Pair(colors.statusLive, Color.White)
        "6" -> Pair(colors.statusWarning, Color.White)
        "4" -> Pair(colors.statusWarning, Color.White)
        "0" -> Pair(colors.statusSuccess.copy(alpha = 0.3f), colors.statusSuccess)
        "Wd", "Nb" -> Pair(colors.statusWarning.copy(alpha = 0.3f), colors.statusWarning)
        else -> Pair(colors.statusInfo.copy(alpha = 0.3f), colors.statusInfo)
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
private fun BowlerStatItem(
    label: String,
    value: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SpellChip(
    spell: BowlerSpellInfo,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (spell.isCurrentSpell) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.statusInfo.copy(alpha = 0.2f),
                            colors.statusInfo.copy(alpha = 0.1f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.glassSurface,
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = spell.figures,
                color = if (spell.isCurrentSpell) colors.statusInfo else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${spell.overStart}-${spell.overEnd}",
                color = colors.textSecondary,
                fontSize = 10.sp,
            )
        }
    }
}
