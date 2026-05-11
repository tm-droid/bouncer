package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
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
