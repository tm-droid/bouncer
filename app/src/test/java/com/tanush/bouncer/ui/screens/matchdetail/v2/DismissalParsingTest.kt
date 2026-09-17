package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.ui.components.DismissalType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dismissal strings as scorecards actually write them.
 *
 * `parseCaught` cut the fielder with `substring(2, bIdx)`, which assumes "c " occupies exactly
 * index 0..1. A caught-and-bowled written "c b Bumrah" puts " b " at index 1, so the range ran
 * backwards and threw inside the Overview tab's layout pass (ANDROID-4S) — a crash, not a bad
 * label. The rules also match a lowercased, TRIMMED copy while the parser receives the original,
 * so the "c" was never guaranteed to be at index 0 either.
 */
class DismissalParsingTest {

    @Test
    fun `a caught dismissal names the bowler`() {
        assertEquals(DismissalType.CAUGHT to "Bumrah", parseDismissal("c Kohli b Bumrah"))
    }

    @Test
    fun `a keeper catch is caught behind`() {
        assertEquals(DismissalType.CAUGHT_BEHIND to "Bumrah", parseDismissal("c †Pant b Bumrah"))
    }

    @Test
    fun `a missing fielder does not crash`() {
        assertEquals(DismissalType.CAUGHT to "Bumrah", parseDismissal("c b Bumrah"))
    }

    @Test
    fun `leading whitespace does not shift the fielder`() {
        assertEquals(DismissalType.CAUGHT_BEHIND to "Bumrah", parseDismissal("  c †Pant b Bumrah"))
    }

    @Test
    fun `caught and bowled keeps its own type`() {
        assertEquals(DismissalType.CAUGHT_AND_BOWLED to "Bumrah", parseDismissal("c & b Bumrah"))
    }

    @Test
    fun `a caught and bowled keeps the bowler's whole name`() {
        // [Cc][\s&bB]+ was greedy over the B as well, so every bowler whose name starts with one
        // lost its first letter: Bumrah, Boult, Broad, Bairstow.
        assertEquals(DismissalType.CAUGHT_AND_BOWLED to "Boult", parseDismissal("c&b Boult"))
    }

    @Test
    fun `a bowled dismissal names the bowler`() {
        assertEquals(DismissalType.BOWLED to "Bumrah", parseDismissal("b Bumrah"))
    }

    @Test
    fun `an lbw is not a catch`() {
        assertEquals(DismissalType.LBW to "Bumrah", parseDismissal("lbw b Bumrah"))
    }

    @Test
    fun `a not out batter has no dismissal`() {
        assertEquals(DismissalType.BOWLED to "", parseDismissal("not out"))
    }
}
