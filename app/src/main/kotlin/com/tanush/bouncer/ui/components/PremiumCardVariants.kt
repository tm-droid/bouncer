package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium glass card with blur effect
 */
@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentColor: Color = GoldPrimary,
    content: @Composable () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "glassCard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = Elevation.high,
                shape = RoundedCornerShape(CornerRadius.md),
                spotColor = accentColor.copy(alpha = 0.2f)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = accentColor.copy(alpha = 0.1f)),
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(CornerRadius.md),
        color = GlassSurface.copy(alpha = 0.85f),
        border = BorderStroke(
            BorderWidth.hairline,
            Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.4f + shimmerOffset * 0.2f),
                    GlassBorder.copy(alpha = 0.2f),
                    accentColor.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Box {
            // Glass overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.05f)
                            )
                        )
                    )
            )
            
            content()
        }
    }
}

/**
 * Premium feature card with icon and description
 */
@Composable
fun PremiumFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color = GoldPrimary,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    PremiumGlassCard(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        onClick = onClick,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        accentColor.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Premium stat display card
 */
@Composable
fun PremiumStatCard(
    value: String,
    label: String,
    icon: ImageVector? = null,
    trend: TrendDirection? = null,
    trendValue: String = "",
    accentColor: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "statCard")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    PremiumGlassCard(
        modifier = modifier,
        accentColor = accentColor
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon with glow
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Value
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )
            
            // Label
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            
            // Trend
            if (trend != null && trendValue.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when (trend) {
                            TrendDirection.UP -> Icons.AutoMirrored.Filled.TrendingUp
                            TrendDirection.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
                            TrendDirection.NEUTRAL -> Icons.AutoMirrored.Filled.TrendingFlat
                        },
                        contentDescription = null,
                        tint = when (trend) {
                            TrendDirection.UP -> ChartGreen
                            TrendDirection.DOWN -> StatusLive
                            TrendDirection.NEUTRAL -> TextTertiary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Text(
                        text = trendValue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (trend) {
                            TrendDirection.UP -> ChartGreen
                            TrendDirection.DOWN -> StatusLive
                            TrendDirection.NEUTRAL -> TextTertiary
                        }
                    )
                }
            }
        }
    }
}

enum class TrendDirection {
    UP, DOWN, NEUTRAL
}

/**
 * Premium info card with key-value pairs
 */
@Composable
fun PremiumInfoCard(
    title: String,
    items: List<Pair<String, String>>,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    text = title.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                hierarchy.secondary.primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Items
            items.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Premium profile/avatar card
 */
@Composable
fun PremiumProfileCard(
    name: String,
    subtitle: String = "",
    imageUrl: String? = null,
    stats: List<Pair<String, String>> = emptyList(),
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "profileCard")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border"
    )

    PremiumGlassCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(
                        2.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                hierarchy.secondary.secondary.copy(alpha = borderAlpha),
                                hierarchy.secondary.primary.copy(alpha = borderAlpha * 0.5f)
                            )
                        ),
                        CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(GlassBorderHighlight)
            ) {
                if (imageUrl != null) {
                    CoilImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(2).uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = hierarchy.secondary.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Name
            Text(
                text = name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            
            // Stats row
            if (stats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    stats.forEach { (label, value) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = value,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = hierarchy.secondary.primary
                            )
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Premium horizontal stat bar
 */
@Composable
fun PremiumStatBar(
    value: Float,
    maxValue: Float = 100f,
    label: String = "",
    valueText: String = "",
    color: Color = GoldPrimary,
    height: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val progress = (value / maxValue).coerceIn(0f, 1f)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label.isNotEmpty() || valueText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                
                Text(
                    text = valueText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(GlassBorder.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(height / 2))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.7f),
                                color,
                                color.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Premium comparison card for head-to-head
 */
@Composable
fun PremiumComparisonCard(
    leftLabel: String,
    rightLabel: String,
    leftValue: Int,
    rightValue: Int,
    leftColor: Color = ChartGreen,
    rightColor: Color = StatusInfo,
    modifier: Modifier = Modifier
) {
    val total = leftValue + rightValue
    val leftProgress = if (total > 0) leftValue.toFloat() / total else 0.5f
    
    val animatedProgress by animateFloatAsState(
        targetValue = leftProgress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "comparison"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Labels and values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = leftValue.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = leftColor
                    )
                    Text(
                        text = leftLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = rightValue.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = rightColor
                    )
                    Text(
                        text = rightLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
            
            // Progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(animatedProgress.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(leftColor)
                )
                Box(
                    modifier = Modifier
                        .weight((1f - animatedProgress).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(rightColor)
                )
            }
        }
    }
}

/**
 * Premium countdown/timer card
 */
@Composable
fun PremiumCountdownCard(
    days: Int,
    hours: Int,
    minutes: Int,
    seconds: Int,
    title: String = "Starts In",
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "countdown")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Time units
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeUnit(value = days, label = "Days")
                
                Text(
                    text = ":",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = hierarchy.secondary.primary.copy(alpha = 0.5f),
                    modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                )
                
                TimeUnit(value = hours, label = "Hrs")
                
                Text(
                    text = ":",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = hierarchy.secondary.primary.copy(alpha = 0.5f),
                    modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                )
                
                TimeUnit(value = minutes, label = "Min")
                
                Text(
                    text = ":",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = hierarchy.secondary.primary.copy(alpha = 0.5f),
                    modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                )
                
                TimeUnit(value = seconds, label = "Sec", isAnimated = true)
            }
        }
    }
}

@Composable
private fun TimeUnit(
    value: Int,
    label: String,
    isAnimated: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val displayValue = value.toString().padStart(2, '0')
    
    val infiniteTransition = rememberInfiniteTransition(label = "timeUnit")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimated) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(
                    hierarchy.secondary.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = displayValue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = hierarchy.secondary.secondary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextTertiary
        )
    }
}
