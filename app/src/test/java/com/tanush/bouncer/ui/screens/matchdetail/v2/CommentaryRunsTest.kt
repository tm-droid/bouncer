package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The runs off a delivery travel as data; the prose is not asked what happened.
 *
 * A single that Cricbuzz calls `", 1 run"` rendered as a dot ball. `bouncer-match-sync` rewrites
 * the line into broadcast prose before storing it — "scampers through for a single" — so the digit
 * is gone, and `event` is four-valued (WICKET|SIX|FOUR|NONE) and could not carry it. The device was
 * re-deriving runs with an unanchored `\b(\d+) runs?\b` over the rewritten line and defaulting to
 * 0, which is a dot. Same failure class as the boundaries `event` was added for, one field later.
 *
 * The `runs` column is now written from the RAW text alongside `event`. These pin both halves: the
 * column wins wherever it exists, and where it does not, an unrecognised line is null — never 0.
 */
class CommentaryRunsTest {

    private fun entry(text: String, runs: Int? = null, event: TimelineEventType? = null) =
        CommentaryEntry(text = text, event = event, ball = "18.5", runs = runs)

    // ── The reported bug ─────────────────────────────────────────────────────

    @Test
    fun `a single with a stored runs value is a 1, not a dot`() {
        val parsed = entry(
            "18.5: Root works it off the hip and scampers through for a single.",
            runs = 1,
            event = TimelineEventType.OTHER,
        ).parseBall()
        assertEquals(1, parsed.runs)
        assertEquals("1", parsed.toBallString())
    }

    @Test
    fun `AI prose with no stored value is unknown, not a dot`() {
        // The pill is not drawn at all for this. Claiming a dot ball is the bug; claiming nothing
        // is honest, and the next row match-sync writes carries the column.
        val parsed = entry(
            "18.5: Root works it off the hip and they steal one to deep square.",
            event = TimelineEventType.OTHER,
        ).parseBall()
        assertNull(parsed.runs)
        assertNull(parsed.toBallString())
    }

    @Test
    fun `a stray total in the prose is not read as the ball's runs`() {
        // The old unanchored regex matched "42 runs" anywhere in the line and put a 42 pill on a
        // dot ball — the same defect, inverted.
        val parsed = entry(
            "18.5: Bumrah nails the yorker and India need 42 runs from 30 balls.",
            event = TimelineEventType.OTHER,
        ).parseBall()
        assertNull(parsed.runs)
    }

    // ── The raw-text fallback (passthrough rows and the on-device scrape) ─────

    @Test
    fun `Cricbuzz's own outcome clause is read when the column is absent`() {
        assertEquals(1, parseBallRuns("Romano Franco to Rhodes, 1 run"))
        assertEquals(3, parseBallRuns("Daniel Douthwaite to James Minto, 3 runs"))
        assertEquals(0, parseBallRuns("Tilly Corteen-Coleman to Ellyse Perry, no run"))
        assertEquals(4, parseBallRuns("Lauren Bell to Fatima Sana, FOUR, to deep extra cover"))
        assertEquals(6, parseBallRuns("Marco Jansen to Tom Banton, SIX, over long-on"))
        assertEquals(0, parseBallRuns("Antony Dhas to Shoaib Md Khan, out Caught by Suresh Kumar!!"))
    }

    @Test
    fun `extras count toward the delivery, with the bare form worth one`() {
        assertEquals(1, parseBallRuns("Starc to Smith, 1 wd, down the leg side"))
        assertEquals(1, parseBallRuns("Starc to Smith, wide, down the leg side"))
        assertEquals(2, parseBallRuns("Starc to Smith, 2 nb, oversteps"))
        assertEquals(1, parseBallRuns("Starc to Smith, 1 lb, off the pad"))
        assertEquals(4, parseBallRuns("Starc to Smith, 4 b, byes to the rope"))
    }

    @Test
    fun `bare-word extras take their runs from the following segment`() {
        // Cricbuzz writes the shorthand with the number attached ("4 lb") and the bare word with
        // the number one segment later ("leg byes, 4 runs"). Reading only the clause scored every
        // one of the latter as 1 — right for the common case, silently wrong for the rest.
        //
        // The two numbers do not mean the same thing: an inline one is the delivery's TOTAL, a
        // following one is the runs SCORED, and a wide's or no-ball's penalty is added to it. See
        // `extraRunsOf` — Cricbuzz's own over summaries are what settle it.
        assertEquals(1, parseBallRuns("Saransh Jain to Sonal Dinusha, leg byes, 1 run, into leg"))
        assertEquals(4, parseBallRuns("Bumrah to Root, leg byes, 4 runs, down to fine leg"))
        assertEquals(4, parseBallRuns("Starc to Smith, byes, 4 runs"))
        // Same delivery, both notations: match 169561's over 5 reads "B1 0 1 0 1 N2 0" for
        // overRuns=6, and its clause is "no ball, 2 runs". The three is the penalty plus the two.
        assertEquals(3, parseBallRuns("Sriya Reddy to Zehra Ender, no ball, 2 runs"))
        assertEquals(2, parseBallRuns("Nathan Edward to Kusal Perera, no ball, 1 run, high full-toss"))
    }

    @Test
    fun `a trailing full stop does not lose the clause`() {
        assertEquals(1, parseBallRuns("Bumrah to Root, 1 run."))
        assertEquals(0, parseBallRuns("Bumrah to Root, no run."))
    }

    @Test
    fun `prose that is not an outcome clause is null, never zero`() {
        assertNull(parseBallRuns("Rain has stopped play."))
        assertNull(parseBallRuns("18.5: Root works it off the hip and scampers through"))
        assertNull(parseBallRuns(""))
    }

    @Test
    fun `no run survives the rewrite and is the one prose signal trusted`() {
        // Wording the model does keep. It can only ever produce a dot; nothing infers a SCORE from
        // prose, which is the inference that caused this bug.
        val parsed = entry(
            "40.4: Ekansh Singh bursts in, and Albert's defensive push yields no run.",
            event = TimelineEventType.OTHER,
        ).parseBall()
        assertEquals(0, parsed.runs)
        assertEquals(".", parsed.toBallString())
    }

    @Test
    fun `a dropped run-out chance is not a dot verdict`() {
        assertNull(
            entry(
                "18.5: A wild throw, and there was no run out to be had at the striker's end.",
                event = TimelineEventType.OTHER,
            ).parseBall().runs,
        )
    }

    // ── The over card ────────────────────────────────────────────────────────

    @Test
    fun `an over of six singles totals six and is not a maiden`() {
        val over = List(6) { entry("18.$it: worked away to mid-on.", runs = 1).parseBall() }
        assertEquals(6, overTotalRuns(over))
        assertTrue("six singles were called a maiden", !isMaidenOver(over))
    }

    @Test
    fun `one unknown ball withholds the over total and the maiden badge`() {
        val over = List(5) { entry("18.$it: no run.", runs = 0).parseBall() } +
            entry("18.6: pushed into the covers.", event = TimelineEventType.OTHER).parseBall()
        assertNull(overTotalRuns(over))
        assertTrue("an over with an unknown ball was called a maiden", !isMaidenOver(over))
    }

    @Test
    fun `a genuine maiden still reads as one`() {
        val over = List(6) { entry("18.$it: no run.", runs = 0).parseBall() }
        assertEquals(0, overTotalRuns(over))
        assertTrue(isMaidenOver(over))
    }

    @Test
    fun `a wide is an extra, so its over is not a maiden even at zero off the bat`() {
        val over = List(5) { entry("18.$it: no run.", runs = 0).parseBall() } +
            entry("18.6: Starc to Smith, 1 wd, down the leg side.", runs = 1).parseBall()
        assertEquals(1, overTotalRuns(over))
        assertTrue(!isMaidenOver(over))
    }
}
