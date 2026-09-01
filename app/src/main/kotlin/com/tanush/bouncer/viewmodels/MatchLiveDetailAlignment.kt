package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.utils.normalizeTeamName

/**
 * Re-orienting the server's team1/team2 onto the screen's team order.
 *
 * `bouncer-match-sync` orients everything it publishes — both scores, and the win probability — to
 * the FIRST-INNINGS batting side, while the screen's order is Cricbuzz's own listing of the
 * fixture. The two agree only when the side listed first also batted first; when they disagree,
 * every score and the probability bar were rendered against the other team, with nothing on screen
 * to indicate it.
 *
 * Top-level rather than methods on the ViewModel: MatchDetailViewModel is at detekt's
 * TooManyFunctions threshold, and these are pure functions of their arguments anyway.
 */

/** Percentages the aggregate file speaks in; the payload speaks in 0..1. */
private const val PERCENT_TOTAL = 100.0

/**
 * True when the server's "team1" is this match's `teams[1]`.
 *
 * A blank name, or one that matches neither side, means "leave it alone": an unrecognised name is
 * not evidence of a swap, and guessing one would corrupt the case that is already correct.
 */
internal fun serverTeam1IsSecondTeam(serverTeam1Name: String?, teams: List<CricketMatch.Team>): Boolean {
    val name = serverTeam1Name?.takeIf { it.isNotBlank() } ?: return false
    val first = teams.getOrNull(0)?.name?.takeIf { it.isNotBlank() } ?: return false
    val second = teams.getOrNull(1)?.name?.takeIf { it.isNotBlank() } ?: return false
    val normalized = normalizeTeamName(name)
    return normalized != normalizeTeamName(first) && normalized == normalizeTeamName(second)
}

/**
 * The payload with its two team slots — and its probability — expressed in [teams] order.
 *
 * The probability is a share of a whole that includes the draw in a Test, so the other side's is
 * `1 - draw - team1`, exactly as the bar itself computes the second segment.
 */
internal fun MatchLiveDetailPayload.alignedTo(teams: List<CricketMatch.Team>): MatchLiveDetailPayload {
    if (!serverTeam1IsSecondTeam(team1Name, teams)) return this
    return copy(
        team1Score = team2Score,
        team1Overs = team2Overs,
        team1Name = team2Name,
        team2Score = team1Score,
        team2Overs = team1Overs,
        team2Name = team1Name,
        winProbabilityTeam1 = winProbabilityTeam1?.let {
            (1.0 - (winProbabilityDraw ?: 0.0) - it).coerceIn(0.0, 1.0)
        },
    )
}

/**
 * The same alignment for the aggregate `live-winprob` file, which names only the side batting now.
 *
 * That is enough: the file also says whether the batting side is the server's team1, so whether the
 * server's team1 is our `teams[0]` is the one comparison this needs. Either field missing means the
 * orientation is unknowable, and an unknown orientation must not move the bar.
 */
internal fun WinProbabilityStore.Probability.alignedTo(
    teams: List<CricketMatch.Team>,
): WinProbabilityStore.Probability {
    val batting = battingTeamName?.takeIf { it.isNotBlank() } ?: return this
    val battingIsServerTeam1 = battingTeamIsTeam1 ?: return this
    val first = teams.getOrNull(0)?.name?.takeIf { it.isNotBlank() } ?: return this
    val battingIsOurFirstTeam = normalizeTeamName(batting) == normalizeTeamName(first)
    if (battingIsOurFirstTeam == battingIsServerTeam1) return this
    return copy(
        team1Percent = (PERCENT_TOTAL - (drawPercent ?: 0.0) - team1Percent)
            .coerceIn(0.0, PERCENT_TOTAL),
    )
}
