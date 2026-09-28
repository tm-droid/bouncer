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
     * The columns search ranks on, for every cached profile.
     *
     * Search scores these in memory (`SearchRepository.searchCachedPlayers`). It used to be a LIKE
     * over name, country and team with `LIMIT 10` and no `ORDER BY`: rows were dropped before they
     * were ranked, a typo matched nothing, and a country hit listed every player from it. Before
     * that it went through a `player_fts MATCH` subquery whose table, declared `@Fts4` with no
     * `contentEntity`, was never populated on any install created after schema v1.
     * `statsJson` is left out; it is the one large column and search never reads it.
     */
    @Query("SELECT name, teamName, role FROM player_cache")
    suspend fun searchCorpus(): List<PlayerSearchRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(player: PlayerCacheEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(players: List<PlayerCacheEntity>)

    @Query("SELECT lastUpdated FROM player_cache WHERE playerId = :id")
    suspend fun getLastUpdated(id: String): Long?

    /**
     * One player's cached row.
     *
     * The table has been written since the cache existed and read back only for search, which
     * indexes three of its columns; `statsJson` had no reader at all. The profile screen scraped
     * Cricbuzz on every open regardless — so the same player had two answers, one in search and a
     * freshly-scraped one on the screen, and the cross-device cache bought nothing.
     */
    @Query("SELECT * FROM player_cache WHERE playerId = :id")
    suspend fun getById(id: String): PlayerCacheEntity?

    @Query("DELETE FROM player_cache WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}

/** The slice of [PlayerCacheEntity] search needs. */
data class PlayerSearchRow(val name: String, val teamName: String, val role: String)
