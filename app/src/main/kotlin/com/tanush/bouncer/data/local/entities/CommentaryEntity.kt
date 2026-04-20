package com.tanush.bouncer.data.local.entities

import androidx.room.Entity

@Entity(tableName = "commentary", primaryKeys = ["matchId", "ballNumber"])
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
