package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bouncer Dimensions System
 *
 * 8-point grid spacing system with semantic component sizing tokens.
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
// Spacing Scale — 8-point grid foundation
// =============================================================================

@Immutable
data class BouncerSpacing(
    /** 4dp — Extra small spacing */
    val xs: Dp = 4.dp,
    /** 8dp — Small spacing (base unit) */
    val sm: Dp = 8.dp,
    /** 16dp — Medium-Large spacing */
    val md: Dp = 16.dp,
    /** 24dp — Large spacing */
    val lg: Dp = 24.dp,
    /** 32dp — Extra large spacing */
    val xl: Dp = 32.dp,
    /** 48dp — Double extra large spacing */
    val xxl: Dp = 48.dp,
    /** 64dp — Triple extra large spacing */
    val xxxl: Dp = 64.dp,
    /** 80dp — Jumbo spacing */
    val jumbo: Dp = 80.dp,
    /** 96dp — Mega spacing */
    val mega: Dp = 96.dp,
    /** 128dp — Ultra spacing */
    val ultra: Dp = 128.dp
)

// =============================================================================
// Corner Radius Scale
// =============================================================================

@Immutable
data class BouncerRadius(
    /** 4dp — Extra small radius */
    val xs: Dp = 4.dp,
    /** 8dp — Small radius */
    val sm: Dp = 8.dp,
    /** 12dp — Medium radius */
    val md: Dp = 12.dp,
    /** 16dp — Large radius */
    val lg: Dp = 16.dp,
    /** 20dp — Extra large radius */
    val xl: Dp = 20.dp,
    /** 24dp — Extra extra large radius */
    val xxl: Dp = 24.dp,
    /** 100dp — Pill/capsule shape */
    val pill: Dp = 100.dp,
    /** 20dp — Standard card corners */
    val card: Dp = 20.dp,
    /** 28dp — Hero card corners */
    val hero: Dp = 28.dp,
    /** 24dp — Bottom sheet corners */
    val sheet: Dp = 24.dp
)

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
// Elevation Levels (matching glass tiers)
// =============================================================================

@Immutable
data class BouncerElevation(
    /** 0dp — No elevation (base surface) */
    val none: Dp = 0.dp,
    /** 1dp — Level 1 elevation */
    val level1: Dp = 1.dp,
    /** 2dp — Level 2 elevation */
    val level2: Dp = 2.dp,
    /** 4dp — Level 3 elevation */
    val level3: Dp = 4.dp,
    /** 6dp — Level 4 elevation */
    val level4: Dp = 6.dp,
    /** 8dp — Level 5 elevation */
    val level5: Dp = 8.dp,
    /** 12dp — Modal elevation */
    val modal: Dp = 12.dp,
    /** 16dp — Overlay elevation */
    val overlay: Dp = 16.dp
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
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
    val screenHorizontal: Dp = 16.dp,
    val screenVertical: Dp = 8.dp,
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
 * Returns spacing based on a multiplier of the base unit (8dp).
 */
fun BouncerSpacing.gridUnit(multiplier: Int): Dp {
    return (8 * multiplier).dp
}

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
    // Spacing scale
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
    
    // Screen padding
    val screenHorizontal: Dp = 16.dp
    val screenVertical: Dp = 8.dp
    val screenTop: Dp = 16.dp
    
    // Card dimensions
    val cardPadding: Dp = 16.dp
    val cardInnerPadding: Dp = 12.dp
    val cardElevation: Dp = 2.dp
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
    val bottomNavLabelSpacing: Dp = 4.dp
    
    // Interactive elements
    val minTapTarget: Dp = 48.dp
    val iconButtonSize: Dp = 48.dp
    val fabSize: Dp = 56.dp
    val chipHeight: Dp = 36.dp
    val chipHorizontalPadding: Dp = 16.dp
    
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
    val glassBlurRadius: Dp = 24.dp
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
