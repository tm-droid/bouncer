package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * Premium player profile and stats components
 */

data class PlayerStats(
    val name: String,
    val role: String = "",
    val imageUrl: String? = null,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val overs: String = "0.0",
    val economy: Float = 0f,
    val maidens: Int = 0,
    val isOnStrike: Boolean = false,
    val isBowling: Boolean = false,
    val isOut: Boolean = false,
    val dismissalInfo: String = ""
)

@Composable
fun PremiumPlayerCard(
    player: PlayerStats,
    modifier: Modifier = Modifier,
    showDetailedStats: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "player")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val colors = BouncerDesignSystem.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.glassSurface,
                        colors.glassSurface
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.goldPrimary.copy(alpha = if (player.isOnStrike) glowAlpha else 0.3f),
                        colors.goldLight.copy(alpha = if (player.isOnStrike) glowAlpha * 0.5f else 0.1f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player info section
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player avatar
                PlayerAvatar(
                    imageUrl = player.imageUrl,
                    name = player.name,
                    size = 56.dp,
                    isOnStrike = player.isOnStrike,
                    isOut = player.isOut
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = player.name,
                            color = if (player.isOut) colors.textMuted else colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (player.isOnStrike) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors.goldPrimary, CircleShape)
                            )
                        }
                    }
                    
                    if (player.role.isNotEmpty()) {
                        Text(
                            text = player.role,
                            color = colors.goldPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    
                    if (player.isOut && player.dismissalInfo.isNotEmpty()) {
                        Text(
                            text = player.dismissalInfo,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Stats section
            if (showDetailedStats) {
                if (player.isBowling) {
                    BowlerStatsDisplay(player)
                } else {
                    BatsmanStatsDisplay(player)
                }
            }
        }
    }
}

@Composable
fun PlayerAvatar(
    imageUrl: String?,
    name: String,
    size: Dp = 48.dp,
    isOnStrike: Boolean = false,
    isOut: Boolean = false
) {
    val colors = BouncerDesignSystem.colors

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect for on-strike player
        if (isOnStrike) {
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .blur(8.dp)
                    .background(colors.goldPrimary.copy(alpha = 0.4f), CircleShape)
            )
        }
        
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (isOut) 
                        GlassSurface 
                    else 
                        colors.glassSurface
                )
                .border(
                    width = 2.dp,
                    brush = if (isOnStrike) {
                        Brush.linearGradient(
                            colors = listOf(colors.goldPrimary, colors.goldLight)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = 0.5f),
                                colors.goldLight.copy(alpha = 0.2f)
                            )
                        )
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                CoilImage(
                    model = imageUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isOut) 0.5f else 1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = name.take(2).uppercase(),
                    color = if (isOut) colors.textMuted else colors.goldPrimary,
                    fontSize = (size.value / 3).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BatsmanStatsDisplay(player: PlayerStats) {
    val colors = BouncerDesignSystem.colors

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Runs and balls
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${player.runs}",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(${player.balls})",
                color = colors.textTertiary,
                fontSize = 14.sp
            )
        }
        
        // Strike rate
        val strikeRate = if (player.balls > 0) {
            (player.runs.toFloat() / player.balls * 100)
        } else 0f
        
        Text(
            text = "SR: %.1f".format(strikeRate),
            color = when {
                strikeRate >= 150 -> Color(0xFF22C55E)
                strikeRate >= 100 -> colors.goldPrimary
                else -> Color(0xFFEF4444)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        
        // Boundaries
        if (player.fours > 0 || player.sixes > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "4s: ${player.fours}",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "6s: ${player.sixes}",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun BowlerStatsDisplay(player: PlayerStats) {
    val colors = BouncerDesignSystem.colors

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Wickets and overs
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${player.wickets}/${player.runs}",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(${player.overs})",
                color = colors.textTertiary,
                fontSize = 14.sp
            )
        }
        
        // Economy
        Text(
            text = "Econ: %.1f".format(player.economy),
            color = when {
                player.economy <= 6 -> Color(0xFF22C55E)
                player.economy <= 8 -> colors.goldPrimary
                else -> Color(0xFFEF4444)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        
        // Maidens
        if (player.maidens > 0) {
            Text(
                text = "Maidens: ${player.maidens}",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun PlayerPerformanceChart(
    recentScores: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color = GoldPrimary
) {
    if (recentScores.isEmpty()) return
    
    val colors = BouncerDesignSystem.colors
    val maxScore = (recentScores.maxOrNull() ?: 0).coerceAtLeast(1)
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "chart"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (recentScores.size - 1).coerceAtLeast(1)
        
        // Create path
        val path = Path()
        val fillPath = Path()
        
        recentScores.forEachIndexed { index, score ->
            val x = index * stepX * animatedProgress
            val y = height - (score.toFloat() / maxScore * height * 0.8f)
            
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(0f, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        fillPath.lineTo(width * animatedProgress, height)
        fillPath.close()
        
        // Draw fill gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.3f),
                    Color.Transparent
                )
            )
        )
        
        // Draw line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw points
        recentScores.forEachIndexed { index, score ->
            val x = index * stepX * animatedProgress
            val y = height - (score.toFloat() / maxScore * height * 0.8f)
            
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = colors.textPrimary,
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun PlayerStatItem(
    label: String,
    value: String,
    trend: Int = 0, // -1 down, 0 neutral, 1 up
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (trend != 0) {
                Icon(
                    imageVector = if (trend > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (trend > 0) Color(0xFF22C55E) else Color(0xFFEF4444),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        Text(
            text = label,
            color = colors.textTertiary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun PlayerRatingBadge(
    rating: Float,
    maxRating: Float = 10f,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors

    val normalizedRating = (rating / maxRating).coerceIn(0f, 1f)
    val ratingColor = when {
        normalizedRating >= 0.8f -> Color(0xFF22C55E)
        normalizedRating >= 0.6f -> colors.goldPrimary
        normalizedRating >= 0.4f -> Color(0xFFFFAA00)
        else -> Color(0xFFEF4444)
    }
    
    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ratingColor.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = ratingColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "%.1f".format(rating),
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlayerOfTheMatchCard(
    player: PlayerStats,
    reason: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "potm")
    
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val starScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star"
    )
    
    val colors = BouncerDesignSystem.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.goldDark.copy(alpha = 0.3f),
                        colors.goldPrimary.copy(alpha = 0.2f),
                        colors.goldDark.copy(alpha = 0.3f)
                    ),
                    start = Offset(shimmerProgress * 1000, 0f),
                    end = Offset((shimmerProgress + 1) * 1000, 0f)
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(colors.goldDark, colors.goldPrimary, colors.goldLight)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with star
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = colors.goldPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(starScale)
                )
                Text(
                    text = "PLAYER OF THE MATCH",
                    color = colors.goldPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = colors.goldPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(starScale)
                )
            }
            
            // Player avatar
            PlayerAvatar(
                imageUrl = player.imageUrl,
                name = player.name,
                size = 80.dp,
                isOnStrike = true
            )
            
            // Player name
            Text(
                text = player.name,
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Role
            if (player.role.isNotEmpty()) {
                Text(
                    text = player.role,
                    color = colors.goldPrimary,
                    fontSize = 14.sp
                )
            }
            
            // Performance summary
            Text(
                text = reason,
                color = colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MiniPlayerBadge(
    name: String,
    imageUrl: String? = null,
    stat: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.glassSurface)
            .border(
                width = 1.dp,
                color = colors.goldPrimary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerAvatar(
            imageUrl = imageUrl,
            name = name,
            size = 28.dp
        )
        
        Column {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stat,
                color = colors.goldPrimary,
                fontSize = 11.sp
            )
        }
    }
}
