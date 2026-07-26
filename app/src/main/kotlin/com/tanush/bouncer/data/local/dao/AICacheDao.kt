package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity

@Dao
interface AICacheDao {

    @Query("SELECT commentary FROM ai_commentary_cache WHERE cacheKey = :key AND timestamp > :cutoffMs")
    suspend fun getCommentary(key: String, cutoffMs: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AICommentaryCacheEntity)

    /**
     * Time-based expiry. Cannot reach permanent rows by design — they carry
     * [com.tanush.bouncer.data.CacheConfig.AI_CACHE_PERMANENT_TIMESTAMP]. Use
     * [enforceRowCeiling] to bound the table.
     */
    @Query("DELETE FROM ai_commentary_cache WHERE timestamp < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long)

    @Query("UPDATE ai_commentary_cache SET lastAccessedAt = :atMs WHERE cacheKey = :key")
    suspend fun touch(key: String, atMs: Long)

    @Query("SELECT COUNT(*) FROM ai_commentary_cache")
    suspend fun countAll(): Int

    /**
     * Size bound for the table, evicting least-recently-accessed rows first.
     *
     * Every other table here is pruned only by age, which is enough because their rows expire.
     * These do not: permanent completed-match commentary is written with a sentinel timestamp and
     * so accumulated for the life of the install, and DatabaseCleanupWorker did not touch this
     * table at all.
     */
    @Query(
        """
        DELETE FROM ai_commentary_cache WHERE cacheKey NOT IN (
            SELECT cacheKey FROM ai_commentary_cache
            ORDER BY lastAccessedAt DESC, timestamp DESC
            LIMIT :keepRows
        )
        """
    )
    suspend fun enforceRowCeiling(keepRows: Int)

    @Query("DELETE FROM ai_commentary_cache")
    suspend fun clearAll()
}
