package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.BouncerRowGroup
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

// Situation, live-state, batsmen and bowler sections for OverviewTabV2's LazyColumn.

internal fun LazyListScope.overviewHeaderSections(
    isUpcoming: Boolean,
    hasAnyContent: Boolean,
) {
    // Nothing here restates the score header.
    //
    // `ScoreHeaderV2` is a sibling of the pager, not a page inside it, so it stays on screen for
    // every tab. This section used to open with the match status, a CRR/RRR tile pair, the recent
    // balls and a six-card "momentum stream" — all four of which the header already shows, three
    // of them from the identical field. A tab whose first screenful repeats the thing pinned above
    // it reads as padding, which is what made this screen feel generated. Overview now starts at
    // the first fact the header does not carry: who is at the crease.
    //
    // No WIN % tile either. The hero below states both sides, the draw and what kind of number it
    // is; a one-sided tile above it showed the same probability rounded differently (49% against
    // the hero's 50%) and read as a second, disagreeing model.
    if (!hasAnyContent) {
        item(key = "no_data") { OverviewNoDataMessage(isUpcoming = isUpcoming) }
    }
}

internal fun LazyListScope.overviewLiveSituation(
    match: CricketMatch,
    isMatchActive: Boolean,
) {
    // The last wicket is the only thing here the header does not already show.
    val lastWicket = match.score?.lastWicket
    if (!lastWicket.isNullOrBlank() && isMatchActive) {
        item(key = "last_wicket") { LastWicketCard(lastWicket) }
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
            item(key = "crease_header") { SectionHeaderV2(title = stringResource(R.string.overview_at_the_crease)) }
            // One container for the pair, not a card each — the partnership is the unit.
            item(key = "crease") {
                BouncerRowGroup(batsmen) { BatsmanCreaseRow(it) }
            }
        }
        (isMatchActive || isCompleted) && fallbackBatsmen.isNotEmpty() -> {
            item(key = "crease_header") {
                SectionHeaderV2(
                    title = stringResource(
                        // Without a not-out batter these rows are the last pair to have
                        // batted, not the current one, and the heading has to say so.
                        if (fallbackHasNotOut) {
                            R.string.overview_at_the_crease
                        } else {
                            R.string.overview_last_batsmen
                        }
                    )
                )
            }
            item(key = "crease_fallback") {
                BouncerRowGroup(fallbackBatsmen) { FallbackBatsmanRow(it) }
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
            item(key = "bowler_header") { SectionHeaderV2(title = stringResource(R.string.overview_current_bowler)) }
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
                item(key = "bowler_header") { SectionHeaderV2(title = stringResource(R.string.overview_best_bowler)) }
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
internal fun LastWicketCard(lastWicket: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(
                text = stringResource(R.string.overview_last_wicket),
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
                Text(text = "Econ $econ", style = typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}
