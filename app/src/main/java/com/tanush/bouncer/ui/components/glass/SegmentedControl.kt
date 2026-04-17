package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Glass-morphism segmented control with animated selection indicator.
 * Based on mockup CSS:
 * .segmented-control { background: rgba(255,255,255,0.04); border-radius: 12px; }
 * .segment-active { background: rgba(212,175,55,0.2); border: 1px solid rgba(212,175,55,0.4); color: #d4af37; }
 *
 * @param items List of tab labels (supports 2-6 items)
 * @param selectedIndex Currently selected index
 * @param onSelect Callback when a segment is selected
 * @param modifier Modifier for the control
 * @param usePillShape Whether to use fully rounded pill shape (true) or rounded rect (false)
 */
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    usePillShape: Boolean = true
) {
    require(items.size in 2..6) { "GlassSegmentedControl supports 2-6 items" }
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val density = LocalDensity.current
    
    val containerShape = if (usePillShape) RoundedCornerShape(percent = 50) else RoundedCornerShape(12.dp)
    val segmentShape = if (usePillShape) RoundedCornerShape(percent = 50) else RoundedCornerShape(10.dp)
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Calculate segment width
    val segmentWidth = remember(containerSize, items.size) {
        if (containerSize.width > 0) {
            with(density) { (containerSize.width / items.size).toDp() - 8.dp } // 4px padding each side (8px total)
        } else {
            0.dp
        }
    }
    
    // Animated indicator offset
    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { (containerSize.width / items.size * selectedIndex).toDp() + 4.dp },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicator_offset"
    )
    
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(containerShape)
            .background(Color.Black.copy(alpha = 0.3f)) // From _2/code.html .segmented-control
            .border(1.dp, colors.glassBorder, containerShape)
            .padding(4.dp) // From _2/code.html padding: 4px
            .onSizeChanged { containerSize = it }
    ) {
        // Animated selection indicator - Refined to match .segment-active
        if (segmentWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(segmentShape)
                    .background(hierarchy.secondary.primary.copy(alpha = 0.9f)) // Solid gold from spec
                    .drawBehind {
                        // Premium Glow Effect
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary.copy(alpha = 0.3f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = size.width * 1.5f
                            )
                        )
                    }
            )
        }
        
        // Segments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else colors.textSecondary, // From spec
                    label = "text_color"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(segmentShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.uppercase(),
                        style = typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Segmented control variant with icons and labels.
 * Used in the main tab navigation (Live, Scorecard, Lineups, Overs, Info).
 */
@Composable
fun GlassSegmentedControlWithIcons(
    items: List<SegmentItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    require(items.size in 2..6) { "GlassSegmentedControlWithIcons supports 2-6 items" }
    
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val density = LocalDensity.current
    
    val containerShape = RoundedCornerShape(percent = 50)
    val segmentShape = RoundedCornerShape(percent = 50)
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    val segmentWidth = remember(containerSize, items.size) {
        if (containerSize.width > 0) {
            with(density) { (containerSize.width / items.size).toDp() - 2.dp }
        } else {
            0.dp
        }
    }
    
    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { (containerSize.width / items.size * selectedIndex).toDp() + 4.dp },
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "indicator_offset"
    )
    
    Box(
        modifier = modifier
            .clip(containerShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, colors.glassBorder, containerShape)
            .padding(4.dp)
            .onSizeChanged { containerSize = it }
    ) {
        // Animated selection indicator
        if (segmentWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset - 4.dp)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(segmentShape)
                    .background(hierarchy.secondary.primary.copy(alpha = 0.9f))
            )
        }
        
        // Segments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else colors.textSecondary,
                    label = "text_color"
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(segmentShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = if (!isSelected) ripple(color = colors.goldPrimary) else null
                        ) { onSelect(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    item.icon?.invoke(textColor)
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = item.label.uppercase(),
                        style = typography.labelSmall.copy(
                            fontSize = typography.labelSmall.fontSize * 0.7f
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Data class for segment items with optional icon.
 */
data class SegmentItem(
    val label: String,
    val icon: (@Composable (Color) -> Unit)? = null
)

/**
 * Filter chip/pill with glass styling.
 * Used for filter pills like T20/ODI/Test/All.
 *
 * @param label Text label for the chip
 * @param isSelected Whether the chip is currently selected
 * @param onClick Callback when chip is clicked
 * @param modifier Modifier for the chip
 */
@Composable
fun GlassChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            hierarchy.secondary.primary.copy(alpha = 0.2f)
        } else {
            colors.glassSurface
        },
        label = "chip_bg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            hierarchy.secondary.primary.copy(alpha = 0.4f)
        } else {
            colors.glassBorder
        },
        label = "chip_border"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            hierarchy.secondary.primary
        } else {
            colors.textSecondary
        },
        label = "chip_text"
    )
    
    val shape = RoundedCornerShape(20.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = hierarchy.secondary.primary)
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.chip,
            color = textColor
        )
    }
}

/**
 * Compact variant of GlassChip with smaller padding.
 */
@Composable
fun GlassChipCompact(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            hierarchy.secondary.primary.copy(alpha = 0.2f)
        } else {
            colors.glassSurface
        },
        label = "chip_bg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            hierarchy.secondary.primary.copy(alpha = 0.4f)
        } else {
            colors.glassBorder
        },
        label = "chip_border"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            hierarchy.secondary.primary
        } else {
            colors.textSecondary
        },
        label = "chip_text"
    )
    
    val shape = RoundedCornerShape(16.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = hierarchy.secondary.primary)
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = textColor
        )
    }
}

/**
 * Horizontal scrolling row of filter chips.
 *
 * @param items List of chip labels
 * @param selectedIndex Currently selected chip index (-1 for none)
 * @param onSelect Callback when a chip is selected
 * @param modifier Modifier for the row
 * @param useCompact Whether to use compact chips
 */
@Composable
fun GlassChipRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    useCompact: Boolean = false,
    spacing: Dp = 8.dp
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items.forEachIndexed { index, label ->
            if (useCompact) {
                GlassChipCompact(
                    label = label,
                    isSelected = index == selectedIndex,
                    onClick = { onSelect(index) }
                )
            } else {
                GlassChip(
                    label = label,
                    isSelected = index == selectedIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

/**
 * Multi-select variant of GlassChipRow.
 *
 * @param items List of chip labels
 * @param selectedIndices Set of currently selected indices
 * @param onToggle Callback when a chip is toggled
 * @param modifier Modifier for the row
 * @param useCompact Whether to use compact chips
 */
@Composable
fun GlassChipRowMultiSelect(
    items: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    useCompact: Boolean = false,
    spacing: Dp = 8.dp
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items.forEachIndexed { index, label ->
            if (useCompact) {
                GlassChipCompact(
                    label = label,
                    isSelected = index in selectedIndices,
                    onClick = { onToggle(index) }
                )
            } else {
                GlassChip(
                    label = label,
                    isSelected = index in selectedIndices,
                    onClick = { onToggle(index) }
                )
            }
        }
    }
}
