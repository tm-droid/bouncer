package com.tanush.bouncer.models

/**
 * Represents a cricket series or tournament (e.g., IPL 2025, Ashes 2025).
 */
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
