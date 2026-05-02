package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "historical_match_cache")
data class HistoricalMatchCacheEntity(
    @PrimaryKey val matchId: String,
    val matchTitle: String,
    val teams: String,       // JSON list as String
    val venue: String,
    val city: String,
    val date: String,
    val season: String,
    val matchType: String,
    val playerOfMatch: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "historical_match_fts")
@Fts4
data class HistoricalMatchFtsEntity(
    val matchId: String,
    val matchTitle: String,
    val teams: String,
    val venue: String,
    val city: String,
    val season: String,
    val matchType: String,
    val playerOfMatch: String
)
