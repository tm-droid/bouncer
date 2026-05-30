package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    // matchedBouncerMatchId: 4 queries filter on it; fetchedAt: ORDER BY + DELETE + WHERE
    indices = [Index("matchedBouncerMatchId"), Index("fetchedAt")]
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val title: String,
    val imgUrl: String,
    val embedUrl: String?,
    val fallbackUrl: String?,
    val source: String,
    val channel: String,
    val type: String,
    val matchedBouncerMatchId: String?,
    val iccMatchId: String?,
    val iplMatchId: String?,
    val leagueName: String,
    val homeTeam: String,
    val awayTeam: String,
    val matchStartDate: String,
    val fetchedAt: Long
)
