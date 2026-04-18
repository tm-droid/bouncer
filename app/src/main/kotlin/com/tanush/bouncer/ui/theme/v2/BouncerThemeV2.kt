package com.tanush.bouncer.ui.theme.v2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            onBackground = colors.onBackground,
            onSurface = colors.onSurface,
            error = colors.error,
            outline = colors.border,
            outlineVariant = colors.divider,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            onBackground = colors.onBackground,
            onSurface = colors.onSurface,
            error = colors.error,
            outline = colors.border,
            outlineVariant = colors.divider,
            scrim = colors.scrim,
        )
    }

    CompositionLocalProvider(
        LocalBouncerColorsV2 provides colors,
        LocalBouncerTypographyV2 provides typography,
        LocalBouncerShapesV2 provides shapes,
        LocalBouncerSpacingV2 provides spacing,
        LocalBouncerElevationV2 provides elevation,
        LocalThemeModeV2 provides themeMode,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography.toMaterial3(),
            shapes = shapes.toMaterial3(),
            content = content,
        )
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
        get() = LocalBouncerColorsV2.current == DarkColors
}
