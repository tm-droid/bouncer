package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
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
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// The series stats table's rows. The name column is fixed and the figures scroll together beneath
// one shared ScrollState, so a column stays a column.

/** The column headings: rank, the name heading, then each figure's heading. */
@Composable
internal fun StatsHeaderRow(name: String, cells: List<String>, scroll: ScrollState) {
    val colors = BouncerThemeV2.colors
    val style = BouncerThemeV2.typography.labelMedium
    val spacing = BouncerThemeV2.spacing
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = style, color = colors.textSecondary, modifier = Modifier.padding(start = spacing.lg).width(RANK_COLUMN))
        Text(name, style = style, color = colors.textSecondary, maxLines = 1, modifier = Modifier.width(NAME_COLUMN))
        StatsFigures(cells, scroll, Modifier.weight(1f)) { _, cell -> FigureText(cell, style, colors.textSecondary, null) }
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
