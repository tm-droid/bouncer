package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable

/** One date heading on a series' schedule and the matches under it, in Cricbuzz's order. */
@Immutable
data class SeriesMatchDay(
    /** Cricbuzz's own heading, e.g. "Thu, 15 Feb 2024". */
    val label: String,
    val matches: List<CricketMatch>,
)

/** One entry in a series' stat-type menu, e.g. `mostRuns` / "Most Runs" / "Batting". */
@Immutable
data class SeriesStatType(
    val value: String,
    val header: String,
    val category: String,
)

/** One format's table for one stat type. */
@Immutable
data class SeriesStatsTable(
    /** "ODI", "T20", "TEST" — from the payload's `{format}StatsList` key. */
    val format: String,
    /** Column headers, starting with "PLAYER". */
    val headers: List<String>,
    val rows: List<SeriesStatsRow>,
)

@Immutable
data class SeriesStatsRow(
    val playerId: String,
    val playerName: String,
    /** One cell per header after "PLAYER". */
    val cells: List<String>,
)

/**
 * Everything one `/stats?statsType=X` page carries: the full stat-type menu, and one table per
 * format the series was played in. A multi-format tour answers both formats on the same page, so a
 * format switch costs no fetch.
 */
@Immutable
data class SeriesStats(
    val types: List<SeriesStatType>,
    val selectedType: String,
    val tables: List<SeriesStatsTable>,
    /** The format Cricbuzz shows first, when it names one the tables carry. */
    val defaultFormat: String?,
) {
    companion object {
        val EMPTY = SeriesStats(types = emptyList(), selectedType = "", tables = emptyList(), defaultFormat = null)
    }
}
