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
    /**
     * Orthogonal to [category] — women's cricket is not a format. A WOMEN arm on the enum forced
     * "ICC Women's T20 World Cup" to choose between the two, and the first-match-wins chain chose
     * LEAGUE, making WOMEN unreachable.
     */
    val isWomens: Boolean = false,
) {
    enum class SeriesCategory { INTERNATIONAL, LEAGUE, DOMESTIC }
}
