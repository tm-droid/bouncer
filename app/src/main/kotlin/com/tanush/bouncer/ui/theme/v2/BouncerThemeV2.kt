package com.tanush.bouncer.ui.theme.v2

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tanush.bouncer.ui.theme.LocalBouncerColors
import com.tanush.bouncer.ui.theme.LocalColorHierarchy
import com.tanush.bouncer.ui.theme.colorHierarchyFor
import com.tanush.bouncer.ui.theme.legacyColorsFor

/**
 * Bouncer V2 theme wrapper.
 *
 * Provides [BouncerColorsV2], [BouncerTypographyV2], [BouncerShapesV2],
 * [BouncerSpacingV2], and [BouncerElevationV2] via CompositionLocals,
 * and also sets up a compatible Material 3 [MaterialTheme].
 *
 * Supports Light / Dark / System modes.
 */

enum class ThemeModeV2 { Light, Dark, System }

val LocalThemeModeV2 = staticCompositionLocalOf { ThemeModeV2.System }

@Composable
fun BouncerThemeV2(
    themeMode: ThemeModeV2 = ThemeModeV2.System,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeModeV2.Light -> false
        ThemeModeV2.Dark -> true
        ThemeModeV2.System -> isSystemInDarkTheme()
    }

    val colors = if (isDark) DarkColors else LightColors
    val typography = BouncerTypographyV2()
    val shapes = BouncerShapesV2()
    val spacing = BouncerSpacingV2()
    val elevation = BouncerElevationV2()

    val materialColorScheme = if (isDark) materialDark(colors) else materialLight(colors)

    SystemBarIconContrast(isDark)

    CompositionLocalProvider(
        LocalBouncerColorsV2 provides colors,
        LocalBouncerTypographyV2 provides typography,
        LocalBouncerShapesV2 provides shapes,
        LocalBouncerSpacingV2 provides spacing,
        LocalBouncerElevationV2 provides elevation,
        LocalThemeModeV2 provides themeMode,
        LocalBouncerColors provides legacyColorsFor(isDark),
        LocalColorHierarchy provides colorHierarchyFor(colors, isDark),
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography.toMaterial3(),
            shapes = shapes.toMaterial3(),
            content = content,
        )
    }
}

/**
 * Every Material role is mapped deliberately, including the ones Bouncer never names itself.
 *
 * The unmapped roles used to fall through to the M3 baseline, which is purple: `secondaryContainer`
 * (#E8DEF8) paints the navigation-bar indicator, selected filter chips and switch tracks, and the
 * `surfaceContainer*` family (#F3EDF7 …) backs every Card, menu and bottom sheet. In light mode that
 * read as a lavender tint over most of the app. Leaving a role unset is never neutral — it is a
 * decision to ship Material's purple.
 */
private fun materialLight(colors: BouncerColorsV2) = lightColorScheme(
    primary = colors.primary,
    onPrimary = colors.onPrimary,
    primaryContainer = colors.primaryContainer,
    onPrimaryContainer = Gray900,
    inversePrimary = Gold300,

    secondary = Gray600,
    onSecondary = White,
    secondaryContainer = Gray100,
    onSecondaryContainer = Gray900,

    tertiary = colors.primaryText,
    onTertiary = White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Gray900,

    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onSurface,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.primary,

    surfaceBright = White,
    surfaceDim = Gray200,
    surfaceContainerLowest = White,
    surfaceContainerLow = Gray50,
    surfaceContainer = Gray100,
    surfaceContainerHigh = Gray200,
    surfaceContainerHighest = Gray300,

    inverseSurface = Gray900,
    inverseOnSurface = Gray50,

    error = colors.error,
    onError = White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    outline = colors.border,
    outlineVariant = colors.divider,
    scrim = colors.scrim,
)

private fun materialDark(colors: BouncerColorsV2) = darkColorScheme(
    primary = colors.primary,
    onPrimary = colors.onPrimary,
    primaryContainer = colors.primaryContainer,
    onPrimaryContainer = Gold300,
    inversePrimary = GoldText,

    secondary = DarkTextSecondary,
    onSecondary = Gray900,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,

    tertiary = Gold300,
    onTertiary = Gray900,
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = Gold300,

    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onSurface,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.primary,

    surfaceBright = DarkSurfaceVariant,
    surfaceDim = DarkBg,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = Color(0xFF131923),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkBorder,

    inverseSurface = DarkTextPrimary,
    inverseOnSurface = Gray900,

    error = colors.error,
    onError = Gray900,
    errorContainer = Color(0xFF401614),
    onErrorContainer = Color(0xFFFECACA),

    outline = colors.border,
    outlineVariant = colors.divider,
    scrim = colors.scrim,
)

/**
 * Matches system-bar icon contrast to the *app's* resolved mode rather than the system's.
 *
 * `enableEdgeToEdge()` in BouncerActivity defaults to `SystemBarStyle.auto`, which reads the system
 * dark setting — so a user on system-light with the app set to Dark got dark icons on a dark bar.
 * At targetSdk 36 edge-to-edge is mandatory, so the content always sits under the bars and this has
 * to be right. Bar *colour* is deliberately not set: those setters are deprecated and ignored.
 */
@Composable
private fun SystemBarIconContrast(isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

/** Convenience accessors — use inside any composable wrapped by [BouncerThemeV2]. */
object BouncerThemeV2 {
    val colors: BouncerColorsV2
        @Composable @ReadOnlyComposable
        get() = LocalBouncerColorsV2.current

    val typography: BouncerTypographyV2
        @Composable @ReadOnlyComposable
        get() = LocalBouncerTypographyV2.current

    val shapes: BouncerShapesV2
        @Composable @ReadOnlyComposable
        get() = LocalBouncerShapesV2.current

    val spacing: BouncerSpacingV2
        @Composable @ReadOnlyComposable
        get() = LocalBouncerSpacingV2.current

    val elevation: BouncerElevationV2
        @Composable @ReadOnlyComposable
        get() = LocalBouncerElevationV2.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalBouncerColorsV2.current.isDark
}
