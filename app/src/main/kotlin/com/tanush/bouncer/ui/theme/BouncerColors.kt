@file:Suppress("DEPRECATION")

package com.tanush.bouncer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Bouncer Design System — Futuristic Glass-Morphism Color Palette (Legacy)
 *
 * @deprecated This file is deprecated. Use the V2 tokens in
 * `ui/theme/v2/ColorTokensV2.kt` — access via `BouncerThemeV2.colors`
 * (incl. `CricketEventColors` for wicket/four/six semantics).
 *
 * Migration Guide:
 * 1. Replace standalone color values (e.g., `GoldPrimaryDark`) with `BouncerThemeV2.colors.primary`
 * 2. Replace `BouncerColors` data class usage with `BouncerColorsV2`
 * 3. Access colors via `BouncerThemeV2.colors` in composables
 *
 * A premium dark-first design system built around deep charcoal backgrounds,
 * warm gold accents, and frosted glass surfaces. Optimized for OLED displays
 * and immersive cricket viewing experiences.
 */

// =============================================================================
// Background Colors — Deep charcoal hierarchy
// =============================================================================

@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val BackgroundBaseDark = Color(0xFF0C1015)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val BackgroundElevated = Color(0xFF171510)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val Surface1Dark = Color(0xFF161C26)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val Surface2Dark = Color(0xFF202834)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val Surface3 = Color(0xFF2B3442)

// =============================================================================
// Gold Accent Colors — Premium warm tones
// =============================================================================

@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val GoldPrimaryDark = Color(0xFFE0B74F)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val GoldDark = Color(0xFFAF8327)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val GoldLightDark = Color(0xFFF3DB93)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val GoldSubtle = Color(0xFF34270D)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val EtchedGold = Color(0xFFE4C56F)  // Handcrafted metallic feel

@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val GoldGradientDark = Brush.verticalGradient(
    0.0f to Color(0xFFF8E7BC),
    0.55f to GoldPrimaryDark,
    1.0f to GoldDark
)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val GoldGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF7DF9A), GoldPrimaryDark, GoldDark)
)

// =============================================================================
// Text Colors — High contrast hierarchy
// =============================================================================

@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val TextPrimaryDark = Color(0xFFFCF7ED)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val TextSecondaryDark = Color(0xFFC1B08D)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val TextTertiaryDark = Color(0xFF8D7E61)
@Deprecated("Use BouncerThemeV2.colors (ui/theme/v2/ColorTokensV2.kt) instead")
val TextMutedDark = Color(0xFF665844)

// =============================================================================
// Status Colors — Clear semantic indicators
// =============================================================================

/**
 * Semantic Status Colors
 * 
 * These colors are used for states, feedback, and cricket events.
 * They remain static regardless of team colors to maintain clarity.
 */

// Primary Status Colors
val StatusLive = Color(0xFFEF4444)      // Red - Live matches, errors
val StatusSuccess = Color(0xFF22C55E)   // Green - Success, wins, boundaries
val StatusWarning = Color(0xFFF59E0B)   // Orange - Warnings, delays
val StatusInfo = Color(0xFF3B82F6)      // Blue - Information, neutral

// Extended Status Colors
val StatusPending = Color(0xFF9CA3AF)    // Gray - Pending/upcoming
val StatusCompleted = StatusSuccess      // Alias for completed
val StatusAbandoned = Color(0xFF6B7280)  // Dark gray - Abandoned matches

// =============================================================================
// Team Colors — Official team palette
// =============================================================================

/**
 * Simple team color palette for single-color uses (badges, indicators, etc).
 * For full TeamColorScheme with primary/secondary/tertiary, see LegacyTeamSchemes in DynamicTheming.kt.
 */
object TeamPrimaryColors {
    val SouthAfricaGreen = Color(0xFF10B981)
    val NewZealandSilver = Color(0xFFC0C0C0)
    val NewZealandDark = Color(0xFF1A1A1A)
    val WestIndiesMaroon = Color(0xFF7D1328)
    val IndiaBlue = Color(0xFF3B82F6)
    val PakistanGreen = Color(0xFF138808)
    val AustraliaGold = Color(0xFFD4AF37) // Matching GoldPrimaryDark
    val EnglandNavy = Color(0xFF1D4ED8)
    val SriLankaBlue = Color(0xFF1E3A8A)
    val BangladeshGreen = Color(0xFF166534)
    val AfghanistanBlue = Color(0xFF2563EB)
    val IrelandGreen = Color(0xFF15803D)
    val ZimbabweRed = Color(0xFFDC2626)
    val NetherlandsOrange = Color(0xFFF97316)
    val ScotlandBlue = Color(0xFF1D4ED8)
    
    fun getTeamColor(teamCode: String): Color {
        return when (teamCode.uppercase()) {
            "SA", "RSA" -> SouthAfricaGreen
            "NZ" -> NewZealandSilver
            "WI" -> WestIndiesMaroon
            "IND", "IN" -> IndiaBlue
            "PAK", "PK" -> PakistanGreen
            "AUS", "AU" -> AustraliaGold
            "ENG", "EN" -> EnglandNavy
            "SL", "SRI" -> SriLankaBlue
            "BAN", "BD" -> BangladeshGreen
            "AFG", "AF" -> AfghanistanBlue
            "IRE", "IR" -> IrelandGreen
            "ZIM", "ZW" -> ZimbabweRed
            "NED", "NL" -> NetherlandsOrange
            "SCO" -> ScotlandBlue
            else -> GoldPrimaryDark
        }
    }
    
    fun getTeamAccent(teamCode: String): Color {
        return getTeamColor(teamCode).copy(alpha = 0.2f)
    }

    fun getTeamEmoji(teamCode: String): String {
        return when (teamCode.uppercase()) {
            "SA", "RSA" -> "🇿🇦"
            "NZ" -> "🇳🇿"
            "WI" -> "🌴"
            "IND", "IN" -> "🇮🇳"
            "PAK", "PK" -> "🇵🇰"
            "AUS", "AU" -> "🇦🇺"
            "ENG", "EN" -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
            "SL", "SRI" -> "🇱🇰"
            "BAN", "BD" -> "🇧🇩"
            "AFG", "AF" -> "🇦🇫"
            "IRE", "IR" -> "🇮🇪"
            "ZIM", "ZW" -> "🇿🇼"
            "NED", "NL" -> "🇳🇱"
            "SCO" -> "🏴󠁧󠁢󠁳󠁣󠁴󠁿"
            else -> "🏏"
        }
    }
}

// =============================================================================
// Glass Morphism — Frosted surface system
// =============================================================================

object GlassOpacity {
    const val BACKGROUND = 0.06f
    const val BACKGROUND_HOVER = 0.08f
    const val BACKGROUND_PRESSED = 0.10f
    const val BORDER = 0.18f
    const val BORDER_HIGHLIGHT = 0.24f
    const val OVERLAY = 0.60f
    const val SCRIM = 0.80f
}

val GlassBackgroundDark = GoldLightDark.copy(alpha = GlassOpacity.BACKGROUND)
val GlassBackgroundHover = GoldLightDark.copy(alpha = GlassOpacity.BACKGROUND_HOVER)
val GlassBackgroundPressed = GoldPrimaryDark.copy(alpha = GlassOpacity.BACKGROUND_PRESSED)
val GlassBorderDark = GoldLightDark.copy(alpha = GlassOpacity.BORDER)
val GlassBorderHighlight = GoldPrimaryDark.copy(alpha = GlassOpacity.BORDER_HIGHLIGHT)
val GlassOverlay = Color.Black.copy(alpha = GlassOpacity.OVERLAY)
val GlassScrim = Color.Black.copy(alpha = GlassOpacity.SCRIM)

// =============================================================================
// Ball-by-Ball Event Colors
// =============================================================================

val BallDot = Color(0xFF4B5563)
val BallRun = Color(0xFF6B7280)
val BallBoundary = Color(0xFF22C55E)
val BallSix = Color(0xFFD4A843)
val BallWicket = Color(0xFFEF4444)
val BallWide = Color(0xFFF59E0B)
val BallNoBall = Color(0xFFF97316)
val BallLegBye = Color(0xFF8B5CF6)
val BallBye = Color(0xFF06B6D4)

// =============================================================================
// Chart & Data Visualization Colors
// =============================================================================

object ChartColors {
    val Primary = GoldPrimaryDark
    val Secondary = Color(0xFF3B82F6)
    val Tertiary = Color(0xFF22C55E)
    val Quaternary = Color(0xFFF97316)
    val Quinary = Color(0xFF8B5CF6)
    
    val Gradient1Start = Color(0xFFD4A843)
    val Gradient1End = Color(0xFFC49A38)
    val Gradient2Start = Color(0xFF3B82F6)
    val Gradient2End = Color(0xFF1D4ED8)
    val Gradient3Start = Color(0xFF22C55E)
    val Gradient3End = Color(0xFF15803D)
    
    val all = listOf(Primary, Secondary, Tertiary, Quaternary, Quinary)
}

// =============================================================================
// Probability & Win Meter Colors
// =============================================================================

val ProbabilityHigh = Color(0xFF22C55E)
val ProbabilityMedium = Color(0xFFF59E0B)
val ProbabilityLow = Color(0xFFEF4444)
val ProbabilityNeutral = Color(0xFF6B7280)

// =============================================================================
// LEGACY COLOR ALIASES — Backward compatibility with old Color.kt
// =============================================================================

// Background tiers (aliases)
val BgBase = BackgroundBaseDark
val BgElevated = BackgroundElevated
val BgRecessed = Color(0xFF040406)

// Gold accent system (6-stop ramp)
val Gold100 = Color(0xFFFFF8E7)
val Gold200 = Color(0xFFFFE9B8)
val Gold300 = Color(0xFFFFD780)
val Gold400 = GoldPrimaryDark
val Gold500 = Color(0xFFC69433)
val Gold600 = Color(0xFF8F661C)
val GoldAccentDeep = GoldPrimaryDark
val GoldAccentLight = GoldLightDark
val GoldAccentDim = GoldDark
val GoldAccentMid = Gold300
val GoldAccentBronze = Color(0xFFCD7F32)
val GoldAccentDark = Gold600

// Blue accent system
val Blue100 = Color(0xFFE3F2FF)
val Blue300 = Color(0xFF64B5F6)
val Blue400 = Color(0xFF2196F3)
val Blue500 = Color(0xFF1976D2)
val LiveBlue = StatusInfo

// Status colors (aliases)
val StatusError = StatusLive
val StatusLiveBg = Color(0xFF7F1D1D)
val StatusBoundary = Color(0xFF3B82F6)
val StatusSix = Color(0xFF8B5CF6)
val StatusWicket = StatusLive
val SuccessGreen = StatusSuccess
val ErrorRed = StatusLive
val WarningOrange = StatusWarning

// Text (aliases)
val TextInverse = BackgroundBaseDark

// Cricket-specific
val WicketRed = StatusLive
val DotBall = Color(0xFF454545)
val MaidenOver = Color(0xFF94A3B8)
val BoundarySix = StatusSix
val BoundaryFour = StatusBoundary
val BoundaryBlue = StatusBoundary
val SixerPurple = StatusSix
val FullLength = Color(0xFF4ADE80)
val GoodLength = GoldPrimaryDark
val ShortPitch = Color(0xFFFB923C)
val Yorker = Color(0xFFF87171)

// Glass (aliases)
val GlassBorderLight = GoldLightDark.copy(alpha = 0.08f)
val GlassSurfaceDark = GoldPrimaryDark.copy(alpha = 0.10f)
val GlassWhite = Color.White.copy(alpha = 0.10f)
val GlassDark = Color.Black.copy(alpha = 0.20f)
val GlassEtchedGold = EtchedGold.copy(alpha = 0.15f)

// Data visualization
val ChartCyan = Color(0xFF22D3EE)
val ChartBlue = Color(0xFF3B82F6)
val ChartGreen = Color(0xFF10B981)
val ChartPurple = Color(0xFF8B5CF6)
val ChartOrange = Color(0xFFF97316)

// Achievement tiers
val TierBronze = Color(0xFFCD7F32)
val TierSilver = Color(0xFFC0C0C0)
val TierGold = GoldPrimaryDark
val TierPlatinum = Color(0xFFE5E4E2)
val TierDiamond = Color(0xFFB9F2FF)

// Surface aliases
val BlackSurface = Surface1Dark
val BlackElevated = Surface2Dark
val BlackBackground = BackgroundBaseDark
val CardSurface = Surface1Dark
val CardSurfaceLight = Surface2Dark
val CardDarkBlue = Color(0xFF1A1F2E)
val CardAlmostBlack = Color(0xFF0A0B0D)

// Status color variants
val SuccessGreenMuted = StatusSuccess.copy(alpha = 0.15f)
val ErrorRedMuted = StatusLive.copy(alpha = 0.15f)
val WarningOrangeMuted = StatusWarning.copy(alpha = 0.15f)
val LiveBlueBright = Color(0xFF60A5FA)
val SuccessGreenBright = Color(0xFF4ADE80)
val ErrorRedBright = Color(0xFFF87171)

// Helper function for live indicator
@androidx.compose.runtime.Composable
fun liveIndicatorColor(): Color = StatusLive

// =============================================================================
// Additional Legacy Color Aliases — Extended backward compatibility
// =============================================================================

// Shiny Gold aliases for backward compatibility with older components
val ShinyGoldDeep = GoldPrimaryDark
val ShinyGoldLight = GoldLightDark
val ShinyGoldMid = Gold500
val ShinyGoldDark = GoldDark
val ShinyGoldBronze = GoldAccentBronze
val ShinyGoldDim = GoldSubtle

// Warning/Status
val WarningYellow = StatusWarning
val LiveRed = StatusLive
val LiveRedPulse = StatusLive.copy(alpha = 0.5f)
val LiveGreen = StatusSuccess
val NeonError = Color(0xFFFF6B6B)
val NeonSuccess = Color(0xFF4ADE80)

// Gold variants
val GoldHairline = GoldPrimaryDark.copy(alpha = 0.3f)
val GoldGradientVariant = Brush.verticalGradient(
    colors = listOf(GoldLightDark, GoldPrimaryDark, GoldDark)
)
val GradientAccentStart = GoldPrimaryDark

// Surface/Card variants
val CardBorder = GlassBorderDark
val CardSurfaceColor = Surface1Dark
val CardSurfaceElevated = Surface2Dark
val PrimaryBackground = BackgroundBaseDark

// Ball event colors
val BoundaryGreen = Color(0xFF22C55E)
val DotBallGray = Color(0xFF4B5563)
val NoBallOrange = Color(0xFFF97316)
val SixGold = GoldPrimaryDark
val WicketBlue = Color(0xFF3B82F6)
val WideBallAmber = Color(0xFFFBBF24)

// Pill colors
val OversPillBg = Surface2Dark
val OversPillText = TextSecondaryDark

// Hero text
val HeroTextPrimary = TextPrimaryDark
val HeroTextSecondary = TextSecondaryDark
val HeroTextMuted = TextMutedDark

// Shimmer
val Shimmer = Color(0xFF2C2C2C)
val ShimmerHighlight = Color(0xFF3C3C3C)

// =============================================================================
// Team Color Object Helper — For legacy code patterns
// =============================================================================

open class TeamColorObject(val primary: Color) {
    val accent: Color get() = primary.copy(alpha = 0.2f)
    val glow: Color get() = primary.copy(alpha = 0.3f)
}

// Team colors (legacy aliases)
val TeamIndia = TeamPrimaryColors.IndiaBlue
val TeamAustralia = TeamPrimaryColors.AustraliaGold
val TeamEngland = TeamPrimaryColors.EnglandNavy
val TeamPakistan = TeamPrimaryColors.PakistanGreen
val TeamSouthAfrica = TeamPrimaryColors.SouthAfricaGreen
val TeamNewZealand = TeamPrimaryColors.NewZealandSilver
val TeamWestIndies = TeamPrimaryColors.WestIndiesMaroon
val TeamSriLanka = TeamPrimaryColors.SriLankaBlue
val TeamBangladesh = TeamPrimaryColors.BangladeshGreen

// =============================================================================
// BouncerColors — Semantic color container
// =============================================================================

/**
 * Semantic color container for Bouncer theme.
 * 
 * @deprecated Use `BouncerColorsV2` from ui/theme/v2/ColorTokensV2.kt instead.
 *
 * Migration: Replace `BouncerColors` with `BouncerColorsV2`.
 * Access via `BouncerThemeV2.colors` in composables.
 */
@Deprecated(
    message = "Use BouncerColorsV2 from ui/theme/v2/ColorTokensV2.kt instead",
)
@Immutable
data class BouncerColors(
    // Backgrounds
    val backgroundBase: Color = BackgroundBaseDark,
    val backgroundElevated: Color = BackgroundElevated,
    val surface1: Color = Surface1Dark,
    val surface2: Color = Surface2Dark,
    val surface3: Color = Surface3,
    
    // Gold Accents
    val goldPrimary: Color = GoldPrimaryDark,
    val goldDark: Color = GoldDark,
    val goldLight: Color = GoldLightDark,
    val goldSubtle: Color = GoldSubtle,
    val etchedGold: Color = EtchedGold,
    val goldGradient: Brush = GoldGradientDark,
    val goldGradientHorizontal: Brush = GoldGradientHorizontal,
    
    // Text
    val textPrimary: Color = TextPrimaryDark,
    val textSecondary: Color = TextSecondaryDark,
    val textTertiary: Color = TextTertiaryDark,
    val textMuted: Color = TextMutedDark,
    
    // Status
    val statusLive: Color = StatusLive,
    val statusSuccess: Color = StatusSuccess,
    val statusWarning: Color = StatusWarning,
    val statusInfo: Color = StatusInfo,
    
    // Glass
    val glassBackground: Color = GlassBackgroundDark,
    val glassBackgroundHover: Color = GlassBackgroundHover,
    val glassBackgroundPressed: Color = GlassBackgroundPressed,
    val glassBorder: Color = GlassBorderDark,
    val glassBorderHighlight: Color = GlassBorderHighlight,
    val glassOverlay: Color = GlassOverlay,
    val glassScrim: Color = GlassScrim,
    val glassSurface: Color = GlassSurfaceDark,
    
    // Ball Colors
    val ballDot: Color = BallDot,
    val ballRun: Color = BallRun,
    val ballBoundary: Color = BallBoundary,
    val ballSix: Color = BallSix,
    val ballWicket: Color = BallWicket,
    val ballWide: Color = BallWide,
    val ballNoBall: Color = BallNoBall,
    val ballLegBye: Color = BallLegBye,
    val ballBye: Color = BallBye,
    
    // Probability
    val probabilityHigh: Color = ProbabilityHigh,
    val probabilityMedium: Color = ProbabilityMedium,
    val probabilityLow: Color = ProbabilityLow,
    val probabilityNeutral: Color = ProbabilityNeutral,
    
    // Match semantic tokens (dynamic, team-based)
    val team: TeamColorScheme = LegacyTeamSchemes.Default,
    val matchPrimary: Color = team.primary,
    val matchSecondary: Color = team.secondary,
    val matchAccent: Color = team.tertiary,
    val onMatchPrimary: Color = team.onPrimary
) {
    /**
     * Legacy static color accessors for backward compatibility.
     * Access via BouncerColors.WicketRed, BouncerColors.SixGold, etc.
     */
    companion object {
        // Ball event colors
        val WicketRed = com.tanush.bouncer.ui.theme.WicketRed
        val SixGold = com.tanush.bouncer.ui.theme.SixGold
        val BoundaryGreen = com.tanush.bouncer.ui.theme.BoundaryGreen
        val DotBallGray = com.tanush.bouncer.ui.theme.DotBallGray
        val WideBallAmber = com.tanush.bouncer.ui.theme.WideBallAmber
        val NoBallOrange = com.tanush.bouncer.ui.theme.NoBallOrange
        
        // Status colors
        val LiveRed = com.tanush.bouncer.ui.theme.LiveRed
        val LiveRedPulse = com.tanush.bouncer.ui.theme.LiveRedPulse
        val LiveGreen = com.tanush.bouncer.ui.theme.LiveGreen
        val WarningYellow = com.tanush.bouncer.ui.theme.WarningYellow
        val NeonError = com.tanush.bouncer.ui.theme.NeonError
        val NeonSuccess = com.tanush.bouncer.ui.theme.NeonSuccess
        
        // Gold colors
        val GoldHairline = com.tanush.bouncer.ui.theme.GoldHairline
        val GoldGradientDark = com.tanush.bouncer.ui.theme.GoldGradientDark
        val EtchedGold = com.tanush.bouncer.ui.theme.EtchedGold
        val GradientAccentStart = com.tanush.bouncer.ui.theme.GradientAccentStart
        val ShinyGoldDeep = com.tanush.bouncer.ui.theme.GoldDark
        val ShinyGoldLight = com.tanush.bouncer.ui.theme.GoldLightDark
        val ShinyGoldMid = com.tanush.bouncer.ui.theme.GoldPrimaryDark
        val ShinyGoldDim = com.tanush.bouncer.ui.theme.GoldSubtle
        val ShinyGoldBronze = com.tanush.bouncer.ui.theme.GoldDark
        
        // Surface colors
        val CardBorder = com.tanush.bouncer.ui.theme.CardBorder
        val CardSurfaceColor = com.tanush.bouncer.ui.theme.CardSurfaceColor
        val CardSurfaceElevated = com.tanush.bouncer.ui.theme.CardSurfaceElevated
        val PrimaryBackground = com.tanush.bouncer.ui.theme.PrimaryBackground
        val GlassBorderDark = com.tanush.bouncer.ui.theme.GlassBorderDark
        val GlassSurfaceDark = com.tanush.bouncer.ui.theme.GlassSurfaceDark
        
        // Pill colors
        val OversPillBg = com.tanush.bouncer.ui.theme.OversPillBg
        val OversPillText = com.tanush.bouncer.ui.theme.OversPillText
        
        // Hero text
        val HeroTextPrimary = com.tanush.bouncer.ui.theme.HeroTextPrimary
        val HeroTextSecondary = com.tanush.bouncer.ui.theme.HeroTextSecondary
        val HeroTextMuted = com.tanush.bouncer.ui.theme.HeroTextMuted
        
        // Shimmer
        val Shimmer = com.tanush.bouncer.ui.theme.Shimmer
        val ShimmerHighlight = com.tanush.bouncer.ui.theme.ShimmerHighlight
        
        // Primary colors
        val GoldPrimaryDark = com.tanush.bouncer.ui.theme.GoldPrimaryDark
        val GoldLightDark = com.tanush.bouncer.ui.theme.GoldLightDark
        val GoldDark = com.tanush.bouncer.ui.theme.GoldDark
        val BackgroundBaseDark = com.tanush.bouncer.ui.theme.BackgroundBaseDark
        val Surface1Dark = com.tanush.bouncer.ui.theme.Surface1Dark
        val Surface2Dark = com.tanush.bouncer.ui.theme.Surface2Dark
        val StatusLive = com.tanush.bouncer.ui.theme.StatusLive
        val StatusSuccess = com.tanush.bouncer.ui.theme.StatusSuccess
        val StatusWarning = com.tanush.bouncer.ui.theme.StatusWarning
        val TextPrimaryDark = com.tanush.bouncer.ui.theme.TextPrimaryDark
        val TextSecondaryDark = com.tanush.bouncer.ui.theme.TextSecondaryDark
        val TextMutedDark = com.tanush.bouncer.ui.theme.TextMutedDark
    }
}

// =============================================================================
// CompositionLocal
// =============================================================================

/**
 * @deprecated Use `LocalBouncerColorsV2` from ui/theme/v2/ColorTokensV2.kt instead.
 */
@Suppress("DEPRECATION")
val LocalBouncerColors = staticCompositionLocalOf { BouncerColors() }

// =============================================================================
// Material 3 Color Scheme Generation
// =============================================================================

/**
 * @deprecated Use `ThemeVariant.Dark.toMaterial3ColorScheme()` from ThemeVariants.kt instead.
 */
@Suppress("DEPRECATION")
val BouncerDarkColorScheme: ColorScheme = darkColorScheme(
    primary = GoldPrimaryDark,
    onPrimary = BackgroundBaseDark,
    primaryContainer = GoldSubtle,
    onPrimaryContainer = GoldLightDark,
    
    secondary = GoldLightDark,
    onSecondary = BackgroundBaseDark,
    secondaryContainer = Surface2Dark,
    onSecondaryContainer = TextPrimaryDark,
    
    tertiary = StatusInfo,
    onTertiary = TextPrimaryDark,
    tertiaryContainer = Color(0xFF1E3A5F),
    onTertiaryContainer = Color(0xFF93C5FD),
    
    error = StatusLive,
    onError = TextPrimaryDark,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFCA5A5),
    
    background = BackgroundBaseDark,
    onBackground = TextPrimaryDark,
    
    surface = BackgroundElevated,
    onSurface = TextPrimaryDark,
    surfaceVariant = Surface1Dark,
    onSurfaceVariant = TextSecondaryDark,
    
    surfaceTint = GoldPrimaryDark,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundBaseDark,
    inversePrimary = GoldDark,
    
    outline = TextTertiaryDark,
    outlineVariant = TextMutedDark,
    scrim = Color.Black,
    
    surfaceBright = Surface3,
    surfaceDim = BackgroundBaseDark,
    surfaceContainer = Surface1Dark,
    surfaceContainerHigh = Surface2Dark,
    surfaceContainerHighest = Surface3,
    surfaceContainerLow = BackgroundElevated,
    surfaceContainerLowest = BackgroundBaseDark
)

/**
 * @deprecated Light themes are not recommended for Bouncer. Use dark variants instead.
 */
@Suppress("DEPRECATION")
val BouncerLightColorScheme: ColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF8E7),
    onPrimaryContainer = GoldDark,
    
    secondary = GoldPrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3D6),
    onSecondaryContainer = Color(0xFF5C4100),
    
    tertiary = StatusInfo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    
    error = StatusLive,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1F2937),
    
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    
    surfaceTint = GoldPrimaryDark,
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF9FAFB),
    inversePrimary = GoldLightDark,
    
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFE5E7EB),
    scrim = Color.Black,
    
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFF3F4F6),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White
)

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Returns the appropriate color scheme based on theme preference.
 * Note: Bouncer enforces dark theme by default for the premium glass experience.
 */
@Composable
fun bouncerColorScheme(darkTheme: Boolean = true): ColorScheme {
    return if (darkTheme) BouncerDarkColorScheme else BouncerLightColorScheme
}

/**
 * Interpolates between two colors based on a probability value (0.0 to 1.0).
 */
fun probabilityColor(probability: Float): Color {
    return when {
        probability >= 0.7f -> ProbabilityHigh
        probability >= 0.4f -> ProbabilityMedium
        probability > 0f -> ProbabilityLow
        else -> ProbabilityNeutral
    }
}

/**
 * Returns a color with glass-like transparency applied.
 */
fun Color.asGlass(alpha: Float = GlassOpacity.BACKGROUND): Color {
    return this.copy(alpha = alpha)
}

/**
 * Creates a subtle glow color for the given base color.
 */
fun Color.glow(alpha: Float = 0.3f): Color {
    return this.copy(alpha = alpha)
}

// =============================================================================
// Additional Helper Functions for Legacy Code
// =============================================================================

/**
 * Returns the glass surface color for glassmorphism effects.
 */
@Composable
fun glassSurfaceColor(): Color = GlassSurfaceDark

/**
 * Blends a color with gold accent for premium effects.
 */
fun Color.blendWithGoldAccents(ratio: Float = 0.2f): Color {
    val r = this.red * (1 - ratio) + GoldPrimaryDark.red * ratio
    val g = this.green * (1 - ratio) + GoldPrimaryDark.green * ratio
    val b = this.blue * (1 - ratio) + GoldPrimaryDark.blue * ratio
    return Color(r, g, b, this.alpha)
}

/**
 * Blends a ColorScheme's primary colors with gold accents.
 */
fun blendWithGoldAccents(colorScheme: ColorScheme, ratio: Float = 0.3f): ColorScheme {
    return colorScheme.copy(
        primary = colorScheme.primary.blendWithGoldAccents(ratio),
        secondary = colorScheme.secondary.blendWithGoldAccents(ratio * 0.5f),
        tertiary = GoldLightDark
    )
}

// =============================================================================
// Theme-aware aliases for the legacy token names
// =============================================================================

/*
 * The raw constants above are the *dark* palette and are now suffixed `Dark`. The unsuffixed names
 * live on here as composable getters that read [LocalBouncerColors], which BouncerThemeV2 provides
 * per theme mode.
 *
 * Why aliases rather than a rewrite: ~260 call sites across ~50 unmigrated files under
 * `ui/components` read these names as plain identifiers. As `val`s they were constant by
 * construction, so light mode drew near-white text onto a white page everywhere they appeared.
 * Re-declaring the same names with a composable getter makes every one of those sites follow the
 * theme without editing them — and the compiler flags the handful used outside composition, which
 * point at the `Dark` constant instead.
 *
 * New code should use `BouncerThemeV2.colors`; these exist to retire the legacy layer gradually.
 */

val BackgroundBase: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.backgroundBase

val Surface1: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.surface1

val Surface2: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.surface2

val GoldPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.goldPrimary

val GoldLight: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.goldLight

val GoldGradient: Brush
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.goldGradient

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.textSecondary

val TextTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.textTertiary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.textMuted

val GlassBackground: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.glassBackground

val GlassBorder: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.glassBorder

val GlassSurface: Color
    @Composable @ReadOnlyComposable get() = LocalBouncerColors.current.glassSurface
