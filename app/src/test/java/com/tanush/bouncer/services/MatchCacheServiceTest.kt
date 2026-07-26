package com.tanush.bouncer.services

import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for the memory tier of MatchCacheService.
 *
 * The load-bearing assertion is [cache is valid immediately after updateCache]. Before the
 * single-clock fix, entries were stamped with SystemClock.elapsedRealtime() (~1e7) and their age
 * was computed as `Date().time - insertedAt` (~1.78e12), so isCacheValid() returned false for
 * every entry ever written. Its only caller — LiveActivityViewModel — therefore refetched from
 * the network on every poll and the 16-entry memory tier was write-only.
 *
 * Expiry-over-time is covered in MemoryLruCacheTest, which can advance the injected clock;
 * MatchCacheService is an object singleton whose cache is constructed at class-init, so the
 * fresh-entry case is what is meaningfully assertable here.
 */
class MatchCacheServiceTest {

    private fun match(id: String) = CricketMatch(
        id = id,
        seriesId = "s1",
        matchTitle = "Test Match $id",
        matchType = "T20",
        status = "Live",
        teams = emptyList(),
        venue = "Venue",
        date = "2026-07-25",
        matchTime = "19:30",
        score = null,
        currentStatus = "Live",
        info = "",
    )

    @Before
    fun setup() = runTest { MatchCacheService.clearCache() }

    @After
    fun teardown() = runTest { MatchCacheService.clearCache() }

    @Test
    fun `cache is valid immediately after updateCache`() = runTest {
        MatchCacheService.updateCache(listOf(match("1")), "Live")
        assertTrue(
            "a just-written entry must be valid — a false here means callers refetch every poll",
            MatchCacheService.isCacheValid("Live"),
        )
    }

    @Test
    fun `cache is valid immediately after updateCache for every status TTL bucket`() = runTest {
        listOf("Live", "Upcoming", "Recent", "SomethingElse").forEach { status ->
            MatchCacheService.updateCache(listOf(match("1")), status)
            assertTrue("status=$status", MatchCacheService.isCacheValid(status))
        }
    }

    @Test
    fun `getMatches returns what updateCache wrote`() = runTest {
        MatchCacheService.updateCache(listOf(match("1"), match("2")), "Live")

        val cached = MatchCacheService.getMatches("Live")
        assertNotNull(cached)
        assertEquals(listOf("1", "2"), cached?.map { it.id })
    }

    @Test
    fun `statuses do not share cache entries`() = runTest {
        MatchCacheService.updateCache(listOf(match("live-1")), "Live")
        MatchCacheService.updateCache(listOf(match("recent-1")), "Recent")

        assertEquals(listOf("live-1"), MatchCacheService.getMatches("Live")?.map { it.id })
        assertEquals(listOf("recent-1"), MatchCacheService.getMatches("Recent")?.map { it.id })
    }

    @Test
    fun `unwritten status is not valid and returns null`() = runTest {
        assertEquals(false, MatchCacheService.isCacheValid("NeverWritten"))
        assertNull(MatchCacheService.getMatches("NeverWritten"))
    }

    @Test
    fun `clearCache invalidates everything`() = runTest {
        MatchCacheService.updateCache(listOf(match("1")), "Live")
        MatchCacheService.clearCache()

        assertEquals(false, MatchCacheService.isCacheValid("Live"))
        assertNull(MatchCacheService.getMatches("Live"))
    }

    @Test
    fun `getOfflineMatches returns memory data regardless of validity`() = runTest {
        MatchCacheService.updateCache(listOf(match("1")), "Live")
        assertEquals(listOf("1"), MatchCacheService.getOfflineMatches("Live")?.map { it.id })
    }
}
