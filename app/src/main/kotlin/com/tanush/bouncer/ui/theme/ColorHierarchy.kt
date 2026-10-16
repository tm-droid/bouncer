@file:Suppress("DEPRECATION")

package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Bouncer 60-30-10 Color Hierarchy
 *
 * Implements the classic 60-30-10 color proportion rule:
 *   60% — Dominant (dark backgrounds, surfaces)
 *   30% — Secondary (team colors — borders, tints, fills, headers)
 *   10% — Accent (Bouncer Gold — CTAs, premium badges, key highlights)
 *
 * Team colors are always available (extracted from logos) and provide
 * the 30% secondary layer on every screen. Gold is rare and premium.
 *
 * Usage:
 * ```kotlin
 * val hierarchy = BouncerHierarchy.current
 * Box(modifier = Modifier.background(hierarchy.dominant.background))
 * Divider(color = hierarchy.secondary.border)
 * Button(colors = ButtonDefaults.buttonColors(containerColor = hierarchy.accent.gold))
 * ```
 */

// =============================================================================
// Dominant Layer (60%) — Dark backgrounds and surfaces
// =============================================================================

@Immutable
data class DominantColors(
    val background: Color,
    val backgroundElevated: Color,
    val backgroundRecessed: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceContainer: Color = Color(0xFF323945),
    val surfaceBright: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val glassBackground: Color,
    val glassBorder: Color,
    val glassBorderHighlight: Color = Color(0xFFD4AF37).copy(alpha = 0.14f)
)

// =============================================================================
// Secondary Layer (30%) — Team colors for tints, borders, fills
// =============================================================================

@Immutable
data class SecondaryColors(
    /** Full team primary color — for important indicators, selected states */
    val primary: Color,
    /** Full team secondary color — for complementary accents */
    val secondary: Color,
    /** Team-tinted dark surface — for card backgrounds */
    val surface: Color,
    /** Team primary at low alpha (~0.08) — very subtle background tint */
    val tint: Color,
    /** Team primary at medium alpha (~0.15) — card/section fills */
    val fill: Color,
    /** Team primary at border alpha (~0.45) — borders, dividers */
    val border: Color,
    /** Team primary at higher alpha (~0.22) — chips, pills, indicators */
    val indicator: Color,
    /** Auto-contrast text on team primary */
    val onPrimary: Color,
    /** Auto-contrast text on team surface */
    val onSurface: Color,
    /** Team gradient for section headers, overlays */
    val gradient: Brush,
    /** Subtle team gradient for card backgrounds */
    val subtleGradient: Brush
)

// =============================================================================
// Accent Layer (10%) — Bouncer Gold for premium moments
// =============================================================================

@Immutable
data class AccentColors(
    /** Primary gold — CTAs, premium badges, selected nav icons */
    val gold: Color,
    /** Dark gold — pressed states, secondary gold elements */
    val goldDark: Color,
    /** Light gold — hover states, highlights */
    val goldLight: Color,
    /** Subtle gold — very faint gold wash */
    val goldSubtle: Color,
    /** Etched gold — metallic feel for premium surfaces */
    val etchedGold: Color,
    /** Gold gradient — for hero CTA buttons */
    val goldGradient: Brush,
    /** Horizontal gold gradient — for premium accents */
    val goldGradientHorizontal: Brush
)

// =============================================================================
// Combined Hierarchy
// =============================================================================

@Immutable
data class ColorHierarchy(
    val dominant: DominantColors,
    val secondary: SecondaryColors,
    val accent: AccentColors
)

// =============================================================================
// CompositionLocal
// =============================================================================

private val DarkColorHierarchy = buildColorHierarchy(
    teamPrimary = GoldDark,
    teamSecondary = GoldPrimaryDark,
    teamSurface = Surface1Dark,
    teamOnPrimary = Color.White,
    teamOnSurface = TextPrimaryDark,
    palette = DarkVariantPalette
)

val LocalColorHierarchy = staticCompositionLocalOf { DarkColorHierarchy }

/**
 * The hierarchy for the theme in force. `BouncerThemeV2` did not provide one, so the ~110 reads
 * across match detail (scorecard rates, prediction, fall of wickets, head to head) all got the dark
 * default in light mode too: `secondary.primary` drew #AF8327 text on white, about 3.4:1. In light
 * mode gold as ink is [com.tanush.bouncer.ui.theme.v2.BouncerColorsV2.primaryText] (5.3:1), and the
 * glass tokens are the light surface and divider. Dark mode is unchanged.
 */
internal fun colorHierarchyFor(colors: com.tanush.bouncer.ui.theme.v2.BouncerColorsV2, isDark: Boolean): ColorHierarchy {
    if (isDark) return DarkColorHierarchy
    val light = buildColorHierarchy(
        teamPrimary = colors.primaryText,
        teamSecondary = colors.primaryText,
        teamSurface = colors.surface,
        teamOnPrimary = colors.onPrimary,
        teamOnSurface = colors.onSurface,
        palette = DarkVariantPalette
    )
    return light.copy(
        dominant = light.dominant.copy(
            background = colors.background,
            backgroundElevated = colors.surface,
            backgroundRecessed = colors.surfaceVariant,
            surface = colors.surface,
            surfaceElevated = colors.surface,
            surfaceContainer = colors.surfaceVariant,
            surfaceBright = colors.surface,
            textPrimary = colors.onSurface,
            textSecondary = colors.textSecondary,
            textTertiary = colors.textSecondary,
            textMuted = colors.textSecondary,
            glassBackground = colors.surface,
            glassBorder = colors.divider,
            glassBorderHighlight = colors.border,
        ),
        accent = light.accent.copy(gold = colors.primaryText),
    )
}

// =============================================================================
// Builder — creates hierarchy from team colors + variant palette
// =============================================================================

fun buildColorHierarchy(
    teamPrimary: Color,
    teamSecondary: Color,
    teamSurface: Color,
    teamOnPrimary: Color,
    teamOnSurface: Color,
    palette: VariantColorPalette
): ColorHierarchy {

    val dominant = DominantColors(
        background = palette.backgroundBase,
        backgroundElevated = palette.backgroundElevated,
        backgroundRecessed = palette.backgroundRecessed,
        surface = palette.surface1,
        surfaceElevated = palette.surface2,
        surfaceContainer = palette.surface3,
        surfaceBright = palette.surfaceBright,
        textPrimary = palette.textPrimary,
        textSecondary = palette.textSecondary,
        textTertiary = palette.textTertiary,
        textMuted = palette.textMuted,
        glassBackground = palette.glassBackground,
        glassBorder = palette.glassBorder,
        glassBorderHighlight = palette.glassBorderHighlight
    )

    val secondary = SecondaryColors(
        primary = teamPrimary,
        secondary = teamSecondary,
        surface = teamSurface,
        tint = teamPrimary.copy(alpha = 0.08f),
        fill = teamPrimary.copy(alpha = 0.15f),
        border = teamPrimary.copy(alpha = 0.45f),
        indicator = teamPrimary.copy(alpha = 0.22f),
        onPrimary = teamOnPrimary,
        onSurface = teamOnSurface,
        gradient = Brush.verticalGradient(
            colors = listOf(
                teamPrimary.copy(alpha = 0.18f),
                teamSecondary.copy(alpha = 0.08f),
                Color.Transparent
            )
        ),
        subtleGradient = Brush.verticalGradient(
            colors = listOf(
                teamPrimary.copy(alpha = 0.06f),
                Color.Transparent
            )
        )
    )

    val accent = AccentColors(
        gold = palette.goldPrimary,
        goldDark = palette.goldDark,
        goldLight = palette.goldLight,
        goldSubtle = palette.goldSubtle,
        etchedGold = EtchedGold,
        goldGradient = Brush.verticalGradient(
            colors = listOf(
                palette.goldLight.copy(alpha = 0.9f),
                palette.goldPrimary,
                palette.goldDark
            )
        ),
        goldGradientHorizontal = Brush.horizontalGradient(
            colors = listOf(
                palette.goldLight,
                palette.goldPrimary,
                palette.goldDark
            )
        )
    )

    return ColorHierarchy(dominant, secondary, accent)
}

// =============================================================================
// Composable builders
// =============================================================================

@Composable
fun rememberColorHierarchy(
    teamColors: TeamColors,
    palette: VariantColorPalette = LocalVariantPalette.current
): ColorHierarchy {
    return remember(teamColors, palette) {
        buildColorHierarchy(
            teamPrimary = teamColors.primary,
            teamSecondary = teamColors.secondary,
            teamSurface = teamColors.surface,
            teamOnPrimary = teamColors.onPrimary,
            teamOnSurface = teamColors.onSurface,
            palette = palette
        )
    }
}

@Composable
fun rememberColorHierarchy(
    teamScheme: TeamColorScheme,
    palette: VariantColorPalette = LocalVariantPalette.current
): ColorHierarchy {
    return remember(teamScheme, palette) {
        buildColorHierarchy(
            teamPrimary = teamScheme.primary,
            teamSecondary = teamScheme.secondary,
            teamSurface = teamScheme.surface,
            teamOnPrimary = teamScheme.onPrimary,
            teamOnSurface = teamScheme.onSurface,
            palette = palette
        )
    }
}

@Composable
fun rememberColorHierarchy(
    teamName: String?,
    palette: VariantColorPalette = LocalVariantPalette.current
): ColorHierarchy {
    val teamColors = rememberTeamColors(teamName)
    return rememberColorHierarchy(teamColors, palette)
}

// =============================================================================
// Accessor object
// =============================================================================

object BouncerHierarchy {
    val current: ColorHierarchy
        @Composable
        @ReadOnlyComposable
        get() = LocalColorHierarchy.current
}

// =============================================================================
// Chart Color Palette — 8 distinct hue-shifted colors for data series
// =============================================================================

/**
 * Provides a set of perceptually distinct chart colors.
 * Each color is available in three shades: fill (light), stroke (medium), emphasis (dark).
 * Colors are hue-shifted at ~40-45° increments for maximum visual separation on dark backgrounds.
 */
@Immutable
data class ChartSeriesColor(
    val fill: Color,
    val stroke: Color,
    val emphasis: Color
)

object ChartColorPalette {
    // 8 distinct hues, optimised for dark backgrounds
    private val series = listOf(
        ChartSeriesColor(
            fill = Color(0x332196F3),     // Blue
            stroke = Color(0xFF2196F3),
            emphasis = Color(0xFF1565C0)
        ),
        ChartSeriesColor(
            fill = Color(0x33FF9800),     // Orange
            stroke = Color(0xFFFF9800),
            emphasis = Color(0xFFE65100)
        ),
        ChartSeriesColor(
            fill = Color(0x3300E676),     // Green
            stroke = Color(0xFF00E676),
            emphasis = Color(0xFF00C853)
        ),
        ChartSeriesColor(
            fill = Color(0x33E040FB),     // Purple
            stroke = Color(0xFFE040FB),
            emphasis = Color(0xFFAA00FF)
        ),
        ChartSeriesColor(
            fill = Color(0x33FF5252),     // Red
            stroke = Color(0xFFFF5252),
            emphasis = Color(0xFFD50000)
        ),
        ChartSeriesColor(
            fill = Color(0x3300BCD4),     // Cyan
            stroke = Color(0xFF00BCD4),
            emphasis = Color(0xFF00838F)
        ),
        ChartSeriesColor(
            fill = Color(0x33FFD740),     // Amber
            stroke = Color(0xFFFFD740),
            emphasis = Color(0xFFFFC400)
        ),
        ChartSeriesColor(
            fill = Color(0x33EC407A),     // Pink
            stroke = Color(0xFFEC407A),
            emphasis = Color(0xFFC51162)
        )
    )

    /** Get a chart color for the given series index (wraps around). */
    fun chartColor(index: Int): ChartSeriesColor = series[index % series.size]

    /** Number of available distinct series colors. */
    val count: Int get() = series.size

    /** All series colors. */
    val all: List<ChartSeriesColor> get() = series
}
