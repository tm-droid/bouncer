package com.tanush.bouncer.ui.theme.v2

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Complete light + dark color palettes for Bouncer V2.
 * Accent: gold, for things you can tap and for the wordmark. Data — scores, names, statuses — is
 * always neutral ink.
 *
 * Contrast is a hard requirement, not a preference: every foreground/background pair defined here
 * is asserted against WCAG in `LightModeContrastTest`. The light palette used to fail badly —
 * `primary` (#E0B74F) measures 1.74:1 against white, so gold text and icons were invisible — which
 * is why [BouncerColorsV2.primaryText] exists as a separate role.
 */

// ─── Raw palette ──────────────────────────────────────────

// Gold accent
val Gold700 = Color(0xFFE0B74F)
val Gold800 = Color(0xFFAF8327)
val Gold300 = Color(0xFFF3DB93)
val GoldContainer = Color(0xFFFDF3C0)
val GoldContainerDark = Color(0xFF34270D)

/**
 * Foreground-safe gold. 5.34:1 on [White] and 5.12:1 on [Gray50] — AA for body text.
 *
 * [Gold700] is a *fill* colour: it pairs with a dark ink at 10.6:1 and is correct behind text, but
 * as ink itself on a light surface it is unreadable. Light mode uses this wherever gold is the ink.
 */
val GoldText = Color(0xFF8A6516)

// Neutrals — light
val White = Color(0xFFFFFFFF)
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray600 = Color(0xFF4B5563)
val Gray900 = Color(0xFF1A1A1A)

// Neutrals — dark
val DarkBg = Color(0xFF0C1015)
val DarkSurface = Color(0xFF161C26)
val DarkSurfaceVariant = Color(0xFF202834)
val DarkBorder = Color(0xFF2B3442)
// Neutral greys. These were a cream #FCF7ED and a tan #C1B08D, which tinted every line of text in
// dark mode toward the gold accent — a warm-cream palette reads as a default, not a choice, and it
// left gold with nothing to stand apart from.
val DarkTextPrimary = Color(0xFFECEEF1)
val DarkTextSecondary = Color(0xFFA0A7B2)

// Semantic
val LiveRed = Color(0xFFDC2626)
val LiveRedDark = Color(0xFFEF4444)
val WinGreen = Color(0xFF16A34A)
val WinGreenDark = Color(0xFF22C55E)
val WinGreenText = Color(0xFF15803D)
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

/**
 * Light-mode event colours. These are drawn as filled pills with [onEvent] text on top, so each is
 * chosen for contrast against white text rather than against the page — the mid-tone Tailwind-600
 * step rather than the -500 step the dark palette can afford.
 */
val LightCricketEventColors = CricketEventColors(
    wicket = LiveRed,
    four = WinGreenText,
    six = Color(0xFF6D28D9),
    wide = Color(0xFFB45309),
    noBall = Color(0xFFC2410C),
    maiden = Color(0xFF0F766E),
    milestone = GoldText,
    matchStart = Color(0xFF1D4ED8),
    matchEnd = Color(0xFF64748B),
    dotBall = Gray500,
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
    /** True for [DarkColors]. Read it instead of comparing palette identity — a `.copy()` breaks that. */
    val isDark: Boolean,

    // Brand
    val primary: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val onPrimary: Color,

    /**
     * Gold as *ink* — accent text, icons, hairlines. Distinct from [primary], which is a fill.
     * On light surfaces the two must differ or the accent is unreadable.
     */
    val primaryText: Color,

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
    /** Title ink on [topBar]. */
    val onTopBar: Color,
    /** Secondary ink on [topBar] — navigation and action icons. */
    val onTopBarMuted: Color,

    // Scrim (overlay dimmer)
    val scrim: Color,

    /**
     * Opaque base for translucent "lift" overlays — glass highlights, hover states, inner strokes.
     * Callers apply their own alpha. White lifts a dark surface; on a light surface only a dark
     * wash reads, so this inverts with the theme instead of being a hardcoded `Color.White`.
     */
    val highlight: Color,

    // Misc
    val shimmer: Color,
    val shimmerHighlight: Color,

    // Cricket event semantics
    val cricket: CricketEventColors,
)

val LightColors = BouncerColorsV2(
    isDark = false,

    primary = Gold700,
    primaryDark = Gold800,
    primaryContainer = GoldContainer,
    onPrimary = DarkBg,
    primaryText = GoldText,

    background = Gray50,
    surface = White,
    surfaceVariant = Gray100,

    onBackground = Gray900,
    onSurface = Gray900,
    textSecondary = Gray600,

    // Was Gray200/DividerLight. A #F0F0F0 divider on a #FAFAFA page is 1.09:1 — it did not render.
    border = Gray400,
    divider = Gray300,

    live = LiveRed,
    win = WinGreenText,
    scheduled = ScheduledGray,
    error = LiveRed,

    // The bar is the page, not a brand slab. It was GoldText (#8A6516), which painted a mustard band
    // across the top of every screen; the wordmark now carries the gold on its own.
    topBar = White,
    onTopBar = GoldText,
    onTopBarMuted = Gray600,

    scrim = ScrimDark,

    highlight = Gray900,

    shimmer = Gray200,
    shimmerHighlight = Gray100,

    cricket = LightCricketEventColors,
)

val DarkColors = BouncerColorsV2(
    isDark = true,

    primary = Gold300,
    primaryDark = Gold700,
    primaryContainer = GoldContainerDark,
    onPrimary = Gray900,
    primaryText = Gold300,

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

    // Same as the background: a separately tinted bar made one more band above the content.
    topBar = DarkBg,
    // Gold title on near-black is the shipped dark bar; onTopBar carries it so the component does
    // not have to branch on mode.
    onTopBar = Gold300,
    onTopBarMuted = DarkTextSecondary,

    scrim = ScrimDark,

    highlight = White,

    shimmer = DarkSurfaceVariant,
    shimmerHighlight = DarkBorder,

    cricket = DarkCricketEventColors,
)

val LocalBouncerColorsV2 = staticCompositionLocalOf { LightColors }
