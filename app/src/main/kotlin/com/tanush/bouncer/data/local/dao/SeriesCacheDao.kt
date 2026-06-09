package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity

@Dao
interface SeriesCacheDao {
    @Query("SELECT * FROM series_cache WHERE seriesId IN (SELECT seriesId FROM series_fts WHERE series_fts MATCH :query) LIMIT 5")
    suspend fun searchSeries(query: String): List<SeriesCacheEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<SeriesCacheEntity>)

    @Query("SELECT MAX(lastUpdated) FROM series_cache")
    suspend fun getLastSyncTime(): Long?

    @Query("DELETE FROM series_cache WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}
