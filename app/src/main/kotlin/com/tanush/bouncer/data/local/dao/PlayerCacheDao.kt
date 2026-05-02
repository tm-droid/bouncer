package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.PlayerCacheEntity

@Dao
interface PlayerCacheDao {
    @Query("SELECT * FROM player_cache WHERE playerId IN (SELECT playerId FROM player_fts WHERE player_fts MATCH :query) LIMIT 10")
    suspend fun searchPlayers(query: String): List<PlayerCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(player: PlayerCacheEntity)

    @Query("SELECT lastUpdated FROM player_cache WHERE playerId = :id")
    suspend fun getLastUpdated(id: String): Long?
}
