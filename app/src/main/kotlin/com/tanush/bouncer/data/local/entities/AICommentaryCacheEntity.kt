package com.tanush.bouncer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_commentary_cache",
    // timestamp: WHERE timestamp > cutoffMs (read) + DELETE WHERE timestamp < cutoffMs (cleanup)
    // lastAccessedAt: ORDER BY for the row-ceiling eviction
    indices = [Index("timestamp"), Index("lastAccessedAt")]
)
data class AICommentaryCacheEntity(
    @PrimaryKey val cacheKey: String,
    val commentary: String,
    /**
     * Expiry clock. Completed-match rows carry [com.tanush.bouncer.data.CacheConfig
     * .AI_CACHE_PERMANENT_TIMESTAMP], which no `timestamp < cutoff` delete can ever match —
     * hence the separate [lastAccessedAt] ordering used by the row-ceiling eviction.
     */
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val lastAccessedAt: Long = 0L,
)
