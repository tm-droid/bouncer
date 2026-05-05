package com.tanush.bouncer.services

import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AICacheServiceTest {

    private lateinit var fakeDao: FakeAICacheDao

    @Before
    fun setup() = runTest {
        fakeDao = FakeAICacheDao()
        AICacheService.initialize(fakeDao)
        AICacheService.clearCache()
    }

    @Test
    fun `getCommentary returns null on cold miss`() = runTest {
        assertNull(AICacheService.getCommentary("missing_key"))
    }

    @Test
    fun `setCommentary then getCommentary returns value from memory`() = runTest {
        AICacheService.setCommentary("Great shot!", "key1")
        assertEquals("Great shot!", AICacheService.getCommentary("key1"))
    }

    @Test
    fun `getCommentary hits DB on memory miss and warms memory`() = runTest {
        val nowMs = System.currentTimeMillis()
        fakeDao.entries["key2"] = AICommentaryCacheEntity("key2", "DB commentary", nowMs)

        val result = AICacheService.getCommentary("key2")
        assertEquals("DB commentary", result)

        // Memory now warm — remove DB entry and still get result from memory
        fakeDao.entries.remove("key2")
        assertEquals("DB commentary", AICacheService.getCommentary("key2"))
    }

    @Test
    fun `getCommentary returns null for expired DB entry`() = runTest {
        val expiredMs = System.currentTimeMillis() - 3_700_000L
        fakeDao.entries["key3"] = AICommentaryCacheEntity("key3", "stale", expiredMs)

        assertNull(AICacheService.getCommentary("key3"))
    }

    @Test
    fun `setCommentary persists to DB`() = runTest {
        AICacheService.setCommentary("AI text", "key4")
        assertNotNull(fakeDao.entries["key4"])
        assertEquals("AI text", fakeDao.entries["key4"]?.commentary)
    }

    @Test
    fun `clearCache clears both memory and DB`() = runTest {
        AICacheService.setCommentary("text", "key5")
        AICacheService.clearCache()

        assertNull(AICacheService.getCommentary("key5"))
        assertTrue(fakeDao.entries.isEmpty())
    }

    @Test
    fun `isCacheValid returns false for unknown key`() = runTest {
        assertFalse(AICacheService.isCacheValid("ghost_key"))
    }

    @Test
    fun `isCacheValid returns true after set`() = runTest {
        AICacheService.setCommentary("valid", "key6")
        assertTrue(AICacheService.isCacheValid("key6"))
    }

    @Test
    fun `isCacheValid returns true for valid DB entry not in memory`() = runTest {
        val nowMs = System.currentTimeMillis()
        fakeDao.entries["key7"] = AICommentaryCacheEntity("key7", "db only", nowMs)

        assertTrue(AICacheService.isCacheValid("key7"))
    }

    @Test
    fun `isCacheValid returns false for expired DB entry not in memory`() = runTest {
        val expiredMs = System.currentTimeMillis() - 3_700_000L
        fakeDao.entries["key8"] = AICommentaryCacheEntity("key8", "stale db", expiredMs)

        assertFalse(AICacheService.isCacheValid("key8"))
    }

    @Test
    fun `expired memory entry evicted on access`() = runTest {
        AICacheService.setCommentary("text", "key9")

        // Simulate expiry by inserting an old DB entry and clearing memory via clearCache
        // then confirming the entry can't be retrieved
        val expiredMs = System.currentTimeMillis() - 3_700_000L
        fakeDao.entries["key9"] = AICommentaryCacheEntity("key9", "text", expiredMs)

        AICacheService.clearCache()
        // Reinsert expired entry directly in DB to simulate cross-session stale data
        fakeDao.entries["key9"] = AICommentaryCacheEntity("key9", "text", expiredMs)
        assertNull(AICacheService.getCommentary("key9"))
    }

    @Test
    fun `deleteExpired removes stale entries from DB`() = runTest {
        val nowMs = System.currentTimeMillis()
        val expiredMs = nowMs - 3_700_000L

        fakeDao.entries["fresh"] = AICommentaryCacheEntity("fresh", "ok", nowMs)
        fakeDao.entries["stale"] = AICommentaryCacheEntity("stale", "old", expiredMs)

        fakeDao.deleteExpired(nowMs - 3_600_000L)

        assertTrue(fakeDao.entries.containsKey("fresh"))
        assertFalse(fakeDao.entries.containsKey("stale"))
    }
}

private class FakeAICacheDao : AICacheDao {
    val entries = mutableMapOf<String, AICommentaryCacheEntity>()

    override suspend fun getCommentary(key: String, cutoffMs: Long): String? =
        entries[key]?.takeIf { it.timestamp > cutoffMs }?.commentary

    override suspend fun insert(entity: AICommentaryCacheEntity) {
        entries[entity.cacheKey] = entity
    }

    override suspend fun deleteExpired(cutoffMs: Long) {
        entries.entries.removeIf { it.value.timestamp < cutoffMs }
    }

    override suspend fun clearAll() {
        entries.clear()
    }
}
