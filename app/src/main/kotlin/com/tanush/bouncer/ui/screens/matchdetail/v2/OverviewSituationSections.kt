package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.ui.components.premium.EliteMomentumTracker
import com.tanush.bouncer.ui.components.premium.InsightGridCard
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// Situation, live-state, batsmen and bowler sections for OverviewTabV2's LazyColumn.

internal fun LazyListScope.overviewHeaderSections(
    match: CricketMatch,
    isUpcoming: Boolean,
    isCompleted: Boolean,
    hasAnyContent: Boolean,
    currentRunRate: String?,
    requiredRunRate: String?,
    winProb: Double?,
) {
    if (!hasAnyContent) {
        item(key = "no_data") { OverviewNoDataMessage(isUpcoming = isUpcoming) }
    }
    if (!match.isLive && match.currentStatus.isNotBlank() && !isUpcoming) {
        item(key = "match_situation") { MatchSituationCard(match, isCompleted) }
    }
    // No WIN % tile here. The hero below states both sides, the draw and what kind of number it is;
    // a one-sided tile above it showed the same probability rounded differently (49% against the
    // hero's 50%) and read as a second, disagreeing model.
    if (currentRunRate != null || requiredRunRate != null) {
        item(key = "quick_stats") { QuickStatsRow(currentRunRate, requiredRunRate) }
    }
}

internal fun LazyListScope.overviewLiveSituation(
    match: CricketMatch,
    recentBalls: List<String>,
    isMatchActive: Boolean,
) {
    if (recentBalls.isNotEmpty()) {
        item(key = "recent_balls") { RecentBallsCard(recentBalls) }
    }
    val lastWicket = match.score?.lastWicket
    if (!lastWicket.isNullOrBlank() && isMatchActive) {
        item(key = "last_wicket") { LastWicketCard(lastWicket) }
    }
    if (recentBalls.isNotEmpty()) {
        val battingOvers = match.teams.firstOrNull { it.isBatting }?.overs
            ?: match.teams.firstOrNull()?.overs ?: "0"
        val currentOverNum = battingOvers.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val momentumEvents = recentBalls.takeLast(6).mapIndexedNotNull { idx, ball ->
            momentumEventFor("$currentOverNum.${idx + 1}", ball)
        }
        if (momentumEvents.isNotEmpty()) {
            item(key = "momentum_tracker") { EliteMomentumTracker(events = momentumEvents) }
        }
    }
}

internal fun LazyListScope.overviewBatsmen(
    batsmen: List<ScrapedBatsman>,
    isMatchActive: Boolean,
    isCompleted: Boolean,
    fallbackBatsmen: List<BatsmanStats>,
    fallbackHasNotOut: Boolean,
) {
    when {
        batsmen.isNotEmpty() -> {
            item(key = "crease_header") { SectionHeaderV2(title = "At the Crease") }
            items(batsmen, key = { "bat_${it.name}" }) { bat -> BatsmanCreaseCard(bat) }
        }
        (isMatchActive || isCompleted) && fallbackBatsmen.isNotEmpty() -> {
            item(key = "crease_header") {
                SectionHeaderV2(title = if (fallbackHasNotOut) "At the Crease" else "Last Batsmen")
            }
            items(fallbackBatsmen.size, key = { "scbat_$it" }) { idx ->
                FallbackBatsmanCard(fallbackBatsmen[idx])
            }
        }
    }
}

internal fun LazyListScope.overviewBowler(
    bowler: ScrapedBowler?,
    isMatchActive: Boolean,
    isCompleted: Boolean,
    scorecard: Scorecard?,
) {
    when {
        bowler != null -> {
            item(key = "bowler_header") { SectionHeaderV2(title = "Current Bowler") }
            item(key = "bowler") {
                BowlerStatCard(
                    name = bowler.name,
                    figures = "${bowler.overs}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}",
                    econ = computeEconomy(bowler.overs, bowler.runs, bowler.economy),
                )
            }
        }
        (isMatchActive || isCompleted) && scorecard != null && scorecard.innings.isNotEmpty() -> {
            val topBowler = scorecard.innings.last().bowlers.maxByOrNull { it.wickets.toIntOrNull() ?: 0 }
            if (topBowler != null) {
                item(key = "bowler_header") { SectionHeaderV2(title = "Best Bowler") }
                item(key = "bowler") {
                    BowlerStatCard(
                        name = topBowler.name,
                        figures = "${topBowler.overs}-${topBowler.maidens}-${topBowler.runs}-${topBowler.wickets}",
                        econ = computeEconomy(topBowler.overs, topBowler.runs, topBowler.economy),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MatchSituationCard(match: CricketMatch, isCompleted: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(text = "Match Status", style = typography.labelMedium, color = colors.textSecondary)
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

@Composable
internal fun QuickStatsRow(
    currentRunRate: String?,
    requiredRunRate: String?,
) {
    val spacing = BouncerThemeV2.spacing
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
    }
}

@Composable
internal fun RecentBallsCard(recentBalls: List<String>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(text = "This Over", style = typography.labelMedium, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.xs))
            RecentBallsStripV2(balls = recentBalls.takeLast(6))
        }
    }
}

@Composable
internal fun LastWicketCard(lastWicket: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(text = "Last Wicket", style = typography.labelMedium, color = colors.textSecondary)
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

@Composable
internal fun BowlerStatCard(name: String, figures: String, econ: String?) {
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
                    text = name,
                    style = typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AnimatedScoreText(
                    text = figures,
                    style = typography.scoreMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (econ != null) {
                Spacer(Modifier.height(spacing.xs))
                Text(text = "Econ: $econ", style = typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}
