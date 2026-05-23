package com.tanush.bouncer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "matches",
    indices = [Index(value = ["isLive"], name = "idx_matches_is_live")]
)
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
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isLive: Boolean = computeIsLive(status, currentStatus)
) {
    companion object {
        fun computeIsLive(status: String, currentStatus: String): Boolean {
            val s = "$status $currentStatus".lowercase()
            return "live" in s || "in progress" in s
        }
    }
}
