package com.tanush.bouncer.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the memory tier.
 *
 * The clock is injected here because `isReturnDefaultValues` stubs SystemClock to a constant 0,
 * which is precisely why the previous wall-clock/uptime mismatch in MatchCacheService could ship
 * undetected: no test could advance time, so no test could observe expiry at all.
 */
class MemoryFifoCacheTest {

    private var now = 1_000L
    private fun cache(maxSize: Int = 4, ttlMs: Long = 100L) =
        MemoryFifoCache<String, String>(maxSize, { ttlMs }, { now })

    // ── TTL ──

    @Test
    fun `entry is valid immediately after put`() {
        val c = cache()
        c.put("k", "v")
        assertTrue(c.isValid("k"))
        assertEquals("v", c.get("k"))
    }

    @Test
    fun `entry expires once past its ttl`() {
        val c = cache(ttlMs = 100L)
        c.put("k", "v")
        now += 101
        assertFalse(c.isValid("k"))
        assertNull(c.get("k"))
    }

    @Test
    fun `entry is still valid exactly at its ttl boundary`() {
        val c = cache(ttlMs = 100L)
        c.put("k", "v")
        now += 100
        assertTrue(c.isValid("k"))
    }

    @Test
    fun `get evicts the expired entry it rejects`() {
        val c = cache(ttlMs = 100L)
        c.put("k", "v")
        now += 101
        assertNull(c.get("k"))
        assertEquals(0, c.size())
    }

    @Test
    fun `isValid does not evict`() {
        val c = cache(ttlMs = 100L)
        c.put("k", "v")
        now += 101
        assertFalse(c.isValid("k"))
        assertEquals(1, c.size())
    }

    // ── Per-key TTL: the behaviour MatchCacheService depends on ──

    @Test
    fun `ttl is resolved per key`() {
        val c = MemoryFifoCache<String, String>(
            maxSize = 4,
            ttlResolver = { key -> if (key == "Live") 6_000L else 60_000L },
            uptimeMs = { now },
        )
        c.put("Live", "a")
        c.put("Recent", "b")

        now += 10_000
        assertFalse("Live should expire after 6s", c.isValid("Live"))
        assertTrue("Recent should survive 10s under a 60s ttl", c.isValid("Recent"))
    }

    // ── Permanent entries ──

    @Test
    fun `permanent entries never expire`() {
        val c = cache(ttlMs = 100L)
        c.put("k", "v", permanent = true)
        now += 10_000_000
        assertTrue(c.isValid("k"))
        assertEquals("v", c.get("k"))
    }

    // ── peek ──

    @Test
    fun `peek returns expired values without evicting`() {
        val c = cache(ttlMs = 100L)
        c.put("k", "v")
        now += 101
        assertEquals("v", c.peek("k"))
        assertEquals(1, c.size())
    }

    @Test
    fun `peek returns null for absent key`() {
        assertNull(cache().peek("nope"))
    }

    // ── Size bound + eviction order ──

    @Test
    fun `size never exceeds maxSize`() {
        val c = cache(maxSize = 3)
        repeat(10) { c.put("k$it", "v$it") }
        assertEquals(3, c.size())
    }

    @Test
    fun `eviction is least-recently-inserted, not least-recently-used`() {
        val c = cache(maxSize = 3)
        c.put("a", "1")
        c.put("b", "2")
        c.put("c", "3")

        // Reading "a" must NOT rescue it — insertion order governs eviction.
        assertEquals("1", c.get("a"))
        c.put("d", "4")

        assertNull("oldest insert should be evicted despite recent read", c.get("a"))
        assertEquals("2", c.get("b"))
        assertEquals("4", c.get("d"))
    }

    @Test
    fun `overwriting an existing key does not evict`() {
        val c = cache(maxSize = 2)
        c.put("a", "1")
        c.put("b", "2")
        c.put("a", "updated")

        assertEquals(2, c.size())
        assertEquals("updated", c.get("a"))
        assertEquals("2", c.get("b"))
    }

    // ── Guard ──

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive maxSize is rejected at construction`() {
        MemoryFifoCache<String, String>(maxSize = 0, ttlMs = 100L)
    }

    // ── Basic map ops ──

    @Test
    fun `remove and clear`() {
        val c = cache()
        c.put("a", "1")
        c.put("b", "2")

        c.remove("a")
        assertNull(c.get("a"))
        assertEquals(1, c.size())

        c.clear()
        assertEquals(0, c.size())
        assertNull(c.get("b"))
    }
}
