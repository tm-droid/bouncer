package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

enum class NavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "home"),
    FAVORITES("Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "favorites"),
    ACHIEVEMENTS("Awards", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "achievements"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "settings")
}

/**
 * Premium bottom navigation bar with glow effects and animations
 */
@Composable
fun PremiumBottomNavigation(
    selectedItem: NavigationItem,
    onItemSelected: (NavigationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "navShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)
    ) {
        // Background with glow and blur
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BouncerDesign.BottomNavHeight)
                .shadow(
                    elevation = Elevation.high,
                    shape = RoundedCornerShape(CornerRadius.lg),
                    spotColor = colors.goldPrimary.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(CornerRadius.lg))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f).copy(alpha = 0.95f),
                            Color.White.copy(alpha = 0.05f).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderWidth.hairline,
                    Brush.linearGradient(
                        colors = listOf(
                            colors.goldPrimary.copy(alpha = 0.3f + shimmerOffset * 0.2f),
                            Color.Transparent,
                            colors.goldPrimary.copy(alpha = 0.15f),
                            Color.Transparent,
                            colors.goldPrimary.copy(alpha = 0.25f)
                        ),
                        start = Offset(shimmerOffset * 500, 0f),
                        end = Offset(shimmerOffset * 500 + 300, 100f)
                    ),
                    RoundedCornerShape(CornerRadius.lg)
                )
        ) {
            // Top highlight line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.goldLight.copy(alpha = 0.3f),
                                colors.goldPrimary.copy(alpha = 0.4f),
                                colors.goldLight.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Navigation items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem.entries.forEach { item ->
                PremiumNavItem(
                    item = item,
                    isSelected = selectedItem == item,
                    onClick = { onItemSelected(item) }
                )
            }
        }
    }
}

@Composable
private fun PremiumNavItem(
    item: NavigationItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val interactionSource = remember { MutableInteractionSource() }
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary else colors.textMuted,
        animationSpec = tween(200),
        label = "iconColor"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "itemGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glow background for selected item
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = glowAlpha * 0.3f),
                                colors.goldPrimary.copy(alpha = glowAlpha * 0.1f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(if (isSelected) 26.dp else 24.dp)
            )
            
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
            ) {
                Text(
                    text = item.title,
                    fontSize = 10.sp,
                    color = colors.goldPrimary,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

/**
 * Minimal floating action button for quick actions
 */
@Composable
fun PremiumFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = BouncerDesignSystem.colors.goldPrimary,
    contentColor: Color = BouncerDesignSystem.colors.backgroundBase
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fabGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            containerColor.copy(alpha = glowAlpha * 0.4f),
                            containerColor.copy(alpha = glowAlpha * 0.1f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape,
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = containerColor.copy(alpha = 0.5f)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Speed dial FAB with expanding options
 */
@Composable
fun SpeedDialFAB(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    items: List<SpeedDialItem>,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fabRotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Speed dial items
        items.forEachIndexed { index, item ->
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(150, delayMillis = index * 50)) +
                        scaleIn(tween(200, delayMillis = index * 50), initialScale = 0.5f) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(200, delayMillis = index * 50)
                        ),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.5f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Label
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = item.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            color = BouncerDesignSystem.colors.textPrimary
                        )
                    }

                    // Mini FAB
                    FloatingActionButton(
                        onClick = {
                            onToggle()
                            item.onClick()
                        },
                        containerColor = item.color,
                        contentColor = BouncerDesignSystem.colors.backgroundBase,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Main FAB
        PremiumFloatingActionButton(
            onClick = onToggle,
            icon = Icons.Default.Add,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}

data class SpeedDialItem(
    val label: String,
    val icon: ImageVector,
    val color: Color = Color(0xFFD4AF37),
    val onClick: () -> Unit
)
