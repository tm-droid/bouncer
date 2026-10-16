package com.tanush.bouncer.ui.theme

import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.components.v2.ballChipInk
import com.tanush.bouncer.ui.theme.v2.BouncerColorsV2
import com.tanush.bouncer.ui.theme.v2.DarkColors
import com.tanush.bouncer.ui.theme.v2.LightColors
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG floors for both palettes.
 *
 * The light palette shipped with `primary` = #E0B74F against a white surface — 1.74:1, so every
 * gold label and icon was invisible — and a #F0F0F0 divider on a #FAFAFA page at 1.09:1, which
 * simply did not render. Neither is the kind of defect a reviewer catches by eye in a diff, and
 * neither shows up in dark mode, so the palette needs an executable floor rather than a convention.
 *
 * Thresholds are the WCAG ones for the roles they actually describe:
 *  - [AA] 4.5:1 for anything rendered as a glyph.
 *  - [AA_LARGE] 3.0:1 for headline-sized text and for icons that carry meaning.
 *  - Dividers and hairlines get a lower, non-WCAG floor: 1.4:1 is not an accessibility guarantee,
 *    it is the line between "subtle" and "absent". WCAG 1.4.11 does not cover decorative rules, and
 *    holding them to 3:1 would mean drawing borders Material itself does not draw.
 */
class LightModeContrastTest {

    private companion object {
        const val AA = 4.5f
        const val AA_LARGE = 3.0f
        const val VISIBLE_HAIRLINE = 1.4f
    }

    private fun assertContrast(fg: Color, bg: Color, min: Float, what: String) {
        val ratio = fg.contrastRatio(bg)
        assertTrue(
            "$what: ${ratio.format()}:1, needs ${min}:1 " +
                "(fg=${fg.hex()} bg=${bg.hex()})",
            ratio >= min,
        )
    }

    private fun Float.format() = String.format(Locale.US, "%.2f", this)

    private fun Color.hex() = String.format(Locale.US, "#%08X", value.shr(32).toInt())

    /** Text pairs must clear AA in either palette — this is the class of bug being pinned. */
    private fun assertTextPairs(colors: BouncerColorsV2, mode: String) {
        assertContrast(colors.onSurface, colors.surface, AA, "$mode onSurface on surface")
        assertContrast(colors.onBackground, colors.background, AA, "$mode onBackground on background")
        assertContrast(colors.textSecondary, colors.surface, AA, "$mode textSecondary on surface")
        assertContrast(colors.textSecondary, colors.background, AA, "$mode textSecondary on background")
        assertContrast(colors.primaryText, colors.surface, AA, "$mode primaryText on surface")
        assertContrast(colors.primaryText, colors.background, AA, "$mode primaryText on background")
        assertContrast(colors.onPrimary, colors.primary, AA, "$mode onPrimary on primary")
        assertContrast(colors.onTopBar, colors.topBar, AA, "$mode onTopBar on topBar")
        assertContrast(colors.onTopBarMuted, colors.topBar, AA_LARGE, "$mode onTopBarMuted on topBar")
    }

    /** Live/win/error are drawn as labels and dots on the page, so they are checked against it. */
    private fun assertSemanticPairs(colors: BouncerColorsV2, mode: String) {
        assertContrast(colors.live, colors.surface, AA_LARGE, "$mode live on surface")
        assertContrast(colors.win, colors.surface, AA_LARGE, "$mode win on surface")
        assertContrast(colors.error, colors.surface, AA_LARGE, "$mode error on surface")
    }

    /**
     * Event colours are read as ink far more often than as fills — the commentary tab, the ball
     * strip and the notification list all draw them as a glyph or a dot on the page.
     */
    private fun assertEventInk(colors: BouncerColorsV2, mode: String, min: Float) {
        val cricket = colors.cricket
        listOf(
            "wicket" to cricket.wicket,
            "four" to cricket.four,
            "six" to cricket.six,
            "wide" to cricket.wide,
            "noBall" to cricket.noBall,
            "maiden" to cricket.maiden,
            "milestone" to cricket.milestone,
            "matchStart" to cricket.matchStart,
            "matchEnd" to cricket.matchEnd,
            "dotBall" to cricket.dotBall,
        ).forEach { (name, ink) ->
            assertContrast(ink, colors.surface, min, "$mode $name on surface")
        }
    }

    @Test
    fun `light text pairs meet AA`() = assertTextPairs(LightColors, "light")

    @Test
    fun `dark text pairs meet AA`() = assertTextPairs(DarkColors, "dark")

    @Test
    fun `light semantic colours are legible on the page`() = assertSemanticPairs(LightColors, "light")

    @Test
    fun `dark semantic colours are legible on the page`() = assertSemanticPairs(DarkColors, "dark")

    @Test
    fun `light event colours are legible as ink`() = assertEventInk(LightColors, "light", AA)

    /**
     * Dark is held to [AA_LARGE] rather than AA. These are the shipped values and they read
     * correctly on a dark ground; the weakest is `dotBall` at ~3.5:1, which is a deliberate mute.
     * Raising the floor here would restyle a mode nobody reported a problem with — the test's job
     * is to stop it getting worse.
     */
    @Test
    fun `dark event colours are legible as ink`() = assertEventInk(DarkColors, "dark", AA_LARGE)

    /**
     * `onEvent` is the label written over a filled ball pill (see `components/v2/RecentBallsStrip`).
     * Only light is asserted, deliberately: the dark palette pairs white with fills chosen to be
     * bright on a dark ground, and white over the composited dark `four` pill measures 2.99:1. No
     * single ink clears AA across the dark set — #1A1A1A would fix `four` and break `six` — so that
     * is a palette decision to make on its own, not a threshold to quietly lower here.
     */
    @Test
    fun `light event pills carry their label`() {
        val cricket = LightColors.cricket
        listOf(
            "wicket" to cricket.wicket,
            "four" to cricket.four,
            "six" to cricket.six,
            "wide" to cricket.wide,
            "noBall" to cricket.noBall,
            "maiden" to cricket.maiden,
            "matchStart" to cricket.matchStart,
            "matchEnd" to cricket.matchEnd,
            "dotBall" to cricket.dotBall,
        ).forEach { (name, fill) ->
            assertContrast(cricket.onEvent, fill, AA, "light onEvent on $name pill")
        }
    }

    /**
     * Light only. The dark palette's divider sits at ~1.13:1 against its surface and has always
     * looked right there — a hairline needs far less separation on a dark ground than on a white
     * one. Holding both to the same number would either fail shipped dark mode or set the light
     * floor below the 1.09:1 that caused this bug.
     */
    @Test
    fun `light dividers and borders are visible`() {
        assertContrast(LightColors.divider, LightColors.surface, VISIBLE_HAIRLINE, "light divider on surface")
        assertContrast(LightColors.border, LightColors.surface, VISIBLE_HAIRLINE, "light border on surface")
        assertContrast(LightColors.border, LightColors.background, VISIBLE_HAIRLINE, "light border on background")
    }

    /**
     * `primary` is a fill and `primaryText` is ink. On a light surface they cannot be the same
     * value: the fill gold is deliberately bright enough to be unreadable as a glyph, which is the
     * whole reason the second role exists.
     */
    @Test
    fun `light separates the fill gold from the ink gold`() {
        assertTrue(
            "LightColors.primary and primaryText must differ — the fill gold is not readable as text",
            LightColors.primary != LightColors.primaryText,
        )
        assertTrue(
            "LightColors.primary is expected to fail AA as ink; if it now passes, collapse the roles",
            LightColors.primary.contrastRatio(LightColors.surface) < AA,
        )
    }

    /**
     * The legacy layer's two dimmest text roles. Both were treated as decoration and drawn as
     * text: the scorecard's balls, fours and sixes, and every caption on the analytics cards.
     */
    @Test
    fun `legacy tertiary and muted text is legible on the surfaces it is drawn on`() {
        for ((mode, legacy) in listOf("dark" to legacyColorsFor(true), "light" to legacyColorsFor(false))) {
            for ((role, ink) in listOf("textTertiary" to legacy.textTertiary, "textMuted" to legacy.textMuted)) {
                assertContrast(ink, legacy.backgroundBase, AA, "$mode legacy $role on backgroundBase")
                assertContrast(ink, legacy.surface1, AA, "$mode legacy $role on surface1")
            }
        }
        // Dark cards also sit on surface2. Light's surface2 is a wash, never a text ground.
        val dark = legacyColorsFor(true)
        assertContrast(dark.textTertiary, dark.surface2, AA, "dark legacy textTertiary on surface2")
        assertContrast(dark.textMuted, dark.surface2, AA, "dark legacy textMuted on surface2")
    }

    /** What the over strip draws: a solid event fill and the ink [ballChipInk] picks for it. */
    @Test
    fun `ball chips carry their glyph in both palettes`() {
        for ((mode, palette) in listOf("light" to LightColors, "dark" to DarkColors)) {
            val cricket = palette.cricket
            listOf("wicket" to cricket.wicket, "four" to cricket.four, "six" to cricket.six, "wide" to cricket.wide)
                .forEach { (name, fill) ->
                    assertContrast(ballChipInk(fill), fill, AA, "$mode ball chip ink on $name")
                }
        }
    }

    @Test
    fun `palettes declare their own mode`() {
        assertTrue("LightColors.isDark must be false", !LightColors.isDark)
        assertTrue("DarkColors.isDark must be true", DarkColors.isDark)
    }

    /**
     * `hierarchy.secondary.primary` is read as text in ~110 places across match detail. The theme
     * never provided the hierarchy, so light mode drew the dark default's #AF8327 on white.
     */
    @Test
    fun `the color hierarchy's ink clears AA on light surfaces`() {
        val h = colorHierarchyFor(LightColors, isDark = false)
        assertContrast(h.secondary.primary, LightColors.surface, AA, "light hierarchy.secondary.primary on surface")
        assertContrast(h.secondary.primary, LightColors.background, AA, "light hierarchy.secondary.primary on background")
        assertContrast(h.dominant.textPrimary, h.dominant.surface, AA, "light hierarchy textPrimary on surface")
        assertContrast(h.dominant.textSecondary, h.dominant.surface, AA, "light hierarchy textSecondary on surface")
    }

    @Test
    fun `dark mode keeps the hierarchy it always had`() {
        assertTrue(colorHierarchyFor(DarkColors, isDark = true) === colorHierarchyFor(LightColors, isDark = true))
    }
}
