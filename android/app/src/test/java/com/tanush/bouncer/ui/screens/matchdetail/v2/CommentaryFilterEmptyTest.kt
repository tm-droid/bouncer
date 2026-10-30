package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A filter that matches nothing in the loaded feed.
 *
 * The tab used to render this as an empty LazyColumn: a blank page with no message, and no way
 * forward either, because "load earlier" is offered under All only. It became the common case when
 * the first page dropped from 150 rows to 48 — eight overs frequently hold no wicket at all, where
 * twenty-five usually held one. The blocks being empty is the condition the tab now draws an empty
 * state for, so it is what this pins.
 */
class CommentaryFilterEmptyTest {

    private fun dot(over: Int, ball: Int) = CommentaryEntry(
        text = "$over.$ball: Ekansh Singh angles one across, pushed back down the ground, no run.",
        event = TimelineEventType.OTHER,
        ball = "$over.$ball",
        sortKey = (over * 10 + ball).toDouble(),
        runs = 0,
        innings = 1,
    )

    /** Eight quiet overs — the shape of a first page on a match nobody has taken a wicket in. */
    private val quietPage = (40 downTo 33).flatMap { over -> (6 downTo 1).map { dot(over, it) } }

    @Test
    fun `a wicket filter over a quiet page has no blocks to draw`() {
        assertTrue(buildCommentaryFeed(quietPage, CommentaryFilter.WICKETS).blocks.isEmpty())
    }

    @Test
    fun `a milestone filter over a quiet page has no blocks to draw`() {
        assertTrue(buildCommentaryFeed(quietPage, CommentaryFilter.MILESTONES).blocks.isEmpty())
    }

    @Test
    fun `the same page under All is not empty`() {
        // The empty state is about the FILTER, never about the feed: rendering it for a feed that
        // has lines would hide them.
        assertFalse(buildCommentaryFeed(quietPage, CommentaryFilter.ALL).blocks.isEmpty())
    }

    @Test
    fun `a page that does hold a wicket still renders it`() {
        val withWicket = quietPage + CommentaryEntry(
            text = "32.6: Singh finds the edge and the keeper does the rest.",
            event = TimelineEventType.WICKET,
            ball = "32.6",
            sortKey = 326.0,
            runs = 0,
            innings = 1,
        )
        assertFalse(buildCommentaryFeed(withWicket, CommentaryFilter.WICKETS).blocks.isEmpty())
    }
}
