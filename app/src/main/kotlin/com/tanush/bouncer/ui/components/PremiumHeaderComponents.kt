package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium animated header with parallax effects and luxury styling
 */
@Composable
fun PremiumAnimatedHeader(
    title: String,
    subtitle: String? = null,
    matchCount: Int = 0,
    selectedStatus: String = "Live",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // Background with ambient particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Rotating gold accent circle
            rotate(rotationAngle, pivot = Offset(size.width * 0.8f, size.height * 0.3f)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ShinyGoldDeep.copy(alpha = 0.05f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.8f, size.height * 0.3f),
                        radius = size.width * 0.4f
                    ),
                    radius = size.width * 0.4f,
                    center = Offset(size.width * 0.8f, size.height * 0.3f)
                )
            }
            
            // Secondary accent
            rotate(-rotationAngle * 0.5f, pivot = Offset(size.width * 0.2f, size.height * 0.7f)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ChartPurple.copy(alpha = 0.03f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.7f),
                        radius = size.width * 0.35f
                    ),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.2f, size.height * 0.7f)
                )
            }
            
            // Shimmer line
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        ShinyGoldDeep.copy(alpha = 0.3f * glowPulse),
                        ShinyGoldLight.copy(alpha = 0.5f * glowPulse),
                        ShinyGoldDeep.copy(alpha = 0.3f * glowPulse),
                        Color.Transparent
                    ),
                    start = Offset(shimmerOffset * size.width * 2 - size.width * 0.5f, 0f),
                    end = Offset(shimmerOffset * size.width * 2 - size.width * 0.5f + 200f, size.height)
                ),
                start = Offset(0f, size.height - 1),
                end = Offset(size.width, size.height - 1),
                strokeWidth = 2f
            )
        }
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Match status badge
                StatusBadge(
                    status = selectedStatus,
                    matchCount = matchCount,
                    glowPulse = glowPulse
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title with shimmer
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = Color.White,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 0.9f + (glowPulse - 0.6f) * 0.25f
                        }
                )
                
                // Subtitle
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusBadge(
    status: String,
    matchCount: Int,
    glowPulse: Float
) {
    val isLive = status.equals("Live", ignoreCase = true)
    val badgeColor = when {
        isLive -> LiveRedPulse
        status.equals("Upcoming", ignoreCase = true) -> ChartBlue
        else -> SuccessGreen
    }
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = badgeColor.copy(alpha = 0.15f),
        modifier = Modifier
            .shadow(
                elevation = if (isLive) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = badgeColor.copy(alpha = if (isLive) 0.3f * glowPulse else 0.15f)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pulsing dot for live
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(badgeColor.copy(alpha = glowPulse), shape = androidx.compose.foundation.shape.CircleShape)
                        .drawBehind {
                            drawCircle(
                                color = badgeColor.copy(alpha = 0.3f * glowPulse),
                                radius = size.maxDimension * (0.8f + glowPulse * 0.5f)
                            )
                        }
                )
            } else {
                Icon(
                    imageVector = when {
                        status.equals("Upcoming", ignoreCase = true) -> Icons.Default.Schedule
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            Text(
                text = "$status - $matchCount match${if (matchCount != 1) "es" else ""}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = badgeColor
            )
        }
    }
}

/**
 * Premium section header with animated accent
 */
@Composable
fun PremiumSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sectionHeader")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated accent line
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ShinyGoldLight.copy(alpha = 0.5f + shimmerOffset * 0.3f),
                            ShinyGoldDeep,
                            ShinyGoldDark.copy(alpha = 0.7f)
                        )
                    )
                )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon?.invoke()
                
                Text(
                    text = title.uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = ShinyGoldDeep
                )
            }
            
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        action?.invoke()
    }
}

/**
 * Premium stat display card with animations
 */
@Composable
fun PremiumStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trend: StatTrend = StatTrend.NEUTRAL,
    trendValue: String? = null,
    accentColor: Color = ShinyGoldDeep
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statCard")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Accent glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.1f, size.height * 0.1f),
                            radius = size.width * 0.6f
                        ),
                        radius = size.width * 0.6f,
                        center = Offset(size.width * 0.1f, size.height * 0.1f)
                    )
                }
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon with glow
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                accentColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    // Trend indicator
                    if (trend != StatTrend.NEUTRAL && trendValue != null) {
                        TrendIndicator(trend = trend, value = trendValue)
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = TextTertiary
                    )
                    
                    Text(
                        text = value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

enum class StatTrend { UP, DOWN, NEUTRAL }

@Composable
private fun TrendIndicator(
    trend: StatTrend,
    value: String
) {
    val (icon, color) = when (trend) {
        StatTrend.UP -> Icons.Default.TrendingUp to SuccessGreen
        StatTrend.DOWN -> Icons.Default.TrendingDown to ErrorRed
        StatTrend.NEUTRAL -> Icons.Default.Remove to TextTertiary
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * Premium countdown timer display
 */
@Composable
fun PremiumCountdown(
    days: Int,
    hours: Int,
    minutes: Int,
    seconds: Int,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = TextTertiary
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (days > 0) {
                CountdownUnit(value = days, label = "DAYS")
                CountdownSeparator()
            }
            CountdownUnit(value = hours, label = "HRS")
            CountdownSeparator()
            CountdownUnit(value = minutes, label = "MIN")
            CountdownSeparator()
            CountdownUnit(value = seconds, label = "SEC", isAnimated = true)
        }
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String,
    isAnimated: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "countdown")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.graphicsLayer {
                if (isAnimated) {
                    scaleX = pulse
                    scaleY = pulse
                }
            }
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = ShinyGoldDeep,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun CountdownSeparator() {
    val infiniteTransition = rememberInfiniteTransition(label = "separator")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "separatorAlpha"
    )
    
    Text(
        text = ":",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = ShinyGoldDeep.copy(alpha = alpha),
        modifier = Modifier.padding(bottom = 16.dp)
    )
}
