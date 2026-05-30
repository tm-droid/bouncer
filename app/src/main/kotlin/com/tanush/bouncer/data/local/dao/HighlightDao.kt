package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(highlights: List<HighlightEntity>)

    @Query("SELECT * FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId ORDER BY type DESC, fetchedAt DESC LIMIT 50")
    fun queryByBouncerMatchId(bouncerMatchId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId AND fetchedAt >= :freshnessCutoff ORDER BY type DESC, fetchedAt DESC LIMIT 50")
    suspend fun queryCachedByMatchId(bouncerMatchId: String, freshnessCutoff: Long): List<HighlightEntity>

    @Query("SELECT * FROM highlights ORDER BY fetchedAt DESC LIMIT 1000")
    suspend fun queryAllRecent(): List<HighlightEntity>

    @Query("SELECT iccMatchId FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId AND iccMatchId IS NOT NULL LIMIT 1")
    suspend fun cachedIccMatchId(bouncerMatchId: String): String?

    @Query("SELECT iplMatchId FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId AND iplMatchId IS NOT NULL LIMIT 1")
    suspend fun cachedIplMatchId(bouncerMatchId: String): String?

    @Query("DELETE FROM highlights WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
