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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.tanush.bouncer.data.remote.MatchContextStore
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.viewmodels.mergedProbabilityHistory
import com.tanush.bouncer.viewmodels.probabilityHistoryFrom
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
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsCricket
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
import com.tanush.bouncer.ui.components.PredictionTrend
import com.tanush.bouncer.ui.components.RunRateComparisonCard
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.theme.StatusInfo
import com.tanush.bouncer.ui.theme.StatusLive
import com.tanush.bouncer.ui.theme.StatusSuccess
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

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
    val spacing = BouncerThemeV2.spacing

    val isMatchActive = matchIsActive(match)
    val isCompleted = match.matchStatus == MatchStatus.Completed
    val isUpcoming = matchIsUpcoming(match, isMatchActive, isCompleted)

    // The whole match's series where the server recorded one, not only this screen's time open.
    val history = remember(scorecard, probabilityHistory, match.teams) {
        mergedProbabilityHistory(probabilityHistoryFrom(scorecard, match.teams), probabilityHistory)
    }

    val overMilestones = matchFlow?.overMilestones
    val maxOver = overMilestones?.maxOfOrNull { it.first } ?: 0

    // This tab recomposes on every ball of a live match; all list transforms are
    // remembered against their inputs so recomposition never re-derives them.
    val derived = rememberOverviewDerivedData(
        match = match,
        scorecard = scorecard,
        matchFlow = matchFlow,
        isMatchActive = isMatchActive,
        probabilityHistory = history,
        commentary = commentary,
    )
    val hasOverData = derived.cumulativeScores.isNotEmpty() && match.teams.size >= 2
    val winProb = match.winProbability
    // The preview or summary, from the match's context file. One small download per match open.
    val context by produceState<MatchContext?>(initialValue = null, match.id) {
        value = MatchContextStore.get(match.id)
    }
    val surface = matchSurfaceFor(context, isCompleted)
    val hasAnyContent = surface != null || hasAnyOverviewContent(
        match, batsmen, bowler, recentBalls, scorecard, commentary,
        history, matchFlow, headToHead, currentRunRate, requiredRunRate, isUpcoming,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.lg,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        overviewHeaderSections(isUpcoming, hasAnyContent)
        // First on a fixture or a finished match, where it is the most there is to read; below
        // the live cards on a match in play, where the score comes first.
        if (!isMatchActive) overviewMatchSurface(surface)
        overviewLiveSituation(match, isMatchActive)
        overviewBatsmen(batsmen, isMatchActive, isCompleted, derived.fallbackBatsmen, derived.fallbackHasNotOut)
        overviewBowler(bowler, isMatchActive, isCompleted, scorecard)
        overviewWinProbability(match, winProb, history, currentRunRate, requiredRunRate)
        if (isMatchActive) overviewMatchSurface(surface)
        overviewPhaseCards(match, matchFlow, derived.ppPhaseList, derived.matchPhases)
        overviewScoringCharts(
            match, derived.worm, hasOverData, derived.cumulativeScores, maxOver,
            derived.wicketOvers, derived.perOverRuns, requiredRunRate,
        )
        overviewScorecardSections(
            match, batsmen, derived.uniqueInnings, scorecard,
            derived.fallOfWicketsByInnings, derived.keyMoments, headToHead,
        )
        overviewCommentary(match, isUpcoming, isCompleted, streamingCommentary, derived.meaningfulCommentary)
        overviewUpcomingInfo(match, isUpcoming)
        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

private fun matchIsActive(match: CricketMatch): Boolean =
    match.matchStatus == MatchStatus.Live || match.currentStatus.lowercase().let {
        it.contains("stumps") || it.contains("break") || it.contains("rain") || it.contains("delay")
    }

private fun matchIsUpcoming(match: CricketMatch, isMatchActive: Boolean, isCompleted: Boolean): Boolean =
    match.matchStatus == MatchStatus.Upcoming ||
        (!isMatchActive && !isCompleted && match.teams.all { it.score.isBlank() || it.score == "0/0" })

private fun hasOverviewData(
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    recentBalls: List<String>,
    scorecard: Scorecard?,
    commentary: List<String>,
    probabilityHistory: List<WinProbabilityPoint>,
): Boolean = batsmen.isNotEmpty() || bowler != null || recentBalls.isNotEmpty() ||
    scorecard != null || commentary.isNotEmpty() || probabilityHistory.isNotEmpty()

private fun hasOverviewMeta(
    match: CricketMatch,
    matchFlow: MatchFlowData?,
    headToHead: H2HPair?,
    currentRunRate: String?,
    requiredRunRate: String?,
    isUpcoming: Boolean,
): Boolean = matchFlow != null || headToHead != null || currentRunRate != null || requiredRunRate != null ||
    (!match.isLive && match.currentStatus.isNotBlank() && !isUpcoming)

@Suppress("LongParameterList")
private fun hasAnyOverviewContent(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    recentBalls: List<String>,
    scorecard: Scorecard?,
    commentary: List<String>,
    probabilityHistory: List<WinProbabilityPoint>,
    matchFlow: MatchFlowData?,
    headToHead: H2HPair?,
    currentRunRate: String?,
    requiredRunRate: String?,
    isUpcoming: Boolean,
): Boolean = hasOverviewData(batsmen, bowler, recentBalls, scorecard, commentary, probabilityHistory) ||
    hasOverviewMeta(match, matchFlow, headToHead, currentRunRate, requiredRunRate, isUpcoming)

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
            // Tabular: these tiles hold live figures that re-render in place.
            Text(text = value, style = typography.scoreMedium, color = colors.onSurface)
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
            color = colors.primaryText,
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


@Composable
internal fun OverviewNoDataMessage(isUpcoming: Boolean) {
    if (isUpcoming) {
        EmptyStateV2(
            icon = Icons.Outlined.Schedule,
            title = stringResource(R.string.overview_not_started_title),
            subtitle = stringResource(R.string.overview_not_started_subtitle),
        )
    } else {
        EmptyStateV2(
            icon = Icons.Outlined.SportsCricket,
            title = stringResource(R.string.overview_pending_title),
            subtitle = stringResource(R.string.overview_pending_subtitle),
        )
    }
}
