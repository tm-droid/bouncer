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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * Bouncer Theme
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
}

/**
 * Alias for BouncerDesignSystem for backward compatibility.
 */
val BouncerDesign = BouncerDesignSystem

// =============================================================================
// Legacy Compatibility Object
// =============================================================================

/**
 * Legacy theme accessor for backward compatibility.
 * Prefer using BouncerDesignSystem for new code.
 */
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
 * This theme enforces dark mode by default for the premium glass effect.
 * The light theme is minimal and primarily exists for system compatibility.
 *
 * @param darkTheme Whether to use dark theme. Defaults to true (enforced).
 * @param dynamicColor Whether to use Material You dynamic colors on Android 12+.
 *                     Defaults to false to preserve the Bouncer brand identity.
 * @param content Composable content rendered inside the theme.
 */
@Composable
fun BouncerTheme(
    darkTheme: Boolean = FORCE_DARK_THEME,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use dark theme for the glass-morphism effect
    val useDarkTheme = if (FORCE_DARK_THEME) true else darkTheme
    
    // Select color scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Even with dynamic colors, we prefer our dark scheme for consistency
            BouncerDarkColorScheme
        }
        useDarkTheme -> BouncerDarkColorScheme
        else -> BouncerLightColorScheme
    }
    
    // Custom Bouncer tokens
    val bouncerColors = BouncerColors(team = LocalTeamColorScheme.current)
    val bouncerTypography = BouncerTypography()
    val bouncerDimensions = BouncerDimensions()
    val bouncerShapes = BouncerShapes()
    
    // System bars styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as Activity
            val window = activity.window
            
            // Enable edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Transparent status bar for immersive glass effect
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            
            // Navigation bar blends with surface
            @Suppress("DEPRECATION")
            window.navigationBarColor = BackgroundBase.toArgb()
            
            // Icon contrast: light icons on dark backgrounds
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }
    
    // Provide all CompositionLocals
    CompositionLocalProvider(
        LocalBouncerColors provides bouncerColors,
        LocalBouncerTypography provides bouncerTypography,
        LocalBouncerDimens provides bouncerDimensions,
        LocalBouncerShapes provides bouncerShapes,
        LocalTeamColorScheme provides bouncerColors.team
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BouncerMaterial3Typography,
            shapes = BouncerMaterial3Shapes,
        ) {
            // =========================================================================
            // Global Design Foundations: Stadium Gradient, Noise, and Gold Bloom
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 1. Stadium Gradient (Background layer)
                // Radial gradient: #1A1A1A (center-top) to #0F1115 to #000000
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    0.0f to Color(0xFF1A1A1A),
                                    0.6f to Color(0xFF0F1115),
                                    1.0f to Color.Black,
                                    center = Offset(size.width / 2f, size.height * 0.1f),
                                    radius = size.height * 0.8f
                                )
                            )
                        }
                )

                // 2. Gold Bloom (Subtle blurred gold radial ellipse)
                // Radial ellipse rgba(212,175,55,0.15), filter: blur(80px)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(80.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    0.0f to Color(0xFFD4AF37).copy(alpha = 0.15f),
                                    1.0f to Color.Transparent,
                                    center = Offset(size.width / 2f, -size.height * 0.05f),
                                    radius = size.width * 1.2f
                                )
                            )
                        }
                )

                // 3. Noise Overlay (Subtle grain effect simulated with alpha)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.04f))
                    // Could be replaced by Modifier.glassmorphism(noiseAlpha = 0.05f)
                )

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
    content: @Composable () -> Unit
) {
    val bouncerColors = BouncerColors()
    val bouncerTypography = BouncerTypography()
    val bouncerDimensions = BouncerDimensions()
    val bouncerShapes = BouncerShapes()
    
    val colorScheme = if (darkTheme) BouncerDarkColorScheme else BouncerLightColorScheme
    
    CompositionLocalProvider(
        LocalBouncerColors provides bouncerColors,
        LocalBouncerTypography provides bouncerTypography,
        LocalBouncerDimens provides bouncerDimensions,
        LocalBouncerShapes provides bouncerShapes
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BouncerMaterial3Typography,
            shapes = BouncerMaterial3Shapes,
            content = content
        )
    }
}
