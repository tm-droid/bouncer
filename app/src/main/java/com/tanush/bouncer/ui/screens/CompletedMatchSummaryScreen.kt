package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardLarge
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.components.glass.MatchStatus
import com.tanush.bouncer.ui.components.glass.MatchStatusBadge
import com.tanush.bouncer.ui.components.glass.PlayerAvatar
import com.tanush.bouncer.ui.components.glass.StatPill
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors

/**
 * Post-match summary screen for completed matches.
 * Glass morphism design showing final scores, result, player of the match, key moments.
 */
@Composable
fun CompletedMatchSummaryScreen(
    match: CricketMatch,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)

    // Extract a POTM name from currentStatus if possible, otherwise placeholder
    val potmName = remember(match.currentStatus) {
        extractPlayerOfTheMatch(match.currentStatus)
    }

    // Build placeholder timeline events
    val timelineEvents = remember(match) {
        buildTimelineEvents(match)
    }

    GlassBloomBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBackClick,
                        contentDescription = "Back"
                    )
                    Text(
                        text = "MATCH SUMMARY",
                        style = typography.headingMedium,
                        color = colors.goldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Result Hero ─────────────────────────────────────────
            item {
                GlassCardLarge(
                    modifier = Modifier.fillMaxWidth(),
                    useGoldBorder = true
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Status badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            MatchStatusBadge(status = MatchStatus.COMPLETED)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Team scores row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Team 1
                            if (team1 != null) {
                                TeamScoreColumn(team = team1)
                            }

                            // VS divider
                            Text(
                                text = "VS",
                                style = typography.labelMedium,
                                color = colors.textMuted,
                                fontWeight = FontWeight.Bold
                            )

                            // Team 2
                            if (team2 != null) {
                                TeamScoreColumn(team = team2)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Result text
                        Text(
                            text = match.currentStatus.ifBlank { match.status },
                            style = typography.bodyMedium,
                            color = colors.goldPrimary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Venue & date
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${match.venue} • ${match.date}",
                            style = typography.labelSmall,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Player of the Match ─────────────────────────────────
            item {
                SectionHeader(
                    icon = Icons.Default.Person,
                    title = "PLAYER OF THE MATCH"
                )
            }
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    useGoldBorder = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PlayerAvatar(
                            imageUrl = null,
                            playerName = potmName,
                            size = 64.dp,
                            showBorder = true
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = potmName,
                                style = typography.headingSmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = extractPotmStat(match),
                                style = typography.bodySmall,
                                color = colors.goldPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = colors.goldPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ── Key Stats ───────────────────────────────────────────
            item {
                SectionHeader(
                    icon = Icons.Default.QueryStats,
                    title = "KEY STATS"
                )
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill(
                                label = "Total Runs",
                                value = calculateTotalRuns(match),
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                label = "Wickets",
                                value = calculateTotalWickets(match),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill(
                                label = "Highest",
                                value = extractHighestScore(match),
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                label = "Best Bowl",
                                value = extractBestBowling(match),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill(
                                label = "Type",
                                value = match.matchType.uppercase(),
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                label = "Series",
                                value = match.matchTitle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Match Timeline ──────────────────────────────────────
            item {
                SectionHeader(
                    icon = Icons.Default.Timeline,
                    title = "MATCH TIMELINE"
                )
            }
            items(timelineEvents) { event ->
                TimelineEntry(
                    time = event.time,
                    description = event.description
                )
            }
        }
    }
}

// ─── Helper Composables ─────────────────────────────────────────────────────

@Composable
private fun TeamScoreColumn(team: CricketMatch.Team) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TeamLogoCircle(
            teamCode = team.acronym.ifBlank { team.name.take(3) },
            logoUrl = team.logoUrl,
            size = 52.dp
        )
        Text(
            text = team.acronym.ifBlank { team.name.take(3) }.uppercase(),
            style = typography.labelMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = team.score.ifBlank { "—" },
            style = typography.scoreSecondary,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        if (team.overs.isNotBlank() && team.overs != "0.0") {
            Text(
                text = "(${team.overs} ov)",
                style = typography.labelSmall,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = typography.labelMedium,
            color = colors.goldPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimelineEntry(
    time: String,
    description: String
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    GlassCardSmall(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gold dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.goldPrimary)
            )
            // Time
            Text(
                text = time,
                style = typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.width(60.dp)
            )
            // Description
            Text(
                text = description,
                style = typography.bodySmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Data Helpers ───────────────────────────────────────────────────────────

private data class TimelineEvent(val time: String, val description: String)

private fun extractPlayerOfTheMatch(currentStatus: String): String {
    // Try to extract a name from status text like "Player of the Match - Virat Kohli"
    val potmRegex = Regex(
        """(?:player of the match|potm|man of the match)[:\-–—\s]+([A-Z][a-z]+ [A-Z][a-z]+)""",
        RegexOption.IGNORE_CASE
    )
    potmRegex.find(currentStatus)?.groupValues?.getOrNull(1)?.let { return it }

    // Fallback: try to find a name-like pattern at significant position
    val nameRegex = Regex("""([A-Z][a-z]+ [A-Z][a-z]+)""")
    nameRegex.find(currentStatus)?.groupValues?.getOrNull(1)?.let { return it }

    return "Player of the Match"
}

private fun extractPotmStat(match: CricketMatch): String {
    val score = match.score
    if (score != null && score.batsmen.isNotEmpty()) {
        val top = score.batsmen.maxByOrNull { it.runs }
        if (top != null) {
            return "${top.runs} (${top.balls}) • ${top.fours}x4 ${top.sixes}x6"
        }
    }
    return "Outstanding performance"
}

private fun calculateTotalRuns(match: CricketMatch): String {
    val scoreRegex = Regex("""(\d+)""")
    val total = match.teams.sumOf { team ->
        scoreRegex.find(team.score)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
    return if (total > 0) total.toString() else "—"
}

private fun calculateTotalWickets(match: CricketMatch): String {
    val wicketRegex = Regex("""/(\d+)""")
    val total = match.teams.sumOf { team ->
        wicketRegex.find(team.score)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
    return if (total > 0) total.toString() else "—"
}

private fun extractHighestScore(match: CricketMatch): String {
    val score = match.score
    if (score != null && score.batsmen.isNotEmpty()) {
        val top = score.batsmen.maxByOrNull { it.runs }
        if (top != null) return "${top.runs}(${top.balls})"
    }
    return "—"
}

private fun extractBestBowling(match: CricketMatch): String {
    val score = match.score
    if (score != null && score.bowlers.isNotEmpty()) {
        val best = score.bowlers.maxByOrNull { it.wickets }
        if (best != null && best.wickets > 0) return "${best.wickets}/${best.runs}"
    }
    return "—"
}

private fun buildTimelineEvents(match: CricketMatch): List<TimelineEvent> {
    val events = mutableListOf<TimelineEvent>()

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)

    // Toss / start
    events.add(TimelineEvent("Start", "${match.matchType.uppercase()} begins at ${match.venue}"))

    if (team1 != null && team1.score.isNotBlank()) {
        events.add(TimelineEvent("1st Inn", "${team1.acronym.ifBlank { team1.name.take(3) }} scored ${team1.score}"))
    }

    events.add(TimelineEvent("Break", "Innings Break"))

    if (team2 != null && team2.score.isNotBlank()) {
        events.add(TimelineEvent("2nd Inn", "${team2.acronym.ifBlank { team2.name.take(3) }} scored ${team2.score}"))
    }

    // Result
    val resultText = match.currentStatus.ifBlank { match.status }
    if (resultText.isNotBlank()) {
        events.add(TimelineEvent("Result", resultText))
    }

    return events
}
