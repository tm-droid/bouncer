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

    @Query("DELETE FROM ai_commentary_cache WHERE timestamp < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long)

    @Query("DELETE FROM ai_commentary_cache")
    suspend fun clearAll()
}
