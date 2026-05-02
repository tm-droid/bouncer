package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity

@Dao
interface SeriesCacheDao {
    @Query("SELECT series_cache.* FROM series_fts JOIN series_cache ON series_fts.rowid = series_cache.rowid WHERE series_fts MATCH :query LIMIT 5")
    suspend fun searchSeries(query: String): List<SeriesCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<SeriesCacheEntity>)

    @Query("SELECT MAX(lastUpdated) FROM series_cache")
    suspend fun getLastSyncTime(): Long?
}
