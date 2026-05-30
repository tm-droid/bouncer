package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_commentary_cache",
    // timestamp: WHERE timestamp > cutoffMs (read) + DELETE WHERE timestamp < cutoffMs (cleanup)
    indices = [Index("timestamp")]
)
data class AICommentaryCacheEntity(
    @PrimaryKey val cacheKey: String,
    val commentary: String,
    val timestamp: Long
)
