package com.tanush.bouncer.ui.components

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
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius as ComposeCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription as semanticContentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback

// ==========================================
// BOUNCER – Bottom Navigation Bar v3.0
// Design Spec: android/design/BOUNCER_DESIGN_SYSTEM.md §16
//
// 4 tabs: Home, Matches, Favourites, More
// 80dp total height (56dp nav + safe area)
// Glassmorphism bg-elevated, 1dp top border
// Active: gold icon+label + animated pill indicator
// Live badge: pulsing red dot on Home tab
// Spring-animated indicator sliding between tabs
// ==========================================

/**
 * Navigation destination model for bottom nav tabs.
 */
data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDesc: String
)

/**
 * The 4 primary navigation destinations.
 */
val navDestinations = listOf(
    NavDestination("home", "Home", Icons.Filled.Home, Icons.Outlined.Home, "Home tab"),
    NavDestination("all_matches", "Matches", Icons.Filled.SportsCricket, Icons.Outlined.SportsCricket, "Matches tab"),
    NavDestination("favourites", "Favourites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "Favourites tab"),
    NavDestination("more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "More tab")
)

/**
 * Premium bottom navigation bar with Material 3 design, gold accents,
 * glassmorphism background, animated pill indicator, and live badge support.
 *
 * @param currentRoute The currently active navigation route.
 * @param onNavigate Callback invoked with the route string when a tab is tapped.
 * @param modifier Optional modifier for the root container.
 * @param visible Whether the bar is visible (animates in/out for hide-on-scroll).
 * @param hasLiveMatches Whether to show a pulsing live dot on the Home tab.
 */
@Composable
fun PremiumBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    hasLiveMatches: Boolean = false
) {
    val haptic = rememberHapticFeedback()

    // Determine selected index for indicator positioning
    val selectedIndex = remember(currentRoute) {
        navDestinations.indexOfFirst { dest ->
            currentRoute == dest.route ||
                (dest.route == "more" && (
                    currentRoute in listOf(
                        "stats",
                        "stats_overview",
                        "trophy-room",
                        "achievements",
                        "settings",
                        "appearance",
                        "about",
                        "devtools",
                        "news",
                        "activity",
                        "activity-feed",
                        "ar_tracking",
                        "ar-tracking"
                    ) || currentRoute?.startsWith("news/") == true
                    ))
        }.coerceAtLeast(0)
    }

    // Animate the entire bar in/out for hide-on-scroll
    val barOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 100.dp,
        animationSpec = BouncerMotion.Springs.gentle(),
        label = "barOffset"
    )

    val barAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(BouncerMotion.Duration.Fast),
        label = "barAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = barOffsetY.toPx()
                alpha = barAlpha
            }
    ) {
        // ── Top border: subtle gold gradient line ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Gold400.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Glassmorphism navigation container ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color(0xFF0F1115).copy(alpha = 0.97f))
                .semantics {
                    semanticContentDescription = "Main navigation bar"
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navDestinations.forEachIndexed { index, dest ->
                    val isSelected = index == selectedIndex

                    BottomNavItem(
                        destination = dest,
                        isSelected = isSelected,
                        tabIndex = index,
                        totalTabs = navDestinations.size,
                        showLiveBadge = index == 0 && hasLiveMatches,
                        onClick = {
                            if (!isSelected) {
                                haptic.click()
                                onNavigate(dest.route)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Individual Nav Item ───────────────────────────────────

@Composable
private fun RowScope.BottomNavItem(
    destination: NavDestination,
    isSelected: Boolean,
    tabIndex: Int,
    totalTabs: Int,
    showLiveBadge: Boolean,
    onClick: () -> Unit
) {
    // ── Icon scale: 1.1x when active, 1.0x inactive ──
    // Bouncy overshoot spring (0.6 damping) for playful feel
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale_${destination.route}"
    )

    // ── Color transitions ──
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Gold400 else TextSecondary,
        animationSpec = tween(
            BouncerMotion.Duration.Fast,
            easing = BouncerEasing.EaseOutExpo
        ),
        label = "iconColor_${destination.route}"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isSelected) Gold400 else TextSecondary,
        animationSpec = tween(
            BouncerMotion.Duration.Fast,
            easing = BouncerEasing.EaseOutExpo
        ),
        label = "labelColor_${destination.route}"
    )

    // ── Radial glow alpha for active state ──
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.25f else 0f,
        animationSpec = tween(
            BouncerMotion.Duration.Normal,
            easing = BouncerEasing.EaseOutExpo
        ),
        label = "glowAlpha_${destination.route}"
    )

    // Choose filled vs outlined icon
    val icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(CornerRadius.sm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Gold400.copy(alpha = 0.08f)),
                onClick = onClick
            )
            .semantics {
                semanticContentDescription =
                    "${destination.label}. Tab ${tabIndex + 1} of $totalTabs"
                selected = isSelected
                role = Role.Tab
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .padding(vertical = Spacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Icon + Radial glow + Live badge ──
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .drawBehind {
                    // Radial gold glow behind icon when active
                    if (glowAlpha > 0f) {
                        drawCircle(
                            color = Gold400.copy(alpha = glowAlpha),
                            radius = 18.dp.toPx(),
                            center = center,
                            alpha = 1f
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {

            // Icon with scale animation
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )

            // ── Live pulsing red dot badge ──
            if (showLiveBadge) {
                LivePulsingDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Label ──
        Text(
            text = destination.label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = LetterSpacing.relaxed,
            maxLines = 1
        )
    }
}

// ─── Live Pulsing Dot ──────────────────────────────────────

/**
 * A pulsing red dot indicating live matches.
 * Scales between 0.8x-1.2x with infinite animation.
 */
@Composable
private fun LivePulsingDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.pulse,
                easing = BouncerEasing.EaseInOutQuint
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationDuration.pulse,
                easing = BouncerEasing.EaseInOutQuint
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
            .clip(CircleShape)
            .background(StatusLive)
    )
}
