package com.tanush.bouncer.ui.components.premium





import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// Scorecard rows. Every figure column is a fixed width and end-aligned, so a column of runs lines up
// down the innings the way a printed card does; the name takes what is left.

private val ROW_PADDING_H = 16.dp
private val ROW_PADDING_V = 10.dp
private val COL_NARROW = 30.dp
private val COL_MID = 38.dp
private val COL_WIDE = 50.dp
private val AVATAR_SIZE = 28.dp
private val FOW_AVATAR_SIZE = 22.dp
internal val AVATAR_GAP = 8.dp

@Composable
private fun RowScope.Cell(text: String, width: Dp, style: TextStyle, color: Color, weight: FontWeight? = null) {
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
private fun ColumnLabels(first: String, labels: List<Pair<String, Dp>>) {
    val colors = BouncerThemeV2.colors
    val style = BouncerThemeV2.typography.labelMedium
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_H, vertical = 6.dp)) {
        // Inset past the rows' avatar so the label sits over the names.
        Text(
            text = first,
            style = style,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f).padding(start = AVATAR_SIZE + AVATAR_GAP),
        )
        labels.forEach { (label, width) -> Cell(label, width, style, colors.textSecondary) }
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
internal fun BattingColumns() = ColumnLabels(
    first = stringResource(R.string.premium_batter),
    labels = listOf("R" to COL_MID, "B" to COL_MID, "4s" to COL_NARROW, "6s" to COL_NARROW, "SR" to COL_WIDE),
)

@Composable
internal fun BowlingColumns() = ColumnLabels(
    first = stringResource(R.string.premium_bowler),
    labels = listOf("O" to COL_MID, "M" to COL_NARROW, "R" to COL_MID, "W" to COL_NARROW, "Econ" to COL_WIDE),
)

/**
 * A batter: name and how they got out on two lines, then the figures. Runs read first and boldest;
 * the innings' top score takes the batting side's colour. "batting" only while the innings is live —
 * every not-out batter of a finished match used to say "Batting" in green.
 */
@Composable
internal fun BattingRow(batsman: BatsmanStats, battingNow: Boolean, isTopScore: Boolean, teamName: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val notOut = batsman.dismissal.isBlank() || batsman.dismissal.contains("not out", ignoreCase = true)
    val ink = rememberTeamInk(teamName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .playerLink(batsman.name, batsman.profileUrl)
            .padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowAvatar(batsman.name, batsman.profileUrl, AVATAR_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (notOut) "${batsman.name} *" else batsman.name,
                style = typography.bodyMedium,
                fontWeight = if (notOut || isTopScore) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dismissalLine(batsman.dismissal, notOut, battingNow),
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Cell(batsman.runs, COL_MID, typography.numeric, if (isTopScore) ink else colors.onSurface, FontWeight.Bold)
        Cell(batsman.balls, COL_MID, typography.numericSmall, colors.textSecondary)
        Cell(batsman.fours, COL_NARROW, typography.numericSmall, colors.textSecondary)
        Cell(batsman.sixes, COL_NARROW, typography.numericSmall, colors.textSecondary)
        Cell(batsman.strikeRate, COL_WIDE, typography.numericSmall, colors.textSecondary)
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun dismissalLine(dismissal: String, notOut: Boolean, battingNow: Boolean): String = when {
    !notOut -> dismissal
    battingNow -> stringResource(R.string.scorecard_batting)
    else -> stringResource(R.string.scorecard_not_out)
}

@Composable
internal fun ExtrasRow(extras: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.scorecard_extras),
            style = typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(extras, style = typography.numericSmall, color = colors.textSecondary)
    }
    HorizontalDivider(color = colors.divider)
}

/** The innings total, which the card never printed: `Innings.total` was parsed and dropped. */
@Composable
internal fun TotalRow(innings: Innings) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val total = innings.total.ifBlank {
        listOf(innings.score, innings.overs.takeIf { it.isNotBlank() }?.let { "(${it.replace("Ov", "").trim()})" })
            .filterNotNull().joinToString(" ")
    }
    if (total.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.scorecard_total),
            style = typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(total, style = typography.numeric, fontWeight = FontWeight.Bold, color = colors.onSurface)
    }
    HorizontalDivider(color = colors.divider)
}

/** "3-87  Kohli  14.2" — the score at the fall, the batter out, the over. */
@Composable
internal fun FallOfWicketRow(fow: FallOfWicket, number: Int) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val runs = fow.score.substringBefore("-").substringBefore("/").trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .playerLink(fow.batsman, "")
            .padding(horizontal = ROW_PADDING_H, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (runs.isNotEmpty()) "$number-$runs" else fow.score,
            style = typography.numeric,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            modifier = Modifier.width(64.dp),
        )
        RowAvatar(fow.batsman, "", FOW_AVATAR_SIZE)
        Text(
            text = fow.batsman,
            style = typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (fow.overs.isNotBlank()) {
            Text(text = fow.overs, style = typography.numericSmall, color = colors.textSecondary)
        }
    }
    HorizontalDivider(color = colors.divider)
}

/** A bowler: wickets read first and boldest; the innings' best figures are bold throughout. */
@Composable
internal fun BowlingRow(bowler: BowlerStats, isBest: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val figures = if (isBest) colors.onSurface else colors.textSecondary
    val extras = bowlingExtras(bowler)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .playerLink(bowler.name, bowler.profileUrl)
            .padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowAvatar(bowler.name, bowler.profileUrl, AVATAR_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bowler.name,
                style = typography.bodyMedium,
                fontWeight = if (isBest) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Under the name, like a batter's dismissal: two more figure columns would leave the
            // name a few letters on a phone.
            if (extras != null) {
                Text(text = extras, style = typography.bodySmall, color = colors.textSecondary, maxLines = 1)
            }
        }
        Cell(bowler.overs, COL_MID, typography.numericSmall, figures)
        Cell(bowler.maidens, COL_NARROW, typography.numericSmall, figures)
        Cell(bowler.runs, COL_MID, typography.numericSmall, figures)
        Cell(bowler.wickets, COL_NARROW, typography.numeric, colors.onSurface, FontWeight.Bold)
        Cell(bowler.economy, COL_WIDE, typography.numericSmall, figures)
    }
    HorizontalDivider(color = colors.divider)
}
