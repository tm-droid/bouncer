package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity

@Dao
interface SeriesCacheDao {
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<SeriesCacheEntity>)

    /**
     * Every cached series, newest-started first.
     *
     * `SeriesSyncWorker` has filled this table since it existed and only search ever read it, so
     * the Series tab — which live-scrapes on every open — could not show a series that search
     * could find, and showed an error rather than a list when the scrape failed. Search ranks this
     * list in memory (`SearchRepository.searchSeries`).
     */
    @Query("SELECT * FROM series_cache ORDER BY startDate DESC")
    suspend fun getAll(): List<SeriesCacheEntity>

    @Query("SELECT MAX(lastUpdated) FROM series_cache")
    suspend fun getLastSyncTime(): Long?

    @Query("DELETE FROM series_cache WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}
