package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.DRSReviewData
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.ui.components.premium.DRSReviewCard
import com.tanush.bouncer.ui.components.premium.PremiumWinProbabilityHero
import com.tanush.bouncer.ui.components.premium.winProbabilityStateFor
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The state of play, while there is play: who is in, the stand, the last wicket, who is bowling,
 * the forecast and the latest lines. Everything else has its own tab — the charts are on Graphs,
 * the fall of wickets on Scorecard, the preview and head to head on Info — and nothing here
 * restates the fixed header above the pager (score, status, CRR/RRR, this over).
 */
@Composable
internal fun LiveTabV2(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    scorecard: Scorecard?,
    matchFlow: MatchFlowData?,
    derived: OverviewDerivedData,
    probabilityHistory: List<WinProbabilityPoint>,
    streamingCommentary: String,
    onOpenCommentary: () -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    val partnership = remember(scorecard) { scorecard?.innings?.lastOrNull()?.let(::currentPartnership) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        overviewBatsmen(batsmen, isMatchActive = true, isCompleted = false, derived.fallbackBatsmen, derived.fallbackHasNotOut)
        // Only beside a pair at the crease (the payload's, or the card's not-out pair at a break):
        // without one, the last innings' stand is not "current".
        if (partnership != null && (batsmen.isNotEmpty() || derived.fallbackHasNotOut)) {
            item(key = "partnership") { PartnershipLine(partnership) }
        }
        overviewLiveSituation(match, isMatchActive = true)
        overviewBowler(bowler, isMatchActive = true, isCompleted = false, scorecard)
        liveLatestReview(matchFlow)
        liveWinProbability(match, probabilityHistory)
        liveRecentCommentary(match, streamingCommentary, derived.meaningfulCommentary, onOpenCommentary)
        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

@Composable
private fun PartnershipLine(partnership: Pair<Int, Int>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    BouncerCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(BouncerThemeV2.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.live_partnership),
                style = typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.live_partnership_value, partnership.first, partnership.second),
                style = typography.numeric,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The most recent review, while play is on. The whole match's reviews are a fact on Info. */
private fun LazyListScope.liveLatestReview(matchFlow: MatchFlowData?) {
    val latest = matchFlow?.drsReviews?.lastOrNull() ?: return
    item(key = "drs_card") {
        DRSReviewCard(
            review = DRSReviewData(
                isSuccessful = latest.isUpheld,
                over = latest.over,
                decision = latest.decision,
                umpireName = latest.umpireName,
            ),
        )
    }
}

/**
 * The forecast bar and how far the last reading moved it. No CRR/RRR line under it any more: the
 * header shows both. The arc over the whole match is on Graphs.
 */
private fun LazyListScope.liveWinProbability(match: CricketMatch, probabilityHistory: List<WinProbabilityPoint>) {
    // Null means the server has no probability for this match. The previous `!= 50.0` test also
    // suppressed the hero for a genuinely even match, since 50 was both the sentinel and a value.
    val winProb = match.winProbability ?: return
    if (match.teams.size < 2) return
    item(key = "win_prob") {
        PremiumWinProbabilityHero(
            team1Name = match.teams[0].acronym.ifBlank { match.teams[0].name.take(3).uppercase() },
            team2Name = match.teams[1].acronym.ifBlank { match.teams[1].name.take(3).uppercase() },
            // Not clamped: the server already clamps to 0.03..0.97.
            team1Probability = (winProb / 100.0).toFloat(),
            drawProbability = match.winProbabilityDraw?.let { (it / 100.0).toFloat() },
            state = winProbabilityStateFor(
                isCompleted = match.isCompleted,
                isPrematch = match.winProbabilityIsPrematch,
                isUpcoming = match.isUpcoming,
            ),
            delta = probabilitySwing(probabilityHistory),
            // Full names, not the acronyms above: the registry's alias table and its fuzzy
            // match both work on names, and a three-letter code resolves to neither.
            team1ColorKey = match.teams[0].name,
            team2ColorKey = match.teams[1].name,
        )
    }
}

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

/** The newest few lines, with the way to the whole feed. Three, not five: the feed is one swipe away. */
private fun LazyListScope.liveRecentCommentary(
    match: CricketMatch,
    streamingCommentary: String,
    meaningfulCommentary: List<String>,
    onOpenCommentary: () -> Unit,
) {
    val hasStreaming = streamingCommentary.isNotBlank()
    if (meaningfulCommentary.isEmpty() && !hasStreaming) {
        item(key = "no_commentary") {
            CommentaryLineCard(text = commentaryPausedMessage(match, isCompleted = false), highlighted = false, muted = true)
        }
        return
    }
    item(key = "commentary_header") {
        SectionHeaderV2(
            title = stringResource(R.string.overview_recent_commentary),
            action = stringResource(R.string.live_all_commentary),
            onAction = onOpenCommentary,
        )
    }
    if (hasStreaming) {
        item(key = "streaming_commentary") {
            CommentaryLineCard(text = cleanCommentary(streamingCommentary), highlighted = true)
        }
    }
    // Keyed by position, not by the line's hash: two identical commentary lines are ordinary
    // (successive dot balls read the same) and a repeated key is a hard crash.
    val recent = meaningfulCommentary.take(RECENT_LINES)
    recent.forEachIndexed { idx, line ->
        item(key = "live_comm_$idx") { CommentaryLineCard(text = cleanCommentary(line), highlighted = false) }
    }
}

private const val RECENT_LINES = 3

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
