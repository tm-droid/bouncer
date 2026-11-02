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

    // Cutoff-free sibling of the above. Since highlights sync incrementally, a row is fetched once
    // and never refreshed, so its age says nothing about whether it is still the right answer —
    // only about when it first arrived.
    @Query("SELECT * FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId ORDER BY type DESC, fetchedAt DESC LIMIT 50")
    suspend fun queryAllByMatchId(bouncerMatchId: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights ORDER BY fetchedAt DESC LIMIT 1000")
    suspend fun queryAllRecent(): List<HighlightEntity>

    // Rows stored before their match was in the `matches` table. They are re-scored when that table
    // fills, since ingest scored them against whatever it held at the time.
    @Query("SELECT * FROM highlights WHERE matchedBouncerMatchId IS NULL ORDER BY fetchedAt DESC LIMIT 500")
    suspend fun queryUnmatched(): List<HighlightEntity>

    @Query("SELECT iccMatchId FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId AND iccMatchId IS NOT NULL LIMIT 1")
    suspend fun cachedIccMatchId(bouncerMatchId: String): String?

    @Query("SELECT iplMatchId FROM highlights WHERE matchedBouncerMatchId = :bouncerMatchId AND iplMatchId IS NOT NULL LIMIT 1")
    suspend fun cachedIplMatchId(bouncerMatchId: String): String?

    @Query("DELETE FROM highlights WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
