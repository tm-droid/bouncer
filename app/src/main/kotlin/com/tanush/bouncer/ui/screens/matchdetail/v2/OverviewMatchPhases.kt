package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.CricketConstants.MatchFormat
import com.tanush.bouncer.models.PowerplayEvent
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.MomentumShift

// Phase breakdown (Powerplay / Middle / Death) derived from ESPNCricinfo over milestones.
//
// Every boundary and threshold here used to be a T20 literal — powerplay 1-6, middle 7-15, death
// 16-20, with run thresholds of 60/30, 80/40 and 70/30. An ODI therefore rendered T20 phases: a
// 10-over powerplay was reported as 6, and the death window stopped at over 20 of 50. Boundaries
// now come from CricketConstants and thresholds scale with the format's par run rate.

/**
 * Phase run thresholds in absolute runs, derived from the format par rate over [overs] overs.
 *
 * Returns null when the format has no par rate (Test, unknown), in which case the caller keeps the
 * phase but reports balanced momentum rather than inventing a comparison.
 */
private fun momentumThresholds(
    format: MatchFormat,
    overs: Int,
    strongFactor: Double,
    weakFactor: Double,
): Pair<Int, Int>? {
    val par = CricketConstants.parRunRate(format) ?: return null
    if (overs <= 0) return null
    val expected = par * overs
    return (expected * strongFactor).toInt() to (expected * weakFactor).toInt()
}

private fun momentumFor(runs: Int, thresholds: Pair<Int, Int>?): MomentumShift = when {
    thresholds == null -> MomentumShift.BALANCED
    runs >= thresholds.first -> MomentumShift.TEAM1_STRONG
    runs <= thresholds.second -> MomentumShift.TEAM2_STRONG
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
    format: MatchFormat,
): List<MatchPhaseData> {
    val ppEndOver = CricketConstants.powerplayEndOver(format)
    val thresholds = momentumThresholds(
        format, ppEndOver,
        CricketConstants.PHASE_POWERPLAY_STRONG_FACTOR,
        CricketConstants.PHASE_POWERPLAY_WEAK_FACTOR,
    )
    if (powerplays.isNotEmpty()) {
        return powerplays.mapIndexed { idx, pp ->
            MatchPhaseData(
                phaseName = "Powerplay ${pp.phase}",
                description = "${pp.runs} runs, ${pp.wickets} wkts",
                overs = pp.overs,
                team1Runs = pp.runs,
                team1Wickets = pp.wickets,
                momentumShift = momentumFor(pp.runs, thresholds),
                isActive = isLive && idx == powerplays.size - 1,
            )
        }
    }
    if (maxOver < ppEndOver) return emptyList()
    val ppRuns = cumRunsAt(overMilestones, ppEndOver)
    val ppWickets = wicketsBetween(scorecard, 1, ppEndOver)
    return listOf(MatchPhaseData(
        phaseName = "Powerplay", description = "$ppRuns runs, $ppWickets wkts",
        overs = "1-$ppEndOver", team1Runs = ppRuns, team1Wickets = ppWickets,
        momentumShift = momentumFor(ppRuns, thresholds),
    ))
}

private fun middleOversPhase(
    overMilestones: List<Pair<Int, Int>>,
    scorecard: Scorecard?,
    format: MatchFormat,
): MatchPhaseData {
    val ppEndOver = CricketConstants.powerplayEndOver(format)
    val middleEndOver = CricketConstants.deathStartOver(format) - 1
    val middleRuns = cumRunsAt(overMilestones, middleEndOver) - cumRunsAt(overMilestones, ppEndOver)
    val middleWickets = wicketsBetween(scorecard, ppEndOver + 1, middleEndOver)
    return MatchPhaseData(
        phaseName = "Middle Overs", description = "$middleRuns runs, $middleWickets wkts",
        overs = "${ppEndOver + 1}-$middleEndOver",
        team1Runs = middleRuns, team1Wickets = middleWickets,
        momentumShift = momentumFor(
            middleRuns,
            momentumThresholds(
                format, middleEndOver - ppEndOver,
                CricketConstants.PHASE_MIDDLE_STRONG_FACTOR,
                CricketConstants.PHASE_MIDDLE_WEAK_FACTOR,
            ),
        ),
    )
}

private fun deathOversPhase(
    maxOver: Int,
    isLive: Boolean,
    overMilestones: List<Pair<Int, Int>>,
    scorecard: Scorecard?,
    format: MatchFormat,
): MatchPhaseData {
    val deathStart = CricketConstants.deathStartOver(format)
    val deathRuns = cumRunsAt(overMilestones, maxOver) - cumRunsAt(overMilestones, deathStart - 1)
    // Bounded by maxOver, not by a hardcoded 20: a rain-shortened or ODI innings ends elsewhere.
    val deathWickets = wicketsBetween(scorecard, deathStart, maxOver)
    return MatchPhaseData(
        phaseName = "Death Overs", description = "$deathRuns runs, $deathWickets wkts",
        overs = "$deathStart-$maxOver", team1Runs = deathRuns, team1Wickets = deathWickets,
        momentumShift = momentumFor(
            deathRuns,
            momentumThresholds(
                format, maxOver - deathStart + 1,
                CricketConstants.PHASE_DEATH_STRONG_FACTOR,
                CricketConstants.PHASE_DEATH_WEAK_FACTOR,
            ),
        ),
        isActive = isLive,
    )
}

internal fun buildMatchPhases(
    overMilestones: List<Pair<Int, Int>>,
    powerplays: List<PowerplayEvent>,
    scorecard: Scorecard?,
    isLive: Boolean,
    format: MatchFormat,
): List<MatchPhaseData> {
    val maxOver = overMilestones.maxOfOrNull { it.first } ?: 0
    val ppEndOver = CricketConstants.powerplayEndOver(format)
    if (maxOver < ppEndOver && powerplays.isEmpty()) return emptyList()

    val deathStart = CricketConstants.deathStartOver(format)
    val phases = mutableListOf<MatchPhaseData>()
    phases += buildPowerplayPhases(powerplays, maxOver, isLive, overMilestones, scorecard, format)
    if (maxOver >= deathStart - 1) phases += middleOversPhase(overMilestones, scorecard, format)
    if (maxOver >= deathStart) {
        phases += deathOversPhase(maxOver, isLive, overMilestones, scorecard, format)
    }
    return phases
}
