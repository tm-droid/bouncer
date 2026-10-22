package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The full scorecard for one innings at a time, chosen from a row of innings tabs.
 *
 * Rows on one surface split by hairlines, figures in tabular digits, and colour only where it
 * identifies a team. It used to draw each row as its own bordered box, the strike rate and economy
 * in gold (the interactive colour), a made-up zig-zag "sparkline" under every bowler, and a second
 * bowling card claiming every bowler had bowled zero dot balls — a figure the app does not have.
 */
@Composable
fun PremiumScorecardTab(
    scorecard: Scorecard?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerThemeV2.colors
    if (scorecard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }
    if (scorecard.innings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_no_scorecard_available), color = colors.textSecondary)
        }
        return
    }

    // Opens on the innings being played, not the first: during a chase the first is finished.
    // Keyed on the count, so a new innings starting brings the reader to it. Saveable, so a swipe
    // to another tab and back does not reset it.
    var selected by rememberSaveable(scorecard.innings.size) { mutableIntStateOf(scorecard.innings.lastIndex) }
    val innings = scorecard.innings.getOrNull(selected) ?: return
    val isLive = LocalScorecardIsLive.current
    val battingNow = isLive && selected == scorecard.innings.lastIndex
    val notBatted = yetToBat(innings, LocalScorecardSquads.current)

    Column(modifier = modifier.fillMaxSize()) {
        if (scorecard.innings.size > 1) {
            InningsTabs(scorecard.innings, selected, onSelect = { selected = it })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item(key = "summary") {
                InningsSummary(innings, requiredRate = if (battingNow) calculateRequiredRunRate(scorecard, selected) else null)
            }
            battingItems(innings, battingNow)
            if (notBatted.isNotEmpty()) {
                item(key = "yet_to_bat") { YetToBatRow(notBatted.map { it.name }, battingNow) }
            }
            fallOfWicketItems(innings)
            partnershipItems(innings)
            bowlingItems(innings)
        }
    }
}

private fun LazyListScope.battingItems(innings: Innings, battingNow: Boolean) {
    item(key = "batting_header") { SectionHeaderV2(title = stringResource(R.string.premium_batting)) }
    item(key = "batting_columns") { BattingColumns() }
    val topScore = innings.batsmen.maxOfOrNull { it.runs.toIntOrNull() ?: 0 } ?: 0
    // Indexed keys: a scorecard can legitimately repeat a name (and a scraped row can duplicate one
    // outright), and a batsman and a bowler in the same LazyColumn collided on the bare name — a
    // duplicate key is a hard crash, not a rendering glitch.
    itemsIndexed(innings.batsmen, key = { index, b -> "bat_${index}_${b.name}" }) { _, batsman ->
        BattingRow(
            batsman = batsman,
            battingNow = battingNow,
            isTopScore = topScore > 0 && batsman.runs.toIntOrNull() == topScore,
            teamName = innings.teamName,
        )
    }
    if (innings.extras.isNotBlank()) item(key = "extras") { ExtrasRow(innings.extras) }
    item(key = "total") { TotalRow(innings) }
}

private fun LazyListScope.fallOfWicketItems(innings: Innings) {
    if (innings.fallOfWickets.isEmpty()) return
    item(key = "fow_header") { SectionHeaderV2(title = stringResource(R.string.scorecard_fall_of_wickets)) }
    itemsIndexed(innings.fallOfWickets, key = { index, f -> "fow_${index}_${f.score}_${f.batsman}" }) { index, fow ->
        FallOfWicketRow(fow = fow, number = if (fow.number > 0) fow.number else index + 1)
    }
}

private fun LazyListScope.partnershipItems(innings: Innings) {
    if (innings.partnerships.isEmpty()) return
    item(key = "partnership_header") { SectionHeaderV2(title = stringResource(R.string.scorecard_partnerships)) }
    item(key = "partnerships") {
        PartnershipBreakdownCard(
            teamName = innings.teamName,
            partnerships = innings.partnerships.mapIndexed { index, p ->
                val totalRuns = p.runs.toIntOrNull() ?: 0
                val totalBalls = p.balls.toIntOrNull() ?: 0
                PartnershipEntry(
                    batter1Name = p.player1,
                    batter1Runs = p.contribution1.toIntOrNull() ?: 0,
                    batter1Balls = 0,
                    batter2Name = p.player2,
                    batter2Runs = p.contribution2.toIntOrNull() ?: 0,
                    batter2Balls = 0,
                    totalRuns = totalRuns,
                    totalBalls = totalBalls,
                    runRate = MatchProjections.currentRunRate(totalRuns, totalBalls) ?: 0.0,
                    wicketNumber = index + 1,
                )
            },
        )
    }
}

private fun LazyListScope.bowlingItems(innings: Innings) {
    if (innings.bowlers.isEmpty()) return
    item(key = "bowling_header") { SectionHeaderV2(title = stringResource(R.string.scorecard_bowling)) }
    item(key = "bowling_columns") { BowlingColumns() }
    val best = bestBowlerIndex(innings)
    itemsIndexed(innings.bowlers, key = { index, b -> "bowl_${index}_${b.name}" }) { index, bowler ->
        BowlingRow(bowler, isBest = index == best)
    }
}

/** One tab per innings with the batting side's crest; the selected tab in the interactive gold. */
@Composable
private fun InningsTabs(innings: List<Innings>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = BouncerThemeV2.colors
    val labels = inningsLabels(innings.map { it.teamName })
    Row(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        innings.forEachIndexed { index, inn ->
            InningsTab(
                teamName = inn.teamName,
                label = labels[index],
                isSelected = index == selected,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InningsTab(teamName: String, label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = BouncerThemeV2.colors
    Column(
        modifier = modifier.clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TeamCrest(teamName = teamName, size = 18.dp)
            Text(
                text = label,
                style = BouncerThemeV2.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.primaryText else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (isSelected) colors.primary else colors.divider),
        )
    }
}

@Composable
private fun InningsSummary(innings: Innings, requiredRate: String?) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(teamName = innings.teamName, size = 28.dp)
            Spacer(Modifier.width(spacing.md))
            Text(
                text = innings.teamName,
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = rememberTeamInk(innings.teamName),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = innings.score, style = typography.scoreLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
            if (innings.overs.isNotBlank()) {
                Spacer(Modifier.width(spacing.xs))
                Text(text = "(${innings.overs.replace("Ov", "").trim()})", style = typography.numericSmall, color = colors.textSecondary)
            }
        }
        val rates = listOfNotNull(
            calculateCurrentRunRate(innings).takeIf { it != "-" }?.let { stringResource(R.string.scorecard_crr, it) },
            requiredRate?.let { stringResource(R.string.scorecard_req, it) },
        )
        if (rates.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            Text(text = rates.joinToString("  ·  "), style = typography.numericSmall, color = colors.textSecondary)
        }
    }
    HorizontalDivider(color = colors.divider)
}
