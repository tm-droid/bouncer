package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium collapsing header with parallax effect
 */
@Composable
fun PremiumCollapsingHeader(
    title: String,
    subtitle: String = "",
    scrollOffset: Float = 0f,
    backgroundContent: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val collapsedHeight = 64.dp
    val expandedHeight = 180.dp

    val progress = (scrollOffset / 300f).coerceIn(0f, 1f)
    val currentHeight = androidx.compose.ui.unit.lerp(expandedHeight, collapsedHeight, progress)

    val shimmerOffset = rememberShimmerOffset()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GlassSurface,
                        colors.backgroundBase.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        CollapsingHeaderBackground(
            scrollOffset = scrollOffset,
            progress = progress,
            backgroundContent = backgroundContent
        )

        CollapsingHeaderGlow(progress = progress)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (onBackClick != null) {
                    HeaderBackButton(onBackClick)
                }

                CollapsingHeaderTitle(
                    title = title,
                    subtitle = subtitle,
                    progress = progress,
                    shimmerOffset = shimmerOffset
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }

        HeaderBottomBorder(
            alpha = 0.3f * progress,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun rememberShimmerOffset(): Float {
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
    return shimmerOffset
}

@Composable
private fun CollapsingHeaderBackground(
    scrollOffset: Float,
    progress: Float,
    backgroundContent: @Composable () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = scrollOffset * 0.3f
                alpha = 1f - progress
            }
    ) {
        backgroundContent()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.backgroundBase.copy(alpha = 0.8f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun CollapsingHeaderGlow(progress: Float) {
    val goldPrimaryColor = GoldPrimary
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    goldPrimaryColor.copy(alpha = 0.1f * (1f - progress)),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.5f
            ),
            radius = size.width * 0.5f,
            center = Offset(size.width * 0.3f, size.height * 0.2f)
        )
    }
}

@Composable
private fun HeaderBackButton(onBackClick: () -> Unit) {
    IconButton(onClick = onBackClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary
        )
    }
}

@Composable
private fun CollapsingHeaderTitle(
    title: String,
    subtitle: String,
    progress: Float,
    shimmerOffset: Float
) {
    val colors = BouncerDesignSystem.colors
    Column {
        Text(
            text = title,
            fontSize = androidx.compose.ui.unit.lerp(28.sp, 18.sp, progress),
            fontWeight = FontWeight.Black,
            color = GoldPrimary.copy(
                alpha = 0.8f + shimmerOffset * 0.2f
            ),
            letterSpacing = if (progress < 0.5f) 2.sp else 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle.isNotEmpty()) {
            AnimatedVisibility(
                visible = progress < 0.7f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary.copy(alpha = 1f - progress),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeaderBottomBorder(alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        GoldPrimary.copy(alpha = alpha),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Premium glass header with blur effect
 */
@Composable
fun GlassHeader(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val glowAlpha = rememberGlassHeaderGlow()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GlassSurface
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GlassSurface, Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBackClick != null) {
                    GlassBackButton(onBackClick = onBackClick, glassSurface = colors.glassSurface)
                }

                Text(
                    text = title.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary.copy(alpha = 0.8f + glowAlpha * 0.5f),
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (onBackClick != null) 8.dp else 16.dp)
                        .etchedText()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = actions
                )
            }

            if (showDivider) {
                HeaderBottomBorder(
                    alpha = 0.3f,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun rememberGlassHeaderGlow(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "glassHeader")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    return glowAlpha
}

@Composable
private fun GlassBackButton(onBackClick: () -> Unit, glassSurface: Color) {
    IconButton(
        onClick = onBackClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(glassSurface)
            .border(1.dp, GoldPrimary.copy(alpha = 0.2f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = GoldPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Premium icon button with glow
 */
@Composable
fun PremiumIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String = "",
    badge: Int = 0,
    tint: Color = TextPrimary,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "iconScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    colors.glassBorder.copy(alpha = 0.15f),
                    CircleShape
                )
                .border(
                    1.dp,
                    colors.glassBorder.copy(alpha = 0.2f),
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = size / 2),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size * 0.5f)
            )
        }

        if (badge > 0) {
            IconButtonBadge(badge = badge, warningColor = colors.statusWarning)
        }
    }
}

@Composable
private fun BoxScope.IconButtonBadge(badge: Int, warningColor: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 4.dp, y = (-4).dp)
            .size(18.dp)
            .background(warningColor, CircleShape)
            .border(1.5.dp, GlassSurface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (badge > 9) "9+" else badge.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Section header with premium styling
 */
@Composable
fun PremiumSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                SectionHeaderIcon(icon = icon, tint = hierarchy.secondary.primary)
            }

            SectionHeaderText(title = title, subtitle = subtitle, colors = colors)
        }

        Box(
            modifier = Modifier
                .weight(0.3f)
                .height(1.dp)
                .padding(horizontal = 12.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.glassBorder.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        if (action != null) {
            action()
        }
    }
}

@Composable
private fun SectionHeaderIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                tint.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SectionHeaderText(
    title: String,
    subtitle: String?,
    colors: BouncerColors
) {
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
            letterSpacing = 1.5.sp
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textTertiary
            )
        }
    }
}

/**
 * Animated tab row with premium styling
 */
@Composable
fun PremiumTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GlassSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                PremiumTabItem(
                    tab = tab,
                    isSelected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PremiumTabItem(
    tab: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(200),
        label = "tabBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldLight else colors.textSecondary,
        animationSpec = tween(200),
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = hierarchy.secondary.primary.copy(alpha = 0.1f)),
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tab.uppercase(),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            letterSpacing = if (isSelected) 1.sp else 0.5.sp
        )
    }
}

/**
 * Premium segmented control
 */
@Composable
fun NavigationSegmentedControl(
    segments: List<String>,
    selectedIndex: Int,
    onSegmentSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.glassBorder.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(3.dp)
        ) {
            segments.forEachIndexed { index, segment ->
                SegmentedControlItem(
                    segment = segment,
                    isSelected = index == selectedIndex,
                    onClick = { onSegmentSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SegmentedControlItem(
    segment: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "segmentScale"
    )

    val background = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                hierarchy.secondary.primary,
                hierarchy.secondary.primary
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = segment,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) colors.backgroundBase else colors.textSecondary
        )
    }
}

/**
 * Premium dropdown with animation
 */
@Composable
fun PremiumDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "dropdownRotation"
    )

    Box(modifier = modifier) {
        DropdownTrigger(
            label = label,
            selectedOption = selectedOption,
            expanded = expanded,
            rotation = rotation,
            onClick = { expanded = !expanded }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(GlassSurface)
        ) {
            options.forEach { option ->
                PremiumDropdownItem(
                    option = option,
                    isSelected = option == selectedOption,
                    primaryColor = hierarchy.secondary.primary,
                    textColor = colors.textPrimary,
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DropdownTrigger(
    label: String,
    selectedOption: String,
    expanded: Boolean,
    rotation: Float,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val borderColor = if (expanded) {
        hierarchy.secondary.primary.copy(alpha = 0.5f)
    } else {
        colors.glassBorder.copy(alpha = 0.3f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = selectedOption,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = hierarchy.secondary.primary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun PremiumDropdownItem(
    option: String,
    isSelected: Boolean,
    primaryColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = option,
                color = if (isSelected) primaryColor else textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        onClick = onClick,
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null
    )
}
