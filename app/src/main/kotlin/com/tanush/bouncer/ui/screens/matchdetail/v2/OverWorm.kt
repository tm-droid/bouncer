package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.lazy.LazyListScope
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.OverPoint
import com.tanush.bouncer.ui.components.OverRunRate
import com.tanush.bouncer.ui.components.RunRateComparisonCard
import com.tanush.bouncer.ui.components.premium.LiveScoringData
import com.tanush.bouncer.ui.components.premium.LiveScoringGraphCard

/**
 * Both innings of a limited-overs match, as the worm and the manhattan draw them.
 *
 * Built from `Scorecard.overs`. The two charts used to plot one line — `team2Scores` was hardcoded
 * empty — because their only source, Cricinfo's "50 runs in 6.3 overs" milestones, carried no
 * innings, so the second innings overwrote the first.
 *
 * [team1Scores]/[team2Scores] are cumulative scores for overs 1..n with no gaps: a missing over
 * ends the series there, because the chart has no way to draw a hole and a carried-forward flat
 * line would state a maiden that was not bowled. [perOver] keeps every over it has a figure for,
 * gaps included — each bar is its own fact.
 */
internal data class OverWorm(
    val team1Name: String,
    val team2Name: String,
    val team1Scores: List<Int>,
    val team2Scores: List<Int>,
    val perOver: List<OverRunRate>,
    val maxOver: Int,
)

/** Null when the first innings has no plottable over 1. Innings 3+ (a Test) are not a worm. */
internal fun overWormOf(overs: List<OverPoint>): OverWorm? {
    val first = overs.filter { it.innings == 1 }.associateBy { it.over }
    val second = overs.filter { it.innings == 2 }.associateBy { it.over }
    val team1Scores = contiguousScores(first)
    if (team1Scores.isEmpty()) return null
    val team2Scores = contiguousScores(second)
    val allOvers = (first.keys + second.keys).sorted()
    val perOver = allOvers.mapNotNull { over ->
        val a = first[over]?.runs
        val b = second[over]?.runs
        if (a == null) return@mapNotNull null
        OverRunRate(over = over, team1Runs = a, team2Runs = b)
    }
    return OverWorm(
        team1Name = first.values.firstNotNullOfOrNull { it.team }.orEmpty(),
        team2Name = second.values.firstNotNullOfOrNull { it.team }.orEmpty(),
        team1Scores = team1Scores,
        team2Scores = team2Scores,
        perOver = perOver,
        maxOver = maxOf(team1Scores.size, team2Scores.size),
    )
}

private fun contiguousScores(byOver: Map<Int, OverPoint>): List<Int> = buildList {
    var over = 1
    while (true) {
        val score = byOver[over]?.score ?: break
        add(score)
        over++
    }
}

/**
 * Worm and manhattan for both innings, labelled by the side the data names. The over-by-over
 * comparison card that sat between them plotted the worm's own cumulative series a second time.
 */
internal fun LazyListScope.wormCharts(
    match: CricketMatch,
    worm: OverWorm,
    requiredRunRate: String?,
) {
    val format = CricketConstants.formatFromMatchType(match.matchType, match.matchTitle)
    val totalOvers = CricketConstants.inningsBalls(format)?.div(BALLS_PER_OVER) ?: worm.maxOver
    val battingScores = worm.team2Scores.ifEmpty { worm.team1Scores }
    item(key = "scoring_graph") {
        val (team1Color, team2Color) = rememberChartColorsFor(match, worm.team1Name)
        LiveScoringGraphCard(
            scoringData = LiveScoringData(
                team1Color = team1Color,
                team2Color = team2Color,
                team1Name = worm.team1Name,
                team2Name = worm.team2Name,
                team1Scores = worm.team1Scores,
                team2Scores = worm.team2Scores,
                currentOver = worm.maxOver,
                projectedScore = MatchProjections.projectedTotalFor(match, battingScores),
                totalOvers = totalOvers,
            ),
        )
    }
    if (worm.perOver.isNotEmpty()) {
        item(key = "run_rate_card") {
            val (team1Color, team2Color) = rememberChartColorsFor(match, worm.team1Name)
            RunRateComparisonCard(
                overData = worm.perOver,
                team1Color = team1Color,
                team2Color = team2Color,
                team1Name = worm.team1Name,
                team2Name = worm.team2Name,
                requiredRunRate = requiredRunRate?.toDoubleOrNull(),
            )
        }
    }
}

private const val BALLS_PER_OVER = 6
