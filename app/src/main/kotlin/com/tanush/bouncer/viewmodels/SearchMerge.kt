package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.SearchRepository.SearchResult.PastPlayerResult
import com.tanush.bouncer.data.SearchRepository.SearchResult.PlayerResult
import com.tanush.bouncer.data.history.HistoryClient
import com.tanush.bouncer.network.PlayerSearchResult
import com.tanush.bouncer.utils.PlayerNameMatch
import com.tanush.bouncer.utils.SearchScore
import com.tanush.bouncer.utils.normalizeForSearch
import com.tanush.bouncer.utils.parseQuery

/** State of the billed online player lookup, shown inline in the Players section. */
enum class PlayerLookupState { IDLE, SEARCHING, UNAVAILABLE }

/**
 * Floor for a player the server returned but the device cannot score — a typo only the server's
 * fuzzy tier caught. Just above the entity threshold: it is a real hit, and a weak one.
 */
internal const val NETWORK_TYPO_FLOOR = 0.45

/** A local player hit at least this good makes the network lookup redundant. */
private const val CONFIDENT_PLAYER_SCORE = 0.95

/** Past players rank below current ones in the same query, as past matches do. */
private const val PAST_PLAYER_WEIGHT = 0.5
private const val MAX_PAST_PLAYERS = 10
private const val MIN_PAST_QUERY_LENGTH = 3

private const val RELEVANT = 0.4
private const val MAX_PLAYER_RESULTS = 10

/**
 * Local player hits plus the server's, ranked on one scale.
 *
 * Network players used to arrive at a flat 0.9, which ranked a substring hit from the server above
 * an exact local one, and lost their Cricbuzz id on the way — so the profile screen re-searched by
 * name and opened whichever same-named player the server listed first. Here every network player
 * is re-scored with the device's own scorer, keeps its id, and fills the id and country into a
 * local hit for the same person instead of duplicating it.
 *
 * [networkQuery] is the normalised query the network results answer. When the user has typed on
 * since, those results are kept only where they still genuinely match — without the floor — so the
 * list neither empties on every keystroke nor shows players the new query does not describe.
 */
internal fun mergePlayers(
    query: String,
    local: List<PlayerResult>,
    network: List<PlayerSearchResult>,
    networkQuery: String?,
): List<PlayerResult> {
    val q = normalizeForSearch(query)
    val current = networkQuery == q
    val merged = local.toMutableList()
    for (player in network) {
        val name = normalizeForSearch(player.name)
        val deviceScore = SearchScore.of(q, name)
        val score = if (current) maxOf(deviceScore, NETWORK_TYPO_FLOOR) else deviceScore
        if (score <= RELEVANT) continue

        val existing = merged.indexOfFirst { same(it, player, name) }
        if (existing >= 0) {
            val hit = merged[existing]
            merged[existing] = hit.copy(
                cricbuzzId = hit.cricbuzzId ?: player.id.takeIf { it.isNotBlank() },
                teamName = hit.teamName.ifBlank { player.country },
            )
        } else {
            merged += PlayerResult(
                playerName = player.name,
                teamName = player.country,
                role = "",
                relevanceScore = score,
                imageUrl = player.imageUrl?.takeIf { it.isNotBlank() },
                cricbuzzId = player.id.takeIf { it.isNotBlank() },
            )
        }
    }
    return merged.sortedByDescending { it.relevanceScore }.take(MAX_PLAYER_RESULTS)
}

/** One person: by Cricbuzz id when both sides have one, else by normalised name. */
private fun same(result: PlayerResult, player: PlayerSearchResult, normalizedName: String): Boolean {
    val id = result.cricbuzzId
    if (id != null && player.id.isNotBlank()) return id == player.id
    return normalizeForSearch(result.playerName) == normalizedName
}

/** Local players from two sources, deduplicated on the normalised name, best score kept. */
internal fun mergeLocalPlayers(vararg sources: List<PlayerResult>): List<PlayerResult> =
    sources.asList().flatten()
        .sortedByDescending { it.relevanceScore }
        .distinctBy { normalizeForSearch(it.playerName) }
        .take(MAX_PLAYER_RESULTS)

/**
 * Whether the Cricbuzz lookup can be skipped. Reads [PlayerResult]s only: a perfect Cricsheet name
 * ([PastPlayerResult]) says nothing about a current player's profile, so it never counts.
 */
internal fun hasConfidentPlayer(players: List<PlayerResult>): Boolean =
    players.any { it.relevanceScore >= CONFIDENT_PLAYER_SCORE }

/**
 * The Cricsheet players that [players] (Cricbuzz) do not already stand for. A history player is hidden
 * when exactly one Cricbuzz row has its surname, first initial and side: that row's page links to the
 * career. With none, or two (an ambiguous name), the player keeps a row of their own.
 */
internal fun attachHistory(history: List<PastPlayerResult>, players: List<PlayerResult>): List<PastPlayerResult> =
    history.filter { h -> players.count { PlayerNameMatch.samePlayer(it.playerName, it.teamName, h.row) } != 1 }

/** Cricsheet players for the query's surname letters; empty for a team pair or a facet-only query. */
internal suspend fun searchPastPlayers(query: String): List<PastPlayerResult> {
    val parsed = parseQuery(normalizeForSearch(query))
    if (parsed.pair != null || parsed.words.isEmpty() || query.trim().length < MIN_PAST_QUERY_LENGTH) return emptyList()
    val name = parsed.words.joinToString(" ")
    val rows = PlayerNameMatch.letters(name).flatMap { HistoryClient.names(it).orEmpty() }
    return PlayerNameMatch.search(name, rows, MAX_PAST_PLAYERS).map { PastPlayerResult(it.row, it.score * PAST_PLAYER_WEIGHT) }
}
