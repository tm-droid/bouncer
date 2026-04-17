package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Primary button with gold gradient fill and dark text.
 * Implements the "Premium Button" look with shimmer and glow.
 */
@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    // Rotation for spinner if needed
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    val shape = RoundedCornerShape(percent = 50)
    
    Box(
        modifier = modifier
            .scale(scale)
            .height(dimens.buttonHeight)
            .clip(shape)
            .background(
                brush = if (enabled && !isLoading) {
                    colors.goldGradientHorizontal
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            hierarchy.secondary.tint.copy(alpha = 0.3f),
                            hierarchy.secondary.tint.copy(alpha = 0.3f)
                        )
                    )
                }
            )
            .drawBehind {
                if (enabled && !isLoading) {
                    // Shimmer effect
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0f)
                        ),
                        start = Offset(size.width * shimmerOffset, 0f),
                        end = Offset(size.width * shimmerOffset + 120.dp.toPx(), size.height)
                    )
                    drawRect(brush = shimmerBrush)
                    
                    // Subtle bottom glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height),
                            radius = size.width * 0.8f
                        ),
                        center = Offset(size.width * 0.5f, size.height),
                        radius = size.width * 0.8f
                    )
                }
            }
            .then(
                if (enabled && !isLoading) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.Black.copy(alpha = 0.15f))
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = dimens.buttonHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            LoadingDots(color = Color.Black)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) Color.Black else colors.textMuted,
                        modifier = Modifier.size(dimens.iconSize.md)
                    )
                    Spacer(modifier = Modifier.width(dimens.spacing.sm))
                }
                Text(
                    text = text.uppercase(),
                    style = typography.labelLarge,
                    color = if (enabled) Color.Black else colors.textMuted,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Secondary button with glass background and gold border/text.
 */
@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled || isLoading -> colors.glassSurface
            isPressed -> hierarchy.secondary.primary.copy(alpha = 0.12f)
            else -> colors.glassSurface
        },
        label = "bg_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled || isLoading -> hierarchy.secondary.primary.copy(alpha = 0.15f)
            isPressed -> hierarchy.secondary.primary.copy(alpha = 0.4f)
            else -> hierarchy.secondary.primary.copy(alpha = 0.25f)
        },
        label = "border_color"
    )
    
    val shape = RoundedCornerShape(percent = 50)
    
    Box(
        modifier = modifier
            .scale(scale)
            .height(dimens.buttonHeight)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (enabled && !isLoading) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = hierarchy.secondary.primary.copy(alpha = 0.1f))
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = dimens.buttonHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            LoadingDots(color = hierarchy.secondary.primary)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) hierarchy.secondary.primary else hierarchy.secondary.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(dimens.iconSize.md)
                    )
                    Spacer(modifier = Modifier.width(dimens.spacing.sm))
                }
                Text(
                    text = text.uppercase(),
                    style = typography.labelLarge,
                    color = if (enabled) hierarchy.secondary.primary else hierarchy.secondary.primary.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LoadingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotScale"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.5f + (scale * 0.5f)))
            )
        }
    }
}

/**
 * Circular icon button with glass background.
 * Used for back buttons, menu buttons, action buttons.
 *
 * @param icon Icon to display
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 * @param contentDescription Accessibility description
 * @param size Size of the button (default 40dp)
 * @param enabled Whether the button is enabled
 * @param useGoldTint Whether to use gold color for the icon
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = BouncerDesignSystem.dimens.minTouchTarget,
    enabled: Boolean = true,
    useGoldTint: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.glassSurface
            isPressed -> colors.glassBackgroundPressed
            else -> colors.glassBackground
        },
        label = "bg_color"
    )
    
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textMuted
            useGoldTint -> if (isPressed) hierarchy.secondary.secondary else hierarchy.secondary.primary.copy(alpha = 0.8f)
            else -> if (isPressed) colors.textPrimary else colors.textSecondary
        },
        label = "icon_color"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, hierarchy.secondary.primary.copy(alpha = 0.2f), CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = hierarchy.secondary.primary)
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .semantics {
                role = Role.Button
                if (!contentDescription.isNullOrBlank()) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Small icon button variant for compact spaces.
 */
@Composable
fun GlassIconButtonSmall(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    useGoldTint: Boolean = true
) {
    GlassIconButton(
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        contentDescription = contentDescription,
        size = BouncerDesignSystem.dimens.buttonHeightCompact,
        enabled = enabled,
        useGoldTint = useGoldTint
    )
}

/**
 * Large icon button variant for prominent actions.
 */
@Composable
fun GlassIconButtonLarge(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    useGoldTint: Boolean = true
) {
    GlassIconButton(
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        contentDescription = contentDescription,
        size = 48.dp,
        enabled = enabled,
        useGoldTint = useGoldTint
    )
}

/**
 * Text-only button with gold text color.
 * Used for inline actions and links.
 *
 * @param text Button label text
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param uppercase Whether to uppercase the text
 */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    uppercase: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> hierarchy.secondary.primary.copy(alpha = 0.4f)
            isPressed -> hierarchy.secondary.secondary
            else -> hierarchy.secondary.primary
        },
        label = "text_color"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "scale"
    )
    
    Text(
        text = if (uppercase) text.uppercase() else text,
        style = typography.buttonCompact,
        color = textColor,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = hierarchy.secondary.primary, bounded = true)
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm)
    )
}

/**
 * Floating action button with glass styling.
 *
 * @param icon Icon to display
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 * @param contentDescription Accessibility description
 */
@Composable
fun GlassFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        hierarchy.secondary.secondary,
                        hierarchy.secondary.primary,
                        hierarchy.secondary.primary
                    )
                )
            )
            .drawBehind {
                // Gold glow shadow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        radius = size.maxDimension * 0.8f
                    ),
                    radius = size.maxDimension * 0.6f
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.Black.copy(alpha = 0.2f))
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Toggle button with on/off state.
 *
 * @param isToggled Current toggle state
 * @param onToggle Callback when toggle state changes
 * @param icon Icon to display
 * @param modifier Modifier for the button
 * @param contentDescription Accessibility description
 */
@Composable
fun GlassToggleButton(
    isToggled: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isToggled) {
            hierarchy.secondary.primary.copy(alpha = 0.2f)
        } else {
            colors.glassBackground
        },
        label = "bg_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isToggled) {
            hierarchy.secondary.primary.copy(alpha = 0.5f)
        } else {
            hierarchy.secondary.primary.copy(alpha = 0.2f)
        },
        label = "border_color"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isToggled) {
            hierarchy.secondary.primary
        } else {
            colors.textSecondary
        },
        label = "icon_color"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = hierarchy.secondary.primary)
            ) { onToggle(!isToggled) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
