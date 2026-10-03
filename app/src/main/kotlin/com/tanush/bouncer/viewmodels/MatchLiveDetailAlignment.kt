package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.models.CricketMatch

/**
 * Re-orienting the server's team1/team2 onto the screen's team order.
 *
 * `bouncer-match-sync` orients everything it publishes — both scores, and the win probability — to
 * the FIRST-INNINGS batting side, while the screen's order is Cricbuzz's own listing of the
 * fixture. The two agree only when the side listed first also batted first; when they disagree,
 * every score and the probability bar were rendered against the wrong team, with nothing on screen
 * to indicate it.
 *
 * Alignment is by Cricbuzz **team id**, not by name: `inningsScoreList` names a side with a short
 * code ("EZONE" for Central Zone, "RSAA" for South Africa A) that matches no full team name, so
 * name comparison would silently never fire. An id the match does not carry — a row cached before
 * the id existed, or a payload from an older function build — means the orientation is unknown,
 * and an unknown orientation must leave the payload exactly as it arrived.
 *
 * Top-level rather than methods on the ViewModel: MatchDetailViewModel is at detekt's
 * TooManyFunctions threshold, and these are pure functions of their arguments anyway.
 */

/** Percentages the aggregate file speaks in; the payload speaks in 0..1. */
private const val PERCENT_TOTAL = 100.0

/** True when the side the server calls team1 is this match's `teams[1]`. */
internal fun serverTeam1IsSecondTeam(serverTeam1Id: String?, teams: List<CricketMatch.Team>): Boolean {
    val id = serverTeam1Id?.takeIf { it.isNotBlank() } ?: return false
    val first = teams.getOrNull(0)?.teamId?.takeIf { it.isNotBlank() } ?: return false
    val second = teams.getOrNull(1)?.teamId?.takeIf { it.isNotBlank() } ?: return false
    return id != first && id == second
}

/**
 * The payload with its two team slots — and its probability — expressed in [teams] order.
 *
 * The probability is a share of a whole that includes the draw in a Test, so the other side's is
 * `1 - draw - team1`, exactly as the bar itself computes its second segment.
 */
internal fun MatchLiveDetailPayload.alignedTo(teams: List<CricketMatch.Team>): MatchLiveDetailPayload {
    if (!serverTeam1IsSecondTeam(team1Id, teams)) return this
    return copy(
        team1Score = team2Score,
        team1Overs = team2Overs,
        team1Name = team2Name,
        team1Id = team2Id,
        team2Score = team1Score,
        team2Overs = team1Overs,
        team2Name = team1Name,
        team2Id = team1Id,
        winProbabilityTeam1 = winProbabilityTeam1?.let {
            (1.0 - (winProbabilityDraw ?: 0.0) - it).coerceIn(0.0, 1.0)
        },
        // Factors exist only where there are two outcomes, so what favoured one side by `d`
        // counted against the other by exactly `d`.
        winProbabilityFactors = winProbabilityFactors.map { it.copy(d = -it.d) },
    )
}

/** The same alignment for the aggregate `live-winprob` file, which carries only the one id. */
internal fun WinProbabilityStore.Probability.alignedTo(
    teams: List<CricketMatch.Team>,
): WinProbabilityStore.Probability {
    if (!serverTeam1IsSecondTeam(team1Id, teams)) return this
    return copy(
        team1Percent = (PERCENT_TOTAL - (drawPercent ?: 0.0) - team1Percent)
            .coerceIn(0.0, PERCENT_TOTAL),
    )
}
