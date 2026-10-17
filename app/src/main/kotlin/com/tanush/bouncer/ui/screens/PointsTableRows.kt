package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.PointsTableEntry
import com.tanush.bouncer.ui.components.v2.FormStrip
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.rememberTeamColors
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

// The points table as rows: a team-colour strip, rank, crest and name, then fixed, end-aligned
// figure columns, so P/W/L/NRR/Pts line up down the table the way a printed one does. It was a
// card of rounded gradient pills with tracked caps headers, green/red ink for wins and losses,
// gold points, and 8sp form letters.

private val COL_RANK = 24.dp
private val COL_SMALL = 28.dp
private val COL_NRR = 56.dp
private val COL_PTS = 40.dp
private val STRIP = 3.dp

@Composable
private fun RowScope.Figure(text: String, width: Dp, style: TextStyle, color: Color, weight: FontWeight? = null) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = weight,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
internal fun StandingsHeader() {
    val colors = BouncerThemeV2.colors
    val style = BouncerThemeV2.typography.labelMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp + STRIP, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = style, color = colors.textSecondary, modifier = Modifier.width(COL_RANK))
        Text(stringResource(R.string.points_team), style = style, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Figure("P", COL_SMALL, style, colors.textSecondary)
        Figure("W", COL_SMALL, style, colors.textSecondary)
        Figure("L", COL_SMALL, style, colors.textSecondary)
        Figure("NRR", COL_NRR, style, colors.textSecondary)
        Figure("Pts", COL_PTS, style, colors.textSecondary)
    }
    HorizontalDivider(color = colors.divider)
}

/**
 * One side's row. [cutoffBelow] draws the qualification line under it — from the table's own
 * `qualified` flags, where it used to be a hardcoded "top four".
 */
@Composable
internal fun StandingsRow(entry: PointsTableEntry, rank: Int, cutoffBelow: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val strip = if (TeamColorRegistry.isKnown(entry.teamName)) rememberTeamColors(entry.teamName).primary else Color.Transparent
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(STRIP).fillMaxHeight().background(strip))
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp - STRIP, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rank.toString(), style = typography.numeric, color = colors.textSecondary, modifier = Modifier.width(COL_RANK))
                TeamCrest(teamName = entry.teamName, logoUrl = entry.logoUrl, size = 24.dp, acronym = entry.teamAcronym.ifBlank { null })
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.teamName,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = rememberTeamInk(entry.teamName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Figure(entry.matches.toString(), COL_SMALL, typography.numeric, colors.textSecondary)
                Figure(entry.won.toString(), COL_SMALL, typography.numeric, colors.onSurface)
                Figure(entry.lost.toString(), COL_SMALL, typography.numeric, colors.textSecondary)
                Figure(signedNrr(entry.netRunRate), COL_NRR, typography.numeric, colors.textSecondary)
                Figure(entry.points.toString(), COL_PTS, typography.scoreMedium, colors.onSurface, FontWeight.Bold)
            }
            val form = entry.recentForm.mapNotNull(::formResultOf).takeLast(FORM_LENGTH)
            if (form.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FormStrip(results = form, cell = 18.dp, modifier = Modifier.padding(start = COL_RANK + 32.dp))
            }
        }
    }
    HorizontalDivider(
        color = if (cutoffBelow) colors.onSurface else colors.divider,
        thickness = if (cutoffBelow) 2.dp else 1.dp,
    )
}

@Composable
internal fun QualificationNote() {
    Text(
        text = stringResource(R.string.points_qualification_note),
        style = BouncerThemeV2.typography.bodySmall,
        color = BouncerThemeV2.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** "+0.412", "-1.050", "0.000": always signed, always three places, so the column aligns. */
internal fun signedNrr(nrr: Double): String {
    val text = String.format(Locale.US, "%.3f", nrr)
    return if (nrr > 0) "+$text" else text
}

/** Cricbuzz's form letters to [FormResult]. Anything it adds later is dropped rather than guessed. */
internal fun formResultOf(letter: String): FormResult? = when (letter.trim().uppercase()) {
    "W" -> FormResult.WON
    "L" -> FormResult.LOST
    "D" -> FormResult.DRAWN
    "T" -> FormResult.TIED
    "NR", "N" -> FormResult.NO_RESULT
    else -> null
}

/** The row under which the qualification line falls: the last qualified side, if any qualified. */
internal fun qualificationCutoff(entries: List<PointsTableEntry>): Int =
    if (entries.all { it.qualified } || entries.none { it.qualified }) -1 else entries.indexOfLast { it.qualified }

private const val FORM_LENGTH = 5
