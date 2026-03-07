package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Premium tooltip, info, and contextual help components
 */

@Composable
fun PremiumTooltip(
    text: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    position: TooltipPosition = TooltipPosition.BOTTOM
) {
    val colors = BouncerDesignSystem.colors
    val goldDark = colors.goldDark
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .alpha(alpha)
                .scale(scale)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val arrowSize = 8.dp.toPx()
                val cornerRadius = 8.dp.toPx()
                
                val path = Path().apply {
                    when (position) {
                        TooltipPosition.TOP -> {
                            // Draw tooltip above with arrow pointing down
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    0f, 0f, size.width, size.height - arrowSize,
                                    CornerRadius(cornerRadius)
                                )
                            )
                            moveTo(size.width / 2 - arrowSize, size.height - arrowSize)
                            lineTo(size.width / 2, size.height)
                            lineTo(size.width / 2 + arrowSize, size.height - arrowSize)
                        }
                        TooltipPosition.BOTTOM -> {
                            // Draw tooltip below with arrow pointing up
                            moveTo(size.width / 2 - arrowSize, arrowSize)
                            lineTo(size.width / 2, 0f)
                            lineTo(size.width / 2 + arrowSize, arrowSize)
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    0f, arrowSize, size.width, size.height,
                                    CornerRadius(cornerRadius)
                                )
                            )
                        }
                        else -> {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    0f, 0f, size.width, size.height,
                                    CornerRadius(cornerRadius)
                                )
                            )
                        }
                    }
                }
                
                drawPath(
                    path = path,
                    color = CardSurfaceColor
                )
                drawPath(
                    path = path,
                    color = goldDark.copy(alpha = 0.3f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            Box(
                modifier = Modifier
                    .padding(
                        top = if (position == TooltipPosition.BOTTOM) 8.dp else 0.dp,
                        bottom = if (position == TooltipPosition.TOP) 8.dp else 0.dp
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

enum class TooltipPosition {
    TOP, BOTTOM, LEFT, RIGHT
}

@Composable
fun InfoPopup(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Info,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "alpha"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .alpha(alpha)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassSurface,
                            GlassSurface
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(colors.goldDark.copy(alpha = 0.5f), colors.goldLight.copy(alpha = 0.2f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.goldDark.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = colors.goldDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,

                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDismiss() }
                    )
                }
                
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun HelpBubble(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF3B82F6).copy(alpha = 0.2f),
                        Color(0xFF6366F1).copy(alpha = 0.1f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF3B82F6).copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = Color(0xFF3B82F6),
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun WarningBanner(
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF9500).copy(alpha = 0.2f),
                        Color(0xFFFFCC00).copy(alpha = 0.1f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFFFF9500).copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFFF9500),
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = Color(0xFFFF9500),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
fun SuccessBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "success")
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF22C55E).copy(alpha = 0.2f),
                        Color(0xFF10B981).copy(alpha = 0.1f)
                    )
                )
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    start = Offset(shimmer * 500, 0f),
                    end = Offset((shimmer + 0.5f) * 500, 0f)
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF22C55E).copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ContextualHelp(
    title: String,
    tips: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(
                width = 1.dp,
                color = colors.goldDark.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                tint = colors.goldDark,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tips.forEach { tip ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.goldDark.copy(alpha = 0.6f))
                    )
                    
                    Text(
                        text = tip,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuickTip(
    tip: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "tip")
    
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    
    if (isVisible) {
        Row(
            modifier = modifier
                .offset(y = bounce.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            colors.goldDark.copy(alpha = 0.2f),
                            colors.goldLight.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = colors.goldDark.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TipsAndUpdates,
                contentDescription = null,
                tint = colors.goldDark,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = tip,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = TextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

@Composable
fun GlossaryItem(
    term: String,
    definition: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = term,
            color = colors.goldDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = definition,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun FeatureSpotlight(
    title: String,
    description: String,
    icon: ImageVector,
    isNew: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight")
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier
            .scale(if (isNew) pulse else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.goldDark.copy(alpha = 0.15f),
                        GlassSurface
                    )
                )
            )
            .border(
                width = if (isNew) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(colors.goldDark, colors.goldLight.copy(alpha = 0.5f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        if (isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.goldDark)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NEW",
                    color = colors.backgroundBase,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.goldDark.copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = colors.goldDark.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun InteractiveGuide(
    steps: List<String>,
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(
                width = 1.dp,
                color = colors.goldDark.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Guide",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${currentStep + 1}/${steps.size}",
                color = colors.goldDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Current step
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.goldDark.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text(
                text = steps[currentStep],
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        
        // Navigation dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, _ ->
                val isActive = index == currentStep
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isActive) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) colors.goldDark else colors.goldDark.copy(alpha = 0.3f)
                        )
                        .clickable { onStepChange(index) }
                )
            }
        }
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onStepChange(currentStep - 1) }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Previous",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            if (currentStep < steps.size - 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onStepChange(currentStep + 1) }
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Next",
                        color = colors.goldDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next",
                        tint = colors.goldDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
