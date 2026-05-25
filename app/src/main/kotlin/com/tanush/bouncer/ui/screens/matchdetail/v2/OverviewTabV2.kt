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
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.TimelineEventExtractor

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
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val isMatchActive = match.isLive || match.currentStatus.lowercase().let {
        it.contains("stumps") || it.contains("break") || it.contains("rain") || it.contains("delay")
    }
    val isCompleted = match.currentStatus.lowercase().let {
        it.contains("won") || it.contains("drawn") || it.contains("tied") || it.contains("no result")
    } || match.status.lowercase().contains("complete")
    val isUpcoming = !isMatchActive && !isCompleted && match.teams.all { it.score.isBlank() || it.score == "0/0" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.lg,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Match situation card for stumps/break/completed
        if (!match.isLive && match.currentStatus.isNotBlank() && !isUpcoming) {
            item(key = "match_situation") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "Match Status",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
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
        }

        // Quick stats row — show for any match with data
        val heuristicProb = match.calculateHeuristicProbability()
        val hasWinPct = heuristicProb != 50.0
        if (currentRunRate != null || requiredRunRate != null || hasWinPct) {
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
                    if (hasWinPct) {
                        QuickStatCard(
                            label = "Win %",
                            value = "${heuristicProb.toInt()}%",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Recent balls strip
        if (recentBalls.isNotEmpty()) {
            item(key = "recent_balls") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "This Over",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        RecentBallsStripV2(balls = recentBalls.takeLast(6))
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
                                text = bat.name + if (bat.isStriker) " *" else "",
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
        } else if ((isMatchActive || isCompleted) && scorecard != null && scorecard.innings.isNotEmpty()) {
            // Fallback: show top batsmen from latest innings when live data unavailable
            val latestInnings = scorecard.innings.last()
            val notOutBatsmen = latestInnings.batsmen.filter {
                it.dismissal.lowercase().contains("not out") || it.dismissal.lowercase().contains("batting")
            }.take(2)
            val topBatsmen = notOutBatsmen.ifEmpty { latestInnings.batsmen.takeLast(2) }
            if (topBatsmen.isNotEmpty()) {
                item(key = "crease_header") {
                    SectionHeaderV2(title = if (notOutBatsmen.isNotEmpty()) "At the Crease" else "Last Batsmen")
                }
                items(topBatsmen.size, key = { "scbat_$it" }) { idx ->
                    val bat = topBatsmen[idx]
                    BouncerCard {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = bat.name,
                                    style = typography.titleSmall,
                                    color = colors.onSurface,
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
                            val details = buildString {
                                append("SR: ${bat.strikeRate}")
                                if (bat.fours != "0") append(" • 4s: ${bat.fours}")
                                if (bat.sixes != "0") append(" • 6s: ${bat.sixes}")
                                if (bat.dismissal.isNotBlank() && !bat.dismissal.lowercase().contains("not out")) {
                                    append(" • ${bat.dismissal}")
                                }
                            }
                            Text(
                                text = details,
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
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
                        val econ = bowler.economy.takeIf { it.isNotBlank() }
                            ?: run {
                                val o = bowler.overs.toDoubleOrNull() ?: 0.0
                                val r = bowler.runs.toDoubleOrNull() ?: 0.0
                                if (o > 0) String.format("%.2f", r / o) else null
                            }
                        if (econ != null) {
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = "Econ: $econ",
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        } else if ((isMatchActive || isCompleted) && scorecard != null && scorecard.innings.isNotEmpty()) {
            // Fallback: show top bowler from latest innings
            val latestInnings = scorecard.innings.last()
            val topBowler = latestInnings.bowlers.maxByOrNull { it.wickets.toIntOrNull() ?: 0 }
            if (topBowler != null) {
                item(key = "bowler_header") {
                    SectionHeaderV2(title = "Best Bowler")
                }
                item(key = "bowler") {
                    BouncerCard {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = topBowler.name,
                                    style = typography.titleSmall,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${topBowler.overs}-${topBowler.maidens}-${topBowler.runs}-${topBowler.wickets}",
                                    style = typography.scoreMedium,
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            val econVal = topBowler.economy.takeIf { it.isNotBlank() }
                                ?: run {
                                    val o = topBowler.overs.toDoubleOrNull() ?: 0.0
                                    val r = topBowler.runs.toDoubleOrNull() ?: 0.0
                                    if (o > 0) String.format("%.2f", r / o) else null
                                }
                            if (econVal != null) {
                                Spacer(Modifier.height(spacing.xs))
                                Text(
                                    text = "Econ: $econVal",
                                    style = typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Win Probability bar — show for any match with meaningful probability data
        if (heuristicProb != 50.0 && match.teams.size >= 2) {
            item(key = "win_prob_header") {
                SectionHeaderV2(title = "Win Probability")
            }
            item(key = "win_prob") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        val team1 = match.teams[0]
                        val team2 = match.teams[1]
                        val prob = (heuristicProb.toFloat() / 100f).coerceIn(0f, 1f)

                        WinProbRow(teamName = team1.acronym.ifBlank { team1.name }, probability = prob)
                        Spacer(Modifier.height(spacing.sm))
                        WinProbRow(teamName = team2.acronym.ifBlank { team2.name }, probability = 1f - prob)
                    }
                }
            }
        }

        // Scorecard Summary for completed/stumps matches
        if (batsmen.isEmpty() && scorecard != null && scorecard.innings.isNotEmpty()) {
            val uniqueInnings = scorecard.innings.distinctBy { "${it.teamName}_${it.score}_${it.overs}" }
            item(key = "scorecard_summary_header") {
                SectionHeaderV2(title = "Innings Summary")
            }
            items(uniqueInnings.size, key = { "inn_summary_$it" }) { idx ->
                val inn = uniqueInnings[idx]
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
        }

        // Recent Commentary (skip for upcoming matches — no meaningful commentary yet)
        val meaningfulCommentary = TimelineEventExtractor.deduplicateByBall(
            commentary
                .map { it.trimEnd('/').trim() }
                .filter { it.isNotBlank() && it.length > 2 && it != "Loading commentary..." }
        )
        val hasStreaming = !isUpcoming && streamingCommentary.isNotBlank()
        val hasCommentary = !isUpcoming && (meaningfulCommentary.isNotEmpty() || hasStreaming)
        if (hasCommentary) {
            item(key = "commentary_header") {
                SectionHeaderV2(title = "Recent Commentary")
            }
            // Show streaming (in-flight) text as the top item while a ball is being processed
            if (hasStreaming) {
                item(key = "streaming_commentary") {
                    BouncerCard {
                        Text(
                            text = streamingCommentary,
                            style = typography.bodySmall,
                            color = colors.primary,
                            modifier = Modifier.padding(spacing.md),
                        )
                    }
                }
            }
            val previewLines = meaningfulCommentary.take(5)
            items(previewLines.size, key = { idx -> "overview_comm_$idx" }) { idx ->
                val line = previewLines[idx].replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim()
                BouncerCard {
                    Text(
                        text = line,
                        style = typography.bodySmall,
                        color = colors.onSurface,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        } else if (!isUpcoming) {
            item(key = "no_commentary") {
                val msg = when {
                    match.currentStatus.lowercase().contains("stumps") -> "Commentary paused — Stumps"
                    match.currentStatus.lowercase().contains("break") -> "Commentary paused — Session break"
                    match.currentStatus.lowercase().contains("rain") -> "Commentary paused — Rain delay"
                    isCompleted -> "Match completed"
                    else -> "Waiting for commentary..."
                }
                BouncerCard {
                    Text(
                        text = msg,
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        }

        // Upcoming match info
        if (isUpcoming) {
            item(key = "upcoming_info") {
                BouncerCard {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = "Match Preview",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        if (match.matchTime.isNotBlank()) {
                            Text(
                                text = "Starts at ${match.matchTime}",
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

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
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
