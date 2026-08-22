@file:Suppress("DEPRECATION")

package com.tanush.bouncer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * Bouncer Theme (Legacy)
 *
 * @deprecated This file is deprecated. Use UnifiedBouncerTheme from the kotlin theme directory instead.
 * 
 * Migration Guide:
 * 1. Replace `BouncerTheme { }` with `UnifiedBouncerTheme { }`
 * 2. Replace `BouncerDesignSystem.colors` with `Bouncer.colors`
 * 3. For theme variants, use: `UnifiedBouncerTheme(variant = ThemeVariant.Midnight) { }`
 * 4. For background skins, use: `UnifiedBouncerTheme(backgroundSkin = BackgroundSkin.StadiumLights()) { }`
 * 
 * New imports:
 * - import com.tanush.bouncer.ui.theme.UnifiedBouncerTheme
 * - import com.tanush.bouncer.ui.theme.Bouncer
 * - import com.tanush.bouncer.ui.theme.ThemeVariant
 * - import com.tanush.bouncer.ui.theme.BackgroundSkin
 *
 * Main theme composable implementing the futuristic glass-morphism design system.
 * Dark-only enforced theme optimized for immersive cricket viewing.
 */

// =============================================================================
// Theme Constants
// =============================================================================

private const val FORCE_DARK_THEME = true

// =============================================================================
// Design System Accessor
// =============================================================================

/**
 * Accessor object for Bouncer design system tokens.
 * Use this similar to MaterialTheme.colorScheme but for Bouncer-specific tokens.
 *
 * Example:
 * ```
 * Text(
 *     text = "Score",
 *     style = BouncerDesignSystem.typography.scoreDisplay,
 *     color = BouncerDesignSystem.colors.goldPrimary
 * )
 * ```
 */
object BouncerDesignSystem {
    /**
     * Current Bouncer color palette.
     */
    val colors: BouncerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerColors.current
    
    /**
     * Current Bouncer typography styles.
     */
    val typography: BouncerTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerTypography.current
    
    /**
     * Current Bouncer dimension tokens.
     */
    val dimens: BouncerDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerDimens.current
    
    /**
     * Current Bouncer shape tokens.
     */
    val shapes: BouncerShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerShapes.current
    
    /**
     * Bottom navigation height.
     */
    val BottomNavHeight = com.tanush.bouncer.ui.theme.BottomNavHeight
    
    /**
     * Glass blur radius for glassmorphism effects.
     */
    val GlassBlur = com.tanush.bouncer.ui.theme.GlassBlur
    
    /**
     * Semantic glass surface color.
     */
    val GlassSurface = com.tanush.bouncer.ui.theme.GlassSurface
    
    /**
     * Handcrafted metallic gold color.
     */
    val EtchedGold = com.tanush.bouncer.ui.theme.EtchedGold

    /**
     * 60-30-10 color hierarchy: dominant (60%), secondary/team (30%), accent/gold (10%).
     */
    val hierarchy: ColorHierarchy
        @Composable
        @ReadOnlyComposable
        get() = LocalColorHierarchy.current
}

/**
 * Alias for BouncerDesignSystem for backward compatibility.
 * 
 * @deprecated Use `Bouncer` object from UnifiedTheme.kt instead.
 */
@Suppress("DEPRECATION")
@Deprecated(
    message = "Use Bouncer object from UnifiedTheme.kt instead",
    replaceWith = ReplaceWith("Bouncer", "com.tanush.bouncer.ui.theme.Bouncer")
)
val BouncerDesign = BouncerDesignSystem

// =============================================================================
// Legacy Compatibility Object
// =============================================================================

/**
 * Legacy theme accessor for backward compatibility.
 * 
 * @deprecated Use `Bouncer` object from UnifiedTheme.kt instead.
 */
@Deprecated(
    message = "Use Bouncer object from UnifiedTheme.kt instead",
    replaceWith = ReplaceWith("Bouncer", "com.tanush.bouncer.ui.theme.Bouncer")
)
object BouncerTheme {
    val dimens: BouncerDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerDimens.current
    
    val colors: BouncerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerColors.current
    
    val typography: BouncerTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerTypography.current
    
    val shapes: BouncerShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalBouncerShapes.current
}

// =============================================================================
// Main Theme Composable
// =============================================================================

/**
 * Bouncer Material 3 theme with custom glass-morphism extensions.
 *
 * @deprecated Use `UnifiedBouncerTheme` from UnifiedTheme.kt instead.
 * 
 * Migration:
 * ```
 * // Old:
 * BouncerTheme { content() }
 * 
 * // New:
 * UnifiedBouncerTheme { content() }
 * ```
 * 
 * This theme enforces dark mode by default for the premium glass effect.
 * The light theme is minimal and primarily exists for system compatibility.
 * 
 * Now supports:
 * - **Theme Variants**: Dark, Midnight, AMOLED
 * - **Background Skins**: Midnight Gradient, Stadium Lights, Carbon Fiber, Aurora, Pure Black, Team Colors
 *
 * @param darkTheme Whether to use dark theme. Defaults to true (enforced).
 * @param dynamicColor Whether to use Material You dynamic colors on Android 12+.
 *                     Defaults to false to preserve the Bouncer brand identity.
 * @param themeVariant Theme color variant (Dark, Midnight, or AMOLED).
 * @param backgroundSkin Background skin style (Midnight Gradient, Stadium Lights, etc.).
 * @param content Composable content rendered inside the theme.
 */
@Deprecated(
    message = "Use UnifiedBouncerTheme from UnifiedTheme.kt instead",
    replaceWith = ReplaceWith(
        "UnifiedBouncerTheme(variant = themeVariant, backgroundSkin = backgroundSkin, content = content)",
        "com.tanush.bouncer.ui.theme.UnifiedBouncerTheme"
    )
)
@Composable
fun BouncerTheme(
    darkTheme: Boolean = FORCE_DARK_THEME,
    dynamicColor: Boolean = false,
    themeVariant: ThemeVariant = ThemeVariant.Dark,
    backgroundSkin: BackgroundSkin = BackgroundSkin.MidnightGradient(),
    content: @Composable () -> Unit
) {
    // Always use dark theme for the glass-morphism effect
    val useDarkTheme = if (FORCE_DARK_THEME) true else darkTheme
    
    // Get variant palette and convert to Material 3 color scheme
    val variantPalette = themeVariant.toPalette()
    
    // Select color scheme - use variant palette if not using dynamic colors
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Even with dynamic colors, we prefer our dark scheme for consistency
            BouncerDarkColorScheme
        }
        else -> variantPalette.toMaterial3ColorScheme()
    }
    
    // Custom Bouncer tokens
    val bouncerColors = BouncerColors(team = LocalTeamColorScheme.current)
    val bouncerTypography = BouncerTypography()
    val bouncerDimensions = BouncerDimensions()
    val bouncerShapes = BouncerShapes()
    
    // 60-30-10 color hierarchy
    val teamScheme = LocalTeamColorScheme.current
    val colorHierarchy = buildColorHierarchy(
        teamPrimary = teamScheme.primary,
        teamSecondary = teamScheme.secondary,
        teamSurface = teamScheme.surface,
        teamOnPrimary = teamScheme.onPrimary,
        teamOnSurface = teamScheme.onSurface,
        palette = variantPalette
    )
    
    // System bars styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as Activity
            val window = activity.window

            // Window/system-bar colour is not set here. `Window.setStatusBarColor` and
            // `setNavigationBarColor` were deprecated in API 35 and are ignored at targetSdk 36+;
            // `enableEdgeToEdge()` in BouncerActivity owns bar appearance, and
            // `WindowCompat.setDecorFitsSystemWindows(window, false)` is redundant beside it.
            // Only icon contrast is still a real API.
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }
    
    // Provide all CompositionLocals including theme variant and background skin
    CompositionLocalProvider(
        LocalBouncerColors provides bouncerColors,
        LocalBouncerTypography provides bouncerTypography,
        LocalBouncerDimens provides bouncerDimensions,
        LocalBouncerShapes provides bouncerShapes,
        LocalTeamColorScheme provides bouncerColors.team,
        LocalThemeVariant provides themeVariant,
        LocalVariantPalette provides variantPalette,
        LocalBackgroundSkin provides backgroundSkin,
        LocalColorHierarchy provides colorHierarchy
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BouncerMaterial3Typography,
            shapes = BouncerMaterial3Shapes,
        ) {
            // =========================================================================
            // Background Skin Layer - Renders selected skin as background
            // =========================================================================
            Box(modifier = Modifier.fillMaxSize()) {
                // Render the selected background skin
                BackgroundSkinLayer(skin = backgroundSkin)
                
                // Render content on top
                content()
            }
        }
    }
}

// =============================================================================
// Theme Utilities
// =============================================================================

/**
 * Composable that provides only the Bouncer colors without full theme setup.
 * Useful for testing or isolated components.
 */
@Composable
fun BouncerColorsProvider(
    colors: BouncerColors = BouncerColors(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBouncerColors provides colors,
        content = content
    )
}

/**
 * Returns true if the current theme is dark.
 * In Bouncer, this is effectively always true due to enforced dark theme.
 */
@Composable
fun isBouncerDarkTheme(): Boolean {
    return FORCE_DARK_THEME || isSystemInDarkTheme()
}

// =============================================================================
// Preview Theme
// =============================================================================

/**
 * Theme wrapper for Compose previews with controlled parameters.
 */
@Composable
fun BouncerPreviewTheme(
    darkTheme: Boolean = true,
    themeVariant: ThemeVariant = ThemeVariant.Dark,
    backgroundSkin: BackgroundSkin = BackgroundSkin.MidnightGradient(),
    content: @Composable () -> Unit
) {
    val bouncerColors = BouncerColors()
    val bouncerTypography = BouncerTypography()
    val bouncerDimensions = BouncerDimensions()
    val bouncerShapes = BouncerShapes()
    val variantPalette = themeVariant.toPalette()
    
    val colorScheme = variantPalette.toMaterial3ColorScheme()
    
    CompositionLocalProvider(
        LocalBouncerColors provides bouncerColors,
        LocalBouncerTypography provides bouncerTypography,
        LocalBouncerDimens provides bouncerDimensions,
        LocalBouncerShapes provides bouncerShapes,
        LocalThemeVariant provides themeVariant,
        LocalVariantPalette provides variantPalette,
        LocalBackgroundSkin provides backgroundSkin,
        LocalColorHierarchy provides buildColorHierarchy(
            teamPrimary = LegacyTeamSchemes.Default.primary,
            teamSecondary = LegacyTeamSchemes.Default.secondary,
            teamSurface = LegacyTeamSchemes.Default.surface,
            teamOnPrimary = LegacyTeamSchemes.Default.onPrimary,
            teamOnSurface = LegacyTeamSchemes.Default.onSurface,
            palette = variantPalette
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BouncerMaterial3Typography,
            shapes = BouncerMaterial3Shapes
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BackgroundSkinLayer(skin = backgroundSkin)
                content()
            }
        }
    }
}
