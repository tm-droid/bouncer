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
            filterCommentary(feed(), "Boundaries").map { it.text },
        )
    }

    @Test
    fun `Wickets returns the dismissal, not the boundaries`() {
        assertEquals(
            listOf(wicketProse),
            filterCommentary(feed(), "Wickets").map { it.text },
        )
    }

    @Test
    fun `All returns every line untouched by the chips`() {
        assertEquals(feed().map { it.text }, filterCommentary(feed(), "All").map { it.text })
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
        assertTrue(filterCommentary(listOf(entry), "Boundaries").isEmpty())
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
        assertEquals(listOf(rawFour), filterCommentary(scraped, "Boundaries").map { it.text })
        assertEquals(listOf(rawWicket), filterCommentary(scraped, "Wickets").map { it.text })
    }

    @Test
    fun `a server verdict beats the text when the two disagree`() {
        // Cricbuzz writes the fielding position into a dismissal line: "caught at the FOUR
        // boundary rope". The server saw a wicket; the text says both.
        val entry = CommentaryEntry(
            "8.4 Caught at the FOUR boundary rope, and that is the innings over.",
            TimelineEventType.WICKET,
        )
        assertTrue(filterCommentary(listOf(entry), "Boundaries").isEmpty())
        assertEquals(1, filterCommentary(listOf(entry), "Wickets").size)
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
        assertEquals(1, filterCommentary(listOf(entry), "Milestones").size)
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
        val entries = listOf(
            CommentaryEntry("40.5: Albert drives.", TimelineEventType.FOUR),
            CommentaryEntry(fourProse, TimelineEventType.FOUR),
        )
        val boundaries = filterCommentary(entries, "Boundaries")
        assertEquals(1, boundaries.size)
        assertEquals(TimelineEventType.FOUR, boundaries.single().event)
    }
}
