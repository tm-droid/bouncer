package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the commentary tab groups a feed that spans more than one innings.
 *
 * `match-commentary` is ordered by `sort_key`, the Cricbuzz timestamp, so the feed is in wall-clock
 * order — which is correct, and is not the same thing as cricket order. A Test's third-innings over
 * 60 is genuinely NEWER than its second-innings over 80.
 *
 * The tab used to reconcile the two by regrouping the whole feed with
 * `groupBy { over }.toSortedMap(reverseOrder())`. That throws chronology away: on match 163017 it
 * rendered over 89 immediately above over 83 as though the innings simply skipped overs 84-88, and
 * the flat Wickets list showed a dismissal at 60.2 above one at 80.5 with nothing to say why.
 *
 * The fixtures below are that match's stored rows.
 */
class CommentaryBlockTest {

    private fun ball(
        label: String,
        innings: Int?,
        text: String = "$label: a delivery.",
    ) = CommentaryEntry(
        text = text,
        event = TimelineEventType.OTHER,
        ball = label,
        sortKey = null,
        innings = innings,
    ).parseBall()

    // ── Order ────────────────────────────────────────────────────────────────

    @Test
    fun `feed order survives an innings change`() {
        // Newest first, exactly as the table returns it: third innings' over 60 is newer than the
        // second innings' over 80.
        val blocks = buildCommentaryBlocks(
            listOf(ball("60.2", innings = 3), ball("80.5", innings = 2)),
        )
        assertEquals(listOf(60, 80), blocks.map { it.overNumber })
    }

    @Test
    fun `an innings change opens a divider and an innings continuing does not`() {
        val blocks = buildCommentaryBlocks(
            listOf(
                ball("60.2", innings = 3),
                ball("60.1", innings = 3),
                ball("80.5", innings = 2),
            ),
        )
        assertEquals(2, blocks.size)
        assertFalse("the feed opened with a divider", blocks[0].startsInnings)
        assertTrue("no divider where the innings changed", blocks[1].startsInnings)
    }

    // ── The fallback, for rows written before `innings` could be trusted ──────

    @Test
    fun `a rising over number going back in time is an innings boundary`() {
        // Both rows carry no innings at all (a scrape fallback). Walking newest-to-oldest the over
        // number can only fall within one innings, so 60 above 80 is a boundary by shape alone.
        val blocks = buildCommentaryBlocks(
            listOf(ball("60.2", innings = null), ball("80.5", innings = null)),
        )
        assertEquals(listOf(60, 80), blocks.map { it.overNumber })
        assertTrue(blocks[1].startsInnings)
    }

    @Test
    fun `a falling over number is the same innings`() {
        val blocks = buildCommentaryBlocks(
            listOf(ball("89.3", innings = null), ball("83.5", innings = null)),
        )
        assertFalse("a gap in the stored overs was read as an innings change", blocks[1].startsInnings)
    }

    // ── Blocking ─────────────────────────────────────────────────────────────

    @Test
    fun `deliveries of one over form one block`() {
        val blocks = buildCommentaryBlocks(
            listOf(ball("83.5", innings = 2), ball("83.4", innings = 2), ball("82.6", innings = 2)),
        )
        assertEquals(listOf(83, 82), blocks.map { it.overNumber })
        assertEquals(2, blocks.first().balls.size)
    }

    @Test
    fun `the same over in two innings does not merge`() {
        val blocks = buildCommentaryBlocks(
            listOf(ball("40.1", innings = 3), ball("40.1", innings = 1)),
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks[1].startsInnings)
    }

    @Test
    fun `a non-delivery line stands alone and carries no over`() {
        val note = CommentaryEntry(
            text = "Drinks on the field.",
            event = null,
            ball = null,
            innings = 2,
        ).parseBall()
        val blocks = buildCommentaryBlocks(listOf(note, ball("83.5", innings = 2)))
        assertEquals(2, blocks.size)
        assertEquals(null, blocks[0].overNumber)
        assertEquals(83, blocks[1].overNumber)
    }

    @Test
    fun `a ball-count format has no over to group by`() {
        // The Hundred counts 1..100. Inventing an over for it is what BallMetric exists to prevent.
        val blocks = buildCommentaryBlocks(
            listOf(
                CommentaryEntry("clears the ropes.", TimelineEventType.SIX, ball = "72", innings = 1)
                    .parseBall(),
            ),
        )
        assertEquals(1, blocks.size)
        assertEquals(null, blocks.single().overNumber)
    }

    // ── Lazy-list keys ───────────────────────────────────────────────────────

    private fun allKeys(keys: List<CommentaryBlockKeys>) = keys.flatMap { listOf(it.innings, it.over) + it.balls }

    @Test
    fun `an existing row keeps its key when a newer ball arrives above it`() {
        val before = listOf(ball("12.3", innings = 1), ball("12.2", innings = 1), ball("12.1", innings = 1))
        val after = listOf(ball("12.4", innings = 1)) + before
        val keysBefore = commentaryItemKeys(buildCommentaryBlocks(before)).flatMap { it.balls }
        val keysAfter = commentaryItemKeys(buildCommentaryBlocks(after)).flatMap { it.balls }
        assertEquals(keysBefore, keysAfter.drop(1))
    }

    @Test
    fun `an existing row keeps its key when a new over opens above it`() {
        val before = listOf(ball("12.6", innings = 1), ball("12.5", innings = 1))
        val after = listOf(ball("13.1", innings = 1)) + before
        val oldBlockBefore = commentaryItemKeys(buildCommentaryBlocks(before)).single()
        val oldBlockAfter = commentaryItemKeys(buildCommentaryBlocks(after)).last()
        assertEquals(oldBlockBefore, oldBlockAfter)
    }

    @Test
    fun `repeated labels and repeated non-delivery text never produce a duplicate key`() {
        // ANDROID-40: a duplicate lazy-list key throws. Same label twice in one innings (a
        // republished line), the same over number in two innings, and two identical notes.
        val feed = listOf(
            ball("12.1", innings = 3),
            CommentaryEntry("Drinks on the field.", null, ball = null, innings = 3).parseBall(),
            ball("12.1", innings = 1, text = "republished"),
            ball("12.1", innings = 1),
            CommentaryEntry("Drinks on the field.", null, ball = null, innings = 1).parseBall(),
            CommentaryEntry("Drinks on the field.", null, ball = null, innings = 1).parseBall(),
        )
        val keys = allKeys(commentaryItemKeys(buildCommentaryBlocks(feed)))
        assertEquals(keys.size, keys.toSet().size)
    }
}
