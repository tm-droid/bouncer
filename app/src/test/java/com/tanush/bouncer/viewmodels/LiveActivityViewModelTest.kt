package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.models.CricketMatch
import org.junit.Assert.assertEquals
import com.tanush.bouncer.ui.components.v2.FailureKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveActivityViewModelTest {

    @Test
    fun `offline poll failure over fresh scores still raises the stale banner`() {
        assertTrue(pollFailureNeedsBanner(statusIsFresh = true, onScreen = true, kind = FailureKind.Offline))
    }

    @Test
    fun `server blip over fresh scores stays quiet, over disk-seeded scores it shows`() {
        assertFalse(pollFailureNeedsBanner(statusIsFresh = true, onScreen = true, kind = FailureKind.Server))
        assertTrue(pollFailureNeedsBanner(statusIsFresh = false, onScreen = true, kind = FailureKind.Server))
    }

    @Test
    fun `a status off screen never raises the banner`() {
        assertFalse(pollFailureNeedsBanner(statusIsFresh = false, onScreen = false, kind = FailureKind.Offline))
    }

    // ── The home screen's "All" status chip ───────────────────────────────────
    //
    // It used to map onto "Live" on its way into the ViewModel, so tapping it moved the highlight
    // to the Live chip and changed nothing else — a control that reads as dead. The round trip is
    // what these guard: the chip label the screen sends must be the status that comes back, or the
    // selection cannot stick.

    @Test
    fun `All fetches and polls all three statuses`() {
        assertEquals(listOf("Live", "Upcoming", "Recent"), statusesToFetch(STATUS_ALL))
    }

    @Test
    fun `a concrete status fetches only itself`() {
        assertEquals(listOf("Live"), statusesToFetch("Live"))
        assertEquals(listOf("Recent"), statusesToFetch("Recent"))
    }

    @Test
    fun `All is the union of the three caches, live first and de-duped`() {
        val live = match("a")
        val upcoming = match("b")
        val recent = match("c")
        val cache = mapOf(
            "Live" to listOf(live),
            // The same fixture appearing in two caches must not appear twice in the feed: a match
            // that has just finished is briefly in both Live and Recent.
            "Upcoming" to listOf(upcoming, live),
            "Recent" to listOf(recent),
        )
        assertEquals(
            listOf("a", "b", "c"),
            matchesForStatus(cache, STATUS_ALL)?.map { it.id },
        )
    }

    @Test
    fun `All is null until at least one cache has landed`() {
        // Null is every caller's not-ready path. Returning an empty list here would blank a
        // populated feed for as long as the first fetch takes.
        assertNull(matchesForStatus(emptyMap(), STATUS_ALL))
        assertEquals(listOf("a"), matchesForStatus(mapOf("Recent" to listOf(match("a"))), STATUS_ALL)?.map { it.id })
    }

    @Test
    fun `a concrete status reads only its own cache`() {
        val cache = mapOf("Live" to listOf(match("a")), "Recent" to listOf(match("b")))
        assertEquals(listOf("a"), matchesForStatus(cache, "Live")?.map { it.id })
        assertNull(matchesForStatus(cache, "Upcoming"))
    }

    private fun match(id: String) = CricketMatch(
        id = id,
        seriesId = "",
        matchTitle = "match $id",
        matchType = "",
        status = "",
        teams = emptyList(),
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = "",
        info = "",
    )
}
