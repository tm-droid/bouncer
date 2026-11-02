package com.tanush.bouncer.ui.components.v2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One classifier for the over-summary glyphs, because two of them disagreed.
 *
 * The commentary tab's chip and the score header's pill each carried their own `when` over the same
 * strings. The tab's matched `"W"` exactly, so `W1` — how Cricbuzz writes a run-out that completed a
 * run — rendered as an ordinary chip there and as a wicket in the header, on the same delivery.
 */
class BallGlyphTest {

    @Test
    fun `a run-out that completed runs is still a wicket`() {
        assertEquals(BallGlyphKind.WICKET, ballGlyphKindOf("W"))
        assertEquals(BallGlyphKind.WICKET, ballGlyphKindOf("W1"))
        assertEquals(BallGlyphKind.WICKET, ballGlyphKindOf("W2"))
    }

    @Test
    fun `a wide is an extra whatever it cost`() {
        listOf("Wd", "Wd3", "Nb", "N1", "B1", "B4", "L1", "LB", "B").forEach {
            assertEquals(it, BallGlyphKind.EXTRA, ballGlyphKindOf(it))
        }
    }

    @Test
    fun `boundaries off the bat are boundaries`() {
        assertEquals(BallGlyphKind.FOUR, ballGlyphKindOf("4"))
        assertEquals(BallGlyphKind.SIX, ballGlyphKindOf("6"))
    }

    @Test
    fun `a four off byes is an extra, not a boundary`() {
        assertEquals(BallGlyphKind.EXTRA, ballGlyphKindOf("B4"))
    }

    @Test
    fun `both spellings of a dot, and the unknown-ball placeholder, are quiet`() {
        assertEquals(BallGlyphKind.DOT, ballGlyphKindOf("0"))
        assertEquals(BallGlyphKind.DOT, ballGlyphKindOf("."))
        assertEquals(BallGlyphKind.DOT, ballGlyphKindOf("–"))
    }

    @Test
    fun `runs off the bat are runs`() {
        assertEquals(BallGlyphKind.RUNS, ballGlyphKindOf("1"))
        assertEquals(BallGlyphKind.RUNS, ballGlyphKindOf("3"))
    }
}
