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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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

/**
 * Premium tab bar with animated indicator
 */
@Composable
fun PremiumTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tabBar")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                PremiumTab(
                    tab = tab,
                    isSelected = selectedIndex == index,
                    glowPulse = glowPulse,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}

data class TabItem(
    val label: String,
    val icon: ImageVector? = null,
    val badge: String? = null
)

@Composable
private fun PremiumTab(
    tab: TabItem,
    isSelected: Boolean,
    glowPulse: Float,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val goldPrimaryColor = hierarchy.secondary.primary
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "tabBg"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary else colors.textSecondary,
        animationSpec = tween(200),
        label = "tabText"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    goldPrimaryColor.copy(alpha = glowPulse * 0.3f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    }
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tab.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Text(
                text = tab.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            
            tab.badge?.let { badge ->
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) colors.goldPrimary else colors.glassBorder
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.backgroundBase else colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Scrollable chip selector
 */
@Composable
fun ChipSelector(
    chips: List<ChipData>,
    selectedChip: ChipData?,
    onChipSelected: (ChipData) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(chips) { chip ->
            SelectableChip(
                chip = chip,
                isSelected = selectedChip == chip,
                onClick = { onChipSelected(chip) }
            )
        }
    }
}

data class ChipData(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val count: Int? = null
)

@Composable
private fun SelectableChip(
    chip: ChipData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "chip_${chip.id}")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chipShimmer"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "chipScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.15f) else GlassSurface,
        animationSpec = tween(200),
        label = "chipBg"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (isSelected) {
                    Modifier.drawWithContent {
                        drawContent()
                        val start = shimmerOffset * size.width * 2 - size.width
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                start = Offset(start, 0f),
                                end = Offset(start + size.width * 0.5f, size.height)
                            )
                        )
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = if (isSelected) {
            BorderStroke(1.dp, hierarchy.secondary.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, colors.glassBorder.copy(alpha = 0.3f))
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chip.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.goldPrimary else colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Text(
                text = chip.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) colors.goldPrimary else colors.textSecondary
            )
            
            chip.count?.let { count ->
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) colors.goldPrimary.copy(alpha = 0.2f) else colors.glassBorder.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.goldPrimary else colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Premium toggle button group
 */
@Composable
fun ToggleButtonGroup(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = GlassSurface
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                ToggleButton(
                    text = option,
                    isSelected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary else Color.Transparent,
        animationSpec = tween(200),
        label = "toggleBg"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.backgroundBase else colors.textSecondary,
        animationSpec = tween(200),
        label = "toggleText"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * Info tooltip card
 */
@Composable
fun InfoTooltip(
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Info,
    accentColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp),
            spotColor = accentColor.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        accentColor.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = message,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Premium notification banner
 */
@Composable
fun NotificationBanner(
    title: String,
    message: String,
    type: BannerType = BannerType.INFO,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val (backgroundColor, accentColor, icon) = when (type) {
        BannerType.INFO -> Triple(LiveBlue.copy(alpha = 0.12f), LiveBlue, Icons.Default.Info)
        BannerType.SUCCESS -> Triple(colors.statusSuccess.copy(alpha = 0.12f), colors.statusSuccess, Icons.Default.CheckCircle)
        BannerType.WARNING -> Triple(WarningOrange.copy(alpha = 0.12f), WarningOrange, Icons.Default.Warning)
        BannerType.ERROR -> Triple(colors.statusLive.copy(alpha = 0.12f), colors.statusLive, Icons.Default.Error)
        BannerType.CELEBRATION -> Triple(hierarchy.secondary.primary.copy(alpha = 0.12f), hierarchy.secondary.primary, Icons.Default.Celebration)
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "banner")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bannerGlow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (type == BannerType.CELEBRATION) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = glowPulse * 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.1f, size.height * 0.5f),
                            radius = size.width * 0.4f
                        ),
                        radius = size.width * 0.4f,
                        center = Offset(size.width * 0.1f, size.height * 0.5f)
                    )
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            onDismiss?.let {
                IconButton(
                    onClick = it,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

enum class BannerType {
    INFO, SUCCESS, WARNING, ERROR, CELEBRATION
}

/**
 * Animated badge
 */
@Composable
fun AnimatedBadge(
    text: String,
    color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (animate) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (animate) 0.6f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeGlow"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (animate) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = glowAlpha * 0.5f),
                                Color.Transparent
                            )
                        ),
                        radius = size.maxDimension
                    )
                }
            }
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.backgroundBase,
            letterSpacing = 0.5.sp
        )
    }
}
