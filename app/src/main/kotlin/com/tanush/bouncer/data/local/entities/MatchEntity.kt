package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey
    val matchId: String,
    val seriesId: String,
    val matchTitle: String,
    val matchType: String,
    val status: String,
    val venue: String,
    val date: String,
    val matchTime: String,
    val currentStatus: String,
    val info: String,
    val team1Name: String,
    val team1Score: String?,
    val team2Name: String,
    val team2Score: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
