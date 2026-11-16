package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.SeriesStatsRow
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// The series stats table's rows. The name column is fixed and the figures scroll together beneath
// one shared ScrollState, so a column stays a column.

/** No column sorted: the order Cricbuzz ranked the table in. */
internal const val NO_SORT = -1

/**
 * The column headings: rank, the name heading, then each figure's heading. A figure heading sorts
 * the table by that column ([onSort] gets its index among [cells]); the sorted one carries an arrow.
 */
@Composable
internal fun StatsHeaderRow(
    name: String,
    cells: List<String>,
    scroll: ScrollState,
    sortColumn: Int,
    descending: Boolean,
    onSort: (Int) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val style = BouncerThemeV2.typography.labelMedium
    val spacing = BouncerThemeV2.spacing
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = style, color = colors.textSecondary, modifier = Modifier.padding(start = spacing.lg).width(RANK_COLUMN))
        Text(
            name,
            style = style,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(NAME_COLUMN),
        )
        StatsFigures(cells, scroll, Modifier.weight(1f)) { index, cell ->
            SortableHeader(cell, if (index == sortColumn) descending else null) { onSort(index) }
        }
    }
}

/** A figure heading that sorts on tap; [descending] is null when this column is not the sorted one. */
@Composable
private fun SortableHeader(text: String, descending: Boolean?, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val style = BouncerThemeV2.typography.labelMedium
    val arrow = when (descending) {
        null -> ""
        true -> "↓"
        false -> "↑"
    }
    Row(
        modifier = Modifier
            .width(CELL_COLUMN)
            .clickable(onClickLabel = stringResource(R.string.series_stats_sort_by, text), onClick = onClick),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = style,
            color = if (descending == null) colors.textSecondary else colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (descending != null) Text(arrow, style = style, color = colors.primaryText)
    }
}

/**
 * [rows] ordered by figure column [column] (an index into each row's cells), largest first when
 * [descending]. A cell that is not a number ("-", "") sorts last either way; ties keep Cricbuzz's order.
 */
internal fun sortedStatRows(rows: List<SeriesStatsRow>, column: Int, descending: Boolean): List<SeriesStatsRow> {
    if (column < 0) return rows
    val (numeric, other) = rows.map { it to statNumber(it.cells.getOrNull(column)) }.partition { it.second != null }
    val ordered = if (descending) numeric.sortedByDescending { it.second } else numeric.sortedBy { it.second }
    return (ordered + other).map { it.first }
}

/** "1,234", "45*", "52.3%" read as numbers; anything else is null. */
private fun statNumber(cell: String?): Double? =
    cell?.trim()?.trimEnd('*', '%')?.replace(",", "")?.toDoubleOrNull()

/** One lazy-list key per row: the player's id, the index when it is blank, and a suffix on a repeat. */
internal fun statRowKeys(rows: List<SeriesStatsRow>): List<String> {
    val seen = HashSet<String>()
    return rows.mapIndexed { i, row ->
        val base = if (row.playerId.isBlank()) "row_i$i" else "row_${row.playerId}"
        if (seen.add(base)) base else "${base}_i$i"
    }
}

/** A player: rank, name, then the figures — the one the table is ranked by reads first. */
@Composable
internal fun StatsRow(rank: Int, name: String, cells: List<String>, scroll: ScrollState, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(modifier = modifier.fillMaxWidth().padding(vertical = spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rank.toString(),
            style = typography.numeric,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = spacing.lg).width(RANK_COLUMN),
        )
        Text(
            name,
            style = typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(NAME_COLUMN),
        )
        StatsFigures(cells, scroll, Modifier.weight(1f)) { index, cell ->
            if (index == 0) {
                FigureText(cell, typography.numeric, colors.onSurface, FontWeight.Bold)
            } else {
                FigureText(cell, typography.numeric, colors.textSecondary, null)
            }
        }
    }
}

@Composable
private fun StatsFigures(
    cells: List<String>,
    scroll: ScrollState,
    modifier: Modifier,
    cell: @Composable (Int, String) -> Unit,
) {
    Row(modifier = modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.End) {
        cells.forEachIndexed { index, text -> cell(index, text) }
        Spacer(Modifier.width(BouncerThemeV2.spacing.lg))
    }
}

@Composable
private fun FigureText(text: String, style: TextStyle, color: Color, weight: FontWeight?) {
    Text(
        text,
        style = style,
        color = color,
        fontWeight = weight,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(CELL_COLUMN),
    )
}


private val NAME_COLUMN = 148.dp
private val CELL_COLUMN = 60.dp
private val RANK_COLUMN = 28.dp
