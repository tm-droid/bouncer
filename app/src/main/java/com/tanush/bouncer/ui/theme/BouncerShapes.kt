package com.tanush.bouncer.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Bouncer Shape System
 *
 * Comprehensive shape tokens for futuristic glass-morphism UI.
 * All shapes follow the design mockups for consistent visual language.
 */

// =============================================================================
// Custom Shape Tokens
// =============================================================================

@Immutable
data class BouncerShapes(
    // Standard Corner Radii
    /** 4dp corners — Extra small elements */
    val extraSmall: Shape = RoundedCornerShape(4.dp),
    /** 8dp corners — Small elements */
    val small: Shape = RoundedCornerShape(8.dp),
    /** 12dp corners — Medium elements, segmented controls */
    val medium: Shape = RoundedCornerShape(12.dp),
    /** 16dp corners — Large elements, glass cards */
    val large: Shape = RoundedCornerShape(16.dp),
    /** 20dp corners — Extra large elements */
    val extraLarge: Shape = RoundedCornerShape(20.dp),
    /** 24dp corners — XXL elements, bottom sheets */
    val xxl: Shape = RoundedCornerShape(24.dp),
    
    // Glass Card Shapes
    /** 16dp corners — Standard glass card */
    val glassCard: Shape = RoundedCornerShape(16.dp),
    /** 20dp corners — Hero glass card */
    val glassCardHero: Shape = RoundedCornerShape(20.dp),
    /** 12dp corners — Compact glass card */
    val glassCardCompact: Shape = RoundedCornerShape(12.dp),
    
    // Chip Shapes
    /** 20dp corners — Standard chip (pill-like) */
    val chip: Shape = RoundedCornerShape(20.dp),
    /** 16dp corners — Compact chip */
    val chipCompact: Shape = RoundedCornerShape(16.dp),
    /** Full pill — Perfectly rounded chip */
    val chipPill: Shape = RoundedCornerShape(percent = 50),
    
    // Bottom Sheet
    /** Top corners only — Bottom sheet */
    val bottomSheet: Shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    ),
    /** Smaller top corners — Compact bottom sheet */
    val bottomSheetCompact: Shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    ),
    
    // Ball Indicators
    /** 6dp corners — Ball indicator in recent balls strip */
    val ballIndicator: Shape = RoundedCornerShape(6.dp),
    /** 8dp corners — Larger ball indicator */
    val ballIndicatorLarge: Shape = RoundedCornerShape(8.dp),
    /** Circular — Round ball indicator */
    val ballIndicatorCircle: Shape = CircleShape,
    
    // Segmented Controls
    /** 12dp corners — Segmented control container */
    val segmentedControl: Shape = RoundedCornerShape(12.dp),
    /** 10dp corners — Individual segment */
    val segment: Shape = RoundedCornerShape(10.dp),
    
    // Avatars
    /** Circular — Player avatars */
    val playerAvatar: Shape = CircleShape,
    /** 8dp corners — Team logo containers */
    val teamLogo: Shape = RoundedCornerShape(8.dp),
    /** 12dp corners — Large team logo containers */
    val teamLogoLarge: Shape = RoundedCornerShape(12.dp),
    
    // Match Card
    /** 16dp corners — Match card */
    val matchCard: Shape = RoundedCornerShape(16.dp),
    /** 20dp corners — Hero match card */
    val matchCardHero: Shape = RoundedCornerShape(20.dp),
    
    // Dialogs & Modals
    /** 28dp corners — Dialog */
    val dialog: Shape = RoundedCornerShape(28.dp),
    /** 24dp corners — Modal */
    val modal: Shape = RoundedCornerShape(24.dp),
    
    // FAB
    /** 16dp corners — FAB */
    val fab: Shape = RoundedCornerShape(16.dp),
    /** 28dp corners — Extended FAB */
    val fabExtended: Shape = RoundedCornerShape(28.dp),
    /** Circular — Small FAB */
    val fabSmall: Shape = CircleShape,
    
    // Navigation
    /** Top corners only — Bottom nav bar */
    val bottomNav: Shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    ),
    /** Right corners only — Navigation rail */
    val navRail: Shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 0.dp
    ),
    
    // Search & Input
    /** Full pill — Search bar */
    val searchBar: Shape = RoundedCornerShape(percent = 50),
    /** 12dp corners — Text field */
    val textField: Shape = RoundedCornerShape(12.dp),
    
    // Miscellaneous
    /** Full pill — Status badge */
    val statusBadge: Shape = RoundedCornerShape(percent = 50),
    /** 8dp corners — Overs pill on hero scorecard */
    val oversPill: Shape = RoundedCornerShape(8.dp),
    /** 3dp corners — Tab indicator */
    val tabIndicator: Shape = RoundedCornerShape(3.dp),
    /** 4dp corners — Tooltip */
    val tooltip: Shape = RoundedCornerShape(4.dp),
    /** 14dp corners — Status card */
    val statusCard: Shape = RoundedCornerShape(14.dp),
    /** 12dp corners — Score card */
    val scoreCard: Shape = RoundedCornerShape(12.dp),
    /** 8dp corners — Image container */
    val image: Shape = RoundedCornerShape(8.dp)
)

// =============================================================================
// CompositionLocal
// =============================================================================

val LocalBouncerShapes = staticCompositionLocalOf { BouncerShapes() }

// =============================================================================
// Material 3 Shapes Mapping
// =============================================================================

val BouncerMaterial3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// =============================================================================
// Standalone Shape References (for legacy compatibility)
// =============================================================================

/** Main match card corners. */
val MatchCardShape = RoundedCornerShape(16.dp)

/** Scorecard item corners. */
val ScoreCardShape = RoundedCornerShape(12.dp)

/** Fully rounded chips. */
val ChipShape = RoundedCornerShape(20.dp)

/** Bottom sheet — rounded top corners only. */
val BottomSheetShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

/** Floating action button corners. */
val FABShape = RoundedCornerShape(16.dp)

/** Dialog corners. */
val DialogShape = RoundedCornerShape(28.dp)

/** Bottom navigation bar — rounded top corners only. */
val BottomNavShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

/** Team logos and images. */
val ImageShape = RoundedCornerShape(8.dp)

/** Fully pill-shaped elements such as status badges. */
val PillShape = RoundedCornerShape(percent = 50)

/** Tab indicator strip. */
val TabIndicatorShape = RoundedCornerShape(3.dp)

/** Hero scorecard — large rounded corners. */
val HeroCardShape = RoundedCornerShape(20.dp)

/** Glassmorphic card surfaces. */
val GlassCardShape = RoundedCornerShape(16.dp)

/** Current player strip / small info cards. */
val StatusCardShape = RoundedCornerShape(14.dp)

/** Overs pill on hero scorecard. */
val OversPillShape = RoundedCornerShape(8.dp)

/** Navigation rail shape (compact). */
val NavRailShape = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = 16.dp,
    bottomEnd = 16.dp,
    bottomStart = 0.dp
)

/** Search bar shape — fully rounded. */
val SearchBarShape = RoundedCornerShape(percent = 50)

/** Ball indicator shape. */
val BallIndicatorShape = RoundedCornerShape(6.dp)

/** Segmented control shape. */
val SegmentedControlShape = RoundedCornerShape(12.dp)
