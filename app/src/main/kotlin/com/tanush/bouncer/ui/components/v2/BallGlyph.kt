package com.tanush.bouncer.ui.components.v2

/**
 * What an over-summary glyph is, in one place.
 *
 * The strings are Cricbuzz's own over-summary notation — `0 1 4 6 W W1 Wd Wd3 Nb N1 B1 L1` — and
 * two renderers draw them: the commentary tab's `BallBadge` and [RecentBallsStripV2]. They used to
 * classify the same strings with two separate `when` blocks that disagreed: one matched `"W"`
 * exactly, so `W1` (a run-out that completed a run) rendered as an ordinary chip on one surface and
 * as a wicket on the other. Classification lives here; each renderer keeps its own treatment,
 * because a filled circle in the score header and a tinted chip in the feed are deliberate.
 */
internal enum class BallGlyphKind { WICKET, SIX, FOUR, EXTRA, DOT, RUNS }

// `W`, and the `W1`/`W2` a run-out that completed runs is written as.
private val WICKET_GLYPH = Regex("^W\\d?$")

// Every extras glyph the over summary writes. The runs travel with the letter — `Wd3` is a wide
// worth three — so the forms with a number are not optional.
private val EXTRAS_GLYPH = Regex("^(?:WD\\d{0,2}|NB|N\\d{1,2}|LB|L\\d{1,2}|B\\d{0,2})$")

// "0" and "." are both written for a dot; "–" is the strip's placeholder for a delivery
// nothing could name, and is quiet for the same reason a dot is.
private val DOT_GLYPHS = setOf("0", ".", "–")

internal fun ballGlyphKindOf(glyph: String): BallGlyphKind {
    val g = glyph.trim().uppercase()
    return when {
        WICKET_GLYPH.matches(g) -> BallGlyphKind.WICKET
        g == "6" -> BallGlyphKind.SIX
        g == "4" -> BallGlyphKind.FOUR
        EXTRAS_GLYPH.matches(g) -> BallGlyphKind.EXTRA
        g in DOT_GLYPHS -> BallGlyphKind.DOT
        else -> BallGlyphKind.RUNS
    }
}
