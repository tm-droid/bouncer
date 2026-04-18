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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

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
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.lg,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Quick stats row
        if (currentRunRate != null || requiredRunRate != null || match.winProbability > 0) {
            item(key = "quick_stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    currentRunRate?.let {
                        QuickStatCard(label = "CRR", value = it, modifier = Modifier.weight(1f))
                    }
                    requiredRunRate?.let {
                        QuickStatCard(label = "RRR", value = it, modifier = Modifier.weight(1f))
                    }
                    if (match.winProbability > 0) {
                        QuickStatCard(
                            label = "Win %",
                            value = "${match.winProbability.toInt()}%",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // At the Crease
        if (batsmen.isNotEmpty()) {
            item(key = "crease_header") {
                SectionHeaderV2(title = "At the Crease")
            }
            items(batsmen, key = { "bat_${it.name}" }) { bat ->
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bat.name + if (bat.isStriker) "*" else "",
                                style = typography.titleSmall,
                                color = colors.onSurface,
                                fontWeight = if (bat.isStriker) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${bat.runs} (${bat.balls})",
                                style = typography.scoreMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "SR: ${bat.strikeRate} • 4s: ${bat.fours} • 6s: ${bat.sixes}",
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        // Current Bowler
        if (bowler != null) {
            item(key = "bowler_header") {
                SectionHeaderV2(title = "Current Bowler")
            }
            item(key = "bowler") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bowler.name,
                                style = typography.titleSmall,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${bowler.overs}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}",
                                style = typography.scoreMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "Econ: ${bowler.economy}",
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        // Win Probability bar
        if (match.winProbability > 0 && match.teams.size >= 2) {
            item(key = "win_prob_header") {
                SectionHeaderV2(title = "Win Probability")
            }
            item(key = "win_prob") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        val team1 = match.teams[0]
                        val team2 = match.teams[1]
                        val prob = (match.winProbability.toFloat() / 100f).coerceIn(0f, 1f)

                        WinProbRow(teamName = team1.acronym.ifBlank { team1.name }, probability = prob)
                        Spacer(Modifier.height(spacing.sm))
                        WinProbRow(teamName = team2.acronym.ifBlank { team2.name }, probability = 1f - prob)
                    }
                }
            }
        }

        // Recent Commentary
        if (commentary.isNotEmpty() && commentary.first() != "Loading commentary...") {
            item(key = "commentary_header") {
                SectionHeaderV2(title = "Recent Commentary")
            }
            val previewLines = commentary.take(5)
            items(previewLines.size, key = { idx -> "overview_comm_$idx" }) { idx ->
                val line = previewLines[idx].replace(Regex("^\\[AI Fused\\]\\s*"), "").trim()
                Text(
                    text = line,
                    style = typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(vertical = spacing.xxs),
                )
            }
        }
    }
}

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
            Text(text = value, style = typography.titleLarge, color = colors.onSurface, fontWeight = FontWeight.Bold)
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
            color = colors.primary,
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
