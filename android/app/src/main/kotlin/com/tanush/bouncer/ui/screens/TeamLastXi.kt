package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.MatchSquads
import com.tanush.bouncer.network.SquadPlayer
import com.tanush.bouncer.ui.components.v2.BouncerRowGroup
import com.tanush.bouncer.ui.components.v2.PersonAvatar
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The eleven [teamName] named for [match], or empty.
 *
 * The page is keyed by the side's name, so the name picks the side out of the match; the team
 * sheet is then joined on that side's Cricbuzz team id, never on names (one page says "AFG", the
 * other "Afghanistan").
 */
internal fun lastElevenOf(match: CricketMatch, teamName: String, squads: MatchSquads?): List<SquadPlayer> {
    val teamId = match.teams.firstOrNull { it.name.equals(teamName, ignoreCase = true) }?.teamId
        ?.takeIf { it.isNotBlank() } ?: return emptyList()
    val side = listOfNotNull(squads?.team1, squads?.team2).firstOrNull { it.teamId == teamId } ?: return emptyList()
    return side.playingXi
}

/** The side's last eleven, each opening their profile. Nothing when the sheet has not loaded. */
internal fun LazyListScope.teamLastXiItems(
    match: CricketMatch?,
    teamName: String,
    players: List<SquadPlayer>,
    onPlayerClick: (name: String, cricbuzzId: String?) -> Unit,
) {
    if (match == null || players.isEmpty()) return
    val opponent = match.teams.firstOrNull { !it.name.equals(teamName, ignoreCase = true) }
    item(key = "last_xi_header") {
        SectionHeaderV2(
            title = stringResource(R.string.teams_last_xi, opponent?.acronym?.ifBlank { null } ?: opponent?.name.orEmpty()),
        )
    }
    item(key = "last_xi") {
        BouncerRowGroup(players) { player -> ElevenRow(player) { onPlayerClick(player.name, player.id.ifBlank { null }) } }
    }
}

@Composable
private fun ElevenRow(player: SquadPlayer, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.cd_open_player, player.name), onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(name = player.name, imageUrl = player.imageUrl, id = player.id, size = 28.dp)
        Spacer(Modifier.width(spacing.sm))
        Column(Modifier.weight(1f)) {
            val marks = listOfNotNull(
                stringResource(R.string.squads_captain_mark).takeIf { player.captain },
                stringResource(R.string.squads_keeper_mark).takeIf { player.keeper },
            )
            val name = if (marks.isEmpty()) player.name else "${player.name} (${marks.joinToString(", ")})"
            Text(name, style = typography.bodyMedium, color = colors.onSurface, maxLines = 1)
            if (player.role.isNotBlank()) {
                Text(player.role, style = typography.labelMedium, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}
