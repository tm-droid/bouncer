package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.MatchSquads
import com.tanush.bouncer.network.SquadPlayer
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * [squads] ordered like [CricketMatch.teams], joined on team id — never on names, which on
 * Cricbuzz are short codes on one page and full names on another.
 */
internal fun MatchSquads.alignedTo(match: CricketMatch): MatchSquads {
    val firstId = match.teams.firstOrNull()?.teamId.orEmpty()
    return if (firstId.isNotBlank() && firstId == team2.teamId) MatchSquads(team2, team1) else this
}

/** Playing XI (with the bench behind a toggle) once announced, otherwise each side's squad. */
internal fun LazyListScope.teamSheetItems(match: CricketMatch, squads: MatchSquads?) {
    val sheet = squads?.alignedTo(match) ?: return
    val left: List<SquadPlayer>
    val right: List<SquadPlayer>
    val title: Int
    if (sheet.hasPlayingXi) {
        left = sheet.team1.playingXi
        right = sheet.team2.playingXi
        title = R.string.squads_playing_xi
    } else {
        left = sheet.team1.squad
        right = sheet.team2.squad
        title = R.string.squads_squads
    }
    if (left.isEmpty() && right.isEmpty()) return
    item(key = "team_sheet_header") { SectionHeaderV2(title = stringResource(title)) }
    item(key = "team_sheet") {
        BouncerCard {
            TeamSheetColumns(sheet.team1.shortName, sheet.team2.shortName, left, right)
            if (sheet.hasPlayingXi && (sheet.team1.bench.isNotEmpty() || sheet.team2.bench.isNotEmpty())) {
                BenchToggle(sheet.team1.bench, sheet.team2.bench)
            }
        }
    }
}

@Composable
private fun TeamSheetColumns(leftName: String, rightName: String, left: List<SquadPlayer>, right: List<SquadPlayer>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(Modifier.padding(vertical = spacing.xs)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm)) {
            Text(leftName, style = typography.labelMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(rightName, style = typography.labelMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        }
        for (i in 0 until maxOf(left.size, right.size)) {
            HorizontalDivider(color = colors.divider)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                PlayerCell(left.getOrNull(i), Modifier.weight(1f))
                PlayerCell(right.getOrNull(i), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlayerCell(player: SquadPlayer?, modifier: Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Column(modifier) {
        if (player == null) return@Column
        val marks = listOfNotNull(
            stringResource(R.string.squads_captain_mark).takeIf { player.captain },
            stringResource(R.string.squads_keeper_mark).takeIf { player.keeper },
        )
        val suffix = if (marks.isEmpty()) "" else " (${marks.joinToString(", ")})"
        Text(
            player.name + suffix,
            style = typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (player.role.isNotBlank()) {
            Text(player.role, style = typography.labelSmall, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun BenchToggle(left: List<SquadPlayer>, right: List<SquadPlayer>) {
    val colors = BouncerThemeV2.colors
    var open by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(color = colors.divider)
    TextButton(onClick = { open = !open }, modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.xs)) {
        Text(
            stringResource(if (open) R.string.squads_hide_bench else R.string.squads_show_bench),
            color = colors.primaryText,
        )
    }
    if (open) TeamSheetColumns("", "", left, right)
}
