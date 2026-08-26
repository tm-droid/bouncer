package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What KIND of delivery it was travels as data too, and an extra is shown for what it was worth.
 *
 * Sibling of [CommentaryRunsTest]. That one fixed the number; this one fixes the two things the
 * number alone could not. `isWide` was still decided by searching the REWRITTEN prose for the word
 * "wide" — the same guess-from-prose failure, one field over — and every wide rendered a flat `WD`,
 * so a `5 wides` and a `wide` were indistinguishable.
 *
 * The glyphs and the arithmetic are Cricbuzz's own, taken from its `overSummary` tokens, which
 * `overRuns` lets us check: `1 Wd3 0 0 4 W W` totals 8, so `Wd<n>` already includes the wide's
 * penalty; `6 Wd 6 6 1 4 N1 1` totals 27, so `N<n>` does NOT — a no-ball is 1 plus what came off
 * the bat.
 */
class CommentaryExtrasTest {

    private fun entry(
        text: String,
        runs: Int? = null,
        extra: String? = null,
        event: TimelineEventType? = TimelineEventType.OTHER,
    ) = CommentaryEntry(text = text, event = event, ball = "18.5", runs = runs, extra = extra)

    // ── The kind comes from the column, not the prose ────────────────────────

    @Test
    fun `a wide is a wide even when the rewrite never says so`() {
        val parsed = entry(
            "18.5: Hazlewood strays down the leg side and the umpire's arms go out.",
            runs = 1,
            extra = "WIDE",
        ).parseBall()
        assertTrue(parsed.isWide)
        assertEquals("Wd", parsed.toBallString())
    }

    @Test
    fun `the column beats the prose when they disagree`() {
        // "wide" appears in the description of a delivery the server classified as a bye. Reading
        // the text would call this a wide and charge it to the bowler.
        val parsed = entry(
            "18.5: beaten wide of the keeper, the batters steal one.",
            runs = 1,
            extra = "BYE",
        ).parseBall()
        assertFalse(parsed.isWide)
        assertEquals("B1", parsed.toBallString())
    }

    @Test
    fun `a legacy row with no column still reads the prose`() {
        val parsed = entry("Starc to Smith, wide, down the leg side", runs = null, extra = null)
            .parseBall()
        assertTrue(parsed.isWide)
        assertEquals(1, parsed.runs)
    }

    // ── Extras are worth what they are worth ─────────────────────────────────

    @Test
    fun `a wide that ran away for more shows the total`() {
        val parsed = entry("Moqim to Rahman, 3 wides, spinning down leg", extra = "WIDE").parseBall()
        assertEquals(3, parsed.runs)
        assertEquals("Wd3", parsed.toBallString())
    }

    @Test
    fun `a boundary wide is five, not one`() {
        val parsed = entry("Shah to Pooran, 5 wides, full-toss down leg", extra = "WIDE").parseBall()
        assertEquals(5, parsed.runs)
        assertEquals("Wd5", parsed.toBallString())
    }

    @Test
    fun `a no-ball adds its penalty to the runs off the bat`() {
        // Cricbuzz writes "no ball, 1 run" and scores the over `N1` — which is two.
        val parsed = entry("Edward to Perera, no ball, 1 run, high full-toss").parseBall()
        assertEquals(2, parsed.runs)
        assertEquals("N1", parsed.toBallString())
    }

    @Test
    fun `a no-ball with nothing off the bat is worth one`() {
        val parsed = entry("Edward to Perera, no ball").parseBall()
        assertEquals(1, parsed.runs)
        assertEquals("Nb", parsed.toBallString())
    }

    @Test
    fun `boundary byes are written FOUR, not a numeral`() {
        // The numeric-only follow-segment regex scored this 1. Cricbuzz scores the over `B4`.
        val parsed = entry("Simarjeet to Chillar, byes, FOUR, through to the rope").parseBall()
        assertEquals(4, parsed.runs)
        assertEquals("B4", parsed.toBallString())
    }

    @Test
    fun `leg byes carry their runs one segment later`() {
        val parsed = entry("Mushtaq to Deshpande, leg byes, 1 run").parseBall()
        assertEquals(1, parsed.runs)
        assertEquals("L1", parsed.toBallString())
    }

    // ── The wicket wording Cricbuzz flashes first ────────────────────────────

    @Test
    fun `the THATS OUT flash node is a recognised dismissal`() {
        // Cricbuzz pushes this short form before the detailed "out Caught by …" line. It was 11 of
        // the 12 unreadable clauses across 8 live matches.
        assertEquals(0, parseBallRuns("Moqim to Simmonds, THATS OUT!! Bowled!!"))
        assertEquals(0, parseBallRuns("Lyon to Shoriful Islam, THATS OUT!! Caught!!"))
        assertEquals(0, parseBallRuns("Sindhu to Patel, THATS OUT!! Lbw!!"))
    }

    // ── A maiden is about the bowler, not the over's total ───────────────────

    private fun over(vararg texts: String) = texts.map { entry(it).parseBall() }

    @Test
    fun `six dots is a maiden`() {
        val balls = over(*Array(6) { "A to B, no run" })
        assertEquals(0, overTotalRuns(balls))
        assertTrue(isMaidenOver(balls))
    }

    @Test
    fun `an over conceding only leg byes is still a maiden`() {
        // Leg byes are not charged to the bowler, so `0 0 0 0 0 L1` scores 1 and is still a maiden.
        // Summing the total and testing it against zero denied a bowler a maiden they had bowled.
        val balls = over(
            "A to B, no run", "A to B, no run", "A to B, no run",
            "A to B, no run", "A to B, no run", "A to B, leg byes, 1 run",
        )
        assertEquals(1, overTotalRuns(balls))
        assertTrue(isMaidenOver(balls))
    }

    @Test
    fun `an over containing a wide is never a maiden`() {
        val balls = over(
            "A to B, no run", "A to B, no run", "A to B, no run",
            "A to B, no run", "A to B, no run", "A to B, no run", "A to B, wide",
        )
        assertFalse(isMaidenOver(balls))
    }

    @Test
    fun `a scoring over is not a maiden and totals its extras`() {
        val balls = over(
            "A to B, 1 run", "A to B, no run", "A to B, 3 wides",
            "A to B, no run", "A to B, FOUR", "A to B, no run", "A to B, no run",
        )
        assertEquals(8, overTotalRuns(balls))
        assertFalse(isMaidenOver(balls))
    }
}
