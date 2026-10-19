package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.rememberSegmentColors
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Head-to-Head statistics data
 */
data class HeadToHeadStats(
    val totalMatches: Int,
    val team1Wins: Int,
    val team2Wins: Int,
    val draws: Int,
    val team1HomeWins: Int,
    val team2HomeWins: Int,
    val lastFiveResults: List<String>, // "W1", "W2", "D" for team1 win, team2 win, draw
    val team1HighestScore: String,
    val team2HighestScore: String,
    val team1LowestScore: String,
    val team2LowestScore: String,
    val lastWinner: String?
)

/** The two sides' meetings, oriented to the caller's team1/team2 (see `H2HRepository`). */
data class HeadToHeadHistory(
    val team1Name: String,
    val team2Name: String,
    val totalMatches: Int,
    val team1Wins: Int,
    val team2Wins: Int,
    val noResults: Int,
    val recentMatches: List<HistoricalMatch> = emptyList(),
)

data class HistoricalMatch(
    val date: String,
    val venue: String,
    val winner: String?, // null for no result
    val team1Score: String,
    val team2Score: String,
    val format: String,
    val margin: String? = null
)

/**
 * The two sides' T20 record against each other: the win split, their highest and lowest totals,
 * and the most recent meetings.
 *
 * One card. There used to be two — this one and `HistoricalStatsCard` — stating the same wins and
 * meetings twice, in gold tracked caps under a shimmering gradient border and radial glows, with a
 * "No result" tile hardcoded to 0, and an "At this venue" panel that was really the most frequent
 * venue among the recent meetings, not this match's ground.
 */
@Composable
fun HeadToHeadCard(
    match: CricketMatch,
    stats: HeadToHeadStats,
    history: HeadToHeadHistory,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val team1Label = team1?.let { it.acronym.ifBlank { it.name } }.orEmpty()
    val team2Label = team2?.let { it.acronym.ifBlank { it.name } }.orEmpty()

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.common_head_to_head),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.h2h_meetings, stats.totalMatches, stats.totalMatches),
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.md))
            WinSplit(stats, team1?.name, team2?.name, team1Label, team2Label)
            ScoreRecords(stats, team1Label, team2Label)
            if (history.recentMatches.isNotEmpty()) {
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = stringResource(R.string.historical_recent_meetings),
                    style = typography.titleSmall,
                    color = colors.onSurface,
                )
                history.recentMatches.take(RECENT_MEETINGS).forEachIndexed { index, meeting ->
                    if (index > 0) HorizontalDivider(color = colors.divider)
                    MeetingRow(meeting, team1Label, team2Label)
                }
            }
        }
    }
}

@Composable
private fun WinSplit(
    stats: HeadToHeadStats,
    team1Name: String?,
    team2Name: String?,
    team1Label: String,
    team2Label: String,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val total = (stats.team1Wins + stats.team2Wins + stats.draws).coerceAtLeast(1)
    val team1Share by animateFloatAsState(
        stats.team1Wins.toFloat() / total, tween(BAR_MILLIS, easing = FastOutSlowInEasing), label = "h2hTeam1",
    )
    val team2Share by animateFloatAsState(
        stats.team2Wins.toFloat() / total, tween(BAR_MILLIS, easing = FastOutSlowInEasing), label = "h2hTeam2",
    )
    val (team1Color, team2Color) = rememberSegmentColors(team1Name, team2Name, colors.surface, SEGMENT_MIN_CONTRAST)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        WinCount(team1Label, stats.team1Wins, rememberTeamInk(team1Name), Alignment.Start)
        Spacer(Modifier.weight(1f))
        if (stats.draws > 0) {
            Text(
                text = stringResource(R.string.h2h_tied_or_no_result, stats.draws),
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
        }
        WinCount(team2Label, stats.team2Wins, rememberTeamInk(team2Name), Alignment.End)
    }
    Spacer(Modifier.height(spacing.sm))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(BouncerThemeV2.shapes.small)
            .background(colors.surfaceVariant),
    ) {
        if (team1Share > 0f) Box(Modifier.weight(team1Share).fillMaxHeight().background(team1Color))
        val rest = 1f - team1Share - team2Share
        if (rest > 0f) Spacer(Modifier.weight(rest))
        if (team2Share > 0f) Box(Modifier.weight(team2Share).fillMaxHeight().background(team2Color))
    }
}

@Composable
private fun WinCount(label: String, wins: Int, ink: Color, align: Alignment.Horizontal) {
    val typography = BouncerThemeV2.typography
    Column(horizontalAlignment = align) {
        Text(text = wins.toString(), style = typography.scoreMedium, color = BouncerThemeV2.colors.onSurface)
        Text(text = label, style = typography.labelMedium, color = ink)
    }
}

@Composable
private fun ScoreRecords(stats: HeadToHeadStats, team1Label: String, team2Label: String) {
    val spacing = BouncerThemeV2.spacing
    val rows = listOfNotNull(
        recordRow(R.string.h2h_highest_total, stats.team1HighestScore, stats.team2HighestScore),
        recordRow(R.string.h2h_lowest_total, stats.team1LowestScore, stats.team2LowestScore),
    )
    if (rows.isEmpty()) return
    Spacer(Modifier.height(spacing.md))
    rows.forEachIndexed { index, (label, scores) ->
        if (index > 0) HorizontalDivider(color = BouncerThemeV2.colors.divider)
        RecordRow(stringResource(label), "$team1Label ${scores.first}", "$team2Label ${scores.second}")
    }
}

/** Null when neither side has a figure: the file writes "0" for a record it does not hold. */
private fun recordRow(label: Int, team1: String, team2: String): Pair<Int, Pair<String, String>>? {
    fun known(score: String) = score.isNotBlank() && score != "0"
    if (!known(team1) && !known(team2)) return null
    return label to ((if (known(team1)) team1 else "–") to (if (known(team2)) team2 else "–"))
}

@Composable
private fun RecordRow(label: String, team1: String, team2: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(text = team1, style = typography.numeric, color = colors.onSurface)
        Spacer(Modifier.width(spacing.md))
        Text(text = team2, style = typography.numeric, color = colors.onSurface)
    }
}

@Composable
private fun MeetingRow(meeting: HistoricalMatch, team1Label: String, team2Label: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val result = meeting.winner?.let { winner ->
        listOfNotNull(stringResource(R.string.h2h_won, winner), meeting.margin).joinToString(" ")
    } ?: stringResource(R.string.h2h_no_result)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = result,
                style = typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = "$team1Label ${meeting.team1Score} · $team2Label ${meeting.team2Score}",
                style = typography.numericSmall,
                color = colors.textSecondary,
            )
        }
        Text(
            text = listOf(meeting.date, meeting.venue).filter { it.isNotBlank() }.joinToString(" · "),
            style = typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BAR_HEIGHT = 8.dp
private const val BAR_MILLIS = 900
private const val RECENT_MEETINGS = 5
/** The same floor the win-probability bar uses: a block of colour, not a glyph. */
private const val SEGMENT_MIN_CONTRAST = 2.2f
