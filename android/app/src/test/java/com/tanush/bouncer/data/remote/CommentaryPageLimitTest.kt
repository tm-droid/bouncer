package com.tanush.bouncer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The commentary feed's page sizes, which are deliberately asymmetric.
 *
 * Opening a match is the read everyone pays and almost nobody uses in full, so it is small; a page
 * behind it was asked for by a reader scrolling back, so it is the deep one. Nothing else pins that
 * asymmetry, and flattening it back to one constant is the easy edit.
 */
class CommentaryPageLimitTest {

    @Test
    fun `the first page is the small one`() {
        assertEquals(48, commentaryPageLimit(olderThan = null))
    }

    @Test
    fun `a page the reader asked for is the deep one`() {
        assertEquals(150, commentaryPageLimit(olderThan = 1_754_000_000.0))
    }

    @Test
    fun `a degraded rung serves a deep page even as a first page`() {
        // The $createdAt and unordered rungs cannot page at all — the cursor filters on sort_key,
        // the attribute whose index just failed — so the one page they serve is the whole feed that
        // reader gets. Sizing it as an entry page would cap a degraded feed at 8 overs forever.
        assertEquals(150, commentaryPageLimit(olderThan = null, degraded = true))
    }
}

/**
 * The query behind the Wickets and Boundaries chips.
 *
 * It must carry the match AND the event: a query that dropped either would return — and bill —
 * rows from every match, or every delivery of this one.
 */
class CommentaryEventQueryTest {

    @Test
    fun `the first page filters on match and event, newest first, one page`() {
        val queries = commentaryEventQueries("170667", listOf("FOUR", "SIX"), olderThan = null)
        assertEquals(
            listOf("equal:match_id", "equal:event", "orderDesc:sort_key", "limit:", "select:"),
            queries.map(::shapeOf),
        )
        assertEquals(true, queries[1].contains("\"FOUR\"") && queries[1].contains("\"SIX\""))
        assertEquals(true, queries[3].contains(COMMENTARY_EVENT_PAGE_LIMIT.toString()))
    }

    @Test
    fun `a page behind adds the cursor on sort_key`() {
        val queries = commentaryEventQueries("170667", listOf("WICKET"), olderThan = 1_791_045_697_567.0)
        assertEquals("lessThan:sort_key", shapeOf(queries[2]))
    }

    /** `method:attribute` of one serialised Appwrite query. */
    private fun shapeOf(query: String): String {
        val field = { name: String -> Regex("\"$name\":\"([^\"]*)\"").find(query)?.groupValues?.get(1).orEmpty() }
        return "${field("method")}:${field("attribute")}"
    }
}
