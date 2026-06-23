package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.MatchStatus
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.DRSReviewData
import com.tanush.bouncer.ui.components.premium.DRSReviewCard
import com.tanush.bouncer.ui.components.premium.LiveScoringData
import com.tanush.bouncer.ui.components.premium.LiveScoringGraphCard
import com.tanush.bouncer.ui.components.premium.PowerPlayAnalysisCard
import com.tanush.bouncer.ui.components.premium.PowerPlayPhase
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.TimelineEventExtractor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.premium.EliteMomentumTracker
import com.tanush.bouncer.ui.components.premium.InsightGridCard
import com.tanush.bouncer.ui.components.premium.MomentumEvent
import com.tanush.bouncer.ui.components.premium.PremiumWinProbabilityHero
import com.tanush.bouncer.ui.components.premium.WinProbabilityChartCard
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.FallOfWicket as ModelFallOfWicket
import com.tanush.bouncer.models.PowerplayEvent
import com.tanush.bouncer.ui.components.DismissalType
import com.tanush.bouncer.ui.components.FallOfWicket as UiFallOfWicket
import com.tanush.bouncer.ui.components.FallOfWicketsCard
import com.tanush.bouncer.ui.components.HighlightHighlightMomentType
import com.tanush.bouncer.ui.components.HighlightMoment
import com.tanush.bouncer.ui.components.HighlightMomentsCard
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.MatchPhasesCard
import com.tanush.bouncer.ui.components.MatchPredictionCard
import com.tanush.bouncer.ui.components.MatchPredictionInfo
import com.tanush.bouncer.ui.components.MomentumShift
import com.tanush.bouncer.ui.components.HeadToHeadCard
import com.tanush.bouncer.ui.components.HistoricalStatsCard
import com.tanush.bouncer.ui.components.OverByOverComparisonCard
import com.tanush.bouncer.ui.components.OverByOverComparisonInfo
import com.tanush.bouncer.ui.components.OverRunRate
import com.tanush.bouncer.ui.components.PredictionFactor
import com.tanush.bouncer.ui.components.PredictionTrend
import com.tanush.bouncer.ui.components.RunRateComparisonCard
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.theme.StatusInfo
import com.tanush.bouncer.ui.theme.StatusLive
import com.tanush.bouncer.ui.theme.StatusSuccess
import kotlin.math.abs

/**
 * OverviewTabV2 — Quick stats, batsmen at crease, current bowler, commentary preview, win probability.
 */
@Composable
fun OverviewTabV2(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    recentBalls: List<String>,
    commentary: List<String>,
    currentRunRate: String?,
    requiredRunRate: String?,
    scorecard: Scorecard? = null,
    streamingCommentary: String = "",
    matchFlow: MatchFlowData? = null,
    probabilityHistory: List<WinProbabilityPoint> = emptyList(),
    headToHead: H2HPair? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val isMatchActive = match.matchStatus == MatchStatus.Live || match.currentStatus.lowercase().let {
        it.contains("stumps") || it.contains("break") || it.contains("rain") || it.contains("delay")
    }
    val isCompleted = match.matchStatus == MatchStatus.Completed
    val isUpcoming = match.matchStatus == MatchStatus.Upcoming ||
        (!isMatchActive && !isCompleted && match.teams.all { it.score.isBlank() || it.score == "0/0" })

    val overMilestones = matchFlow?.overMilestones
    val maxOver = overMilestones?.maxOfOrNull { it.first } ?: 0
    val cumulativeScores: List<Int> = if (!overMilestones.isNullOrEmpty()) {
        buildList {
            var prev = 0
            for (over in 1..maxOver) {
                val milestone = overMilestones.lastOrNull { it.first <= over }
                val cumulative = milestone?.second ?: prev
                add(cumulative)
                prev = cumulative
            }
        }
    } else emptyList()
    val hasOverData = cumulativeScores.isNotEmpty() && match.teams.size >= 2

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.lg,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Match situation card for stumps/break/completed
        if (!match.isLive && match.currentStatus.isNotBlank() && !isUpcoming) {
            item(key = "match_situation") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "Match Status",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = match.currentStatus,
                            style = typography.titleSmall,
                            color = if (isCompleted) colors.win else colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Quick stats row — show for any match with data
        val heuristicProb = match.calculateHeuristicProbability()
        val hasWinPct = heuristicProb != 50.0
        if (currentRunRate != null || requiredRunRate != null || hasWinPct) {
            item(key = "quick_stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    currentRunRate?.let {
                        InsightGridCard(
                            modifier = Modifier.weight(1f),
                            label = "CRR",
                            value = it,
                            subValue = "curr rate",
                            icon = Icons.Default.Speed,
                        )
                    }
                    requiredRunRate?.let {
                        InsightGridCard(
                            modifier = Modifier.weight(1f),
                            label = "RRR",
                            value = it,
                            subValue = "reqd rate",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                        )
                    }
                    if (hasWinPct) {
                        InsightGridCard(
                            modifier = Modifier.weight(1f),
                            label = "WIN %",
                            value = "${heuristicProb.toInt()}%",
                            subValue = match.teams.firstOrNull()?.acronym?.take(3) ?: "--",
                            icon = Icons.Default.AutoAwesome,
                            isHighPriority = true,
                        )
                    }
                }
            }
        }

        // Recent balls strip
        if (recentBalls.isNotEmpty()) {
            item(key = "recent_balls") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "This Over",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        RecentBallsStripV2(balls = recentBalls.takeLast(6))
                    }
                }
            }
        }

        // Last wicket
        val lastWicket = match.score?.lastWicket
        if (!lastWicket.isNullOrBlank() && isMatchActive) {
            item(key = "last_wicket") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "Last Wicket",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = lastWicket,
                            style = typography.bodyMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Momentum tracker — key events derived from current over's ball outcomes
        if (recentBalls.isNotEmpty()) {
            val battingOvers = match.teams.firstOrNull { it.isBatting }?.overs
                ?: match.teams.firstOrNull()?.overs ?: "0"
            val currentOverNum = battingOvers.split(".").firstOrNull()?.toIntOrNull() ?: 0
            val momentumEvents = recentBalls.takeLast(6).mapIndexedNotNull { idx, ball ->
                val overLabel = "$currentOverNum.${idx + 1}"
                when (ball.uppercase()) {
                    "W" -> MomentumEvent(overLabel, "WICKET", "Fall of wicket", "W", Icons.Default.HeartBroken, StatusLive)
                    "6" -> MomentumEvent(overLabel, "SIX", "Boundary — 6 runs", "6", Icons.Default.Flare, StatusSuccess)
                    "4" -> MomentumEvent(overLabel, "FOUR", "Boundary — 4 runs", "4", Icons.Default.Flare, Gold400)
                    "0" -> MomentumEvent(overLabel, "DOT BALL", "Pressure on batting side", "·", Icons.Default.Security, StatusInfo)
                    else -> null
                }
            }
            if (momentumEvents.isNotEmpty()) {
                item(key = "momentum_tracker") {
                    EliteMomentumTracker(events = momentumEvents)
                }
            }
        }

        // At the Crease
        if (batsmen.isNotEmpty()) {
            item(key = "crease_header") {
                SectionHeaderV2(title = "At the Crease")
            }
            items(batsmen, key = { "bat_${it.name}" }) { bat ->
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bat.name + if (bat.isStriker) " *" else "",
                                style = typography.titleSmall,
                                color = colors.onSurface,
                                fontWeight = if (bat.isStriker) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            AnimatedScoreText(
                                text = "${bat.runs} (${bat.balls})",
                                style = typography.scoreMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "SR: ${bat.strikeRate} • 4s: ${bat.fours} • 6s: ${bat.sixes}",
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        } else if ((isMatchActive || isCompleted) && scorecard != null && scorecard.innings.isNotEmpty()) {
            // Fallback: show top batsmen from latest innings when live data unavailable
            val latestInnings = scorecard.innings.last()
            val notOutBatsmen = latestInnings.batsmen.filter {
                it.dismissal.lowercase().contains("not out") || it.dismissal.lowercase().contains("batting")
            }.take(2)
            val topBatsmen = notOutBatsmen.ifEmpty { latestInnings.batsmen.takeLast(2) }
            if (topBatsmen.isNotEmpty()) {
                item(key = "crease_header") {
                    SectionHeaderV2(title = if (notOutBatsmen.isNotEmpty()) "At the Crease" else "Last Batsmen")
                }
                items(topBatsmen.size, key = { "scbat_$it" }) { idx ->
                    val bat = topBatsmen[idx]
                    BouncerCard {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = bat.name,
                                    style = typography.titleSmall,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${bat.runs} (${bat.balls})",
                                    style = typography.scoreMedium,
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(spacing.xs))
                            val details = buildString {
                                append("SR: ${bat.strikeRate}")
                                if (bat.fours != "0") append(" • 4s: ${bat.fours}")
                                if (bat.sixes != "0") append(" • 6s: ${bat.sixes}")
                                if (bat.dismissal.isNotBlank() && !bat.dismissal.lowercase().contains("not out")) {
                                    append(" • ${bat.dismissal}")
                                }
                            }
                            Text(
                                text = details,
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        // Current Bowler
        if (bowler != null) {
            item(key = "bowler_header") {
                SectionHeaderV2(title = "Current Bowler")
            }
            item(key = "bowler") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bowler.name,
                                style = typography.titleSmall,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            AnimatedScoreText(
                                text = "${bowler.overs}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}",
                                style = typography.scoreMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        val econ = bowler.economy.takeIf { it.isNotBlank() }
                            ?: run {
                                val o = bowler.overs.toDoubleOrNull() ?: 0.0
                                val r = bowler.runs.toDoubleOrNull() ?: 0.0
                                if (o > 0) String.format("%.2f", r / o) else null
                            }
                        if (econ != null) {
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = "Econ: $econ",
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        } else if ((isMatchActive || isCompleted) && scorecard != null && scorecard.innings.isNotEmpty()) {
            // Fallback: show top bowler from latest innings
            val latestInnings = scorecard.innings.last()
            val topBowler = latestInnings.bowlers.maxByOrNull { it.wickets.toIntOrNull() ?: 0 }
            if (topBowler != null) {
                item(key = "bowler_header") {
                    SectionHeaderV2(title = "Best Bowler")
                }
                item(key = "bowler") {
                    BouncerCard {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = topBowler.name,
                                    style = typography.titleSmall,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${topBowler.overs}-${topBowler.maidens}-${topBowler.runs}-${topBowler.wickets}",
                                    style = typography.scoreMedium,
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            val econVal = topBowler.economy.takeIf { it.isNotBlank() }
                                ?: run {
                                    val o = topBowler.overs.toDoubleOrNull() ?: 0.0
                                    val r = topBowler.runs.toDoubleOrNull() ?: 0.0
                                    if (o > 0) String.format("%.2f", r / o) else null
                                }
                            if (econVal != null) {
                                Spacer(Modifier.height(spacing.xs))
                                Text(
                                    text = "Econ: $econVal",
                                    style = typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Win Probability — simple hero (always shown when heuristic available)
        if (heuristicProb != 50.0 && match.teams.size >= 2) {
            item(key = "win_prob") {
                PremiumWinProbabilityHero(
                    team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                    team1Probability = (heuristicProb.toFloat() / 100f).coerceIn(0.05f, 0.95f),
                )
            }
        }

        // Win Probability chart — full time-series analytics (shown when history data is available)
        if (probabilityHistory.size >= 2 && match.teams.size >= 2) {
            item(key = "win_prob_chart") {
                WinProbabilityChartCard(
                    team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                    team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                    probabilityHistory = probabilityHistory,
                    currentTeam1Probability = probabilityHistory.last().team1Probability,
                )
            }
        }

        // MatchPredictionCard — win probability framed as a prediction card
        if (probabilityHistory.size >= 2 && match.teams.size >= 2) {
            val latestProb = probabilityHistory.last()
            val prevProb = probabilityHistory[probabilityHistory.size - 2]
            val probDelta = latestProb.team1Probability - prevProb.team1Probability
            val trend = when {
                probDelta > 0.05f -> PredictionTrend.TEAM1_RISING
                probDelta < -0.05f -> PredictionTrend.TEAM2_RISING
                else -> PredictionTrend.STABLE
            }
            item(key = "prediction_card") {
                MatchPredictionCard(
                    predictionInfo = MatchPredictionInfo(
                        team1Name = match.teams[0].name,
                        team2Name = match.teams[1].name,
                        team1WinProbability = latestProb.team1Probability,
                        team2WinProbability = latestProb.team2Probability,
                        predictionTrend = trend,
                        predictedWinner = if (latestProb.team1Probability >= 0.5f) match.teams[0].name else match.teams[1].name,
                        predictionFactors = buildList {
                            val crr = currentRunRate?.toFloatOrNull()
                            val rrr = requiredRunRate?.toFloatOrNull()
                            if (crr != null && rrr != null && rrr > 0f) {
                                add(PredictionFactor(
                                    name = "Run Rate",
                                    impact = ((crr / rrr) - 1f).coerceIn(-1f, 1f),
                                    description = "CRR $crr vs RRR $rrr",
                                ))
                            }
                        },
                    ),
                )
            }
        }

        // Powerplay summary from ESPNCricinfo
        val ppPhases = matchFlow?.powerplays
        if (!ppPhases.isNullOrEmpty()) {
            val battingTeamName = match.teams.firstOrNull { it.isBatting }?.name
                ?: match.teams.firstOrNull()?.name ?: ""
            item(key = "powerplay_card") {
                PowerPlayAnalysisCard(
                    teamName = battingTeamName,
                    phases = ppPhases.map { pp ->
                        PowerPlayPhase(
                            name = "PP${pp.phase}",
                            overs = pp.overs,
                            runs = pp.runs,
                            wickets = pp.wickets,
                            runRate = if (pp.overs.contains("-")) {
                                val parts = pp.overs.split(Regex("[–\\-]"))
                                val start = parts.firstOrNull()?.toDoubleOrNull() ?: 0.0
                                val end = parts.lastOrNull()?.toDoubleOrNull() ?: 6.0
                                val oversPlayed = (end - start).coerceAtLeast(0.1)
                                pp.runs / oversPlayed
                            } else 0.0,
                            boundaries = 0,
                            sixes = 0,
                            dotBalls = 0,
                        )
                    },
                )
            }
        }

        // MatchPhasesCard — full phase breakdown (PP + Middle + Death)
        val matchPhases = buildMatchPhases(
            overMilestones = overMilestones ?: emptyList(),
            powerplays = matchFlow?.powerplays ?: emptyList(),
            scorecard = scorecard,
            isLive = isMatchActive,
        )
        if (matchPhases.size >= 2 && match.teams.size >= 2) {
            item(key = "match_phases_card") {
                MatchPhasesCard(
                    phases = matchPhases,
                    team1Name = match.teams[0].name,
                    team2Name = match.teams[1].name,
                )
            }
        }

        // DRS reviews from ESPNCricinfo — show most recent review
        val latestDrs = matchFlow?.drsReviews?.lastOrNull()
        if (latestDrs != null) {
            item(key = "drs_card") {
                DRSReviewCard(
                    review = DRSReviewData(
                        isSuccessful = latestDrs.isUpheld,
                        over = latestDrs.over,
                        decision = latestDrs.decision,
                        umpireName = latestDrs.umpireName,
                    ),
                )
            }
        }

        // Over-by-over scoring graph from ESPNCricinfo score milestones
        if (hasOverData) {
            item(key = "scoring_graph") {
                LiveScoringGraphCard(
                    scoringData = LiveScoringData(
                        team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                        team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                        team1Scores = cumulativeScores,
                        team2Scores = emptyList(),
                        currentOver = maxOver,
                        projectedScore = cumulativeScores.lastOrNull() ?: 0,
                    ),
                )
            }
        }

        // OverByOverComparisonCard — cumulative scores as dual-line chart
        if (hasOverData) {
            val wicketOvers = scorecard?.innings?.lastOrNull()?.fallOfWickets
                ?.mapNotNull { it.overs.split(".").firstOrNull()?.toIntOrNull() }
                ?: emptyList()
            item(key = "over_by_over_card") {
                OverByOverComparisonCard(
                    comparisonInfo = OverByOverComparisonInfo(
                        team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                        team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                        team1Scores = cumulativeScores,
                        team2Scores = emptyList(),
                        currentOver = maxOver,
                        highlightOvers = wicketOvers,
                    ),
                )
            }
        }

        // RunRateComparisonCard — per-over run differential
        if (!overMilestones.isNullOrEmpty() && match.teams.size >= 2) {
            val perOverRuns = buildList {
                var prev = 0
                overMilestones.sortedBy { it.first }.forEach { (over, cum) ->
                    add(OverRunRate(over = over, team1Runs = cum - prev, team2Runs = null))
                    prev = cum
                }
            }
            item(key = "run_rate_card") {
                RunRateComparisonCard(
                    overData = perOverRuns,
                    team1Name = match.teams[0].name,
                    team2Name = match.teams[1].name,
                    requiredRunRate = requiredRunRate?.toDoubleOrNull(),
                )
            }
        }

        // Scorecard Summary for completed/stumps matches
        if (batsmen.isEmpty() && scorecard != null && scorecard.innings.isNotEmpty()) {
            val uniqueInnings = scorecard.innings.distinctBy { "${it.teamName}_${it.score}_${it.overs}" }
            item(key = "scorecard_summary_header") {
                SectionHeaderV2(title = "Innings Summary")
            }
            items(uniqueInnings.size, key = { "inn_summary_$it" }) { idx ->
                val inn = uniqueInnings[idx]
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = inn.teamName,
                                style = typography.titleSmall,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${inn.score} (${inn.overs})",
                                style = typography.scoreMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // FallOfWicketsCard — from scorecard
        if (scorecard != null) {
            scorecard.innings.forEachIndexed { idx, innings ->
                if (innings.fallOfWickets.isNotEmpty()) {
                    val uiFow = mapFallOfWickets(innings.fallOfWickets, innings.batsmen)
                    item(key = "fow_card_$idx") {
                        FallOfWicketsCard(
                            wickets = uiFow,
                            teamName = innings.teamName,
                            currentScore = innings.score.split("/").firstOrNull()?.toIntOrNull() ?: 0,
                            currentOvers = innings.overs.toFloatOrNull() ?: 0f,
                        )
                    }
                }
            }
        }

        // KeyMomentsCard — pivotal events from probability history
        val keyMoments = probabilityHistory.mapIndexedNotNull { idx, point ->
            val event = point.keyEvent ?: return@mapIndexedNotNull null
            val prevProb = probabilityHistory.getOrNull(idx - 1)
            val impact = if (prevProb != null) {
                (abs(point.team1Probability - prevProb.team1Probability) * 2f).coerceIn(0f, 1f)
            } else 0.5f
            HighlightMoment(
                overNumber = point.over,
                title = event,
                description = "${(point.team1Probability * 100).toInt()}% win probability",
                type = when {
                    event.contains("wicket", ignoreCase = true) -> HighlightHighlightMomentType.WICKET
                    event.contains("six", ignoreCase = true) -> HighlightHighlightMomentType.SIX
                    event.contains("four", ignoreCase = true) || event.contains("boundary", ignoreCase = true) -> HighlightHighlightMomentType.FOUR
                    event.contains("50", ignoreCase = true) || event.contains("half century", ignoreCase = true) -> HighlightHighlightMomentType.FIFTY
                    event.contains("100", ignoreCase = true) || event.contains("century", ignoreCase = true) -> HighlightHighlightMomentType.CENTURY
                    else -> HighlightHighlightMomentType.MILESTONE
                },
                impactScore = impact,
                timestamp = "Over ${point.over.toInt()}",
            )
        }
        if (keyMoments.size >= 2) {
            item(key = "key_moments_header") { SectionHeaderV2(title = "Key Moments") }
            item(key = "key_moments_card") { HighlightMomentsCard(moments = keyMoments) }
        }

        // HeadToHeadCard + HistoricalStatsCard — from Appwrite H2H stats (Cricsheet-derived)
        headToHead?.let { h2h ->
            item(key = "h2h_card") {
                HeadToHeadCard(match = match, stats = h2h.stats)
            }
            item(key = "historical_card") {
                HistoricalStatsCard(data = h2h.history)
            }
        }

        // Recent Commentary (skip for upcoming matches — no meaningful commentary yet)
        val meaningfulCommentary = TimelineEventExtractor.deduplicateByBall(
            commentary
                .map { it.trimEnd('/').trim() }
                .filter { it.isNotBlank() && it.length > 2 && it != "Loading commentary..." }
        )
        val hasStreaming = !isUpcoming && streamingCommentary.isNotBlank()
        val hasCommentary = !isUpcoming && (meaningfulCommentary.isNotEmpty() || hasStreaming)
        if (hasCommentary) {
            item(key = "commentary_header") {
                SectionHeaderV2(title = "Recent Commentary")
            }
            // Show streaming (in-flight) text as the top item while a ball is being processed
            if (hasStreaming) {
                item(key = "streaming_commentary") {
                    val displayStreaming = streamingCommentary
                        .replace(Regex("^\\[AI Fused\\]\\s*"), "")
                        .trimEnd('/')
                        .trim()
                    BouncerCard {
                        Text(
                            text = displayStreaming,
                            style = typography.bodySmall,
                            color = colors.primary,
                            modifier = Modifier.padding(spacing.md),
                        )
                    }
                }
            }
            val previewLines = meaningfulCommentary.take(5)
            items(previewLines, key = { line -> "overview_comm_${line.hashCode()}" }) { line ->
                val formatted = line.replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim()
                BouncerCard {
                    Text(
                        text = formatted,
                        style = typography.bodySmall,
                        color = colors.onSurface,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        } else if (!isUpcoming) {
            item(key = "no_commentary") {
                val msg = when {
                    match.currentStatus.lowercase().contains("stumps") -> "Commentary paused — Stumps"
                    match.currentStatus.lowercase().contains("break") -> "Commentary paused — Session break"
                    match.currentStatus.lowercase().contains("rain") -> "Commentary paused — Rain delay"
                    isCompleted -> "Match completed"
                    else -> "Waiting for commentary..."
                }
                BouncerCard {
                    Text(
                        text = msg,
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        }

        // Upcoming match info
        if (isUpcoming) {
            item(key = "upcoming_info") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "Match Preview",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        if (match.matchTime.isNotBlank()) {
                            Text(
                                text = "Starts at ${match.matchTime}",
                                style = typography.titleSmall,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(spacing.xs))
                        }
                        Text(
                            text = "${match.matchType} • ${match.venue}",
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    BouncerCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = typography.labelSmall, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.xxs))
            Text(text = value, style = typography.titleLarge, color = colors.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WinProbRow(teamName: String, probability: Float) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = teamName,
            style = typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        LinearProgressIndicator(
            progress = { probability },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = colors.primary,
            trackColor = colors.surfaceVariant,
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = "${(probability * 100).toInt()}%",
            style = typography.labelMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

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
    scorecard: com.tanush.bouncer.models.Scorecard?,
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
