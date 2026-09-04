package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
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
 * Which of the two payload slots is batting, found by matching the live score line against each
 * side's latest innings.
 *
 * The payload never names the batting side, and `team1` is the wrong answer for the whole of a
 * second innings. Comparing the scores is the only signal available, and it is exact: `score` and
 * the side aggregates are built from the same Cricbuzz totals.
 */
internal fun battingSlot(payload: MatchLiveDetailPayload): Int? {
    val live = liveInningsScore(payload.score) ?: return null
    return listOf(payload.team1Score, payload.team2Score)
        .indexOfFirst { latestInnings(it) == live }
        .takeIf { it >= 0 }
}

internal fun slotName(payload: MatchLiveDetailPayload, slot: Int): String? =
    (if (slot == 0) payload.team1Name else payload.team2Name)?.takeIf { it.isNotBlank() }

internal fun slotOvers(payload: MatchLiveDetailPayload, slot: Int): String? =
    (if (slot == 0) payload.team1Overs else payload.team2Overs)?.takeIf { it.isNotBlank() }

/**
 * The full team name behind a payload slot, resolved through the match's own teams by team id.
 *
 * A name-based join here would silently never fire, which is the failure mode the id exists to
 * remove.
 */
internal fun matchTeamNameForSlot(
    payload: MatchLiveDetailPayload,
    match: CricketMatch?,
    slot: Int,
): String? {
    val slotId = (if (slot == 0) payload.team1Id else payload.team2Id)?.takeIf { it.isNotBlank() }
        ?: return null
    return match?.teams?.firstOrNull { it.teamId == slotId }?.name?.takeIf { it.isNotBlank() }
}

/**
 * Over notation for each wicket that has fallen in the innings being batted.
 *
 * The scorecard's last innings is the one in progress; earlier innings' wickets belong to a bar
 * that is no longer on screen.
 */
internal fun currentInningsFallOfWicketOvers(payload: MatchLiveDetailPayload): List<String> =
    payload.scorecard?.innings?.lastOrNull()?.fallOfWickets?.map { it.overs }.orEmpty()
