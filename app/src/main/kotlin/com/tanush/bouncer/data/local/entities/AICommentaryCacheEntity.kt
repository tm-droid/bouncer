package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_commentary_cache")
data class AICommentaryCacheEntity(
    @PrimaryKey val cacheKey: String,
    val commentary: String,
    val timestamp: Long
)
