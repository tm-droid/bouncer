package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.ui.components.premium.CurrentBowlerCard
import com.tanush.bouncer.ui.components.premium.CurrentPlayersCard
import com.tanush.bouncer.ui.components.premium.EnhancedScoreboardCard
import com.tanush.bouncer.ui.components.premium.HeroScorecardCard
import com.tanush.bouncer.ui.components.premium.TeamScoreCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.WindowSizeClass
import com.tanush.bouncer.ui.theme.rememberWindowSizeClass

/**
 * Adaptive live match screen layout inspired by a broadcast control-room UI.
 *
 * Renders the hero scorecard at the top (replacing a video player) alongside
 * a data-dense scoreboard, current partnership, and bowling information.
 *
 * Layout adapts to device size:
 * - **COMPACT** (phone portrait): Single column, all stacked vertically
 * - **MEDIUM** (phone landscape/small tablet): Two columns — hero + scoreboard side by side
 * - **EXPANDED** (large tablet): Full two-column layout with spacious arrangement
 *
 * @param match          The current [CricketMatch] data (score, teams, status).
 * @param scrapedBatsmen Detailed batsman stats from web scraping.
 * @param currentBowler  The bowler currently in action.
 * @param recentBalls    Recent ball-by-ball deliveries.
 */
@Composable
fun LiveMatchScreen(
    match: CricketMatch,
    scrapedBatsmen: List<ScrapedBatsman>,
    currentBowler: ScrapedBowler?,
    recentBalls: List<String>,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = rememberWindowSizeClass()

    when (windowSizeClass) {
        WindowSizeClass.COMPACT -> CompactLayout(
            match = match,
            scrapedBatsmen = scrapedBatsmen,
            currentBowler = currentBowler,
            recentBalls = recentBalls,
            modifier = modifier,
        )

        WindowSizeClass.MEDIUM -> MediumLayout(
            match = match,
            scrapedBatsmen = scrapedBatsmen,
            currentBowler = currentBowler,
            recentBalls = recentBalls,
            modifier = modifier,
        )

        WindowSizeClass.EXPANDED -> ExpandedLayout(
            match = match,
            scrapedBatsmen = scrapedBatsmen,
            currentBowler = currentBowler,
            recentBalls = recentBalls,
            modifier = modifier,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPACT layout — phone portrait, single column
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactLayout(
    match: CricketMatch,
    scrapedBatsmen: List<ScrapedBatsman>,
    currentBowler: ScrapedBowler?,
    recentBalls: List<String>,
    modifier: Modifier = Modifier,
) {
    val battingTeam = match.teams.firstOrNull { it.isBatting }
    val bowlingTeam = match.teams.firstOrNull { !it.isBatting }
    val dimens = BouncerDesignSystem.dimens

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimens.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
    ) {
        // ── 1. Hero Scorecard ───────────────────────────────────────────
        item(key = "hero") {
            HeroScorecardCard(
                match = match,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.heroCardHeightCompact),
            )
        }

        // ── 2. Current Players ──────────────────────────────────────────
        item(key = "currentPlayers") {
            val currentBatsmen = match.score?.batsmen ?: emptyList()
            AnimatedVisibility(
                visible = currentBatsmen.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CurrentPlayersCard(
                    batsmen = currentBatsmen,
                    teamCode = battingTeam?.acronym ?: ""
                )
            }
        }

        // ── 3. Status cards row (batting + bowling) ─────────────────────
        item(key = "statusCards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            ) {
                // Team score card
                if (battingTeam != null) {
                    TeamScoreCard(
                        teamAcronym = battingTeam.acronym.ifBlank { battingTeam.name },
                        teamLogoUrl = battingTeam.logoUrl,
                        score = battingTeam.score,
                        overs = battingTeam.overs,
                        subtitle = buildSubtitle(match),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Current bowler card
                CurrentBowlerCard(
                    teamAcronym = bowlingTeam?.acronym?.ifBlank { bowlingTeam.name } ?: "",
                    teamLogoUrl = bowlingTeam?.logoUrl,
                    bowler = currentBowler ?: match.currentBowler,
                    recentBalls = recentBalls.ifEmpty {
                        match.recentBalls ?: match.score?.recentBalls
                            ?.split(",")?.map { it.trim() } ?: emptyList()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── 4. Enhanced Scoreboard ──────────────────────────────────────
        item(key = "scoreboard") {
            EnhancedScoreboardCard(
                teamName = battingTeam?.name ?: match.teams.firstOrNull()?.name ?: "",
                opponentName = bowlingTeam?.name ?: match.teams.getOrNull(1)?.name ?: "",
                batsmen = scrapedBatsmen.ifEmpty {
                    match.scrapedBatsmen ?: emptyList()
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MEDIUM layout — two-column, hero left + scoreboard right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediumLayout(
    match: CricketMatch,
    scrapedBatsmen: List<ScrapedBatsman>,
    currentBowler: ScrapedBowler?,
    recentBalls: List<String>,
    modifier: Modifier = Modifier,
) {
    val battingTeam = match.teams.firstOrNull { it.isBatting }
    val bowlingTeam = match.teams.firstOrNull { !it.isBatting }
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
    ) {
        // Left column — hero + current players + status cards
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            HeroScorecardCard(
                match = match,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.heroCardHeight),
            )

            val currentBatsmen = match.score?.batsmen ?: emptyList()
            if (currentBatsmen.isNotEmpty()) {
                CurrentPlayersCard(
                    batsmen = currentBatsmen,
                    teamCode = battingTeam?.acronym ?: ""
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
            ) {
                if (battingTeam != null) {
                    TeamScoreCard(
                        teamAcronym = battingTeam.acronym.ifBlank { battingTeam.name },
                        teamLogoUrl = battingTeam.logoUrl,
                        score = battingTeam.score,
                        overs = battingTeam.overs,
                        subtitle = buildSubtitle(match),
                        modifier = Modifier.weight(1f),
                    )
                }

                CurrentBowlerCard(
                    teamAcronym = bowlingTeam?.acronym?.ifBlank { bowlingTeam.name } ?: "",
                    teamLogoUrl = bowlingTeam?.logoUrl,
                    bowler = currentBowler ?: match.currentBowler,
                    recentBalls = resolveRecentBalls(match, recentBalls),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Right column — scoreboard
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
        ) {
            EnhancedScoreboardCard(
                teamName = battingTeam?.name ?: match.teams.firstOrNull()?.name ?: "",
                opponentName = bowlingTeam?.name ?: match.teams.getOrNull(1)?.name ?: "",
                batsmen = scrapedBatsmen.ifEmpty {
                    match.scrapedBatsmen ?: emptyList()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EXPANDED layout — full tablet layout, two columns with spacious cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedLayout(
    match: CricketMatch,
    scrapedBatsmen: List<ScrapedBatsman>,
    currentBowler: ScrapedBowler?,
    recentBalls: List<String>,
    modifier: Modifier = Modifier,
) {
    val battingTeam = match.teams.firstOrNull { it.isBatting }
    val bowlingTeam = match.teams.firstOrNull { !it.isBatting }
    val dimens = BouncerDesignSystem.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
    ) {
        // Top row — Hero scorecard + Scoreboard side by side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
        ) {
            // Left: Hero card + current players stacked
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
            ) {
                HeroScorecardCard(
                    match = match,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                val currentBatsmen = match.score?.batsmen ?: emptyList()
                if (currentBatsmen.isNotEmpty()) {
                    CurrentPlayersCard(
                        batsmen = currentBatsmen,
                        teamCode = battingTeam?.acronym ?: ""
                    )
                }
            }

            // Right: Enhanced scoreboard
            EnhancedScoreboardCard(
                teamName = battingTeam?.name ?: match.teams.firstOrNull()?.name ?: "",
                opponentName = bowlingTeam?.name ?: match.teams.getOrNull(1)?.name ?: "",
                batsmen = scrapedBatsmen.ifEmpty {
                    match.scrapedBatsmen ?: emptyList()
                },
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
            )
        }

        // Bottom row — Status cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
        ) {
            if (battingTeam != null) {
                TeamScoreCard(
                    teamAcronym = battingTeam.acronym.ifBlank { battingTeam.name },
                    teamLogoUrl = battingTeam.logoUrl,
                    score = battingTeam.score,
                    overs = battingTeam.overs,
                    subtitle = buildSubtitle(match),
                    modifier = Modifier.weight(1f),
                )
            }

            // Optional: Add a spacer or additional info card in the middle
            Spacer(modifier = Modifier.weight(0.5f))

            CurrentBowlerCard(
                teamAcronym = bowlingTeam?.acronym?.ifBlank { bowlingTeam.name } ?: "",
                teamLogoUrl = bowlingTeam?.logoUrl,
                bowler = currentBowler ?: match.currentBowler,
                recentBalls = resolveRecentBalls(match, recentBalls),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildSubtitle(match: CricketMatch): String {
    val crr = match.score?.currentRunRate
    val parts = mutableListOf<String>()

    val battingTeam = match.teams.firstOrNull { it.isBatting }
    if (battingTeam != null) {
        // "AUS choose to bat"
        val tossInfo = match.currentStatus
            .takeIf { it.contains("chose to", ignoreCase = true) || it.contains("choose to", ignoreCase = true) }
        if (tossInfo != null) {
            parts.add(tossInfo.take(40))
        }
    }

    if (!crr.isNullOrBlank()) {
        parts.add("CRR: $crr")
    }

    val rrr = match.score?.requiredRunRate
    if (!rrr.isNullOrBlank()) {
        parts.add("RRR: $rrr")
    }

    return parts.joinToString(" • ")
}

private fun resolveRecentBalls(match: CricketMatch, provided: List<String>): List<String> {
    return provided.ifEmpty {
        match.recentBalls ?: match.score?.recentBalls
            ?.split(",")?.map { it.trim() } ?: emptyList()
    }
}
