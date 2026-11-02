package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.MatchContextStore
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.v2.BouncerRowGroup
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * What happened, once it is over: the server's summary, then who made the difference in each
 * innings. The result and every innings total are the header's ("99 & 156/4" for a Test); the
 * full tables are Scorecard's; the probability arc is on Graphs. A finished match's
 * win-probability bar is not shown here, because 100/0 is the result restated.
 */
@Composable
fun SummaryTabV2(
    match: CricketMatch,
    scorecard: Scorecard?,
    hasCommentary: Boolean,
    onOpenCommentary: () -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    // First = loaded. Until the context file answers, an empty tab is not yet known to be empty.
    val context by produceState<Pair<Boolean, MatchContext?>>(initialValue = false to null, match.id) {
        value = true to MatchContextStore.get(match.id, finished = true)
    }
    val surface = matchSurfaceFor(context.second, isCompleted = true)
    val innings = scorecard?.innings.orEmpty()
    val summaryResult = remember(match, scorecard) { summaryResultOf(match, scorecard) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        summaryResult?.let { item(key = "summary_result") { SummaryResultCard(it, match) } }
        overviewMatchSurface(surface)
        innings.forEachIndexed { idx, inn -> topPerformerItems(idx, inn) }
        if (surface == null && innings.isEmpty() && context.first) {
            item(key = "summary_empty") {
                EmptyStateV2(
                    icon = Icons.Outlined.SportsCricket,
                    title = stringResource(R.string.summary_empty_title),
                    subtitle = stringResource(R.string.summary_empty_subtitle),
                )
            }
        }
        if (hasCommentary) {
            item(key = "all_commentary") {
                TextButton(onClick = onOpenCommentary) {
                    Text(stringResource(R.string.live_all_commentary), color = BouncerThemeV2.colors.primaryText)
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

/** The two highest scorers and the two best bowling figures of one innings, as the card ranks them. */
internal fun topPerformers(innings: Innings): Pair<List<BatsmanStats>, List<BowlerStats>> {
    val batters = innings.batsmen
        .filter { (it.runs.toIntOrNull() ?: 0) > 0 }
        .sortedWith(compareByDescending<BatsmanStats> { it.runs.toIntOrNull() ?: 0 }.thenBy { it.balls.toIntOrNull() ?: Int.MAX_VALUE })
        .take(TOP_PER_INNINGS)
    val bowlers = innings.bowlers
        .filter { (it.wickets.toIntOrNull() ?: 0) > 0 }
        .sortedWith(compareByDescending<BowlerStats> { it.wickets.toIntOrNull() ?: 0 }.thenBy { it.runs.toIntOrNull() ?: Int.MAX_VALUE })
        .take(TOP_PER_INNINGS)
    return batters to bowlers
}

private const val TOP_PER_INNINGS = 2

private fun LazyListScope.topPerformerItems(index: Int, innings: Innings) {
    val (batters, bowlers) = topPerformers(innings)
    if (batters.isEmpty() && bowlers.isEmpty()) return
    item(key = "top_header_$index") {
        SectionHeaderV2(title = stringResource(R.string.summary_innings_title, innings.teamName))
    }
    if (batters.isNotEmpty()) {
        item(key = "top_bat_$index") { BouncerRowGroup(batters) { FallbackBatsmanRow(it) } }
    }
    bowlers.forEachIndexed { b, bowler ->
        item(key = "top_bowl_${index}_$b") {
            BowlerStatCard(
                name = bowler.name,
                figures = "${bowler.overs}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}",
                econ = computeEconomy(bowler.overs, bowler.runs, bowler.economy),
            )
        }
    }
}
