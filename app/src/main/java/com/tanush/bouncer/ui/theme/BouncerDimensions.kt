package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bouncer Dimensions System
 *
 * 4dp-grid spacing system with semantic component sizing tokens.
 * Designed for consistent, harmonious layouts across all screen sizes.
 */

// =============================================================================
// Top-Level Dimension Constants
// =============================================================================

/** Standard bottom navigation bar height */
val BottomNavHeight = 64.dp

/** Glass blur radius for glassmorphism effects */
val GlassBlur = 24.dp

// =============================================================================
// Icon Sizes
// =============================================================================

@Immutable
data class BouncerIconSizes(
    /** 16dp — Small icons */
    val sm: Dp = 16.dp,
    /** 20dp — Medium icons */
    val md: Dp = 20.dp,
    /** 24dp — Large icons (standard) */
    val lg: Dp = 24.dp,
    /** 28dp — Extra large icons */
    val xl: Dp = 28.dp,
    /** 32dp — Jumbo icons */
    val jumbo: Dp = 32.dp,
    /** 40dp — Hero icons */
    val hero: Dp = 40.dp
)

// =============================================================================
// Component Dimensions
// =============================================================================

@Immutable
data class BouncerDimensions(
    val spacing: BouncerSpacing = BouncerSpacing(),
    val radius: BouncerRadius = BouncerRadius(),
    val iconSize: BouncerIconSizes = BouncerIconSizes(),
    val elevation: BouncerElevation = BouncerElevation(),
    
    // Shortcut properties for legacy compatibility
    val xxs: Dp = spacing.micro,
    val xs: Dp = spacing.xs,
    val sm: Dp = spacing.sm,
    val md: Dp = spacing.md,
    val lg: Dp = spacing.lg,
    val xl: Dp = spacing.xl,
    val xxl: Dp = spacing.xxl,
    val xxxl: Dp = spacing.xxxl,
    val screenHorizontal: Dp = spacing.md,
    val screenVertical: Dp = spacing.sm,
    val cardSpacing: Dp = 12.dp,
    
    // Card Dimensions
    /** 16dp — Standard card internal padding */
    val cardPadding: Dp = 16.dp,
    /** 12dp — Compact card internal padding */
    val cardPaddingCompact: Dp = 12.dp,
    /** 24dp — Vertical gap between sections */
    val sectionGap: Dp = 24.dp,
    /** 12dp — Gap between cards in a list */
    val cardGap: Dp = 12.dp,
    
    // Navigation
    /** 56dp — Standard header/toolbar height */
    val headerHeight: Dp = 56.dp,
    /** 64dp — Bottom navigation bar height */
    val bottomNavHeight: Dp = 64.dp,
    /** 48dp — Tab bar height */
    val tabBarHeight: Dp = 48.dp,
    
    // Screen Layout
    /** 16dp — Horizontal screen padding */
    val screenHorizontalPadding: Dp = 16.dp,
    /** 8dp — Vertical screen padding */
    val screenVerticalPadding: Dp = 8.dp,
    /** 16dp — Top screen padding */
    val screenTopPadding: Dp = 16.dp,
    
    // Avatar & Media Sizes
    /** 40dp — Player avatar size */
    val playerAvatarSize: Dp = 40.dp,
    /** 48dp — Large player avatar */
    val playerAvatarLarge: Dp = 48.dp,
    /** 32dp — Small player avatar */
    val playerAvatarSmall: Dp = 32.dp,
    /** 32dp — Standard flag/logo size */
    val flagSize: Dp = 32.dp,
    /** 24dp — Small flag/logo size */
    val flagSizeSmall: Dp = 24.dp,
    /** 48dp — Large flag/logo size */
    val flagSizeLarge: Dp = 48.dp,
    /** 64dp — Hero flag/logo size */
    val flagSizeHero: Dp = 64.dp,
    
    // Ball Indicators
    /** 30dp — Ball indicator size in recent balls */
    val ballIndicatorSize: Dp = 30.dp,
    /** 26dp — Compact ball indicator size */
    val ballIndicatorSizeCompact: Dp = 26.dp,
    /** 36dp — Large ball indicator size */
    val ballIndicatorSizeLarge: Dp = 36.dp,
    
    // Match Card
    /** 160dp — Minimum height for match cards */
    val matchCardMinHeight: Dp = 160.dp,
    /** 200dp — Hero card height */
    val heroCardHeight: Dp = 200.dp,
    /** 180dp — Compact hero card height */
    val heroCardHeightCompact: Dp = 180.dp,
    
    // Interactive Elements
    /** 48dp — Minimum touch target size */
    val minTouchTarget: Dp = 48.dp,
    /** 52dp — Default primary/secondary button height */
    val buttonHeight: Dp = 52.dp,
    /** 48dp — Compact button height */
    val buttonHeightCompact: Dp = 48.dp,
    /** 20dp — Default horizontal content padding inside buttons */
    val buttonHorizontalPadding: Dp = 20.dp,
    /** 36dp — Chip height */
    val chipHeight: Dp = 36.dp,
    /** 32dp — Compact chip height */
    val chipHeightCompact: Dp = 32.dp,
    /** 56dp — FAB size */
    val fabSize: Dp = 56.dp,
    /** 40dp — Small FAB size */
    val fabSizeSmall: Dp = 40.dp,
    
    // Status Indicators
    /** 8dp — Live dot indicator size */
    val liveDotSize: Dp = 8.dp,
    /** 6dp — Small status dot */
    val statusDotSmall: Dp = 6.dp,
    /** 10dp — Large status dot */
    val statusDotLarge: Dp = 10.dp,
    
    // Dividers & Borders
    /** 0.5dp — Standard divider thickness */
    val dividerThickness: Dp = 0.5.dp,
    /** 1dp — Border width */
    val borderWidth: Dp = 1.dp,
    /** 2dp — Focus indicator border width */
    val focusBorderWidth: Dp = 2.dp,
    
    // Glassmorphism
    /** 30dp — Standard glass blur radius */
    val glassBlurRadius: Dp = 30.dp,
    /** 40dp — Strong glass blur radius */
    val glassBlurRadiusStrong: Dp = 40.dp,
    /** 20dp — Subtle glass blur radius */
    val glassBlurRadiusSubtle: Dp = 20.dp,
    
    // Scoreboard
    /** 64dp — Scoreboard row height */
    val scoreboardRowHeight: Dp = 64.dp,
    /** 52dp — Compact scoreboard row height */
    val scoreboardRowHeightCompact: Dp = 52.dp,
    /** 40dp — Stat column width */
    val scoreboardStatColumnWidth: Dp = 40.dp,
    /** 28dp — Scoreboard header height */
    val scoreboardHeaderHeight: Dp = 28.dp,
    
    // Status Cards
    /** 120dp — Status card height */
    val statusCardHeight: Dp = 120.dp,
    /** 160dp — Minimum status card width */
    val statusCardMinWidth: Dp = 160.dp,
    
    // Current Players Strip
    /** 88dp — Players strip height */
    val playersStripHeight: Dp = 88.dp,
    
    // Breakpoints
    /** 600dp — Compact width breakpoint */
    val compactWidthBreakpoint: Dp = 600.dp,
    /** 840dp — Medium width breakpoint */
    val mediumWidthBreakpoint: Dp = 840.dp,
    /** 1200dp — Expanded width breakpoint */
    val expandedWidthBreakpoint: Dp = 1200.dp
) {
    // Animation Durations (in milliseconds)
    companion object {
        const val ANIM_FAST = 150
        const val ANIM_NORMAL = 300
        const val ANIM_SLOW = 500
        const val ANIM_VERY_SLOW = 1000
        const val ANIM_SHIMMER = 1500
    }
}

// =============================================================================
// CompositionLocal
// =============================================================================

val LocalBouncerDimens = staticCompositionLocalOf { BouncerDimensions() }

// =============================================================================
// Helper Extensions
// =============================================================================

/**
 * Compact mode dimensions for smaller screens.
 */
val CompactBouncerDimensions = BouncerDimensions(
    cardPadding = 12.dp,
    sectionGap = 20.dp,
    headerHeight = 48.dp,
    bottomNavHeight = 56.dp,
    tabBarHeight = 40.dp,
    screenHorizontalPadding = 12.dp,
    playerAvatarSize = 36.dp,
    flagSize = 28.dp,
    ballIndicatorSize = 24.dp,
    matchCardMinHeight = 140.dp,
    heroCardHeight = 160.dp
)

// =============================================================================
// LEGACY COMPATIBILITY — BouncerDimens object
// =============================================================================

/**
 * Legacy BouncerDimens object for backward compatibility.
 * 
 * This object maintains the old static accessor pattern used by existing code.
 * New code should use BouncerDesignSystem.dimens or LocalBouncerDimens.current instead.
 */
object BouncerDimens {
    private val spacing = BouncerTokenScales.spacing
    private val radius = BouncerTokenScales.radius
    private val elevation = BouncerTokenScales.elevation

    // Spacing scale
    val xxs: Dp = spacing.micro
    val xs: Dp = spacing.xs
    val sm: Dp = spacing.sm
    val md: Dp = spacing.md
    val lg: Dp = spacing.lg
    val xl: Dp = spacing.xl
    val xxl: Dp = spacing.xxl
    val xxxl: Dp = spacing.xxxl
    
    // Screen padding
    val screenHorizontal: Dp = spacing.md
    val screenVertical: Dp = spacing.sm
    val screenTop: Dp = spacing.md
    
    // Card dimensions
    val cardPadding: Dp = spacing.md
    val cardInnerPadding: Dp = 12.dp
    val cardElevation: Dp = elevation.sm
    val cardSpacing: Dp = 12.dp
    
    // Match card specific
    val teamLogoSize: Dp = 36.dp
    val teamLogoSizeLarge: Dp = 48.dp
    val liveDotSize: Dp = 8.dp
    val recentBallSize: Dp = 28.dp
    val scoreFontContainerHeight: Dp = 32.dp
    
    // Bottom navigation
    val bottomNavHeight: Dp = 64.dp
    val bottomNavIconSize: Dp = 24.dp
    val bottomNavLabelSpacing: Dp = spacing.xs
    
    // Interactive elements
    val minTapTarget: Dp = 48.dp
    val iconButtonSize: Dp = 48.dp
    val fabSize: Dp = 56.dp
    val chipHeight: Dp = 36.dp
    val chipHorizontalPadding: Dp = spacing.md
    
    // Dividers & borders
    val dividerThickness: Dp = 0.5.dp
    val borderWidth: Dp = 1.dp
    val focusBorderWidth: Dp = 2.dp
    
    // Animation durations (milliseconds)
    const val animFast: Int = 150
    const val animNormal: Int = 300
    const val animSlow: Int = 500
    const val animVerySlow: Int = 1000
    
    // Hero scorecard
    val heroCardHeight: Dp = 220.dp
    val heroCardHeightCompact: Dp = 180.dp
    val heroTeamLogoSize: Dp = 56.dp
    val heroTeamLogoSizeLarge: Dp = 72.dp
    val heroScoreSize: Dp = 32.dp
    
    // Glassmorphism
    val glassBlurRadius: Dp = spacing.lg
    val glassBlurRadiusStrong: Dp = 32.dp
    val glassBorderWidth: Dp = 1.dp
    const val glassFrostIntensity: Float = 0.15f
    
    // Enhanced scoreboard
    val scoreboardItemHeight: Dp = 64.dp
    val scoreboardAvatarSize: Dp = 36.dp
    val scoreboardStatColumnWidth: Dp = 36.dp
    val scoreboardHeaderHeight: Dp = 28.dp
    
    // Status cards
    val statusCardHeight: Dp = 120.dp
    val statusCardMinWidth: Dp = 160.dp
    
    // Current players strip
    val currentPlayersHeight: Dp = 88.dp
    val playerAvatarSize: Dp = 40.dp
    
    // Adaptive breakpoints
    val compactMaxWidth: Dp = 600.dp
    val mediumMaxWidth: Dp = 840.dp
}
