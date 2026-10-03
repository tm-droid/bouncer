package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.OverPoint
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint

/**
 * The match's win-probability series as `bouncer-match-sync` recorded it, one point per over.
 *
 * The swing chart used to be the device's own accumulation, so it began when the screen was opened
 * and a reader arriving in the last over saw a flat line. The server now stamps each completed
 * over with the probability at that moment; this reads those stamps, in this screen's team order.
 *
 * Top-level and pure for the same reason as `MatchLiveDetailAlignment`: MatchDetailViewModel is at
 * detekt's function limit, and nothing here needs it.
 */

/** An over worth this many runs is an event on its own, wicket or not. */
private const val BIG_OVER_RUNS = 15

/** The fewest stamped overs that make a series worth drawing instead of the session's own. */
private const val MIN_SERVER_POINTS = 2

internal fun probabilityHistoryFrom(
    scorecard: Scorecard?,
    teams: List<CricketMatch.Team>,
): List<WinProbabilityPoint> {
    val teamId = scorecard?.probabilityTeamId?.takeIf { it.isNotBlank() } ?: return emptyList()
    val flip = serverTeam1IsSecondTeam(teamId, teams)
    val overs = scorecard.overs.sortedWith(compareBy({ it.innings }, { it.over }))
    var offset = 0
    var innings = overs.firstOrNull()?.innings ?: return emptyList()
    var lastOver = 0
    var previous: OverPoint? = null
    val points = mutableListOf<WinProbabilityPoint>()
    for (point in overs) {
        if (point.innings != innings) {
            offset += lastOver
            innings = point.innings
            previous = null
        }
        lastOver = point.over
        val p = point.p
        if (p != null) {
            val team1 = (if (flip) 1.0 - p else p).toFloat().coerceIn(0f, 1f)
            points += WinProbabilityPoint(
                over = (offset + point.over).toFloat(),
                team1Probability = team1,
                team2Probability = 1f - team1,
                keyEvent = keyEventOf(point, previous),
            )
        }
        previous = point
    }
    return points
}

/**
 * What happened in [point]'s over, when the history can say. A wicket count needs the over before
 * it — or the innings' first over, where the count before was zero — so an over that follows a gap
 * names no wicket rather than attributing every wicket since the gap to it.
 */
private fun keyEventOf(point: OverPoint, previous: OverPoint?): String? {
    val fell = wicketsIn(point, previous)
    val runs = point.runs ?: 0
    return when {
        fell == 1 -> "Wicket"
        fell > 1 -> "$fell wickets"
        runs >= BIG_OVER_RUNS -> "$runs runs off the over"
        else -> null
    }
}

/** Wickets that fell in [point]'s over, or 0 when the over before it is not there to compare with. */
private fun wicketsIn(point: OverPoint, previous: OverPoint?): Int {
    val before = when {
        previous != null && previous.over == point.over - 1 -> previous.wickets
        point.over == 1 -> 0
        else -> null
    } ?: return 0
    return (point.wickets ?: return 0) - before
}

/**
 * The series the chart draws: the server's when it has one, ending on the session's latest
 * reading — which is newer than the last completed over, and is the point that carries the
 * model's factors. With no server series the session's own is used unchanged.
 */
internal fun mergedProbabilityHistory(
    server: List<WinProbabilityPoint>,
    session: List<WinProbabilityPoint>,
): List<WinProbabilityPoint> {
    if (server.size < MIN_SERVER_POINTS) return session
    val live = session.lastOrNull() ?: return server
    val end = server.last()
    // Placed just past the last completed over: the session counts overs in the innings being
    // batted, the series counts them across the match, and "now" is after both.
    return server + live.copy(over = end.over + HALF_OVER)
}

private const val HALF_OVER = 0.5f
