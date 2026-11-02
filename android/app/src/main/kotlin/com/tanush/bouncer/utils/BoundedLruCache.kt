package com.tanush.bouncer.utils

/**
 * A map with a hard entry cap that discards the least recently used entry when full.
 *
 * Written because three separate long-lived maps grew without any bound: the per-match commentary
 * buffer in `CommentaryFusionService`, and both `matchIdMap` and `commentaryAvailabilityCache` in
 * `CricInfoScraper`. Each was keyed by match id and each only ever had entries added, so a session
 * that browsed many matches accumulated one entry per match for the life of the process.
 *
 * Access-ordered rather than insertion-ordered: a match being actively watched should not be evicted
 * because it was first seen a long time ago.
 *
 * Every method is synchronized. Two of the maps this replaces were plain [LinkedHashMap]s mutated
 * from `Dispatchers.IO` coroutines with no synchronization at all, which can corrupt the bucket
 * table, not merely lose a write.
 */
class BoundedLruCache<K : Any, V : Any>(private val maxEntries: Int) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    private val map = object : LinkedHashMap<K, V>(maxEntries, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxEntries
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun getOrPut(key: K, defaultValue: () -> V): V = map.getOrPut(key, defaultValue)

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    /** Drop every entry matching [predicate]. Used to evict entries that have aged past a TTL. */
    @Synchronized
    fun removeIf(predicate: (Map.Entry<K, V>) -> Boolean) {
        map.entries.removeAll(predicate)
    }

    @Synchronized
    fun size(): Int = map.size

    companion object {
        private const val LOAD_FACTOR = 0.75f
    }
}
