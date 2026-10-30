package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.utils.TimelineEventExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The commentary tab's filter chips, over the shapes `match-commentary` actually stores.
 *
 * Written after the Boundaries chip was found to return an empty list on any match where AI
 * generation was working. It classified by running `\bFOUR\b` / `\bSIX\b` over the stored line —
 * but `bouncer-match-sync` rewrites every line into broadcast prose before storing it, and the
 * prompt asks for "sharp active verbs" and "no clichés", so the tokens are gone. The only rows
 * still carrying them were the ones where generation had FAILED and raw Cricbuzz text was written
 * through, which is why the chip appeared to work exactly when the pipeline was broken.
 *
 * The fixtures below are real rows read from the table on 2026-08-05, plus the prose forms a
 * boundary takes once rewritten. [`prose is genuinely unclassifiable from its text alone`] is the
 * guard that matters: it fails if anyone reverts the filter to reading the line.
 */
class CommentaryFilterTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────
    // Verbatim from match-commentary, ai_generated = true.
    private val dotProse =
        "40.4: Ekansh Singh bursts in to deliver a rising length ball that Ralphie Albert " +
            "watches intently, his defensive push yielding no run as the fielders snap in."
    private val fourProse =
        "40.5: Albert leans into the drive and the ball races away to the rope, the fielder at " +
            "cover never so much as moving."
    private val sixProse =
        "40.6: Albert clears the ropes with a flat-batted heave over long-on, and the crowd rises."
    private val wicketProse =
        "41.1: Singh finds the outside edge, the keeper does the rest, and Albert walks off."
    // ai_generated = false — the passthrough path, still Cricbuzz's own words.
    private val rawFour = "Bex Tyson to Heather Graham, FOUR, square of the wicket"
    private val rawWicket =
        "Antony Dhas to Shoaib Md Khan, out Caught by Suresh Kumar!! " +
            "Shoaib Md Khan c Suresh Kumar b Antony Dhas 4(2) [4s-1]"

    private fun feed() = listOf(
        CommentaryEntry(dotProse, TimelineEventType.OTHER),
        CommentaryEntry(fourProse, TimelineEventType.FOUR),
        CommentaryEntry(sixProse, TimelineEventType.SIX),
        CommentaryEntry(wicketProse, TimelineEventType.WICKET),
    )

    // ── The regression ───────────────────────────────────────────────────────

    @Test
    fun `prose is genuinely unclassifiable from its text alone`() {
        // If this ever starts failing it means the fixtures drifted toward text the old regexes
        // happen to match — and every assertion below would then pass for the wrong reason.
        listOf(dotProse, fourProse, sixProse, wicketProse).forEach { line ->
            assertNull(
                "the whole bug is that this text carries no event: $line",
                TimelineEventExtractor.detectBallType(line),
            )
        }
    }

    @Test
    fun `Boundaries returns the fours and sixes the server classified`() {
        assertEquals(
            listOf(fourProse, sixProse),
            filterCommentary(feed(), CommentaryFilter.BOUNDARIES).map { it.text },
        )
    }

    @Test
    fun `Wickets returns the dismissal, not the boundaries`() {
        assertEquals(
            listOf(wicketProse),
            filterCommentary(feed(), CommentaryFilter.WICKETS).map { it.text },
        )
    }

    @Test
    fun `All returns every line untouched by the chips`() {
        assertEquals(feed().map { it.text }, filterCommentary(feed(), CommentaryFilter.ALL).map { it.text })
    }

    // ── The two ways an event can be absent ──────────────────────────────────

    @Test
    fun `an ordinary delivery is excluded even when its prose mentions a number`() {
        // OTHER means "the server looked and nothing happened". It must NOT fall through to text
        // classification, or this line's "six" and "four" put a dot ball in the Boundaries tab.
        val entry = CommentaryEntry(
            "18.2: Bumrah nails the yorker, no run, and India need six from four.",
            TimelineEventType.OTHER,
        )
        assertTrue(filterCommentary(listOf(entry), CommentaryFilter.BOUNDARIES).isEmpty())
        assertFalse(entry.parseBall().isSix)
        assertFalse(entry.parseBall().isFour)
    }

    @Test
    fun `a line with no server verdict is still classified from its text`() {
        // The scraper fallback and any row written before the event column existed. Cricbuzz's own
        // uppercase tokens are what the fallback reads.
        val scraped = listOf(
            CommentaryEntry(rawFour, event = null),
            CommentaryEntry(rawWicket, event = null),
        )
        assertEquals(listOf(rawFour), filterCommentary(scraped, CommentaryFilter.BOUNDARIES).map { it.text })
        assertEquals(listOf(rawWicket), filterCommentary(scraped, CommentaryFilter.WICKETS).map { it.text })
    }

    @Test
    fun `a server verdict beats the text when the two disagree`() {
        // Cricbuzz writes the fielding position into a dismissal line: "caught at the FOUR
        // boundary rope". The server saw a wicket; the text says both.
        val entry = CommentaryEntry(
            "8.4 Caught at the FOUR boundary rope, and that is the innings over.",
            TimelineEventType.WICKET,
        )
        assertTrue(filterCommentary(listOf(entry), CommentaryFilter.BOUNDARIES).isEmpty())
        assertEquals(1, filterCommentary(listOf(entry), CommentaryFilter.WICKETS).size)
    }

    // ── Milestones read the text on purpose ──────────────────────────────────

    @Test
    fun `Milestones reads the words, which survive the rewrite`() {
        // match-sync deliberately does not classify milestones, so the chip must not consult the
        // event column — a fifty scored off a four would otherwise be invisible under Milestones.
        val entry = CommentaryEntry(
            "31.3: Burns pushes it away for a single and brings up a composed fifty at Hove.",
            TimelineEventType.OTHER,
        )
        assertEquals(1, filterCommentary(listOf(entry), CommentaryFilter.MILESTONES).size)
    }

    // ── Ball prefix ──────────────────────────────────────────────────────────

    @Test
    fun `the stored colon form parses as a ball, not a stray note`() {
        // match-sync writes "40.4: ..."; an earlier `^(\d+\.\d+)\s+` demanded whitespace after the
        // number, so every server line scored overNumber = 0f and lost its 4/6/W pill and its over
        // grouping. Both forms must work — the scraper still writes the space form.
        assertEquals(40.5f, CommentaryEntry(fourProse, null).parseBall().overNumber, 0.001f)
        assertEquals(
            8.4f,
            CommentaryEntry("8.4 Starc bangs it in short.", null).parseBall().overNumber,
            0.001f,
        )
        assertEquals(
            "Albert clears the ropes with a flat-batted heave over long-on, and the crowd rises.",
            CommentaryEntry(sixProse, TimelineEventType.SIX).parseBall().ballText,
        )
    }

    @Test
    fun `a parsed boundary renders its badge`() {
        assertEquals("6", CommentaryEntry(sixProse, TimelineEventType.SIX).parseBall().toBallString())
        assertEquals("4", CommentaryEntry(fourProse, TimelineEventType.FOUR).parseBall().toBallString())
        assertEquals("W", CommentaryEntry(wicketProse, TimelineEventType.WICKET).parseBall().toBallString())
        assertEquals(".", CommentaryEntry(dotProse, TimelineEventType.OTHER).parseBall().toBallString())
    }

    @Test
    fun `dedup keeps the event attached to the line it belongs to`() {
        // The progressive-update pass rewrites the text it keeps, so the verdict has to travel with
        // it. Cricbuzz sends the same ball twice, the longer one wins, and it is the boundary.
        //
        // Neither entry carries a `ball` column — this is the on-device scrape fallback shape — so
        // the dedup key comes from the `40.5` in the prose. That fallback is why filterCommentary
        // reads the column *first* and the text second, rather than one or the other.
        val entries = listOf(
            CommentaryEntry("40.5: Albert drives.", TimelineEventType.FOUR),
            CommentaryEntry(fourProse, TimelineEventType.FOUR),
        )
        val boundaries = filterCommentary(entries, CommentaryFilter.BOUNDARIES)
        assertEquals(1, boundaries.size)
        assertEquals(TimelineEventType.FOUR, boundaries.single().event)
    }

    // ── The sparse-over regression ───────────────────────────────────────────

    @Test
    fun `an over of raw passthrough rows keeps every delivery under All`() {
        // Over 18 of ETPL match 169380, verbatim from match-commentary on 2026-09-12. All six rows
        // are ai_generated = false, so none carries a ball prefix in its text, and three of them
        // are byte-identical. The tab rendered this as 18.4 and 18.1 alone: pass 1 keyed on the
        // line text and collapsed the identical ones, then pass 2 compared across ball numbers on
        // a 15-character prefix — "Tymal Mills to " — and deleted every short delivery in favour of
        // the one long line, which is always the wicket or the boundary.
        val over = rawOver18()
        assertEquals(over, filterCommentary(over, CommentaryFilter.ALL))
    }

    @Test
    fun `the chips still narrow that over to its wicket`() {
        // The chips must keep working on rows the dedup pass no longer touches.
        val wickets = filterCommentary(rawOver18(), CommentaryFilter.WICKETS)
        assertEquals(1, wickets.size)
        assertEquals("18.1", wickets.single().ball)
    }

    // ── Rows that share a ball label (measured on match-commentary, 2026-10-03) ──

    @Test
    fun `a wide and the legal ball after it are both kept`() {
        // Match 163077, over 88: Cricbuzz labels the wide with the ball that follows it. Keyed on
        // the label alone the longer line won, which is the wide, and the four vanished.
        val wide = row("88.4", "88.4: Nasir Lone loses his line with a wild full toss far outside off for a wide.", 2.0, extra = "WIDE")
        val four = row("88.4", "88.4: Smaran Ravichandran pulls through mid-wicket for four.", 3.0, TimelineEventType.FOUR)
        assertEquals(listOf(four, wide), filterCommentary(listOf(four, wide), CommentaryFilter.ALL))
    }

    @Test
    fun `a corrected delivery replaces the one Cricbuzz withdrew`() {
        // Match 171061, 10.3: a single, republished as a catch that never happened, then as the
        // single again. The phantom wicket is the longest of the three.
        val first = row("10.3", "10.3: Sahan Arachchige works it square for a single.", 1.0)
        val caught = "10.3: Sahan Arachchige lifts it into the air where Mustafizur Rahman completes the catch."
        val phantom = row("10.3", caught, 2.0, TimelineEventType.WICKET)
        val last = row("10.3", "10.3: Sahan Arachchige nudges it leg side for a single.", 3.0)
        val feed = listOf(last, phantom, first)
        assertEquals(listOf(last), filterCommentary(feed, CommentaryFilter.ALL))
        assertTrue(filterCommentary(feed, CommentaryFilter.WICKETS).isEmpty())
    }

    @Test
    fun `the same label in another innings is another delivery`() {
        val chase = row("10.3", "10.3: Short one, pulled for a single.", 9.0, innings = 2)
        val firstInnings = row("10.3", "10.3: A much longer line about the same label in the first innings.", 1.0, innings = 1)
        assertEquals(2, filterCommentary(listOf(chase, firstInnings), CommentaryFilter.ALL).size)
    }

    @Test
    fun `a match-situation line is a note between two overs, not a delivery`() {
        // Written by match-sync with a blank ball and a sort key just under the next over's first
        // delivery. It must not join either over, or their ball counts and totals are wrong.
        val next = row("11.1", "11.1: Saif Hassan to Sahan Arachchige, no run.", 9.0)
        val note = row("", "Sahan Arachchige is cutting loose now with sixes off successive balls.", 8.0)
        val six = row("10.5", "10.5: Six to Sahan Arachchige off Rishad Hossain.", 2.0, TimelineEventType.SIX)
        val feed = filterCommentary(listOf(next, note, six), CommentaryFilter.ALL)
        assertEquals(3, feed.size)
        val blocks = buildCommentaryBlocks(feed.map { it.parseBall() })
        assertEquals(listOf(11, null, 10), blocks.map { it.overNumber })
        assertNull(blocks[1].balls.single().ballLabel)
    }

    private fun row(
        ball: String,
        text: String,
        sortKey: Double,
        event: TimelineEventType = TimelineEventType.OTHER,
        extra: String? = null,
        innings: Int = 1,
    ) = CommentaryEntry(text = text, event = event, ball = ball, sortKey = sortKey, extra = extra, innings = innings)

    private fun rawOver18() = listOf(
        raw("18.1", "Tymal Mills to Ashwin, out Lbw!! Ashwin lbw b Tymal Mills 1(2)", TimelineEventType.WICKET),
        raw("18.2", "Tymal Mills to Matthew Hollard, no run", TimelineEventType.OTHER),
        raw("18.3", "Tymal Mills to Matthew Hollard, no run", TimelineEventType.OTHER),
        raw("18.4", "Tymal Mills to Matthew Hollard, 1 run, to short mid-wicket", TimelineEventType.OTHER),
        raw("18.5", "Tymal Mills to Shankar, 1 run, to point", TimelineEventType.OTHER),
        raw("18.6", "Tymal Mills to Matthew Hollard, no run", TimelineEventType.OTHER),
    )

    private fun raw(ball: String, text: String, event: TimelineEventType) =
        CommentaryEntry(text = text, event = event, ball = ball)
}
