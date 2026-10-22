package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.HighlightMomentsCard
import com.tanush.bouncer.ui.components.MatchPhasesCard
import com.tanush.bouncer.ui.components.MatchPredictionCard
import com.tanush.bouncer.ui.components.RunRateComparisonCard
import com.tanush.bouncer.ui.components.premium.LiveScoringData
import com.tanush.bouncer.ui.components.premium.LiveScoringGraphCard
import com.tanush.bouncer.ui.components.premium.WinProbabilityChartCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Every chart the match has data for, and nothing else: the win-probability arc with the moments
 * that moved it and what the model weighed, the worm, the manhattan, the phases, and where each
 * batter's runs went. They used to sit between the batting pair and the commentary on Overview.
 *
 * Offered only when [hasGraphs] is true, so it is never a tab of empty states.
 */
@Composable
internal fun GraphsTabV2(
    match: CricketMatch,
    scorecard: Scorecard?,
    derived: OverviewDerivedData,
    probabilityHistory: List<WinProbabilityPoint>,
    requiredRunRate: String?,
) {
    val spacing = BouncerThemeV2.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        graphsWinProbability(match, probabilityHistory, derived)
        graphsScoring(match, derived, requiredRunRate)
        if (derived.matchPhases.isNotEmpty() && match.teams.size >= 2) {
            item(key = "match_phases_card") {
                val (team1Color, team2Color) = rememberMatchChartColors(match)
                MatchPhasesCard(
                    team1Color = team1Color,
                    team2Color = team2Color,
                    phases = derived.matchPhases,
                    team1Name = match.teams[0].name,
                    team2Name = match.teams[1].name,
                )
            }
        }
        graphsWagonWheels(scorecard)
        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

/** Whether [GraphsTabV2] would draw at least one chart. Each clause is one of its gates. */
internal fun hasGraphs(
    match: CricketMatch,
    scorecard: Scorecard?,
    derived: OverviewDerivedData,
    probabilityHistory: List<WinProbabilityPoint>,
): Boolean {
    val wheels = scorecard?.innings.orEmpty().indices.any { wagonWheelsFor(scorecard?.wagon.orEmpty(), it + 1).isNotEmpty() }
    // Drawn only against two named sides.
    val sided = listOf(
        probabilityHistory.size >= 2,
        derived.cumulativeScores.isNotEmpty(),
        derived.perOverRuns.isNotEmpty(),
        derived.matchPhases.isNotEmpty(),
    )
    val unsided = listOf(derived.keyMoments.size >= KEY_MOMENTS_MIN, derived.worm != null, wheels)
    return (match.teams.size >= 2 && sided.any { it }) || unsided.any { it }
}

/** One key moment is a point, not a story; the card needs two to say anything. */
private const val KEY_MOMENTS_MIN = 2

private fun LazyListScope.graphsWinProbability(
    match: CricketMatch,
    probabilityHistory: List<WinProbabilityPoint>,
    derived: OverviewDerivedData,
) {
    if (probabilityHistory.size >= 2 && match.teams.size >= 2) {
        item(key = "win_prob_chart") {
            val (team1Color, team2Color) = rememberMatchChartColors(match)
            WinProbabilityChartCard(
                team1Color = team1Color,
                team2Color = team2Color,
                team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                probabilityHistory = probabilityHistory,
            )
        }
    }
    // The moments are the chart's own key events, so they sit under it.
    if (derived.keyMoments.size >= KEY_MOMENTS_MIN) {
        item(key = "key_moments_header") { SectionHeaderV2(title = stringResource(R.string.overview_key_moments)) }
        item(key = "key_moments_card") { HighlightMomentsCard(moments = derived.keyMoments) }
    }
    if (probabilityHistory.size >= 2 && match.teams.size >= 2) {
        item(key = "prediction_card") {
            MatchPredictionCard(
                predictionInfo = predictionInfoFor(
                    match,
                    probabilityHistory,
                    factorLabels = mapOf(
                        "teams" to stringResource(R.string.prediction_factor_teams),
                        "ground" to stringResource(R.string.prediction_factor_ground),
                        "elevens" to stringResource(R.string.prediction_factor_elevens),
                    ),
                ),
            )
        }
    }
}

/** The worm and manhattan from the server's over history, or from Cricinfo's milestones without it. */
private fun LazyListScope.graphsScoring(match: CricketMatch, derived: OverviewDerivedData, requiredRunRate: String?) {
    val worm = derived.worm
    if (worm != null) {
        wormCharts(match, worm, requiredRunRate)
        return
    }
    if (match.teams.size < 2) return
    if (derived.cumulativeScores.isNotEmpty()) {
        item(key = "scoring_graph") {
            val (team1Color, team2Color) = rememberMatchChartColors(match)
            LiveScoringGraphCard(
                scoringData = LiveScoringData(
                    team1Color = team1Color,
                    team2Color = team2Color,
                    team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                    team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                    team1Scores = derived.cumulativeScores,
                    team2Scores = emptyList(),
                    currentOver = derived.cumulativeScores.size,
                    projectedScore = MatchProjections.projectedTotalFor(match, derived.cumulativeScores),
                ),
            )
        }
    }
    if (derived.perOverRuns.isNotEmpty()) {
        item(key = "run_rate_card") {
            val (team1Color, team2Color) = rememberMatchChartColors(match)
            RunRateComparisonCard(
                overData = derived.perOverRuns,
                team1Color = team1Color,
                team2Color = team2Color,
                team1Name = match.teams[0].name,
                team2Name = match.teams[1].name,
                requiredRunRate = requiredRunRate?.toDoubleOrNull(),
            )
        }
    }
}

/** One row of wheels per innings, newest first: during a chase the chase is what is being read. */
private fun LazyListScope.graphsWagonWheels(scorecard: Scorecard?) {
    val innings = scorecard?.innings.orEmpty()
    for (idx in innings.indices.reversed()) {
        val wheels = wagonWheelsFor(scorecard?.wagon.orEmpty(), idx + 1)
        if (wheels.isEmpty()) continue
        item(key = "wagon_header_$idx") {
            SectionHeaderV2(title = stringResource(R.string.graphs_wagon_title, innings[idx].teamName))
        }
        item(key = "wagon_wheels_$idx") { WagonWheelRow(wheels, edgePadding = 0.dp) }
    }
}
