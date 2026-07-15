package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.PowerplayEvent
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.MomentumShift

// Phase breakdown (Powerplay / Middle / Death) derived from ESPNCricinfo over milestones.

private fun momentumFor(runs: Int, strongAt: Int, weakAt: Int): MomentumShift = when {
    runs >= strongAt -> MomentumShift.TEAM1_STRONG
    runs <= weakAt -> MomentumShift.TEAM2_STRONG
    else -> MomentumShift.BALANCED
}

private fun cumRunsAt(overMilestones: List<Pair<Int, Int>>, over: Int): Int =
    overMilestones.lastOrNull { it.first <= over }?.second ?: 0

private fun wicketsBetween(scorecard: Scorecard?, from: Int, to: Int): Int =
    scorecard?.innings?.lastOrNull()?.fallOfWickets?.count { fow ->
        (fow.overs.split(".").firstOrNull()?.toIntOrNull() ?: 0) in from..to
    } ?: 0

private fun buildPowerplayPhases(
    powerplays: List<PowerplayEvent>,
    maxOver: Int,
    isLive: Boolean,
    overMilestones: List<Pair<Int, Int>>,
    scorecard: Scorecard?,
): List<MatchPhaseData> {
    if (powerplays.isNotEmpty()) {
        return powerplays.mapIndexed { idx, pp ->
            MatchPhaseData(
                phaseName = "Powerplay ${pp.phase}",
                description = "${pp.runs} runs, ${pp.wickets} wkts",
                overs = pp.overs,
                team1Runs = pp.runs,
                team1Wickets = pp.wickets,
                momentumShift = momentumFor(pp.runs, 60, 30),
                isActive = isLive && idx == powerplays.size - 1,
            )
        }
    }
    if (maxOver < 6) return emptyList()
    val ppRuns = cumRunsAt(overMilestones, 6)
    val ppWickets = wicketsBetween(scorecard, 1, 6)
    return listOf(MatchPhaseData(
        phaseName = "Powerplay", description = "$ppRuns runs, $ppWickets wkts",
        overs = "1-6", team1Runs = ppRuns, team1Wickets = ppWickets,
        momentumShift = momentumFor(ppRuns, 60, 30),
    ))
}

private fun middleOversPhase(
    overMilestones: List<Pair<Int, Int>>,
    scorecard: Scorecard?,
): MatchPhaseData {
    val middleRuns = cumRunsAt(overMilestones, 15) - cumRunsAt(overMilestones, 6)
    val middleWickets = wicketsBetween(scorecard, 7, 15)
    return MatchPhaseData(
        phaseName = "Middle Overs", description = "$middleRuns runs, $middleWickets wkts",
        overs = "7-15", team1Runs = middleRuns, team1Wickets = middleWickets,
        momentumShift = momentumFor(middleRuns, 80, 40),
    )
}

private fun deathOversPhase(
    maxOver: Int,
    isLive: Boolean,
    overMilestones: List<Pair<Int, Int>>,
    scorecard: Scorecard?,
): MatchPhaseData {
    val deathRuns = cumRunsAt(overMilestones, maxOver) - cumRunsAt(overMilestones, 15)
    val deathWickets = wicketsBetween(scorecard, 16, 20)
    return MatchPhaseData(
        phaseName = "Death Overs", description = "$deathRuns runs, $deathWickets wkts",
        overs = "16-$maxOver", team1Runs = deathRuns, team1Wickets = deathWickets,
        momentumShift = momentumFor(deathRuns, 70, 30),
        isActive = isLive,
    )
}

internal fun buildMatchPhases(
    overMilestones: List<Pair<Int, Int>>,
    powerplays: List<PowerplayEvent>,
    scorecard: Scorecard?,
    isLive: Boolean,
): List<MatchPhaseData> {
    val maxOver = overMilestones.maxOfOrNull { it.first } ?: 0
    if (maxOver < 6 && powerplays.isEmpty()) return emptyList()

    val phases = mutableListOf<MatchPhaseData>()
    phases += buildPowerplayPhases(powerplays, maxOver, isLive, overMilestones, scorecard)
    if (maxOver >= 15) phases += middleOversPhase(overMilestones, scorecard)
    if (maxOver >= 16) phases += deathOversPhase(maxOver, isLive, overMilestones, scorecard)
    return phases
}
