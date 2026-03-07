package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Key moments card highlighting significant match events
 */

data class HighlightMoment(
    val overNumber: Float,
    val title: String,
    val description: String,
    val type: HighlightHighlightMomentType,
    val playerName: String? = null,
    val playerImageUrl: String? = null,
    val runs: Int? = null,
    val timestamp: String = "",
    val impactScore: Float = 0.5f // 0-1, how impactful this moment was
)

enum class HighlightHighlightMomentType(val emoji: String, val color: Color) {
    WICKET("🎯", WicketRed),
    SIX("6️⃣", BoundarySix),
    FOUR("4️⃣", BoundaryFour),
    FIFTY("🔥", ShinyGoldMid),
    CENTURY("💯", ShinyGoldDeep),
    FIVE_WICKETS("🏆", SuccessGreen),
    CATCH("👏", ChartBlue),
    RUNOUT("⚡", WarningYellow),
    DRS_REVIEW("📺", Color.Magenta),
    DROPPED_CATCH("😱", TextTertiary),
    MILESTONE("⭐", ShinyGoldLight),
    INJURY("🏥", Color.Red),
    RAIN_DELAY("🌧️", ChartBlue),
    POWERPLAY_END("⚡", BoundarySix),
    SUPER_OVER("🎉", Color.Magenta)
}

@Composable
fun HighlightMomentsCard(
    moments: List<HighlightMoment>,
    modifier: Modifier = Modifier,
    onMomentClick: ((HighlightMoment) -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "moments_animation")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Key Moments",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    color = colors.goldPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${moments.size} MOMENTS",
                        color = colors.goldDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Timeline visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val timelineY = size.height / 2
                    
                    // Draw timeline
                    drawLine(
                        color = GlassBorder,
                        start = Offset(0f, timelineY),
                        end = Offset(size.width, timelineY),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    
                    // Draw moment markers
                    val maxOver = moments.maxOfOrNull { it.overNumber } ?: 20f
                    moments.forEach { moment ->
                        val x = (moment.overNumber / maxOver) * size.width
                        val markerRadius = 8f + (moment.impactScore * 6f)
                        
                        // Glow effect
                        drawCircle(
                            color = moment.type.color.copy(alpha = 0.3f * glowAlpha),
                            radius = markerRadius + 6f,
                            center = Offset(x, timelineY)
                        )
                        
                        // Marker
                        drawCircle(
                            color = moment.type.color,
                            radius = markerRadius,
                            center = Offset(x, timelineY)
                        )
                    }
                }
            }
            
            // Over labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "overs",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
                val maxOver = moments.maxOfOrNull { it.overNumber }?.toInt() ?: 20
                Text(
                    text = "$maxOver",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Moments list
            moments.sortedByDescending { it.impactScore }.take(5).forEachIndexed { index, moment ->
                MomentRow(
                    moment = moment,
                    onClick = { onMomentClick?.invoke(moment) }
                )
                if (index < minOf(4, moments.size - 1)) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            // View all if more moments
            if (moments.size > 5) {
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "View all ${moments.size} moments",
                        color = colors.statusInfo,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentRow(
    moment: HighlightMoment,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = moment.type.color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(moment.type.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = moment.type.emoji,
                    fontSize = 22.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = moment.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "${moment.overNumber} ov",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = moment.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                
                if (moment.playerName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = moment.playerName,
                        color = moment.type.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Impact indicator
            ImpactIndicator(impact = moment.impactScore)
        }
    }
}

@Composable
private fun ImpactIndicator(impact: Float) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "impact_animation")
    
    // Capture colors for Canvas scope
    val statusLiveColor = colors.statusLive
    val statusWarningColor = colors.statusWarning
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (impact > 0.8f) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                
                // Background
                drawCircle(
                    color = GlassSurface,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 3f)
                )
                
                // Impact arc
                val sweepAngle = 360f * impact
                val impactColor = when {
                    impact > 0.8f -> statusLiveColor
                    impact > 0.5f -> statusWarningColor
                    else -> TextTertiary
                }
                
                drawArc(
                    color = impactColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }
            
            Text(
                text = "${(impact * 10).toInt()}",
                color = colors.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = "IMPACT",
            color = colors.textMuted,
            fontSize = 7.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Single highlighted moment card for featured display
 */
@Composable
fun FeaturedMomentCard(
    moment: HighlightMoment,
    modifier: Modifier = Modifier,
    onLike: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "featured_animation")
    
    val borderPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "border_phase"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box {
            // Animated gradient border effect
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            radiusX = 24.dp.toPx(),
                            radiusY = 24.dp.toPx()
                        )
                    )
                }
                
                drawPath(
                    path = path,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            moment.type.color.copy(alpha = 0.5f),
                            Color.Transparent,
                            moment.type.color.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(
                            size.width / 2 + cos(borderPhase * PI / 180f).toFloat() * size.width / 2,
                            size.height / 2 + sin(borderPhase * PI / 180f).toFloat() * size.height / 2
                        )
                    ),
                    style = Stroke(width = 3f)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🌟",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MOMENT OF THE MATCH",
                            color = colors.goldDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                    
                    Text(
                        text = "${moment.overNumber} ov",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Moment emoji large
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = moment.type.emoji,
                        fontSize = 64.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Title
                Text(
                    text = moment.title,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description
                Text(
                    text = moment.description,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Player name
                if (moment.playerName != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (moment.playerImageUrl != null) {
                            CoilImage(
                                model = moment.playerImageUrl,
                                contentDescription = moment.playerName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = moment.playerName,
                            color = moment.type.color,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (onLike != null) {
                        IconButton(onClick = onLike) {
                            Icon(
                                imageVector = Icons.Filled.FavoriteBorder,
                                contentDescription = "Like",
                                tint = colors.statusLive.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = colors.statusInfo.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
