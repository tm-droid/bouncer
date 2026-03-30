package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerTheme

/**
 * Navigation items for the bottom nav bar.
 * Each item has an icon, label resource ID, and route identifier.
 */
enum class NavItem(
    val icon: ImageVector,
    val labelResId: Int,
    val route: String
) {
    HOME(Icons.Outlined.Home, R.string.nav_home, "home"),
    MATCHES(Icons.Outlined.SportsCricket, R.string.nav_matches, "matches"),
    CENTER_ACTION(Icons.Outlined.Add, R.string.nav_quick_action, "quick_action"),
    NEWS(Icons.Outlined.Newspaper, R.string.nav_news, "news"),
    SETTINGS(Icons.Outlined.Settings, R.string.nav_settings, "settings")
}

/**
 * Premium floating glass bottom navigation bar.
 *
 * Features:
 * - Floating pill design with glass-morphism background
 * - Gold accent center FAB elevated above the bar
 * - Active state with gold icon + animated label
 * - Smooth scale animations on selection
 * - Safe area handling for navigation bars
 * - Pulsing live badge on Matches tab when live matches exist
 *
 * @param selectedItem Currently selected navigation item
 * @param onItemSelected Callback when a nav item is tapped
 * @param onCenterClick Callback when the center FAB is tapped
 * @param modifier Modifier for the container
 * @param bottomPadding Additional bottom padding (on top of nav bar safe area)
 * @param hasLiveMatch Whether there are live matches (shows badge on Matches tab)
 */
@Composable
fun BouncerBottomNav(
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = BouncerDesignSystem.dimens.md,
    hasLiveMatch: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.lg)
            .padding(bottom = bottomPadding)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating glass pill container with subtle gold top border
        Column {
            // Subtle gold top border line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.2f to colors.goldPrimary.copy(alpha = 0.10f),
                            0.5f to colors.goldPrimary.copy(alpha = 0.15f),
                            0.8f to colors.goldPrimary.copy(alpha = 0.10f),
                            1.0f to Color.Transparent
                        )
                    )
            )
        GlassNavBarContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.bottomNavHeight)
                    .padding(horizontal = dimens.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side items: Home, Matches
                NavItem.entries.take(2).forEach { item ->
                    NavBarItem(
                        item = item,
                        isSelected = selectedItem == item,
                        onClick = { onItemSelected(item) },
                        hasLiveBadge = item == NavItem.MATCHES && hasLiveMatch
                    )
                }

                // Center FAB spacer
                Spacer(modifier = Modifier.width(dimens.bottomNavHeight))

                // Right side items: News, Settings
                NavItem.entries.drop(3).forEach { item ->
                    NavBarItem(
                        item = item,
                        isSelected = selectedItem == item,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
        } // Close Column

        // Elevated center FAB
        CenterFab(
            onClick = onCenterClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-dimens.lg))
        )
    }
}

/**
 * Glass morphism container for the navigation bar.
 */
@Composable
private fun GlassNavBarContainer(
    content: @Composable () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val shape = RoundedCornerShape(dimens.radius.pill)

    // Gradient border from gold
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            colors.goldLight.copy(alpha = 0.4f),
            colors.goldPrimary.copy(alpha = 0.25f),
            colors.goldDark.copy(alpha = 0.15f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.6f),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(
                color = Color.Black.copy(alpha = 0.65f)
            )
            .drawBehind {
                // Glass background gradient
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Individual navigation bar item with animated states and optional live badge.
 */
@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasLiveBadge: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate icon size on selection
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) dimens.iconSize.xl else dimens.iconSize.lg,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "iconSize"
    )

    // Animate scale on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    // Label text
    val label = stringResource(item.labelResId)

    // Icon color
    val iconColor = if (isSelected) colors.goldPrimary else colors.textTertiary

    Column(
        modifier = Modifier
            .width(dimens.fabSize)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with glow effect when selected + live badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(dimens.minTouchTarget)
                .then(
                    if (isSelected) {
                        Modifier.drawBehind {
                            drawCircle(
                                color = colors.goldPrimary.copy(alpha = 0.15f),
                                radius = (dimens.minTouchTarget / 2).toPx()
                            )
                        }
                    } else Modifier
                )
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
            
            // Pulsing live badge indicator
            if (hasLiveBadge) {
                PulsingLiveDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                )
            }
        }

        // Animated label (only shown when selected)
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f)
        ) {
            Text(
                text = label,
                color = colors.goldPrimary,
                style = BouncerDesignSystem.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.xxs)
            )
        }
    }
}

/**
 * Pulsing red dot indicator for live matches.
 */
@Composable
private fun PulsingLiveDot(modifier: Modifier = Modifier) {
    val dimens = BouncerDesignSystem.dimens
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = modifier
            .size(dimens.liveDotSize)
            .scale(pulseScale)
            .graphicsLayer { alpha = pulseAlpha }
            .background(
                color = Color(0xFFFF3B30), // Live red
                shape = CircleShape
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.5f),
                shape = CircleShape
            )
    )
}

/**
 * Elevated center FAB with gold gradient.
 */
@Composable
private fun CenterFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate scale/rotation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "fabScale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isPressed) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fabRotation"
    )

    // Gold gradient brush
    val goldGradient = Brush.linearGradient(
        colors = listOf(
            colors.goldLight,
            colors.goldPrimary,
            colors.goldDark
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .size(dimens.fabSize)
            .shadow(
                elevation = dimens.elevation.overlay,
                shape = CircleShape,
                spotColor = colors.goldPrimary.copy(alpha = 0.5f),
                ambientColor = colors.goldPrimary.copy(alpha = 0.3f)
            )
            .scale(scale)
            .clip(CircleShape)
            .background(goldGradient)
            .border(
                width = dimens.focusBorderWidth,
                color = Color.Black.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner glow highlight
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.3f, size.height * 0.3f),
                            radius = size.minDimension * 0.5f
                        )
                    )
                }
        )

        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Quick Action",
            tint = Color.Black,
            modifier = Modifier
                .size(dimens.iconSize.xl)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

/**
 * Simplified BouncerBottomNav that works with route strings.
 * 
 * @param currentRoute The current navigation route
 * @param onNavigate Callback when navigation is requested
 * @param hasLiveMatch Whether there are live matches (shows badge on Matches tab)
 * @param modifier Modifier for the container
 */
@Composable
fun BouncerBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    hasLiveMatch: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedItem = NavItem.entries.find { it.route == currentRoute } ?: NavItem.HOME

    BouncerBottomNav(
        selectedItem = selectedItem,
        onItemSelected = { onNavigate(it.route) },
        onCenterClick = { onNavigate(NavItem.CENTER_ACTION.route) },
        modifier = modifier,
        hasLiveMatch = hasLiveMatch
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1014)
@Composable
private fun BouncerBottomNavPreview() {
    BouncerTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFF0F1014)),
            contentAlignment = Alignment.BottomCenter
        ) {
            BouncerBottomNav(
                selectedItem = NavItem.HOME,
                onItemSelected = {},
                onCenterClick = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1014)
@Composable
private fun BouncerBottomNavMatchesSelectedPreview() {
    BouncerTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFF0F1014)),
            contentAlignment = Alignment.BottomCenter
        ) {
            BouncerBottomNav(
                selectedItem = NavItem.MATCHES,
                onItemSelected = {},
                onCenterClick = {}
            )
        }
    }
}
