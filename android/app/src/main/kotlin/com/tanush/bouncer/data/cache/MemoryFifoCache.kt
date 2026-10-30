package com.tanush.bouncer.data.cache

import android.os.SystemClock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe in-memory cache with per-key TTL expiration and a hard size ceiling.
 *
 * Extracted from the duplicated memory-tier scaffolding that previously lived inside
 * each cache service (AICacheService, MatchCacheService, ...). This handles ONLY the
 * in-memory layer — Room/Appwrite/disk tiers stay in the owning service.
 *
 * Eviction is FIFO, not LRU: the backing LinkedHashMap is built with `accessOrder = false`,
 * so [get] does not bump recency and the oldest *insertion* is evicted first. That matches the
 * access pattern of the callers, which warm memory from a slower tier rather than re-reading a
 * hot working set. The class was called `MemoryLruCache` until the name was corrected — see
 * [com.tanush.bouncer.utils.BoundedLruCache] for the real LRU.
 *
 * TTL is resolved per key via [ttlResolver], so a caller whose expiry varies by key
 * (e.g. MatchCacheService: 6s for Live, 60s for Upcoming/Recent) configures it here
 * instead of re-deriving entry age itself. This is deliberate: entry timestamps are
 * stamped from [SystemClock.elapsedRealtime] and are meaningless against wall-clock
 * epoch, so no timestamp is exposed to callers at all — see [peek] and [isValid].
 *
 * [permanent] entries (e.g. completed-match commentary) never expire on TTL, mirroring
 * the `Long.MAX_VALUE` sentinel semantics in the persistent tiers.
 *
 * Callers that need a compound memory+persistence operation to be atomic should continue
 * to hold their own outer mutex; this class's internal lock only guarantees per-call
 * thread-safety of the memory map.
 */
open class MemoryFifoCache<K, V>(
    private val maxSize: Int = 256,
    private val ttlResolver: (K) -> Long,
    // The single place this cache's clock is chosen. Injectable because SystemClock is stubbed
    // to a constant 0 under `isReturnDefaultValues`, which would otherwise make TTL untestable.
    private val uptimeMs: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Convenience for callers whose TTL is the same for every key. */
    constructor(maxSize: Int, ttlMs: Long) : this(maxSize, { ttlMs })

    private data class CacheEntry<V>(
        val value: V,
        // Monotonic uptime, NOT wall-clock epoch. Never compare against System.currentTimeMillis().
        val insertedAtUptimeMs: Long,
        val permanent: Boolean = false,
    )

    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    private val cache = LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, false)
    private val lock = ReentrantReadWriteLock()

    /** Returns the cached value if present and not expired, else null. Expired entries are evicted. */
    fun get(key: K): V? {
        lock.read { cache[key] }?.let { if (!isExpired(key, it)) return it.value }
        // Slow path: the entry looked expired under the read lock. Re-read under the write lock
        // before evicting — a put() can land in the gap between the two locks, and an
        // unconditional remove() would throw away the fresh value it just wrote.
        return lock.write {
            val current = cache[key] ?: return@write null
            if (isExpired(key, current)) {
                cache.remove(key)
                null
            } else {
                current.value
            }
        }
    }

    /**
     * Returns the cached value regardless of TTL, without evicting. For offline/stale-read paths
     * that deliberately prefer expired data over nothing.
     */
    fun peek(key: K): V? = lock.read { cache[key]?.value }

    /** True if a non-expired entry exists for [key]. Does not evict. */
    fun isValid(key: K): Boolean {
        val entry = lock.read { cache[key] } ?: return false
        return !isExpired(key, entry)
    }

    fun put(key: K, value: V, permanent: Boolean = false) = lock.write {
        if (!cache.containsKey(key) && cache.size >= maxSize) evictOldest()
        cache[key] = CacheEntry(value, uptimeMs(), permanent)
    }

    fun remove(key: K) {
        lock.write { cache.remove(key) }
    }

    fun clear() {
        lock.write { cache.clear() }
    }

    fun size(): Int = lock.read { cache.size }

    private fun isExpired(key: K, entry: CacheEntry<V>): Boolean =
        !entry.permanent && uptimeMs() - entry.insertedAtUptimeMs > ttlResolver(key)

    private fun evictOldest() {
        cache.entries.firstOrNull()?.let { cache.remove(it.key) }
    }
}
