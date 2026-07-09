package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.FallOfWicket as ModelFallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PowerplayEvent
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.DismissalType
import com.tanush.bouncer.ui.components.FallOfWicket as UiFallOfWicket
import com.tanush.bouncer.ui.components.HighlightHighlightMomentType
import com.tanush.bouncer.ui.components.HighlightMoment
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.MomentumShift
import com.tanush.bouncer.ui.components.OverRunRate
import com.tanush.bouncer.ui.components.premium.PowerPlayPhase
import com.tanush.bouncer.utils.TimelineEventExtractor
import kotlin.math.abs

/** Derived collections for OverviewTabV2, memoized so per-ball recompositions don't re-derive them. */
internal class OverviewDerivedData(
    val cumulativeScores: List<Int>,
    val fallbackBatsmen: List<BatsmanStats>,
    val fallbackHasNotOut: Boolean,
    val ppPhaseList: List<PowerPlayPhase>,
    val matchPhases: List<MatchPhaseData>,
    val wicketOvers: List<Int>,
    val perOverRuns: List<OverRunRate>,
    val uniqueInnings: List<Innings>,
    val fallOfWicketsByInnings: List<List<UiFallOfWicket>>,
    val keyMoments: List<HighlightMoment>,
    val meaningfulCommentary: List<String>,
)

@Composable
internal fun rememberOverviewDerivedData(
    match: CricketMatch,
    scorecard: Scorecard?,
    matchFlow: MatchFlowData?,
    isMatchActive: Boolean,
    probabilityHistory: List<WinProbabilityPoint>,
    commentary: List<String>,
): OverviewDerivedData = remember(match, scorecard, matchFlow, isMatchActive, probabilityHistory, commentary) {
    val overMilestones = matchFlow?.overMilestones.orEmpty()
    val latestInnings = scorecard?.innings?.lastOrNull()
    val notOutBatsmen = latestInnings?.batsmen?.filter {
        val d = it.dismissal.lowercase()
        d.contains("not out") || d.contains("batting")
    }?.take(2).orEmpty()
    val (cumulativeScores, perOverRuns) = buildOverAggregates(overMilestones)

    OverviewDerivedData(
        cumulativeScores = cumulativeScores,
        fallbackBatsmen = notOutBatsmen.ifEmpty { latestInnings?.batsmen?.takeLast(2).orEmpty() },
        fallbackHasNotOut = notOutBatsmen.isNotEmpty(),
        ppPhaseList = matchFlow?.powerplays.orEmpty().map(::toPowerPlayPhase),
        matchPhases = buildMatchPhases(
            overMilestones = overMilestones,
            powerplays = matchFlow?.powerplays ?: emptyList(),
            scorecard = scorecard,
            isLive = isMatchActive,
        ),
        wicketOvers = latestInnings?.fallOfWickets
            ?.mapNotNull { it.overs.split(".").firstOrNull()?.toIntOrNull() }
            ?: emptyList(),
        perOverRuns = perOverRuns,
        uniqueInnings = scorecard?.innings?.distinctBy { "${it.teamName}_${it.score}_${it.overs}" }.orEmpty(),
        fallOfWicketsByInnings = scorecard?.innings.orEmpty().map { innings ->
            if (innings.fallOfWickets.isNotEmpty()) {
                mapFallOfWickets(innings.fallOfWickets, innings.batsmen)
            } else emptyList()
        },
        keyMoments = buildKeyMoments(probabilityHistory),
        meaningfulCommentary = TimelineEventExtractor.deduplicateByBall(
            commentary
                .map { it.trimEnd('/').trim() }
                .filter { it.isNotBlank() && it.length > 2 && it != "Loading commentary..." }
        ),
    )
}

/** Per-over cumulative scores and run deltas derived from milestone points in one pass. */
private fun buildOverAggregates(
    overMilestones: List<Pair<Int, Int>>,
): Pair<List<Int>, List<OverRunRate>> {
    if (overMilestones.isEmpty()) return emptyList<Int>() to emptyList()
    val maxOver = overMilestones.maxOf { it.first }
    val cumulative = buildList {
        var prev = 0
        for (over in 1..maxOver) {
            val cum = overMilestones.lastOrNull { it.first <= over }?.second ?: prev
            add(cum)
            prev = cum
        }
    }
    val perOver = buildList {
        var prev = 0
        overMilestones.sortedBy { it.first }.forEach { (over, cum) ->
            add(OverRunRate(over = over, team1Runs = cum - prev, team2Runs = null))
            prev = cum
        }
    }
    return cumulative to perOver
}

private fun toPowerPlayPhase(pp: PowerplayEvent): PowerPlayPhase = PowerPlayPhase(
    name = "PP${pp.phase}",
    overs = pp.overs,
    runs = pp.runs,
    wickets = pp.wickets,
    runRate = if (pp.overs.contains("-")) {
        val parts = pp.overs.split(Regex("[–\\-]"))
        val start = parts.firstOrNull()?.toDoubleOrNull() ?: 0.0
        val end = parts.lastOrNull()?.toDoubleOrNull() ?: 6.0
        pp.runs / (end - start).coerceAtLeast(0.1)
    } else 0.0,
    boundaries = 0,
    sixes = 0,
    dotBalls = 0,
)

private fun buildKeyMoments(probabilityHistory: List<WinProbabilityPoint>): List<HighlightMoment> =
    probabilityHistory.mapIndexedNotNull { idx, point ->
        val event = point.keyEvent ?: return@mapIndexedNotNull null
        val prevProb = probabilityHistory.getOrNull(idx - 1)
        val impact = if (prevProb != null) {
            (abs(point.team1Probability - prevProb.team1Probability) * 2f).coerceIn(0f, 1f)
        } else 0.5f
        HighlightMoment(
            overNumber = point.over,
            title = event,
            description = "${(point.team1Probability * 100).toInt()}% win probability",
            type = momentTypeFor(event),
            impactScore = impact,
            timestamp = "Over ${point.over.toInt()}",
        )
    }

private val momentKeywordTypes = listOf(
    listOf("wicket") to HighlightHighlightMomentType.WICKET,
    listOf("six") to HighlightHighlightMomentType.SIX,
    listOf("four", "boundary") to HighlightHighlightMomentType.FOUR,
    listOf("50", "half century") to HighlightHighlightMomentType.FIFTY,
    listOf("100", "century") to HighlightHighlightMomentType.CENTURY,
)

private fun momentTypeFor(event: String): HighlightHighlightMomentType =
    momentKeywordTypes.firstOrNull { (keywords, _) ->
        keywords.any { event.contains(it, ignoreCase = true) }
    }?.second ?: HighlightHighlightMomentType.MILESTONE

private fun mapFallOfWickets(
    fowList: List<ModelFallOfWicket>,
    batsmen: List<BatsmanStats>,
): List<UiFallOfWicket> {
    val batsmanByName = batsmen.associateBy { it.name.lowercase().trim() }
    var prevScore = 0
    return fowList
        .sortedBy { it.number }
        .mapIndexed { idx, fow ->
            val stats = batsmanByName[fow.batsman.lowercase().trim()]
            val (dismissalType, bowlerName) = parseDismissal(stats?.dismissal ?: "")
            val score = fow.score.toIntOrNull() ?: 0
            val partnershipRuns = (score - prevScore).coerceAtLeast(0)
            prevScore = score
            UiFallOfWicket(
                wicketNumber = fow.number.takeIf { it > 0 } ?: (idx + 1),
                score = score,
                overs = fow.overs.toFloatOrNull() ?: 0f,
                batsmanName = fow.batsman,
                batsmanRuns = stats?.runs?.toIntOrNull() ?: 0,
                batsmanBalls = stats?.balls?.toIntOrNull() ?: 0,
                bowlerName = bowlerName,
                dismissalType = dismissalType,
                partnershipRuns = partnershipRuns,
            )
        }
}

private fun parseDismissal(dismissal: String): Pair<DismissalType, String> {
    val d = dismissal.lowercase().trim()
    return when {
        d.isBlank() || d.contains("not out") || d.contains("batting") ->
            DismissalType.BOWLED to ""
        d.startsWith("c&b") || d.startsWith("c & b") -> {
            val bowler = dismissal.replaceFirst(Regex("^[Cc][\\s&bB]+"), "").trim()
            DismissalType.CAUGHT_AND_BOWLED to bowler
        }
        d.startsWith("lbw") -> {
            val bowler = dismissal.replaceFirst(Regex("(?i)^lbw\\s+b\\s+"), "").trim()
            DismissalType.LBW to bowler
        }
        d.startsWith("run out") -> {
            val fielder = dismissal
                .replaceFirst(Regex("(?i)^run out\\s*\\("), "")
                .removeSuffix(")")
                .trim()
            DismissalType.RUN_OUT to fielder
        }
        d.startsWith("st ") -> {
            val bIdx = dismissal.indexOf(" b ", ignoreCase = true)
            val bowler = if (bIdx >= 0) dismissal.substring(bIdx + 3).trim() else ""
            DismissalType.STUMPED to bowler
        }
        d.startsWith("hit wicket") -> {
            val bowler = dismissal.replaceFirst(Regex("(?i)^hit wicket\\s+b\\s+"), "").trim()
            DismissalType.HIT_WICKET to bowler
        }
        d.startsWith("c ") && d.contains(" b ") -> {
            val bIdx = dismissal.indexOf(" b ", ignoreCase = true)
            val fielder = if (bIdx >= 0) dismissal.substring(2, bIdx).trim() else ""
            val bowler = if (bIdx >= 0) dismissal.substring(bIdx + 3).trim() else ""
            if (fielder.startsWith("†") || fielder.lowercase() == "wk") {
                DismissalType.CAUGHT_BEHIND to bowler
            } else {
                DismissalType.CAUGHT to bowler
            }
        }
        d.startsWith("b ") -> DismissalType.BOWLED to dismissal.substring(2).trim()
        else -> DismissalType.CAUGHT to ""
    }
}

private fun buildMatchPhases(
    overMilestones: List<Pair<Int, Int>>,
    powerplays: List<PowerplayEvent>,
    scorecard: Scorecard?,
    isLive: Boolean,
): List<MatchPhaseData> {
    val maxOver = overMilestones.maxOfOrNull { it.first } ?: 0
    if (maxOver < 6 && powerplays.isEmpty()) return emptyList()

    val phases = mutableListOf<MatchPhaseData>()
    val lastInnings = scorecard?.innings?.lastOrNull()

    fun cumAt(over: Int): Int = overMilestones.lastOrNull { it.first <= over }?.second ?: 0
    fun wicketsIn(from: Int, to: Int): Int =
        lastInnings?.fallOfWickets?.count { fow ->
            (fow.overs.split(".").firstOrNull()?.toIntOrNull() ?: 0) in from..to
        } ?: 0

    if (powerplays.isNotEmpty()) {
        powerplays.forEachIndexed { idx, pp ->
            phases.add(MatchPhaseData(
                phaseName = "Powerplay ${pp.phase}",
                description = "${pp.runs} runs, ${pp.wickets} wkts",
                overs = pp.overs,
                team1Runs = pp.runs,
                team1Wickets = pp.wickets,
                momentumShift = when {
                    pp.runs >= 60 -> MomentumShift.TEAM1_STRONG
                    pp.runs <= 30 -> MomentumShift.TEAM2_STRONG
                    else -> MomentumShift.BALANCED
                },
                isActive = isLive && idx == powerplays.size - 1,
            ))
        }
    } else if (maxOver >= 6) {
        val ppRuns = cumAt(6)
        val ppWickets = wicketsIn(1, 6)
        phases.add(MatchPhaseData(
            phaseName = "Powerplay", description = "$ppRuns runs, $ppWickets wkts",
            overs = "1-6", team1Runs = ppRuns, team1Wickets = ppWickets,
            momentumShift = when {
                ppRuns >= 60 -> MomentumShift.TEAM1_STRONG
                ppRuns <= 30 -> MomentumShift.TEAM2_STRONG
                else -> MomentumShift.BALANCED
            },
        ))
    }

    if (maxOver >= 15) {
        val middleRuns = cumAt(15) - cumAt(6)
        val middleWickets = wicketsIn(7, 15)
        phases.add(MatchPhaseData(
            phaseName = "Middle Overs", description = "$middleRuns runs, $middleWickets wkts",
            overs = "7-15", team1Runs = middleRuns, team1Wickets = middleWickets,
            momentumShift = when {
                middleRuns >= 80 -> MomentumShift.TEAM1_STRONG
                middleRuns <= 40 -> MomentumShift.TEAM2_STRONG
                else -> MomentumShift.BALANCED
            },
        ))
    }

    if (maxOver >= 16) {
        val deathRuns = cumAt(maxOver) - cumAt(15)
        val deathWickets = wicketsIn(16, 20)
        phases.add(MatchPhaseData(
            phaseName = "Death Overs", description = "$deathRuns runs, $deathWickets wkts",
            overs = "16-$maxOver", team1Runs = deathRuns, team1Wickets = deathWickets,
            momentumShift = when {
                deathRuns >= 70 -> MomentumShift.TEAM1_STRONG
                deathRuns <= 30 -> MomentumShift.TEAM2_STRONG
                else -> MomentumShift.BALANCED
            },
            isActive = isLive,
        ))
    }

    return phases
}
