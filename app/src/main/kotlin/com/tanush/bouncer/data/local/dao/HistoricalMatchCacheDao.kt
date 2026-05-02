package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.HistoricalMatchCacheEntity

@Dao
interface HistoricalMatchCacheDao {
    @Query("SELECT historical_match_cache.* FROM historical_match_fts JOIN historical_match_cache ON historical_match_fts.rowid = historical_match_cache.rowid WHERE historical_match_fts MATCH :query LIMIT 10")
    suspend fun searchHistoricalMatches(query: String): List<HistoricalMatchCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(matches: List<HistoricalMatchCacheEntity>)

    @Query("SELECT COUNT(*) FROM historical_match_cache")
    suspend fun count(): Int
}
