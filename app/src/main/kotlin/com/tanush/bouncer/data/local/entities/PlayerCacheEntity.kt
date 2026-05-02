package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "player_cache")
data class PlayerCacheEntity(
    @PrimaryKey val playerId: String,
    val name: String,
    val country: String,
    val role: String,
    val teamName: String,
    val statsJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "player_fts")
@Fts4
data class PlayerFtsEntity(
    val playerId: String,
    val name: String,
    val country: String,
    val role: String,
    val teamName: String
)
