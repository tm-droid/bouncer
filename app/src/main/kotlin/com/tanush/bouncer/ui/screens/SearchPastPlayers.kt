package com.tanush.bouncer.ui.screens

import androidx.compose.ui.res.pluralStringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.viewmodels.SearchTab

/** Cricsheet-only players (mostly retired); a tap opens their career page. */
internal fun pastPlayersSection(
    players: List<SearchRepository.SearchResult.PastPlayerResult>,
    limit: Int,
    compact: Boolean,
    query: String,
    actions: SearchActions,
): SearchSection? = players.sectionOrNull(
    R.string.search_section_past_players, "pp", SearchTab.PLAYERS, limit, compact, actions,
    key = { it.row.id },
) { result ->
    PlayerSearchRow(
        playerName = result.row.name,
        teamName = result.row.team,
        role = pluralStringResource(R.plurals.search_past_player_matches, result.row.matches, result.row.matches),
        imageUrl = null,
        query = query,
        onClick = { actions.onHistoryPlayer(result.row.id, result.row.name) },
    )
}
