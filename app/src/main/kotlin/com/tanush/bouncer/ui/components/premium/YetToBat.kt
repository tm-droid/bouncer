package com.tanush.bouncer.ui.components.premium

import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.network.MatchSquads
import com.tanush.bouncer.network.SquadPlayer

/**
 * The batting side's named eleven who have not batted in [innings], in batting-order-sheet order.
 *
 * Joined on Cricbuzz profile ids only — the card's `profileUrl` against `SquadPlayer.id` — never on
 * names, which differ between the two pages ("Rahmat Shah (c)" on one, "Rahmat Shah" on the other).
 * The side is the eleven that holds any of the innings' batters. Empty when the elevens are not
 * named, when any batter on the card has no id (a partial join would list a batter who has
 * batted), or when nobody has batted yet.
 */
internal fun yetToBat(innings: Innings, squads: MatchSquads?): List<SquadPlayer> {
    val sheet = squads?.takeIf { it.hasPlayingXi } ?: return emptyList()
    val battedIds = innings.batsmen.map { CricBuzzUrls.profileIdOf(it.profileUrl) }
    if (battedIds.isEmpty() || null in battedIds) return emptyList()
    val batted = battedIds.filterNotNull().toSet()
    val side = listOf(sheet.team1, sheet.team2).firstOrNull { team -> team.playingXi.any { it.id in batted } }
        ?: return emptyList()
    return side.playingXi.filter { it.id !in batted }
}
