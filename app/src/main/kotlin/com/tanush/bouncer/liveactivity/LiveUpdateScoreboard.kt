package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler

/**
 * The match state a Live Update draws, as fields rather than as a paragraph.
 *
 * There are two renderings of the same card and they need the same facts in different shapes. The
 * promotable `BigTextStyle` path can only emit one run of text, so it joins these into lines; the
 * custom-layout path binds them to separate views so the figures form a column and the batting
 * side's score can be large. Deriving both from one structure is what keeps them from drifting —
 * the string form used to be the only form, which made a layout impossible to add without
 * re-parsing it.
 */
data class LiveUpdateScoreboard(
    /** Cricbuzz short code of the side batting, e.g. "NAM". */
    val battingName: String?,
    /** That side's whole contribution: "31/4", or "393 & 46/1" in a multi-day match. */
    val battingScore: String?,
    /** Over figure of the innings in progress, without brackets: "9.5". */
    val battingOvers: String?,
    val opponentName: String?,
    val opponentScore: String?,
    /** Striker first. At most two. */
    val batters: List<PlayerLine>,
    val bowler: PlayerLine?,
    /** Last six deliveries, verbatim from the scraper. Empty when the payload carries none. */
    val recentBalls: List<String>,
    /** "Need 304 runs · RRR 7.59", or Cricbuzz's own prose with its session prefix stripped. */
    val statusLine: String?,
) {
    /** A name and the figures that belong to it, kept apart so they can be laid out in a column. */
    data class PlayerLine(val name: String, val figure: String, val isStriker: Boolean = false)
}

/** How many of `recentBalls` to show. One over's worth, which is what the references show. */
private const val RECENT_BALLS = 6

/** At most two batters are at the crease; a payload listing more is a scrape artefact. */
private const val BATTERS_AT_CREASE = 2

/** Cricbuzz writes a session prefix onto the status: "Day 4: 1st Session - Kent need 371 runs". */
private const val STATUS_PREFIX_SEPARATOR = " - "

internal fun scoreboardOf(payload: MatchLiveDetailPayload, battingSlot: Int?): LiveUpdateScoreboard {
    val opponentSlot = battingSlot?.let { if (it == 0) 1 else 0 }
    return LiveUpdateScoreboard(
        battingName = battingSlot?.let { slotName(payload, it) }
            ?: payload.situation?.battingTeamName?.takeIf { it.isNotBlank() },
        battingScore = battingSlot?.let { slotScore(payload, it) } ?: liveInningsScore(payload.score),
        battingOvers = liveOvers(payload.score),
        opponentName = opponentSlot?.let { slotName(payload, it) },
        opponentScore = opponentSlot?.let { slotScore(payload, it) },
        batters = payload.batsmen
            .sortedByDescending { it.isStriker }
            .take(BATTERS_AT_CREASE)
            .map(::batterLine),
        bowler = payload.bowler?.let(::bowlerLine),
        recentBalls = payload.recentBalls
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .takeLast(RECENT_BALLS),
        statusLine = statusLine(payload),
    )
}

/** "Jan Nicol Loftie-Eaton" / "5 (7)" — the asterisk is a separate flag, not part of the figure. */
private fun batterLine(batsman: ScrapedBatsman) = LiveUpdateScoreboard.PlayerLine(
    name = batsman.name,
    figure = "${batsman.runs} (${batsman.balls})",
    isStriker = batsman.isStriker,
)

/** "Eathan Bosch" / "2/19 (4.5)" — wickets first, as bowling figures are always quoted. */
private fun bowlerLine(bowler: ScrapedBowler): LiveUpdateScoreboard.PlayerLine {
    val overs = bowler.overs.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
    return LiveUpdateScoreboard.PlayerLine(
        name = bowler.name,
        figure = "${bowler.wickets}/${bowler.runs}$overs",
    )
}

/**
 * The one line that says where the match actually stands.
 *
 * A chase is stated from [LiveSituation]'s numbers, which are exact. Otherwise Cricbuzz's own prose
 * is used with its session prefix stripped: "Day 4: 1st Session - Kent need 371 runs" is two facts,
 * and only the second belongs on a card this short. `substringAfterLast` returns the whole string
 * when the separator is absent, so "Innings Break" passes through untouched.
 */
private fun statusLine(payload: MatchLiveDetailPayload): String? {
    chaseLine(payload.situation)?.let { return it }
    return payload.status?.trim()
        ?.substringAfterLast(STATUS_PREFIX_SEPARATOR)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** "Need 304 runs from 40.1 overs · RRR 7.59", from the numbers rather than from prose. */
private fun chaseLine(situation: LiveSituation?): String? {
    val needed = situation?.runsNeeded?.takeIf { it > 0 } ?: return null
    val overs = situation.oversRemaining?.takeIf { it > 0.0 }?.let { "from ${trimOvers(it)} overs" }
    val rate = situation.requiredRunRate?.takeIf { it > 0.0 }?.let { "RRR ${"%.2f".format(it)}" }
    val head = listOfNotNull("Need $needed runs", overs).joinToString(" ")
    return listOfNotNull(head, rate).joinToString(PART_SEPARATOR)
}

/** 10.0 -> "10", 10.3 -> "10.3". An over figure with a trailing ".0" reads like a decimal. */
private fun trimOvers(overs: Double): String =
    if (overs % 1.0 == 0.0) overs.toInt().toString() else overs.toString()
