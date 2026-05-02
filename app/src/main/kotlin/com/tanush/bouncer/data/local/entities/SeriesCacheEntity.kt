package com.tanush.bouncer.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "series_cache")
data class SeriesCacheEntity(
    @PrimaryKey val seriesId: String,
    val name: String,
    val category: String,
    val startDate: String,
    val endDate: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = SeriesCacheEntity::class)
@Entity(tableName = "series_fts")
data class SeriesFtsEntity(
    val seriesId: String,
    val name: String,
    val category: String
)
