package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable

@Immutable
data class Series(
    val id: String,
    val name: String,
    val season: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val format: String = "",
    val category: SeriesCategory = SeriesCategory.INTERNATIONAL,
) {
    enum class SeriesCategory { INTERNATIONAL, LEAGUE, DOMESTIC, WOMEN }
}
