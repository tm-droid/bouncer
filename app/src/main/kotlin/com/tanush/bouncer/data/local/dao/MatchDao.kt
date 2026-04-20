package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.MatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMatch(match: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMatches(matches: List<MatchEntity>)

    @Query("SELECT * FROM matches WHERE matchId = :matchId")
    fun getMatch(matchId: String): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE status LIKE '%live%' OR currentStatus LIKE '%live%' ORDER BY lastUpdated DESC")
    fun getLiveMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY lastUpdated DESC")
    fun getAllMatches(): Flow<List<MatchEntity>>

    @Query("DELETE FROM matches WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}
