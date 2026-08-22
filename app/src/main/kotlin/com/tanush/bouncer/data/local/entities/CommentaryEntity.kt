package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.Index

// idx_commentary_timestamp exists for DatabaseCleanupWorker's
// `DELETE FROM commentary WHERE timestamp < :before`, which was a full table scan on every weekly
// run. Every other table already had its cleanup column indexed (highlights.fetchedAt,
// ai_commentary_cache.timestamp); this one was missed.
@Entity(
    tableName = "commentary",
    primaryKeys = ["matchId", "ballNumber"],
    indices = [Index(value = ["timestamp"], name = "idx_commentary_timestamp")],
)
data class CommentaryEntity(
    val matchId: String,
    val ballNumber: Int,
    val overNumber: String,
    val commentary: String,
    val runs: Int,
    val isWicket: Boolean,
    val isBoundary: Boolean,
    val timestamp: Long
)
