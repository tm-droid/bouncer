@file:Suppress("unused", "ObjectPropertyName")

package com.tanush.bouncer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// Backward-compatibility bridge
//
// The legacy Dimensions.kt / Shape.kt / Typography.kt files exposed these
// objects. They have been superseded by BouncerDimens, BouncerShapes, and
// BouncerTypography. This file keeps existing consumer code compiling while
// migration proceeds.  **Delete this file once all callers are updated.**
// ============================================================================

// ─── Spacing ────────────────────────────────────────────────────────────────

object Spacing {
    val micro: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp

    // Numbered aliases used by legacy code
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 20.dp
    val space6: Dp = 24.dp
    val space8: Dp = 32.dp

    val screenHorizontal: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val sectionGap: Dp = 24.dp
}

// ─── CornerRadius ───────────────────────────────────────────────────────────

object CornerRadius {
    val Zero: Dp = 0.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val pill: Dp = 50.dp
    val circular: Dp = 50.dp
}

// ─── BorderWidth ────────────────────────────────────────────────────────────

object BorderWidth {
    val hairline: Dp = 0.5.dp
    val medium: Dp = 1.dp
}

// ─── Elevation ──────────────────────────────────────────────────────────────

object Elevation {
    val medium: Dp = 2.dp
    val high: Dp = 6.dp
}

// ─── CardSize ───────────────────────────────────────────────────────────────

object CardSize {
    val logoSmall: Dp = 28.dp
    val logoMedium: Dp = 36.dp
}

// ─── LetterSpacing ──────────────────────────────────────────────────────────

object LetterSpacing {
    val none = 0.sp
    val relaxed = 0.5.sp
    val ultraWide = 1.5.sp
}

// ─── IconSize ────────────────────────────────────────────────────────────────

object IconSize {
    val xs: Dp = 16.dp
    val sm: Dp = 20.dp
    val md: Dp = 24.dp
    val lg: Dp = 32.dp
}

// ─── CricketShapes ──────────────────────────────────────────────────────────

object CricketShapes {
    val matchCard = RoundedCornerShape(16.dp)
}

// ─── Legacy BouncerColors Object ────────────────────────────────────────────
// This object maintains backward compatibility with code that uses
// BouncerColorsCompat.PropertyName syntax. New code should use
// BouncerDesignSystem.colors instead.
// Note: Renamed from BouncerColors to BouncerColorsCompat to avoid conflict
// with the new data class BouncerColors.

@Suppress("unused", "MemberVisibilityCanBePrivate")
object BouncerColorsCompat {
    // Live match indicator
    val LiveRed = StatusLive
    val LiveRedDark = StatusLive

    // Ball-by-ball event colours
    val WicketRed = BallWicket
    val BoundaryGreen = BallBoundary
    val SixGold = BallSix
    val DotBallGray = BallDot
    val WideBallAmber = BallWide
    val NoBallOrange = BallNoBall

    // Match result colours
    val WinGreen = StatusSuccess
    val LossRed = StatusLive
    val DrawGray = TextTertiary

    // Chip / filter UI
    val ChipSelectedBg = GoldPrimary.copy(alpha = 0.12f)
    val ChipSelectedBgDark = GoldPrimary.copy(alpha = 0.16f)
    val ChipUnselectedBg = Surface1
    val ChipUnselectedBgDark = Surface2
    val ChipSelectedContent = GoldPrimary
    val ChipSelectedContentDark = GoldLight

    // Cricket pitch visualisation
    val PitchLightGreen = androidx.compose.ui.graphics.Color(0xFF7CB342)
    val PitchDarkGreen = androidx.compose.ui.graphics.Color(0xFF558B2F)
    val PitchStripLight = androidx.compose.ui.graphics.Color(0xFF9CCC65)
    val PitchStripDark = androidx.compose.ui.graphics.Color(0xFF689F38)
    val PitchCrease = androidx.compose.ui.graphics.Color(0xFFE0E0E0)

    // Card / surface gradients
    val GradientStartLight = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val GradientEndLight = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
    val GradientStartDark = Surface1
    val GradientEndDark = BackgroundBase
    val GradientAccentStart = GoldPrimary
    val GradientAccentEnd = GoldDark
    val GradientGoldStart = GoldLight
    val GradientGoldEnd = GoldPrimary

    // Misc
    val Shimmer = Surface2
    val ShimmerHighlight = Surface3
    val Divider = TextMuted
    val DividerDark = Surface2
    val ScrimOverlay = GlassScrim

    // Glassmorphism & Hero Scorecard
    val GlassSurface = GlassBackground
    val GlassSurfaceDark = GlassBackground
    val GlassSurfaceLight = GlassBackgroundHover
    val GlassBorder = com.tanush.bouncer.ui.theme.GlassBorder
    val GlassBorderTop = GlassBorderHighlight

    // Hero scorecard gradients
    val HeroGradientStart = BackgroundBase
    val HeroGradientMid = Surface1
    val HeroGradientEnd = Surface2
    val HeroGradientStartDark = BackgroundBase
    val HeroGradientMidDark = BackgroundElevated
    val HeroGradientEndDark = Surface1

    // Score display on hero
    val HeroTextPrimary = TextPrimary
    val HeroTextSecondary = TextSecondary
    val HeroTextMuted = TextMuted

    // Overs pill on hero
    val OversPillBg = GlassBackground
    val OversPillText = TextSecondary

    // Material 3 schemes
    val LightColorScheme = BouncerLightColorScheme
    val DarkColorScheme = BouncerDarkColorScheme
}
