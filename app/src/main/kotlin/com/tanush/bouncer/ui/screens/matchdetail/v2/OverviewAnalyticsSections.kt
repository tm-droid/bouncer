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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.ui.components.FallOfWicket as UiFallOfWicket
import com.tanush.bouncer.ui.components.FallOfWicketsCard
import com.tanush.bouncer.ui.components.HeadToHeadCard
import com.tanush.bouncer.ui.components.MatchPhaseData
import com.tanush.bouncer.ui.components.MatchPhasesCard
import com.tanush.bouncer.ui.components.MatchPredictionCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.premium.DRSReviewCard
import com.tanush.bouncer.ui.components.premium.LiveScoringData
import com.tanush.bouncer.ui.components.premium.LiveScoringGraphCard
import com.tanush.bouncer.ui.components.premium.PremiumWinProbabilityHero
import com.tanush.bouncer.ui.components.premium.winProbabilityStateFor
import com.tanush.bouncer.ui.components.premium.WinProbabilityChartCard
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

// Win-probability, phase, scoring-chart, scorecard, commentary and upcoming sections.

internal fun LazyListScope.overviewWinProbability(
    match: CricketMatch,
    winProb: Double?,
    probabilityHistory: List<WinProbabilityPoint>,
    currentRunRate: String?,
    requiredRunRate: String?,
) {
    // Null means the server has no probability for this match. The previous `!= 50.0` test also
    // suppressed the hero for a genuinely even match, since 50 was both the sentinel and a value.
    if (winProb != null && match.teams.size >= 2) {
        item(key = "win_prob") {
            PremiumWinProbabilityHero(
                team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                // Not clamped to 0.05..0.95 any more. The server already clamps to 0.03..0.97, and
                // clamping a second time turned a finished match's 100/0 into 95/5 — a bar that
                // gave the losing side a five per cent chance of a result that had happened.
                team1Probability = (winProb / 100.0).toFloat(),
                drawProbability = match.winProbabilityDraw?.let { (it / 100.0).toFloat() },
                state = winProbabilityStateFor(
                    isCompleted = match.isCompleted,
                    isPrematch = match.winProbabilityIsPrematch,
                    isUpcoming = match.isUpcoming,
                ),
                detail = runRateDetail(currentRunRate, requiredRunRate),
                delta = probabilitySwing(probabilityHistory),
                // Full names, not the acronyms above: the registry's alias table and its fuzzy
                // match both work on names, and a three-letter code resolves to neither.
                team1ColorKey = match.teams[0].name,
                team2ColorKey = match.teams[1].name,
            )
        }
    }
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

/** The supporting numbers under the bar, or null when neither rate is known. */
private fun runRateDetail(currentRunRate: String?, requiredRunRate: String?): String? = listOfNotNull(
    currentRunRate?.takeIf { it.isNotBlank() }?.let { "CRR $it" },
    requiredRunRate?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { "RRR $it" },
).joinToString("  ·  ").ifBlank { null }

/**
 * How far the first team's probability has moved since the previous reading.
 *
 * Two points, not a window: the bar is answering "what just changed", and averaging over several
 * overs would smear a wicket into the balls either side of it.
 */
private fun probabilitySwing(history: List<WinProbabilityPoint>): Float? {
    if (history.size < 2) return null
    return history.last().team1Probability - history[history.size - 2].team1Probability
}

internal fun LazyListScope.overviewPhaseCards(
    matchFlow: MatchFlowData?,
    matchPhases: List<MatchPhaseData>,
) {
    // The powerplays are the first rows of this card (`buildMatchPhases` takes them), so there is
    // no separate powerplay card: it used to state the same runs and wickets a second time.
    if (matchPhases.isNotEmpty()) {
        item(key = "match_phases_card") { MatchPhasesCard(phases = matchPhases) }
    }
    val reviews = matchFlow?.drsReviews.orEmpty()
    if (reviews.isNotEmpty()) {
        item(key = "drs_card") { DRSReviewCard(reviews = reviews) }
    }
}

internal fun LazyListScope.overviewScoringCharts(
    match: CricketMatch,
    worm: OverWorm?,
    hasOverData: Boolean,
    cumulativeScores: List<Int>,
    maxOver: Int,
    wicketOvers: List<Int>,
    requiredRunRate: String?,
) {
    if (worm != null) {
        wormCharts(match, worm, wicketOvers, requiredRunRate)
        return
    }
    if (hasOverData) {
        item(key = "scoring_graph") {
            val (team1Color, team2Color) = rememberMatchChartColors(match)
            LiveScoringGraphCard(
                scoringData = LiveScoringData(
                    team1Color = team1Color,
                    team2Color = team2Color,
                    team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
                    team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
                    team1Scores = cumulativeScores,
                    team2Scores = emptyList(),
                    currentOver = maxOver,
                    projectedScore = MatchProjections.projectedTotalFor(match, cumulativeScores),
                    battingWicketOvers = wicketOvers,
                ),
            )
        }
    }
    // No manhattan here: these milestones ("50 runs in 6.3 overs") are spans of several overs, and
    // bars drawn from them would show a 50-run "over". The worm above carries them honestly.
}

internal fun LazyListScope.overviewScorecardSections(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    uniqueInnings: List<Innings>,
    scorecard: Scorecard?,
    fallOfWicketsByInnings: List<List<UiFallOfWicket>>,
    headToHead: H2HPair?,
) {
    if (batsmen.isEmpty() && uniqueInnings.isNotEmpty()) {
        item(key = "scorecard_summary_header") { SectionHeaderV2(title = stringResource(R.string.overview_innings_summary)) }
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
                        teamColor = rememberTeamInk(innings.teamName),
                    )
                }
            }
        }
    }
    headToHead?.let { h2h ->
        item(key = "h2h_card") { HeadToHeadCard(match = match, stats = h2h.stats, history = h2h.history) }
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
            item(key = "commentary_header") { SectionHeaderV2(title = stringResource(R.string.overview_recent_commentary)) }
            if (hasStreaming) {
                item(key = "streaming_commentary") {
                    CommentaryLineCard(text = cleanCommentary(streamingCommentary), highlighted = true)
                }
            }
            // Keyed by position, not by the line's hash: two identical commentary lines are
            // ordinary (successive dot balls read the same) and a repeated key is a hard crash.
            val recent = meaningfulCommentary.take(5)
            items(recent.size, key = { "overview_comm_$it" }) { idx ->
                CommentaryLineCard(text = cleanCommentary(recent[idx]), highlighted = false)
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
                Text(
                    text = stringResource(R.string.overview_match_preview),
                    style = typography.labelMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(spacing.xs))
                if (match.matchTime.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.overview_starts_at, match.matchTime),
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
    // The ball in progress reads first by weight. It was drawn in the fill gold, which is the
    // interactive colour and, in light mode, 1.7:1 on white.
    val textColor = if (muted) colors.textSecondary else colors.onSurface
    BouncerCard {
        // Cricbuzz marks its non-delivery bulletins ("<b>12:10 Local Time…</b>") with bold tags. The
        // Commentary tab rendered them; this card printed them literally.
        Text(
            text = remember(text) { parseBoldTags(text) },
            style = typography.bodyMedium,
            fontWeight = if (highlighted) FontWeight.SemiBold else null,
            color = textColor,
            modifier = Modifier.padding(spacing.md),
        )
    }
}
