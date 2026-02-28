package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
 *
 * @param selectedItem Currently selected navigation item
 * @param onItemSelected Callback when a nav item is tapped
 * @param onCenterClick Callback when the center FAB is tapped
 * @param modifier Modifier for the container
 * @param bottomPadding Additional bottom padding (on top of nav bar safe area)
 */
@Composable
fun BouncerBottomNav(
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 16.dp
) {
    val colors = BouncerDesignSystem.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = bottomPadding)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating glass pill container
        GlassNavBarContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side items: Home, Matches
                NavItem.entries.take(2).forEach { item ->
                    NavBarItem(
                        item = item,
                        isSelected = selectedItem == item,
                        onClick = { onItemSelected(item) }
                    )
                }

                // Center FAB spacer
                Spacer(modifier = Modifier.width(64.dp))

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

        // Elevated center FAB
        CenterFab(
            onClick = onCenterClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
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
    val shape = RoundedCornerShape(100.dp) // Full pill shape

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
 * Individual navigation bar item with animated states.
 */
@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate icon size on selection
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 26.dp else 24.dp,
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
            .width(56.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with glow effect when selected
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (isSelected) {
                        Modifier.drawBehind {
                            drawCircle(
                                color = colors.goldPrimary.copy(alpha = 0.15f),
                                radius = 20.dp.toPx()
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
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
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
            .size(56.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                spotColor = colors.goldPrimary.copy(alpha = 0.5f),
                ambientColor = colors.goldPrimary.copy(alpha = 0.3f)
            )
            .scale(scale)
            .clip(CircleShape)
            .background(goldGradient)
            .border(
                width = 2.dp,
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
                .size(28.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

/**
 * Simplified BouncerBottomNav that works with route strings.
 */
@Composable
fun BouncerBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem = NavItem.entries.find { it.route == currentRoute } ?: NavItem.HOME

    BouncerBottomNav(
        selectedItem = selectedItem,
        onItemSelected = { onNavigate(it.route) },
        onCenterClick = { onNavigate(NavItem.CENTER_ACTION.route) },
        modifier = modifier
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
