package com.tanush.bouncer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Tab item data
 */
data class PremiumTabItem(
    val title: String,
    val icon: ImageVector? = null,
    val badge: Int? = null // For notification counts
)

/**
 * Premium Tab Row with animated gold underline
 */
@Composable
fun PremiumTabRow(
    tabs: List<PremiumTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tab_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                PremiumTab(
                    tab = tab,
                    isSelected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Animated indicator
        AnimatedTabIndicator(
            selectedIndex = selectedIndex,
            tabCount = tabs.size,
            shimmerOffset = shimmerOffset,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PremiumTab(
    tab: PremiumTabItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.5f,
        animationSpec = tween(300),
        label = "tab_alpha"
    )
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tab_scale"
    )
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (tab.icon != null) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) ShinyGoldDeep else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            
            Text(
                text = tab.title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) ShinyGoldDeep else TextSecondary,
                letterSpacing = if (isSelected) 0.sp else (-0.2).sp
            )
            
            // Badge
            if (tab.badge != null && tab.badge > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(NeonError, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tab.badge > 9) "9+" else tab.badge.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedTabIndicator(
    selectedIndex: Int,
    tabCount: Int,
    shimmerOffset: Float,
    modifier: Modifier = Modifier
) {
    val targetOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat() / tabCount,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicator_offset"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        val indicatorWidth = size.width / tabCount * 0.6f
        val tabWidth = size.width / tabCount
        val startX = (tabWidth * selectedIndex) + (tabWidth - indicatorWidth) / 2
        
        // Glow effect
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    ShinyGoldDeep.copy(alpha = 0.3f),
                    ShinyGoldDeep.copy(alpha = 0.5f),
                    ShinyGoldDeep.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                startX = startX - 20f,
                endX = startX + indicatorWidth + 20f
            ),
            topLeft = Offset(startX - 10f, -2f),
            size = Size(indicatorWidth + 20f, 7f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        
        // Main indicator with gradient
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    ShinyGoldLight,
                    ShinyGoldDeep,
                    ShinyGoldDark
                ),
                startX = startX,
                endX = startX + indicatorWidth
            ),
            topLeft = Offset(startX, 0f),
            size = Size(indicatorWidth, size.height),
            cornerRadius = CornerRadius(2f, 2f)
        )
        
        // Shimmer effect on indicator
        val shimmerStart = shimmerOffset - 100f
        val shimmerEnd = shimmerOffset + 100f
        
        if (shimmerStart < startX + indicatorWidth && shimmerEnd > startX) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.5f),
                        Color.Transparent
                    ),
                    startX = shimmerStart,
                    endX = shimmerEnd
                ),
                topLeft = Offset(startX, 0f),
                size = Size(indicatorWidth, size.height),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

/**
 * Segmented Control with premium styling
 */
@Composable
fun PremiumSegmentedControl(
    segments: List<String>,
    selectedIndex: Int,
    onSegmentSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .border(
                0.5.dp,
                CardBorder.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            segments.forEachIndexed { index, segment ->
                val isSelected = index == selectedIndex
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) ShinyGoldDeep else Color.Transparent,
                    animationSpec = tween(200),
                    label = "segment_bg"
                )
                
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) PrimaryBackground else TextSecondary,
                    animationSpec = tween(200),
                    label = "segment_text"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSegmentSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = segment,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * Pill Tab Row - rounded pill style tabs
 */
@Composable
fun PillTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy
                ),
                label = "pill_scale"
            )
            
            Surface(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(index) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) ShinyGoldDeep else Color.Transparent,
                border = if (!isSelected) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        CardBorder.copy(alpha = 0.3f)
                    )
                } else null
            ) {
                Text(
                    text = tab,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) PrimaryBackground else TextSecondary
                )
            }
        }
    }
}

/**
 * Glowing Tab with icon
 */
@Composable
fun GlowingIconTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.3f else 0f,
        animationSpec = tween(300),
        label = "glow_alpha"
    )
    
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "icon_scale"
    )
    
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .drawBehind {
                    if (glowAlpha > 0) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.8f
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) ShinyGoldDeep else TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) ShinyGoldDeep else TextTertiary
        )
    }
}

/**
 * Scrolling Tab Indicator with dots
 */
@Composable
fun ScrollingTabIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy
                ),
                label = "dot_width"
            )
            
            val color by animateColorAsState(
                targetValue = if (isSelected) ShinyGoldDeep else CardBorder,
                animationSpec = tween(200),
                label = "dot_color"
            )
            
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Animated underline for text buttons
 */
@Composable
fun AnimatedUnderlineButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val underlineWidth by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "underline_width"
    )
    
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) TextPrimary else TextSecondary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth(underlineWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ShinyGoldLight,
                            ShinyGoldDeep,
                            ShinyGoldDark
                        )
                    ),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}
