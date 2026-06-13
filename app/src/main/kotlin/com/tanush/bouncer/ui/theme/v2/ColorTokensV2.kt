package com.tanush.bouncer.ui.theme.v2

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Complete light + dark color palettes for Bouncer V2.
 * Primary brand: premium warm gold — prestigious, bold, and distinct.
 */

// ─── Raw palette ──────────────────────────────────────────

// Gold brand — premium, prestigious
val Gold700 = Color(0xFFE0B74F)
val Gold800 = Color(0xFFAF8327)
val Gold300 = Color(0xFFF3DB93)
val GoldContainer = Color(0xFFFDF3C0)
val GoldContainerDark = Color(0xFF34270D)

// Neutrals — light
val White = Color(0xFFFFFFFF)
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray900 = Color(0xFF1A1A1A)

// Neutrals — dark
val DarkBg = Color(0xFF0C1015)
val DarkSurface = Color(0xFF161C26)
val DarkSurfaceVariant = Color(0xFF202834)
val DarkBorder = Color(0xFF2B3442)
val DarkTextPrimary = Color(0xFFFCF7ED)
val DarkTextSecondary = Color(0xFFC1B08D)

// Semantic
val LiveRed = Color(0xFFDC2626)
val LiveRedDark = Color(0xFFEF4444)
val WinGreen = Color(0xFF16A34A)
val WinGreenDark = Color(0xFF22C55E)
val ScheduledGray = Color(0xFF9CA3AF)

// Extras
val DividerLight = Color(0xFFF0F0F0)
val ScrimDark = Color(0x99000000)

/**
 * Semantic colors for cricket events (wicket, boundaries, extras, match lifecycle).
 * Single source of truth — screens must use these instead of hex literals so
 * light/dark variants stay consistent everywhere an event is rendered.
 */
@Immutable
data class CricketEventColors(
    val wicket: Color,
    val four: Color,
    val six: Color,
    val wide: Color,
    val noBall: Color,
    val maiden: Color,
    val milestone: Color,
    val matchStart: Color,
    val matchEnd: Color,
    val dotBall: Color,
    val onEvent: Color,
)

val LightCricketEventColors = CricketEventColors(
    wicket = LiveRed,
    four = WinGreen,
    six = Color(0xFF7C3AED),
    wide = Color(0xFFF59E0B),
    noBall = Color(0xFFF97316),
    maiden = Color(0xFF0D9488),
    milestone = Gold800,
    matchStart = Color(0xFF3B82F6),
    matchEnd = Color(0xFF94A3B8),
    dotBall = Gray400,
    onEvent = White,
)

val DarkCricketEventColors = CricketEventColors(
    wicket = LiveRedDark,
    four = WinGreenDark,
    six = Color(0xFF8B5CF6),
    wide = Color(0xFFFBBF24),
    noBall = Color(0xFFFB923C),
    maiden = Color(0xFF2DD4BF),
    milestone = Gold300,
    matchStart = Color(0xFF60A5FA),
    matchEnd = Color(0xFF64748B),
    dotBall = Gray500,
    onEvent = White,
)

@Immutable
data class BouncerColorsV2(
    // Brand
    val primary: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val onPrimary: Color,

    // Backgrounds & surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,

    // Text
    val onBackground: Color,
    val onSurface: Color,
    val textSecondary: Color,

    // Borders & dividers
    val border: Color,
    val divider: Color,

    // Semantic
    val live: Color,
    val win: Color,
    val scheduled: Color,
    val error: Color,

    // Top bar
    val topBar: Color,
    val onTopBar: Color,

    // Scrim (overlay dimmer)
    val scrim: Color,

    // Misc
    val shimmer: Color,
    val shimmerHighlight: Color,

    // Cricket event semantics
    val cricket: CricketEventColors,
)

val LightColors = BouncerColorsV2(
    primary = Gold700,
    primaryDark = Gold800,
    primaryContainer = GoldContainer,
    onPrimary = DarkBg,

    background = Gray50,
    surface = White,
    surfaceVariant = Gray100,

    onBackground = Gray900,
    onSurface = Gray900,
    textSecondary = Gray500,

    border = Gray200,
    divider = DividerLight,

    live = LiveRed,
    win = WinGreen,
    scheduled = ScheduledGray,
    error = LiveRed,

    topBar = Gold800,
    onTopBar = White,

    scrim = ScrimDark,

    shimmer = Gray200,
    shimmerHighlight = Gray100,

    cricket = LightCricketEventColors,
)

val DarkColors = BouncerColorsV2(
    primary = Gold300,
    primaryDark = Gold700,
    primaryContainer = GoldContainerDark,
    onPrimary = Gray900,

    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,

    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    textSecondary = DarkTextSecondary,

    border = DarkBorder,
    divider = DarkSurfaceVariant,

    live = LiveRedDark,
    win = WinGreenDark,
    scheduled = ScheduledGray,
    error = LiveRedDark,

    topBar = Color(0xFF131923),
    onTopBar = DarkTextPrimary,

    scrim = ScrimDark,

    shimmer = DarkSurfaceVariant,
    shimmerHighlight = DarkBorder,

    cricket = DarkCricketEventColors,
)

val LocalBouncerColorsV2 = staticCompositionLocalOf { LightColors }
