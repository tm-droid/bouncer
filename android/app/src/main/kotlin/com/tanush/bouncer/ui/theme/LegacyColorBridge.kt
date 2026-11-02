@file:Suppress("DEPRECATION")

package com.tanush.bouncer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.v2.Gold800
import com.tanush.bouncer.ui.theme.v2.GoldContainer
import com.tanush.bouncer.ui.theme.v2.GoldText
import com.tanush.bouncer.ui.theme.v2.Gray100
import com.tanush.bouncer.ui.theme.v2.Gray200
import com.tanush.bouncer.ui.theme.v2.Gray400
import com.tanush.bouncer.ui.theme.v2.Gray500
import com.tanush.bouncer.ui.theme.v2.Gray50
import com.tanush.bouncer.ui.theme.v2.Gray600
import com.tanush.bouncer.ui.theme.v2.Gray900
import com.tanush.bouncer.ui.theme.v2.White
import com.tanush.bouncer.ui.theme.v2.DarkTextSecondary

/**
 * Light/dark instances of the legacy [BouncerColors] palette, so `BouncerDesignSystem.colors`
 * follows the user's theme.
 *
 * [LocalBouncerColors] is read — directly or through `BouncerDesignSystem.colors` — by ~46
 * component files under the `ui/components` tree, but nothing ever provided it: its only provider was the
 * legacy `BouncerTheme`, which the app does not call and which is hard-forced dark anyway. Every one
 * of those reads therefore returned the static dark default, so in light mode those components drew
 * charcoal surfaces and near-white text onto a white page. `BouncerThemeV2` now provides
 * [legacyColorsFor].
 *
 * [LegacyDarkColors] is `BouncerColors()` — the constructor defaults *are* the dark palette — with
 * two text roles raised to a legible contrast. The light instance is new throughout.
 *
 * This is a bridge, not a destination: it buys light mode for the unmigrated component layer without
 * a 260-call-site rewrite. New code uses `BouncerThemeV2.colors`.
 */
internal val LegacyDarkTextMuted = Color(0xFF8A919C)

internal val LegacyDarkColors: BouncerColors = BouncerColors(
    // The two dimmest text roles are the exception to "the defaults are the dark palette". Their
    // defaults (#8D7E61, #665844) are 4.3:1 and 2.5:1 on a card, and both are drawn as text: the
    // scorecard's balls, fours and sixes, the analytics cards' captions and axis labels. They are
    // neutral here, like every other dark text grey, and clear 4.5:1 on surface1 and surface2.
    textTertiary = DarkTextSecondary,
    textMuted = LegacyDarkTextMuted,
)

internal val LegacyLightColors: BouncerColors = BouncerColors(
    backgroundBase = Gray50,
    backgroundElevated = White,
    surface1 = White,
    surface2 = Gray100,
    surface3 = Gray200,

    // Gold as ink, not as fill: #E0B74F is 1.74:1 on white. See v2.GoldText.
    goldPrimary = GoldText,
    goldDark = Color(0xFF6B4E11),
    goldLight = Gold800,
    goldSubtle = GoldContainer,
    etchedGold = GoldText,
    goldGradient = Brush.verticalGradient(
        0.0f to Gold800,
        0.55f to GoldText,
        1.0f to Color(0xFF6B4E11),
    ),
    goldGradientHorizontal = Brush.horizontalGradient(
        colors = listOf(Gold800, GoldText, Color(0xFF6B4E11)),
    ),

    textPrimary = Gray900,
    textSecondary = Gray600,
    textTertiary = Gray500,
    // Drawn as text across the scorecard, so it is held to the text floor: Gray400 was 2.5:1.
    textMuted = Gray500,

    statusLive = Color(0xFFDC2626),
    statusSuccess = Color(0xFF15803D),
    statusWarning = Color(0xFFB45309),
    statusInfo = Color(0xFF1D4ED8),

    // The dark palette lifts a glass card with translucent gold; on white that is invisible, so the
    // light one settles it with a neutral wash instead.
    glassBackground = Gray900.copy(alpha = 0.04f),
    glassBackgroundHover = Gray900.copy(alpha = 0.07f),
    glassBackgroundPressed = GoldText.copy(alpha = 0.12f),
    glassBorder = Gray900.copy(alpha = 0.14f),
    glassBorderHighlight = GoldText.copy(alpha = 0.32f),
    // Overlay and scrim dim content in either mode — deliberately unchanged.
    glassOverlay = GlassOverlay,
    glassScrim = GlassScrim,
    glassSurface = Gray900.copy(alpha = 0.05f),

    ballDot = Gray500,
    ballRun = Gray600,
    ballBoundary = Color(0xFF15803D),
    ballSix = GoldText,
    ballWicket = Color(0xFFDC2626),
    ballWide = Color(0xFFB45309),
    ballNoBall = Color(0xFFC2410C),
    ballLegBye = Color(0xFF6D28D9),
    ballBye = Color(0xFF0E7490),

    probabilityHigh = Color(0xFF15803D),
    probabilityMedium = Color(0xFFB45309),
    probabilityLow = Color(0xFFDC2626),
    probabilityNeutral = Gray600,
)

internal fun legacyColorsFor(isDark: Boolean): BouncerColors =
    if (isDark) LegacyDarkColors else LegacyLightColors
