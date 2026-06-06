package com.tanush.bouncer.data.cache

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe in-memory LRU cache with TTL expiration.
 *
 * Extracted from the duplicated memory-tier scaffolding that previously lived inside
 * each cache service (AICacheService, MatchCacheService, ...). This handles ONLY the
 * in-memory layer — Room/Appwrite/disk tiers stay in the owning service.
 *
 * Eviction is least-recently-inserted: [get] does not bump recency (insertion order),
 * matching the access pattern of the callers that warm memory from a slower tier.
 *
 * [permanent] entries (e.g. completed-match commentary) never expire on TTL, mirroring
 * the `Long.MAX_VALUE` sentinel semantics in the persistent tiers.
 *
 * Callers that need a compound memory+persistence operation to be atomic should continue
 * to hold their own outer mutex; this class's internal lock only guarantees per-call
 * thread-safety of the memory map.
 */
open class MemoryLruCache<K, V>(
    private val maxSize: Int = 256,
    private val ttlMs: Long,
) {
    data class CacheEntry<V>(
        val value: V,
        val insertedAt: Long,
        val permanent: Boolean = false,
    )

    private val cache = LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, false)
    private val lock = ReentrantReadWriteLock()

    /** Returns the cached value if present and not expired, else null. Expired entries are evicted. */
    fun get(key: K): V? {
        val entry = lock.read { cache[key] } ?: return null
        if (isExpired(entry)) {
            lock.write { cache.remove(key) }
            return null
        }
        return entry.value
    }

    /** Returns the raw entry (incl. metadata) without TTL eviction. Null if absent. */
    fun getEntry(key: K): CacheEntry<V>? = lock.read { cache[key] }

    /** True if a non-expired entry exists for [key]. Does not evict. */
    fun isValid(key: K): Boolean {
        val entry = lock.read { cache[key] } ?: return false
        return !isExpired(entry)
    }

    fun put(key: K, value: V, permanent: Boolean = false) = lock.write {
        if (!cache.containsKey(key) && cache.size >= maxSize) evictOldest()
        cache[key] = CacheEntry(value, System.currentTimeMillis(), permanent)
    }

    fun remove(key: K) {
        lock.write { cache.remove(key) }
    }

    fun clear() {
        lock.write { cache.clear() }
    }

    fun size(): Int = lock.read { cache.size }

    private fun isExpired(entry: CacheEntry<V>): Boolean =
        !entry.permanent && System.currentTimeMillis() - entry.insertedAt > ttlMs

    private fun evictOldest() {
        cache.entries.firstOrNull()?.let { cache.remove(it.key) }
    }
}
