package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoundedLruCacheTest {

    @Test
    fun `the map never grows past its cap`() {
        // The three maps this replaces grew one entry per match id for the life of the process.
        val cache = BoundedLruCache<String, Int>(3)
        repeat(100) { cache["match_$it"] = it }
        assertEquals(3, cache.size())
    }

    @Test
    fun `the least recently used entry is the one dropped`() {
        val cache = BoundedLruCache<String, Int>(3)
        cache["a"] = 1
        cache["b"] = 2
        cache["c"] = 3
        cache["d"] = 4
        assertNull("a was the oldest and should be gone", cache["a"])
        assertEquals(2, cache["b"])
    }

    @Test
    fun `reading an entry protects it from eviction`() {
        // Access-ordered, not insertion-ordered: a match being actively watched must not be evicted
        // just because it was the first one opened.
        val cache = BoundedLruCache<String, Int>(3)
        cache["a"] = 1
        cache["b"] = 2
        cache["c"] = 3
        assertEquals(1, cache["a"])
        cache["d"] = 4
        assertNotNull("a was read most recently and must survive", cache["a"])
        assertNull("b is now the oldest", cache["b"])
    }

    @Test
    fun `getOrPut counts as an access`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        assertEquals(1, cache.getOrPut("a") { 99 })
        cache["c"] = 3
        assertEquals(1, cache["a"])
        assertNull(cache["b"])
    }

    @Test
    fun `removeIf drops exactly the matching entries`() {
        val cache = BoundedLruCache<String, Int>(10)
        repeat(6) { cache["k$it"] = it }
        cache.removeIf { (_, v) -> v % 2 == 0 }
        assertEquals(3, cache.size())
        assertNull(cache["k0"])
        assertEquals(1, cache["k1"])
    }

    @Test
    fun `remove returns the previous value`() {
        val cache = BoundedLruCache<String, Int>(4)
        cache["a"] = 1
        assertEquals(1, cache.remove("a"))
        assertNull(cache.remove("a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive cap is rejected`() {
        BoundedLruCache<String, Int>(0)
    }

    @Test
    fun `concurrent writers do not corrupt the map`() {
        // Two of the maps this replaces were plain LinkedHashMaps written from Dispatchers.IO
        // coroutines, where a concurrent resize can lose entries or spin forever.
        val cache = BoundedLruCache<String, Int>(64)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) { worker ->
            pool.execute {
                start.await()
                repeat(500) { cache["w${worker}_$it"] = it }
                done.countDown()
            }
        }
        start.countDown()
        assertEquals(true, done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(64, cache.size())
    }
}
