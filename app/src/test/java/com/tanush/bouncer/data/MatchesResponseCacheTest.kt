package com.tanush.bouncer.data

import com.tanush.bouncer.network.models.SanwebMatchesListResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MatchesResponseCacheTest {

    private var now = 0L
    private val cache = MatchesResponseCache(uptimeMs = { now })

    private fun response(count: Int) = SanwebMatchesListResponse(matches = emptyList(), count = count)

    @Test
    fun `returns cached response within TTL`() {
        val resp = response(1)
        cache.put("live", resp)
        now += CacheConfig.MATCHES_RESPONSE_TTL_LIVE_MS
        assertSame(resp, cache.get("live"))
    }

    @Test
    fun `expires live response after its TTL`() {
        cache.put("live", response(1))
        now += CacheConfig.MATCHES_RESPONSE_TTL_LIVE_MS + 1
        assertNull(cache.get("live"))
    }

    @Test
    fun `live TTL is shorter than upcoming and recent`() {
        // The whole point of the per-type split: fixtures change a few times a day, scores do not.
        cache.put("live", response(1))
        cache.put("upcoming", response(2))
        now += CacheConfig.MATCHES_RESPONSE_TTL_LIVE_MS + 1

        assertNull(cache.get("live"))
        assertNotNull(cache.get("upcoming"))
    }

    @Test
    fun `type key is case insensitive`() {
        // Callers pass "Live" (poll loop) and "live" (worker) for the same list.
        cache.put("Live", response(3))
        assertEquals(3, cache.get("live")?.count)
    }

    @Test
    fun `clear drops every type`() {
        cache.put("live", response(1))
        cache.put("recent", response(2))
        cache.clear()
        assertNull(cache.get("live"))
        assertNull(cache.get("recent"))
    }

    @Test
    fun `single flight collapses concurrent callers onto one execution`() = runTest {
        val singleFlight = SingleFlight<String, Int>()
        val executions = AtomicInteger(0)
        val block: suspend () -> Int = {
            executions.incrementAndGet()
            delay(50)
            42
        }

        val callers = List(5) { async { singleFlight.run("live", block) } }
        val results = callers.map { it.await() }

        assertEquals(listOf(42, 42, 42, 42, 42), results)
        assertEquals("all five callers should share one fetch", 1, executions.get())
    }

    @Test
    fun `single flight runs again once the shared execution has finished`() = runTest {
        val singleFlight = SingleFlight<String, Int>()
        val executions = AtomicInteger(0)
        val block: suspend () -> Int = { executions.incrementAndGet() }

        singleFlight.run("live", block)
        singleFlight.run("live", block)

        // Sequential callers are not duplicates — the entry must be released on completion, not
        // held forever (which would pin a stale result).
        assertEquals(2, executions.get())
        assertEquals(0, singleFlight.inFlightCount())
    }

    @Test
    fun `single flight keys are independent`() = runTest {
        val singleFlight = SingleFlight<String, String>()
        val executions = AtomicInteger(0)

        val live = async { singleFlight.run("live") { executions.incrementAndGet(); delay(50); "live" } }
        val recent = async { singleFlight.run("recent") { executions.incrementAndGet(); delay(50); "recent" } }

        assertEquals("live", live.await())
        assertEquals("recent", recent.await())
        assertEquals(2, executions.get())
    }
}
