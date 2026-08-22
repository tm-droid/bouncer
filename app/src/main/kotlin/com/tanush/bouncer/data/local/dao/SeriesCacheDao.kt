package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity

@Dao
interface SeriesCacheDao {
    /**
     * Substring search over series name and category. See [PlayerCacheDao.searchPlayers] for why
     * the `series_fts MATCH` subquery this replaced could never return a row on a fresh install.
     *
     * LIKE also makes the acronym case work without tokenisation games: a caller expanding "IPL"
     * to "Indian Premier League" matches, and so does a raw "premier".
     */
    @Query(
        """
        SELECT * FROM series_cache
        WHERE name LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        LIMIT 5
        """
    )
    suspend fun searchSeries(query: String): List<SeriesCacheEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<SeriesCacheEntity>)

    @Query("SELECT MAX(lastUpdated) FROM series_cache")
    suspend fun getLastSyncTime(): Long?

    @Query("DELETE FROM series_cache WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}
