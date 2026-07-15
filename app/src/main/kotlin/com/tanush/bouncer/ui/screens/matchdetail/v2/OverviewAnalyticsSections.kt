package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.DRSReviewData
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.ui.components.FallOfWicket as UiFallOfWicket
import com.tanush.bouncer.ui.components.FallOfWicketsCard
import com.tanush.bouncer.ui.components.HeadToHeadCard
import com.tanush.bouncer.ui.components.HighlightMoment
import com.tanush.bouncer.ui.components.HighlightMomentsCard
import com.tanush.bouncer.ui.components.HistoricalStatsCard
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.MatchPhasesCard
import com.tanush.bouncer.ui.components.MatchPredictionCard
import com.tanush.bouncer.ui.components.OverByOverComparisonCard
import com.tanush.bouncer.ui.components.OverByOverComparisonInfo
import com.tanush.bouncer.ui.components.OverRunRate
import com.tanush.bouncer.ui.components.RunRateComparisonCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.premium.DRSReviewCard
import com.tanush.bouncer.ui.components.premium.LiveScoringData
import com.tanush.bouncer.ui.components.premium.LiveScoringGraphCard
import com.tanush.bouncer.ui.components.premium.PowerPlayAnalysisCard
import com.tanush.bouncer.ui.components.premium.PowerPlayPhase
import com.tanush.bouncer.ui.components.premium.PremiumWinProbabilityHero
import com.tanush.bouncer.ui.components.premium.WinProbabilityChartCard
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// Win-probability, phase, scoring-chart, scorecard, commentary and upcoming sections.

internal fun LazyListScope.overviewWinProbability(
    match: CricketMatch,
    heuristicProb: Double,
    probabilityHistory: List<WinProbabilityPoint>,
    currentRunRate: String?,
    requiredRunRate: String?,
) {
    if (heuristicProb != 50.0 && match.teams.size >= 2) {
        item(key = "win_prob") {
            PremiumWinProbabilityHero(
                team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                team1Probability = (heuristicProb.toFloat() / 100f).coerceIn(0.05f, 0.95f),
            )
        }
    }
    if (probabilityHistory.size >= 2 && match.teams.size >= 2) {
        item(key = "win_prob_chart") {
            WinProbabilityChartCard(
                team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                probabilityHistory = probabilityHistory,
                currentTeam1Probability = probabilityHistory.last().team1Probability,
            )
        }
        item(key = "prediction_card") {
            MatchPredictionCard(
                predictionInfo = predictionInfoFor(match, probabilityHistory, currentRunRate, requiredRunRate),
            )
        }
    }
}

internal fun LazyListScope.overviewPhaseCards(
    match: CricketMatch,
    matchFlow: MatchFlowData?,
    ppPhaseList: List<PowerPlayPhase>,
    matchPhases: List<MatchPhaseData>,
) {
    if (ppPhaseList.isNotEmpty()) {
        val battingTeamName = match.teams.firstOrNull { it.isBatting }?.name
            ?: match.teams.firstOrNull()?.name ?: ""
        item(key = "powerplay_card") {
            PowerPlayAnalysisCard(teamName = battingTeamName, phases = ppPhaseList)
        }
    }
    if (matchPhases.size >= 2 && match.teams.size >= 2) {
        item(key = "match_phases_card") {
            MatchPhasesCard(
                phases = matchPhases,
                team1Name = match.teams[0].name,
                team2Name = match.teams[1].name,
            )
        }
    }
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
}

internal fun LazyListScope.overviewScoringCharts(
    match: CricketMatch,
    hasOverData: Boolean,
    cumulativeScores: List<Int>,
    maxOver: Int,
    wicketOvers: List<Int>,
    perOverRuns: List<OverRunRate>,
    requiredRunRate: String?,
) {
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
    if (perOverRuns.isNotEmpty() && match.teams.size >= 2) {
        item(key = "run_rate_card") {
            RunRateComparisonCard(
                overData = perOverRuns,
                team1Name = match.teams[0].name,
                team2Name = match.teams[1].name,
                requiredRunRate = requiredRunRate?.toDoubleOrNull(),
            )
        }
    }
}

internal fun LazyListScope.overviewScorecardSections(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    uniqueInnings: List<Innings>,
    scorecard: Scorecard?,
    fallOfWicketsByInnings: List<List<UiFallOfWicket>>,
    keyMoments: List<HighlightMoment>,
    headToHead: H2HPair?,
) {
    if (batsmen.isEmpty() && uniqueInnings.isNotEmpty()) {
        item(key = "scorecard_summary_header") { SectionHeaderV2(title = "Innings Summary") }
        items(uniqueInnings.size, key = { "inn_summary_$it" }) { idx -> ScorecardInningsCard(uniqueInnings[idx]) }
    }
    if (scorecard != null) {
        scorecard.innings.forEachIndexed { idx, innings ->
            val uiFow = fallOfWicketsByInnings.getOrNull(idx).orEmpty()
            if (uiFow.isNotEmpty()) {
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
    if (keyMoments.size >= 2) {
        item(key = "key_moments_header") { SectionHeaderV2(title = "Key Moments") }
        item(key = "key_moments_card") { HighlightMomentsCard(moments = keyMoments) }
    }
    headToHead?.let { h2h ->
        item(key = "h2h_card") { HeadToHeadCard(match = match, stats = h2h.stats) }
        item(key = "historical_card") { HistoricalStatsCard(data = h2h.history) }
    }
}

internal fun LazyListScope.overviewCommentary(
    match: CricketMatch,
    isUpcoming: Boolean,
    isCompleted: Boolean,
    streamingCommentary: String,
    meaningfulCommentary: List<String>,
) {
    val hasStreaming = !isUpcoming && streamingCommentary.isNotBlank()
    val hasCommentary = !isUpcoming && (meaningfulCommentary.isNotEmpty() || hasStreaming)
    when {
        hasCommentary -> {
            item(key = "commentary_header") { SectionHeaderV2(title = "Recent Commentary") }
            if (hasStreaming) {
                item(key = "streaming_commentary") {
                    CommentaryLineCard(text = cleanCommentary(streamingCommentary), highlighted = true)
                }
            }
            items(meaningfulCommentary.take(5), key = { line -> "overview_comm_${line.hashCode()}" }) { line ->
                CommentaryLineCard(text = cleanCommentary(line), highlighted = false)
            }
        }
        !isUpcoming -> item(key = "no_commentary") {
            CommentaryLineCard(text = commentaryPausedMessage(match, isCompleted), highlighted = false, muted = true)
        }
    }
}

internal fun LazyListScope.overviewUpcomingInfo(match: CricketMatch, isUpcoming: Boolean) {
    if (!isUpcoming) return
    item(key = "upcoming_info") {
        val colors = BouncerThemeV2.colors
        val typography = BouncerThemeV2.typography
        val spacing = BouncerThemeV2.spacing
        BouncerCard {
            Column(modifier = Modifier.padding(spacing.md)) {
                Text(text = "Match Preview", style = typography.labelMedium, color = colors.textSecondary)
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

@Composable
internal fun ScorecardInningsCard(inn: Innings) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
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

@Composable
internal fun CommentaryLineCard(text: String, highlighted: Boolean, muted: Boolean = false) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val textColor = when {
        highlighted -> colors.primary
        muted -> colors.textSecondary
        else -> colors.onSurface
    }
    BouncerCard {
        Text(
            text = text,
            style = typography.bodySmall,
            color = textColor,
            modifier = Modifier.padding(spacing.md),
        )
    }
}
