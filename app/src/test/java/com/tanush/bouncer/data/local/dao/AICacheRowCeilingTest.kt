package com.tanush.bouncer.data.local.dao

import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The eviction contract for `ai_commentary_cache`.
 *
 * Completed-match rows are written with [CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP], so the
 * time-based `DELETE ... WHERE timestamp < cutoff` can never match them — and DatabaseCleanupWorker
 * did not sweep this table at all. The result was a table that grew for the life of the install.
 * These assert the row ceiling that replaces that, exercised against the same in-memory model the
 * service tests use.
 */
class AICacheRowCeilingTest {

    private val dao = InMemoryAICacheDao()

    private fun entity(key: String, timestamp: Long, lastAccessedAt: Long) =
        AICommentaryCacheEntity(key, "commentary for $key", timestamp, lastAccessedAt)

    @Test
    fun `permanent rows are immune to time-based expiry`() = runTest {
        dao.insert(entity("permanent", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, 0L))
        dao.insert(entity("ordinary", 1_000L, 0L))

        dao.deleteExpired(cutoffMs = Long.MAX_VALUE - 1)

        assertEquals("only the ordinary row should expire", 1, dao.countAll())
        assertTrue(dao.entries.containsKey("permanent"))
    }

    @Test
    fun `row ceiling bounds the table even when every row is permanent`() = runTest {
        repeat(50) {
            dao.insert(entity("k$it", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, it.toLong()))
        }
        assertEquals(50, dao.countAll())

        dao.enforceRowCeiling(keepRows = 20)

        assertEquals(20, dao.countAll())
    }

    @Test
    fun `eviction keeps the most recently accessed rows`() = runTest {
        dao.insert(entity("cold", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, lastAccessedAt = 1L))
        dao.insert(entity("warm", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, lastAccessedAt = 50L))
        dao.insert(entity("hot", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, lastAccessedAt = 99L))

        dao.enforceRowCeiling(keepRows = 2)

        assertNull("least recently accessed must go first", dao.entries["cold"])
        assertTrue(dao.entries.containsKey("warm"))
        assertTrue(dao.entries.containsKey("hot"))
    }

    @Test
    fun `reading a row makes it survive the next eviction`() = runTest {
        dao.insert(entity("a", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, lastAccessedAt = 1L))
        dao.insert(entity("b", CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP, lastAccessedAt = 2L))

        // "a" is read, so it is no longer the coldest.
        dao.touch("a", atMs = 100L)
        dao.enforceRowCeiling(keepRows = 1)

        assertTrue("the row that was read must survive", dao.entries.containsKey("a"))
        assertNull(dao.entries["b"])
    }

    @Test
    fun `a ceiling above the row count evicts nothing`() = runTest {
        repeat(5) { dao.insert(entity("k$it", 1_000L, it.toLong())) }
        dao.enforceRowCeiling(keepRows = CacheConfig.AI_CACHE_MAX_ROWS)
        assertEquals(5, dao.countAll())
    }
}

/**
 * Mirrors the SQL in [AICacheDao] — same ordering (lastAccessedAt DESC, timestamp DESC) and same
 * keep-N semantics.
 */
private class InMemoryAICacheDao : AICacheDao {
    val entries = mutableMapOf<String, AICommentaryCacheEntity>()

    override suspend fun getCommentary(key: String, cutoffMs: Long): String? =
        entries[key]?.takeIf { it.timestamp > cutoffMs }?.commentary

    override suspend fun insert(entity: AICommentaryCacheEntity) {
        entries[entity.cacheKey] = entity
    }

    override suspend fun deleteExpired(cutoffMs: Long) {
        entries.entries.removeIf { it.value.timestamp < cutoffMs }
    }

    override suspend fun touch(key: String, atMs: Long) {
        entries[key]?.let { entries[key] = it.copy(lastAccessedAt = atMs) }
    }

    override suspend fun countAll(): Int = entries.size

    override suspend fun enforceRowCeiling(keepRows: Int) {
        val keep = entries.values
            .sortedWith(
                compareByDescending<AICommentaryCacheEntity> { it.lastAccessedAt }
                    .thenByDescending { it.timestamp }
            )
            .take(keepRows)
            .map { it.cacheKey }
            .toSet()
        entries.entries.removeIf { it.key !in keep }
    }

    override suspend fun clearAll() {
        entries.clear()
    }
}
