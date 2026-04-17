package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Toss information data
 */
data class TossInfo(
    val winningTeam: String,
    val winningTeamLogo: String? = null,
    val losingTeam: String,
    val losingTeamLogo: String? = null,
    val decision: TossDecision,
    val venue: String? = null
)

enum class TossDecision {
    BAT, BOWL
}

/**
 * Premium Toss Information Card
 * Shows toss winner with animated coin flip and decision
 */
@Composable
fun TossInformationCard(
    tossInfo: TossInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "toss")
    
    // Coin shimmer effect
    val coinShimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coinShimmer"
    )
    
    // Subtle rotation for coin
    val coinRotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coinRotation"
    )
    
    // Glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.08f),
                            Color.Transparent,
                            hierarchy.secondary.primary.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🪙",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TOSS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = hierarchy.secondary.primary
                    )
                }
                
                // Animated coin
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val goldPrimary = hierarchy.secondary.primary
                    val goldLight = hierarchy.secondary.secondary
                    val goldDark = hierarchy.secondary.primary
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = size.minDimension / 2 - 4.dp.toPx()
                        
                        rotate(coinRotation, pivot = Offset(centerX, centerY)) {
                            // Outer glow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        goldPrimary.copy(alpha = 0.4f * glowPulse),
                                        goldPrimary.copy(alpha = 0.1f * glowPulse),
                                        Color.Transparent
                                    ),
                                    center = Offset(centerX, centerY),
                                    radius = radius * 1.4f
                                ),
                                radius = radius * 1.4f,
                                center = Offset(centerX, centerY)
                            )
                            
                            // Coin base
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        goldLight,
                                        goldDark,
                                        goldPrimary,
                                        Color(0xFF8B6914)
                                    ),
                                    start = Offset(centerX - radius, centerY - radius),
                                    end = Offset(centerX + radius, centerY + radius)
                                ),
                                radius = radius,
                                center = Offset(centerX, centerY)
                            )
                            
                            // Coin rim
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        goldLight.copy(alpha = 0.8f),
                                        goldPrimary.copy(alpha = 0.5f)
                                    )
                                ),
                                radius = radius,
                                center = Offset(centerX, centerY),
                                style = Stroke(3.dp.toPx())
                            )
                            
                            // Inner circle
                            drawCircle(
                                color = goldPrimary.copy(alpha = 0.3f),
                                radius = radius * 0.7f,
                                center = Offset(centerX, centerY),
                                style = Stroke(1.5.dp.toPx())
                            )
                            
                            // Shimmer highlight
                            val shimmerOffset = (coinShimmer * 2 - 1) * radius * 2
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.4f),
                                        Color.Transparent
                                    ),
                                    start = Offset(centerX + shimmerOffset - radius/2, centerY - radius),
                                    end = Offset(centerX + shimmerOffset + radius/2, centerY + radius)
                                ),
                                radius = radius,
                                center = Offset(centerX, centerY)
                            )
                        }
                    }
                    
                    // Decision text on coin
                    Text(
                        text = if (tossInfo.decision == TossDecision.BAT) "🏏" else "🎯",
                        fontSize = 28.sp
                    )
                }
                
                // Winning team section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WON BY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp,
                        color = colors.textSecondary
                    )
                    
                    // Winner display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Team logo
                        if (tossInfo.winningTeamLogo != null) {
                            CoilImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(tossInfo.winningTeamLogo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = tossInfo.winningTeam,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                hierarchy.secondary.primary.copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(hierarchy.secondary.secondary, hierarchy.secondary.primary)
                                        ),
                                        shape = CircleShape
                                    )
                                    .padding(2.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        
                        Text(
                            text = tossInfo.winningTeam,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
                
                // Decision section with premium styling
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = hierarchy.secondary.primary.copy(alpha = 0.1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        hierarchy.secondary.primary.copy(alpha = 0.5f),
                                        hierarchy.secondary.primary.copy(alpha = 0.3f),
                                        hierarchy.secondary.primary.copy(alpha = 0.5f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ELECTED TO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                color = colors.textSecondary
                            )
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (tossInfo.decision == TossDecision.BAT) 
                                    colors.statusSuccess.copy(alpha = 0.15f) 
                                else 
                                    LiveBlue.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (tossInfo.decision == TossDecision.BAT) "BAT FIRST" else "BOWL FIRST",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (tossInfo.decision == TossDecision.BAT) colors.statusSuccess else LiveBlue,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Venue if available
                tossInfo.venue?.let { venue ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = venue,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact toss display for headers
 */
@Composable
fun CompactTossInfo(
    tossInfo: TossInfo,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = hierarchy.secondary.primary.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = hierarchy.secondary.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🪙",
                fontSize = 14.sp
            )
            
            Text(
                text = tossInfo.winningTeam,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            
            Text(
                text = "elected to",
                fontSize = 11.sp,
                color = colors.textSecondary
            )
            
            Text(
                text = if (tossInfo.decision == TossDecision.BAT) "bat" else "bowl",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (tossInfo.decision == TossDecision.BAT) colors.statusSuccess else LiveBlue
            )
        }
    }
}
