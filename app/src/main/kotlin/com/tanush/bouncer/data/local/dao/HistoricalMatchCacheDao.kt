package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tanush.bouncer.data.local.entities.HistoricalMatchCacheEntity

@Dao
interface HistoricalMatchCacheDao {
    @Query("SELECT * FROM historical_match_cache WHERE matchId IN (SELECT matchId FROM historical_match_fts WHERE historical_match_fts MATCH :query) LIMIT 10")
    suspend fun searchHistoricalMatches(query: String): List<HistoricalMatchCacheEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(matches: List<HistoricalMatchCacheEntity>)

    @Query("SELECT COUNT(*) FROM historical_match_cache")
    suspend fun count(): Int

    @Query("SELECT MAX(lastUpdated) FROM historical_match_cache")
    suspend fun getLastSyncTime(): Long?

    @Query("DELETE FROM historical_match_cache WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}
