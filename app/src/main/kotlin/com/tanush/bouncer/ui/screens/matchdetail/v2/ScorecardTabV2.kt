package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * ScorecardTabV2 — Clean scorecard with innings tabs, batting table, bowling table, FOW.
 */
@Composable
fun ScorecardTabV2(scorecard: Scorecard?) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    if (scorecard == null || scorecard.innings.isEmpty()) {
        EmptyStateV2(
            title = "No scorecard",
            subtitle = "Scorecard will appear once the match starts",
        )
        return
    }

    // Deduplicate innings — some data sources return duplicates
    val uniqueInnings = remember(scorecard) {
        scorecard.innings.distinctBy { "${it.teamName}_${it.score}_${it.overs}" }
    }
    var selectedInnings by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Innings tabs
        ScrollableTabRow(
            selectedTabIndex = selectedInnings.coerceIn(0, uniqueInnings.lastIndex.coerceAtLeast(0)),
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            edgePadding = spacing.lg,
            indicator = { tabPositions ->
                if (selectedInnings < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedInnings]),
                        color = colors.primary,
                    )
                }
            },
            divider = { HorizontalDivider(color = colors.divider) },
        ) {
            uniqueInnings.forEachIndexed { index, innings ->
                Tab(
                    selected = selectedInnings == index,
                    onClick = { selectedInnings = index },
                    text = {
                        Text(
                            text = innings.teamName,
                            style = typography.labelLarge,
                            fontWeight = if (selectedInnings == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedInnings == index) colors.primary else colors.textSecondary,
                        )
                    },
                )
            }
        }

        // Innings content
        val innings = uniqueInnings.getOrNull(selectedInnings) ?: return
        InningsContent(innings)
    }
}

@Composable
private fun InningsContent(innings: Innings) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.md),
    ) {
        // Innings header
        item(key = "innings_header") {
            // Smart overs display — avoid "20 Ov ov" duplication
            val oversDisplay = innings.overs.let { ov ->
                if (ov.lowercase().contains("ov")) ov else "$ov ov"
            }
            Text(
                text = "${innings.teamName} — ${innings.score} ($oversDisplay)",
                style = typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        }

        // BATTING header
        item(key = "batting_header") {
            SectionHeaderV2(title = "BATTING")
        }

        // Column headers
        item(key = "bat_col_header") {
            BattingColumnHeader()
        }

        // Batsmen rows
        itemsIndexed(innings.batsmen, key = { _, b -> "bat_${b.name}" }) { index, batsman ->
            BatsmanRow(batsman = batsman, isAlternate = index % 2 == 1)
        }

        // Extras & Total
        if (innings.extras.isNotBlank()) {
            item(key = "extras") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm),
                ) {
                    Text(
                        text = "Extras: ${innings.extras}",
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        item(key = "total") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                val totalOversDisplay = innings.overs.let { ov ->
                    if (ov.lowercase().contains("ov")) ov else "$ov Ov"
                }
                val totalScore = innings.total.ifBlank { innings.score }
                Text(
                    text = "TOTAL: $totalScore ($totalOversDisplay)",
                    style = typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // BOWLING header
        item(key = "bowling_header") {
            Spacer(Modifier.height(spacing.md))
            SectionHeaderV2(title = "BOWLING")
        }
        item(key = "bowl_col_header") {
            BowlingColumnHeader()
        }
        items(innings.bowlers, key = { "bowl_${it.name}" }) { bowler ->
            BowlerRow(bowler)
        }

        // Fall of Wickets
        if (innings.fallOfWickets.isNotEmpty()) {
            item(key = "fow_header") {
                Spacer(Modifier.height(spacing.md))
                SectionHeaderV2(title = "FALL OF WICKETS")
            }
            item(key = "fow") {
                Text(
                    text = innings.fallOfWickets.joinToString("  ") {
                        "${it.number}-${it.score} (${it.batsman}, ${it.overs})"
                    },
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
        }

        // Partnerships
        if (innings.partnerships.isNotEmpty()) {
            item(key = "partnership_header") {
                Spacer(Modifier.height(spacing.md))
                SectionHeaderV2(title = "PARTNERSHIPS")
            }
            items(innings.partnerships, key = { "part_${it.player1}_${it.player2}" }) { p ->
                Text(
                    text = "${p.player1} & ${p.player2}: ${p.runs} (${p.balls})",
                    style = typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xxs),
                )
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

@Composable
private fun BattingColumnHeader() {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Batter", style = typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
        HeaderCell("R")
        HeaderCell("B")
        HeaderCell("4s")
        HeaderCell("6s")
        HeaderCell("SR")
    }
    HorizontalDivider(color = BouncerThemeV2.colors.divider)
}

@Composable
private fun BatsmanRow(batsman: BatsmanStats, isAlternate: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val isNotOut = batsman.dismissal.lowercase().let { it.contains("not out") || it == "batting" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlternate) colors.surfaceVariant.copy(alpha = 0.3f) else colors.surface)
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = batsman.name,
                style = typography.bodyMedium,
                color = if (isNotOut) colors.primary else colors.onSurface,
                fontWeight = if (isNotOut) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            StatCell(batsman.runs, isBold = true)
            StatCell(batsman.balls)
            StatCell(batsman.fours)
            StatCell(batsman.sixes)
            StatCell(batsman.strikeRate)
        }
        if (batsman.dismissal.isNotBlank() && !isNotOut) {
            Text(
                text = batsman.dismissal,
                style = typography.bodySmall,
                color = colors.textSecondary,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun BowlingColumnHeader() {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Bowler", style = typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
        HeaderCell("O")
        HeaderCell("M")
        HeaderCell("R")
        HeaderCell("W")
        HeaderCell("Econ")
    }
    HorizontalDivider(color = BouncerThemeV2.colors.divider)
}

@Composable
private fun BowlerRow(bowler: BowlerStats) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bowler.name,
            style = typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        StatCell(bowler.overs)
        StatCell(bowler.maidens)
        StatCell(bowler.runs)
        StatCell(bowler.wickets, isBold = true)
        StatCell(bowler.economy)
    }
}

@Composable
private fun HeaderCell(text: String) {
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelSmall,
        color = BouncerThemeV2.colors.textSecondary,
        modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.xs),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatCell(text: String, isBold: Boolean = false) {
    Text(
        text = text,
        style = BouncerThemeV2.typography.bodySmall,
        color = BouncerThemeV2.colors.onSurface,
        modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.xs),
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
    )
}
