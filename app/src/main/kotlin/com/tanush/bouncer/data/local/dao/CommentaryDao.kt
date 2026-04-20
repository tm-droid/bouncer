package com.tanush.bouncer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tanush.bouncer.data.local.entities.CommentaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommentary(commentary: List<CommentaryEntity>)

    @Query("SELECT * FROM commentary WHERE matchId = :matchId ORDER BY ballNumber DESC")
    fun getCommentary(matchId: String): Flow<List<CommentaryEntity>>

    @Query("SELECT * FROM commentary WHERE matchId = :matchId ORDER BY ballNumber DESC LIMIT :limit OFFSET :offset")
    suspend fun getCommentaryPage(matchId: String, limit: Int, offset: Int): List<CommentaryEntity>

    @Query("DELETE FROM commentary WHERE matchId = :matchId")
    suspend fun deleteCommentary(matchId: String)
}
