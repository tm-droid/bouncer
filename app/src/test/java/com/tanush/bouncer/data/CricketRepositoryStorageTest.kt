package com.tanush.bouncer.data

import com.tanush.bouncer.data.remote.AppwriteClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What happens to the match list when Appwrite Storage's file is old, missing, or fresh.
 *
 * This path used to end in a call to a separately-hosted scraper API whenever the file was past its
 * freshness window — which is every late cron run, not only an outage. That fallback is gone, so the
 * behaviour at the edges is now load-bearing and pinned here:
 *
 *  - fresh  -> served and cached, so the 6s Live poll stops re-downloading
 *  - stale  -> served anyway but NOT cached, so the next call retries rather than being answered
 *              from memory with the same old payload. Showing a slightly old list beats showing
 *              none, which is what "discard it" would now mean.
 *  - absent -> an error, not an empty list. `downloadFile` returns null both when the network is
 *              down and when the read guard is latched on a 402, and "no matches today" is a
 *              confidently wrong thing to render in either case.
 */
class CricketRepositoryStorageTest {

    @Before
    fun setUp() {
        mockkObject(AppwriteClient)
        MatchesResponseCache.shared.clear()
    }

    @After
    fun tearDown() {
        unmockkObject(AppwriteClient)
        MatchesResponseCache.shared.clear()
    }

    private fun payload(ageMs: Long, count: Int = 3): ByteArray {
        val fetchedAt = System.currentTimeMillis() - ageMs
        return ("""{"fetchedAt":$fetchedAt,"data":{"matches":[],"count":$count}}""").toByteArray()
    }

    private fun storageReturns(bytes: ByteArray?) {
        coEvery { AppwriteClient.downloadFile(any(), any(), any()) } returns bytes
    }

    @Test
    fun `a fresh file is served and cached`() = runTest {
        storageReturns(payload(ageMs = 0))

        val first = CricketRepository.getMatches("live")
        val second = CricketRepository.getMatches("live")

        assertTrue(first is AppResult.Success)
        assertEquals(3, (second as AppResult.Success).data.count)
        // Second call answered from memory: one download for two calls.
        coVerify(exactly = 1) { AppwriteClient.downloadFile(any(), any(), any()) }
    }

    @Test
    fun `a stale file is still served`() = runTest {
        storageReturns(payload(ageMs = CacheConfig.matchStorageMaxAgeMs("live") + 60_000))

        val result = CricketRepository.getMatches("live")

        assertTrue("a stale list beats no list", result is AppResult.Success)
        assertEquals(3, (result as AppResult.Success).data.count)
    }

    @Test
    fun `a stale file is not cached, so the next call retries`() = runTest {
        storageReturns(payload(ageMs = CacheConfig.matchStorageMaxAgeMs("live") + 60_000))

        CricketRepository.getMatches("live")
        CricketRepository.getMatches("live")

        // Two downloads for two calls — the opposite of the fresh case, and the whole reason
        // serving stale data is safe: it cannot get stuck on screen.
        coVerify(exactly = 2) { AppwriteClient.downloadFile(any(), any(), any()) }
    }

    @Test
    fun `no file at all is an error rather than an empty list`() = runTest {
        storageReturns(null)

        val result = CricketRepository.getMatches("live")

        assertTrue(result is AppResult.Error)
        assertTrue(
            "the user-facing message must not blame the network — a latched 402 looks identical",
            (result as AppResult.Error).exception is AppException.UnknownException,
        )
    }
}
