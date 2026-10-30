package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins the store keys the match detail screen uses for its two ViewModels.
 *
 * `ViewModelStore` is one flat map keyed by string, shared by every type in it, and
 * `ViewModelProvider` treats a key whose entry is the wrong type as a miss — it builds a
 * replacement and `ViewModelStore.put` clears whatever it evicted. Two ViewModels on one screen
 * sharing `match.id` therefore rebuilt and cleared each other on every recomposition: measured at
 * 1.07 constructions/second sustained, median ViewModel lifetime 79 ms, and no load coroutine ever
 * living long enough to finish.
 *
 * The stubs stand in for the real ViewModels (which need an Application) — what is under test is
 * the key strings and `ViewModelProvider`'s behaviour, not the ViewModel bodies.
 */
class ViewModelStoreKeyTest {

    private class DetailStub : ViewModel() {
        var cleared = false
        public override fun onCleared() { cleared = true }
    }

    private class HighlightsStub : ViewModel() {
        var cleared = false
        public override fun onCleared() { cleared = true }
    }

    private object StubFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            DetailStub::class.java -> DetailStub() as T
            HighlightsStub::class.java -> HighlightsStub() as T
            else -> error("unexpected $modelClass")
        }
    }

    @Test
    fun `the two screen ViewModels do not share a store key`() {
        assertNotEquals(
            MatchDetailViewModel.storeKey(MATCH_ID),
            HighlightsViewModel.storeKey(MATCH_ID),
        )
    }

    @Test
    fun `keys stay distinct across matches and are stable for one match`() {
        assertEquals(MatchDetailViewModel.storeKey(MATCH_ID), MatchDetailViewModel.storeKey(MATCH_ID))
        assertNotEquals(MatchDetailViewModel.storeKey(MATCH_ID), MatchDetailViewModel.storeKey("143628"))
        assertNotEquals(HighlightsViewModel.storeKey(MATCH_ID), HighlightsViewModel.storeKey("143628"))
    }

    @Test
    fun `repeated lookups with the screen's keys return the same instances and clear nothing`() {
        val store = ViewModelStore()
        val provider = ViewModelProvider(store, StubFactory)

        val detailKey = MatchDetailViewModel.storeKey(MATCH_ID)
        val highlightsKey = HighlightsViewModel.storeKey(MATCH_ID)

        val firstDetail = provider[detailKey, DetailStub::class.java]
        val firstHighlights = provider[highlightsKey, HighlightsStub::class.java]

        // Stands in for recomposition: the screen re-runs both lookups every pass.
        repeat(RECOMPOSITIONS) {
            assertSame(firstDetail, provider[detailKey, DetailStub::class.java])
            assertSame(firstHighlights, provider[highlightsKey, HighlightsStub::class.java])
        }

        assertFalse(firstDetail.cleared)
        assertFalse(firstHighlights.cleared)
    }

    /** The bug itself, so the mechanism above is pinned and not just the current key strings. */
    @Test
    fun `a shared key makes each lookup evict and clear the other ViewModel`() {
        val store = ViewModelStore()
        val provider = ViewModelProvider(store, StubFactory)

        val detail = provider[MATCH_ID, DetailStub::class.java]
        val highlights = provider[MATCH_ID, HighlightsStub::class.java]

        assertTrueThat(detail.cleared, "detail ViewModel cleared by the highlights lookup")
        assertSame(highlights, provider[MATCH_ID, HighlightsStub::class.java])

        provider[MATCH_ID, DetailStub::class.java]
        assertTrueThat(highlights.cleared, "highlights ViewModel cleared by the detail lookup")
    }

    private fun assertTrueThat(actual: Boolean, message: String) =
        assertEquals(message, true, actual)

    private companion object {
        const val MATCH_ID = "144783"
        const val RECOMPOSITIONS = 50
    }
}
