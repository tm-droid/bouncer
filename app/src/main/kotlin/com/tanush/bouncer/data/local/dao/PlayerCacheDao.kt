package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tanush.bouncer.data.local.entities.PlayerCacheEntity

@Dao
interface PlayerCacheDao {
    /**
     * Substring search over the columns a user actually types.
     *
     * This used to go through a `player_fts MATCH` subquery. That FTS table is declared `@Fts4`
     * with no `contentEntity`, so Room creates it with no sync triggers — the triggers that
     * populated it existed only inside MIGRATION_1_2, which a fresh install never runs. Every
     * install created after schema v1 therefore searched a permanently empty index. The table
     * holds at most a few hundred rows, so LIKE is both correct and cheap enough.
     */
    @Query(
        """
        SELECT * FROM player_cache
        WHERE name LIKE '%' || :query || '%'
           OR country LIKE '%' || :query || '%'
           OR teamName LIKE '%' || :query || '%'
        LIMIT 10
        """
    )
    suspend fun searchPlayers(query: String): List<PlayerCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(player: PlayerCacheEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(players: List<PlayerCacheEntity>)

    @Query("SELECT lastUpdated FROM player_cache WHERE playerId = :id")
    suspend fun getLastUpdated(id: String): Long?

    @Query("DELETE FROM player_cache WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}
