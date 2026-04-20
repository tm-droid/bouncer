package com.tanush.bouncer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Bouncer Theme Variants
 * 
 * Provides three dark theme variants optimized for different use cases:
 * - Dark: Standard premium dark theme (default)
 * - Midnight: Deeper blues for a more immersive experience
 * - AMOLED: Pure blacks for OLED displays and maximum battery savings
 * 
 * All variants maintain the gold accent (#D4AF37) as primary for brand consistency.
 */

// =============================================================================
// Theme Variant Enum
// =============================================================================

/**
 * Available theme variants for Bouncer.
 * Each variant provides a unique visual experience while maintaining brand identity.
 */
enum class ThemeVariant(
    val displayName: String,
    val description: String
) {
    /**
     * Standard dark theme - Premium glass-morphism design.
     * Background: Deep charcoal (#0F1115)
     */
    Dark(
        displayName = "Dark",
        description = "Premium dark theme with rich charcoal backgrounds"
    ),
    
    /**
     * Midnight theme - Deeper blues for immersive viewing.
     * Background: Deep midnight blue (#080B12)
     */
    Midnight(
        displayName = "Midnight",
        description = "Deep blue tones for late-night viewing"
    ),
    
    /**
     * AMOLED theme - Pure blacks for OLED displays.
     * Background: Pure black (#000000)
     */
    AMOLED(
        displayName = "AMOLED Black",
        description = "Pure black for OLED displays and battery saving"
    )
}

// =============================================================================
// Variant Color Palettes
// =============================================================================

/**
 * Color palette for a theme variant.
 * Contains all background, surface, and accent colors for the variant.
 */
@Immutable
data class VariantColorPalette(
    // Background hierarchy
    val backgroundBase: Color,
    val backgroundElevated: Color,
    val backgroundRecessed: Color,
    
    // Surface hierarchy (glass tiers)
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceBright: Color,
    
    // Gold accents (consistent across variants)
    val goldPrimary: Color = GoldPrimary,
    val goldDark: Color = GoldDark,
    val goldLight: Color = GoldLight,
    val goldSubtle: Color,
    
    // Text hierarchy
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val textMuted: Color = TextMuted,
    
    // Glass effects
    val glassBackground: Color,
    val glassBorder: Color,
    val glassBorderHighlight: Color
)

// =============================================================================
// Predefined Variant Palettes
// =============================================================================

/**
 * Dark variant palette - Standard premium dark theme.
 */
val DarkVariantPalette = VariantColorPalette(
    backgroundBase = BackgroundBase,
    backgroundElevated = BackgroundElevated,
    backgroundRecessed = Color(0xFF05070A),
    surface1 = Surface1,
    surface2 = Surface2,
    surface3 = Surface3,
    surfaceBright = Color(0xFF364151),
    goldSubtle = GoldSubtle,
    glassBackground = GoldPrimary.copy(alpha = 0.08f),
    glassBorder = GoldLight.copy(alpha = 0.16f),
    glassBorderHighlight = GoldPrimary.copy(alpha = 0.24f)
)

/**
 * Midnight variant palette - Deep blues for immersive viewing.
 */
val MidnightVariantPalette = VariantColorPalette(
    backgroundBase = Color(0xFF081018),
    backgroundElevated = Color(0xFF101827),
    backgroundRecessed = Color(0xFF04070C),
    surface1 = Color(0xFF121C2B),
    surface2 = Color(0xFF1A2638),
    surface3 = Color(0xFF253349),
    surfaceBright = Color(0xFF314259),
    goldSubtle = GoldSubtle,
    glassBackground = GoldPrimary.copy(alpha = 0.07f),
    glassBorder = GoldLight.copy(alpha = 0.15f),
    glassBorderHighlight = GoldPrimary.copy(alpha = 0.22f)
)

/**
 * AMOLED variant palette - Pure blacks for OLED displays.
 */
val AMOLEDVariantPalette = VariantColorPalette(
    backgroundBase = Color.Black,
    backgroundElevated = Color(0xFF050505),
    backgroundRecessed = Color.Black,
    surface1 = Color(0xFF0B0B0B),
    surface2 = Color(0xFF141414),
    surface3 = Color(0xFF1C1C1C),
    surfaceBright = Color(0xFF262626),
    goldSubtle = Color(0xFF241B08),
    glassBackground = GoldPrimary.copy(alpha = 0.06f),
    glassBorder = GoldLight.copy(alpha = 0.14f),
    glassBorderHighlight = GoldPrimary.copy(alpha = 0.20f)
)

// =============================================================================
// Variant to Palette Mapping
// =============================================================================

/**
 * Returns the color palette for the given theme variant.
 */
fun ThemeVariant.toPalette(): VariantColorPalette = when (this) {
    ThemeVariant.Dark -> DarkVariantPalette
    ThemeVariant.Midnight -> MidnightVariantPalette
    ThemeVariant.AMOLED -> AMOLEDVariantPalette
}

/**
 * Converts a VariantColorPalette to a Material 3 ColorScheme.
 */
fun VariantColorPalette.toMaterial3ColorScheme(): ColorScheme = darkColorScheme(
    primary = goldPrimary,
    onPrimary = backgroundBase,
    primaryContainer = goldSubtle,
    onPrimaryContainer = goldLight,
    
    secondary = goldLight,
    onSecondary = backgroundBase,
    secondaryContainer = surface2,
    onSecondaryContainer = textPrimary,
    
    tertiary = StatusInfo,
    onTertiary = textPrimary,
    tertiaryContainer = Color(0xFF1E3A5F),
    onTertiaryContainer = Color(0xFF93C5FD),
    
    error = StatusLive,
    onError = textPrimary,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFCA5A5),
    
    background = backgroundBase,
    onBackground = textPrimary,
    
    surface = backgroundElevated,
    onSurface = textPrimary,
    surfaceVariant = surface1,
    onSurfaceVariant = textSecondary,
    
    surfaceTint = goldPrimary,
    inverseSurface = textPrimary,
    inverseOnSurface = backgroundBase,
    inversePrimary = goldDark,
    
    outline = textTertiary,
    outlineVariant = textMuted,
    scrim = Color.Black,
    
    surfaceBright = surfaceBright,
    surfaceDim = backgroundBase,
    surfaceContainer = surface1,
    surfaceContainerHigh = surface2,
    surfaceContainerHighest = surface3,
    surfaceContainerLow = backgroundElevated,
    surfaceContainerLowest = backgroundBase
)

// =============================================================================
// CompositionLocal
// =============================================================================

/**
 * CompositionLocal for the current theme variant.
 */
val LocalThemeVariant = staticCompositionLocalOf { ThemeVariant.Dark }

/**
 * CompositionLocal for the current variant color palette.
 */
val LocalVariantPalette = staticCompositionLocalOf { DarkVariantPalette }

// =============================================================================
// Theme Variant Accessor
// =============================================================================

/**
 * Accessor object for theme variant information.
 */
object ThemeVariants {
    /**
     * Current theme variant.
     */
    val current: ThemeVariant
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeVariant.current
    
    /**
     * Current variant color palette.
     */
    val palette: VariantColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalVariantPalette.current
    
    /**
     * Returns true if using the AMOLED variant.
     */
    val isAMOLED: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeVariant.current == ThemeVariant.AMOLED
    
    /**
     * Returns true if using the Midnight variant.
     */
    val isMidnight: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeVariant.current == ThemeVariant.Midnight
    
    /**
     * Returns true if using the standard Dark variant.
     */
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeVariant.current == ThemeVariant.Dark
    
    /**
     * All available theme variants.
     */
    val all: List<ThemeVariant> = ThemeVariant.entries
}
