package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Wickets and Boundaries chips over a match longer than the loaded feed.
 *
 * The feed opens at 48 rows — about eight overs — and the chips filtered only that, so a reader saw
 * the last eight overs' wickets and had no way to the rest: "load earlier" is offered under All.
 * The rows were in the table all along. The chips now add the match's event rows, fetched by the
 * `event` column, to the loaded feed.
 */
class CommentaryEventFilterTest {

    private fun ball(innings: Int, over: Int, ball: Int, event: TimelineEventType, key: Double) = CommentaryEntry(
        text = "$over.$ball: a delivery",
        event = event,
        ball = "$over.$ball",
        sortKey = key,
        runs = if (event == TimelineEventType.FOUR) 4 else 0,
        innings = innings,
    )

    /** Second innings, overs 13-20, with one wicket at 18.3 — the shape of a first page. */
    private val loaded = (20 downTo 13).flatMap { over ->
        (6 downTo 1).map { b ->
            val event = if (over == 18 && b == 3) TimelineEventType.WICKET else TimelineEventType.OTHER
            ball(innings = 2, over = over, ball = b, event = event, key = 2_000.0 + over * 10 + b)
        }
    }

    /** What the table answers for WICKET: the loaded one again, and three the feed never held. */
    private val fetchedWickets = listOf(
        ball(2, 18, 3, TimelineEventType.WICKET, 2_183.0),
        ball(2, 4, 2, TimelineEventType.WICKET, 2_042.0),
        ball(1, 19, 6, TimelineEventType.WICKET, 1_196.0),
        ball(1, 7, 1, TimelineEventType.WICKET, 1_071.0),
    )

    private fun labels(feed: CommentaryFeed) = feed.blocks.flatMap { block -> block.balls.map { it.ballLabel } }

    @Test
    fun `the loaded feed alone shows only the wicket inside it`() {
        assertEquals(listOf("18.3"), labels(buildCommentaryFeed(loaded, CommentaryFilter.WICKETS)))
    }

    @Test
    fun `with the fetched rows the filter shows every wicket of the match, newest first`() {
        val feed = buildCommentaryFeed(loaded, CommentaryFilter.WICKETS, fetchedWickets)
        assertEquals(listOf("18.3", "4.2", "19.6", "7.1"), labels(feed))
        assertEquals(listOf(2, 2, 1, 1), feed.blocks.map { it.innings })
    }

    @Test
    fun `a row in both lists appears once`() {
        val merged = mergeEventRows(loaded, fetchedWickets)
        assertEquals(loaded.size + 3, merged.size)
        assertEquals(merged.size, merged.map { it.sortKey }.toSet().size)
    }

    @Test
    fun `a live event in the loaded feed stays above older fetched ones`() {
        val fresh = ball(2, 20, 6, TimelineEventType.WICKET, 2_206.5)
        val feed = buildCommentaryFeed(listOf(fresh) + loaded, CommentaryFilter.WICKETS, fetchedWickets)
        assertEquals("20.6", labels(feed).first())
    }

    @Test
    fun `nothing fetched leaves the loaded feed as it was`() {
        assertSame(loaded, mergeEventRows(loaded, emptyList()))
        assertSame(loaded, mergeEventRows(loaded, listOf(fetchedWickets.first())))
    }

    @Test
    fun `only wickets and boundaries are asked of the table`() {
        assertEquals(listOf("WICKET"), CommentaryFilter.WICKETS.eventKind?.events)
        assertEquals(listOf("FOUR", "SIX"), CommentaryFilter.BOUNDARIES.eventKind?.events)
        assertEquals(null, CommentaryFilter.ALL.eventKind)
        // match-sync writes no MILESTONE event, so there is nothing to ask for.
        assertEquals(null, CommentaryFilter.MILESTONES.eventKind)
    }
}
