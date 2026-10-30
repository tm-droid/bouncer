package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.models.CricketMatch

/**
 * Reading a live payload's score strings.
 *
 * `bouncer-match-sync` publishes three shapes that have to be told apart:
 *
 *  - `score` — the innings being batted, with its over figure: `"234/1 (56.4)"`.
 *  - `team1Score` / `team2Score` — a side's whole contribution, which in a multi-day match is two
 *    innings joined: `"266 & 234/1"`.
 *  - `team1Name` / `team2Name` — Cricbuzz short codes ("EZONE" for Central Zone), which match no
 *    full team name. Anything joining a payload slot to the app's own teams must go by **team id**.
 *
 * "team1" is the side that batted **first**, not `teams[0]` and not the side currently batting.
 */

/** Separates a multi-day side's two innings, matching `teamInningsScore` on both sides of the wire. */
private const val INNINGS_JOIN = " & "

/** `"234/1 (56.4)"` -> `"234/1"`. */
internal fun liveInningsScore(score: String?): String? =
    score?.substringBefore(" (")?.trim()?.takeIf { it.isNotEmpty() }

/** `"234/1 (56.4)"` -> `"56.4"`. Absent when the score carries no over figure. */
internal fun liveOvers(score: String?): String? =
    score?.substringAfter(" (", "")?.substringBefore(")")?.trim()?.takeIf { it.isNotEmpty() }

/** `"234/1"` -> 1. Null when the score is runs alone, which is how a completed innings is written. */
internal fun wicketsIn(score: String): Int? = score.substringAfter('/', "").trim().toIntOrNull()

/** `"266 & 234/1"` -> `"234/1"`. */
internal fun latestInnings(score: String?): String? =
    score?.substringAfterLast(INNINGS_JOIN)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Which of the two payload slots is batting.
 *
 * `situation.battingTeamIsTeam1` is the answer where the row has one: the server compares **team
 * ids**, which is the only comparison that holds for a side batting its second innings in a Test.
 *
 * The score comparison below is the fallback for rows written before `situation` existed. It works
 * — `score` and the side aggregates are built from the same Cricbuzz totals, so the match is
 * exact — but it is string equality, so any formatting drift on the server takes the batting side,
 * the accent colour and the crest out together and silently.
 */
internal fun battingSlot(payload: MatchLiveDetailPayload): Int? {
    payload.situation?.battingTeamIsTeam1?.let { return if (it) 0 else 1 }
    val live = liveInningsScore(payload.score) ?: return null
    return listOf(payload.team1Score, payload.team2Score)
        .indexOfFirst { latestInnings(it) == live }
        .takeIf { it >= 0 }
}

internal fun slotName(payload: MatchLiveDetailPayload, slot: Int): String? =
    (if (slot == 0) payload.team1Name else payload.team2Name)?.takeIf { it.isNotBlank() }

/** A side's whole contribution, which in a multi-day match is two innings joined: `"266 & 234/1"`. */
internal fun slotScore(payload: MatchLiveDetailPayload, slot: Int): String? =
    (if (slot == 0) payload.team1Score else payload.team2Score)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * The full team name behind a payload slot, resolved through the match's own teams.
 *
 * **Team id first**, because that is the only join that cannot be fooled by a short code. But
 * `CricketMatch.Team.teamId` is populated by `CricBuzzScraper` alone — the Appwrite
 * `poll-live-matches` blob does not emit it — so which list source served a match decides whether
 * the id is there at all, and the crest appeared on roughly half of live matches for no reason a
 * reader could see.
 *
 * The acronym fallback closes that. It is safe here in a way a name join is **not** safe for
 * alignment: [singleOrNull] means an ambiguous code resolves to nothing, and the worst outcome is
 * a card with no crest rather than a score against the wrong team. `TeamAcronymManager` reads a
 * curated map — "South Africa" is "RSA", which no initials rule produces — and falls back to
 * [com.tanush.bouncer.utils.acronymFor] when it has not been initialised.
 */
internal fun matchTeamNameForSlot(
    payload: MatchLiveDetailPayload,
    match: CricketMatch?,
    slot: Int,
): String? {
    val teams = match?.teams?.filter { it.name.isNotBlank() }.orEmpty()
    if (teams.isEmpty()) return null

    val slotId = (if (slot == 0) payload.team1Id else payload.team2Id)?.takeIf { it.isNotBlank() }
    if (slotId != null) {
        teams.firstOrNull { it.teamId == slotId }?.let { return it.name }
    }

    val code = slotName(payload, slot) ?: return null
    return teams.singleOrNull { TeamAcronymManager.getAcronym(it.name).equals(code, ignoreCase = true) }?.name
}
