package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.glass.BallEvent
import com.tanush.bouncer.ui.components.glass.BallsRow
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.LiveIndicator
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.bouncerClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.ScrapedBatsman

/**
 * Primary match card — glass morphism variant.
 *
 * Layout (top → bottom):
 * 1. Header — match-type chip (glass pill), series/title, LiveIndicator
 * 2. Team rows — TeamLogoCircle, acronym, score & overs
 * 3. Status — gold for live, muted for others
 * 4. Footer — BallsRow with recent balls (live only)
 */
@Composable
fun MatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
    isRefreshing: Boolean = false,
    liveAiCommentary: String? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val shapes = BouncerDesignSystem.shapes

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .bouncerClickable(
                enabled = !isRefreshing,
                onClick = onClick
            )
            .graphicsLayer {
                alpha = if (isRefreshing) 0.6f else 1f
            },
        useGoldBorder = match.isLive,
        shape = shapes.matchCard,
        contentPadding = PaddingValues(dimens.spacing.md)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── 1. Header ───────────────────────────────────────────────
            HeaderRow(match = match)

            Spacer(modifier = Modifier.height(dimens.spacing.md))

            // ── 2. Teams ────────────────────────────────────────────────
            if (match.score != null) {
                match.teams.forEachIndexed { index, team ->
                    TeamRow(team = team, hasScore = true, matchTime = match.matchTime)
                    if (index == 0 && match.teams.size > 1) {
                        Spacer(modifier = Modifier.height(dimens.spacing.sm))
                    }
                }
            } else {
                UpcomingMatchView(match = match)
            }

            // ── NEW: Strike Row (Not-out batters) ───────────────────────
            val batters = match.scrapedBatsmen.orEmpty()
            if (match.isLive && batters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimens.spacing.md))
                StrikeRow(batters = batters)
            }
            
            // ── NEW: Win Probability ────────────────────────────────────
            if (match.isLive) {
                Spacer(modifier = Modifier.height(dimens.spacing.md))
                WinProbabilityBar(
                    probability = match.winProbability,
                    team1Color = TeamPrimaryColors.getTeamColor(match.teams.getOrNull(0)?.acronym ?: ""),
                    team2Color = TeamPrimaryColors.getTeamColor(match.teams.getOrNull(1)?.acronym ?: "")
                )
            }

            // ── 3. Status ───────────────────────────────────────────────
            val statusText = match.currentStatus.ifBlank { match.info }
            if (statusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(dimens.spacing.md))
                Text(
                    text = statusText,
                    style = typography.bodySmall,
                    color = if (match.isLive) colors.etchedGold else colors.textMuted,
                    fontWeight = if (match.isLive) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 4. Footer — recent balls (live only) ────────────────────
            val rawBalls = match.recentBalls
                ?: match.score?.recentBalls?.split(",")?.map { it.trim() }
            AnimatedVisibility(
                visible = match.isLive && !rawBalls.isNullOrEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(dimens.spacing.md))
                    BallsRow(
                        events = rawBalls.orEmpty().map { parseBallString(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Header row ──────────────────────────────────────────────────────────────

@Composable
private fun HeaderRow(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val shapes = BouncerDesignSystem.shapes

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            modifier = Modifier.weight(1f)
        ) {
            // Match type chip — glass pill with etched gold text
            GlassCardSmall(
                shape = shapes.chipPill,
                showNoiseOverlay = false,
                contentPadding = PaddingValues(horizontal = dimens.spacing.sm, vertical = 2.dp)
            ) {
                Text(
                    text = match.matchType,
                    style = typography.labelSmall,
                    color = colors.etchedGold  // Use the new handcrafted gold
                )
            }

            Text(
                text = match.matchTitle,
                style = typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = match.isLive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LiveIndicator(modifier = Modifier.padding(start = dimens.spacing.sm))
        }
    }
}

// ── Upcoming Match View ──────────────────────────────────────────────────

@Composable
private fun UpcomingMatchView(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Team 1
        val team1 = match.teams.getOrNull(0)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            TeamLogoCircle(
                teamCode = team1?.acronym ?: "",
                logoUrl = team1?.logoUrl,
                size = 56.dp, // Design _14 Spec
                showBorder = true
            )
            Spacer(modifier = Modifier.height(dimens.spacing.sm))
            Text(
                text = team1?.acronym ?: "",
                style = typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        // Countdown Timer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1.2f)
        ) {
            Text(
                text = "Starts in",
                style = typography.labelSmall,
                color = colors.goldPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = match.matchTime.ifBlank { "02:45" },
                style = typography.scoreSecondary,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = 2.sp
            )
            Text(
                text = match.venue.split(",").lastOrNull()?.trim() ?: "Lord's, London",
                style = typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.width(100.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // Team 2
        val team2 = match.teams.getOrNull(1)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            TeamLogoCircle(
                teamCode = team2?.acronym ?: "",
                logoUrl = team2?.logoUrl,
                size = 56.dp, // Design _14 Spec
                showBorder = true
            )
            Spacer(modifier = Modifier.height(dimens.spacing.sm))
            Text(
                text = team2?.acronym ?: "",
                style = typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }
    }
}

// ── Team row ────────────────────────────────────────────────────────────────

@Composable
private fun TeamRow(
    team: CricketMatch.Team,
    hasScore: Boolean,
    matchTime: String
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val isBatting = team.isBatting
    val teamCode = team.acronym.ifBlank { team.name }
    val textColor = if (isBatting) {
        TeamPrimaryColors.getTeamColor(teamCode)
    } else {
        colors.textSecondary
    }
    val fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Team logo - Refined size (w-12 = 48dp in _14 header, but match card uses jumbo=32dp usually)
        TeamLogoCircle(
            teamCode = teamCode,
            logoUrl = team.logoUrl,
            size = if (hasScore) dimens.iconSize.jumbo else 48.dp, // Larger for upcoming (design _14)
            showBorder = true,
            showGlow = isBatting
        )

        Spacer(modifier = Modifier.width(dimens.spacing.md))

        // Team name/code
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = teamCode,
                style = if (hasScore) typography.bodyMedium else typography.headingSmall,
                fontWeight = if (hasScore) fontWeight else FontWeight.Bold,
                color = if (hasScore) textColor else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!hasScore && matchTime.isNotBlank()) {
                Text(
                    text = team.name,
                    style = typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }

        // Score & overs OR match time (Countdown refined)
        if (hasScore && team.score.isNotBlank()) {
            Text(
                text = team.score,
                style = typography.scoreCompact,
                fontWeight = FontWeight.Bold,
                color = if (isBatting) colors.textPrimary else colors.textSecondary
            )
            if (team.overs.isNotBlank() && team.overs != "0.0") {
                Spacer(modifier = Modifier.width(dimens.spacing.xs))
                Text(
                    text = "(${team.overs} ov)",
                    style = typography.bodySmall,
                    color = colors.textMuted
                )
            }
        } else if (!hasScore && matchTime.isNotBlank() && teamCode == "AUS") { // Only show once in the middle for upcoming
             // This is handled by a separate block in the parent for center alignment
        }
    }
}

// ── NEW: Strike Row Component ──────────────────────────────────────────────

@Composable
private fun StrikeRow(batters: List<ScrapedBatsman>) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        batters.take(2).forEach { batter ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                if (batter.isStriker) colors.goldPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .drawBehind {
                        // Gold-inset left border Spec for striker
                        if (batter.isStriker) {
                            drawRect(
                                color = colors.goldPrimary,
                                size = Size(4.dp.toPx(), size.height)
                            )
                        }
                    }
                    .padding(start = dimens.spacing.md, end = dimens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = batter.name,
                        style = typography.bodySmall,
                        color = if (batter.isStriker) colors.textPrimary else colors.textSecondary,
                        fontWeight = if (batter.isStriker) FontWeight.Bold else FontWeight.Normal
                    )
                    if (batter.isStriker) {
                        Spacer(modifier = Modifier.width(dimens.spacing.xs))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.goldPrimary)
                        )
                    }
                }
                
                Text(
                    text = "${batter.runs}(${batter.balls})",
                    style = typography.labelSmall,
                    color = if (batter.isStriker) colors.goldPrimary else colors.textMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── NEW: Win Probability Bar ────────────────────────────────────────────────

@Composable
private fun WinProbabilityBar(
    probability: Double,
    team1Color: Color,
    team2Color: Color,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${probability.toInt()}%",
                style = typography.labelSmall,
                color = team1Color,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "WIN CHANCE",
                style = typography.labelSmall,
                color = colors.textMuted,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
            
            Text(
                text = "${(100 - probability).toInt()}%",
                style = typography.labelSmall,
                color = team2Color,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Refined slim Win Meter (h-0.5 = 2dp per 디자인 _14)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((probability / 100).toFloat().coerceIn(0.01f, 0.99f))
                        .background(team1Color)
                        .drawBehind {
                            // Gold shadow/glow if probability is high
                            if (probability > 70) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(team1Color, Color.Transparent)
                                    ),
                                    alpha = 0.3f
                                )
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(((100 - probability) / 100).toFloat().coerceIn(0.01f, 0.99f))
                        .background(team2Color)
                )
            }
        }
    }
}

// ── Ball string → BallEvent parser ──────────────────────────────────────────

private fun parseBallString(ball: String): BallEvent {
    val lower = ball.trim().lowercase()
    return when {
        lower == "w" || lower.startsWith("wk") || lower.contains("wicket") -> BallEvent.WICKET
        lower == "wd" || lower.contains("wide") -> BallEvent.WIDE
        lower == "nb" || lower.contains("no ball") || lower.contains("noball") -> BallEvent.NO_BALL
        lower == "lb" || lower.contains("leg bye") -> BallEvent.LEG_BYE
        lower == "b" || (lower.contains("bye") && !lower.contains("leg")) -> BallEvent.BYE
        lower == "6" || lower.contains("six") -> BallEvent.SIX
        lower == "4" || lower.contains("four") || lower.contains("boundary") -> BallEvent.FOUR
        lower == "3" -> BallEvent.THREE
        lower == "2" -> BallEvent.DOUBLE
        lower == "1" -> BallEvent.SINGLE
        lower == "0" || lower == "." || lower.contains("dot") -> BallEvent.DOT
        else -> {
            val num = lower.toIntOrNull()
            when (num) {
                0 -> BallEvent.DOT
                1 -> BallEvent.SINGLE
                2 -> BallEvent.DOUBLE
                3 -> BallEvent.THREE
                4 -> BallEvent.FOUR
                6 -> BallEvent.SIX
                else -> BallEvent.DOT
            }
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MatchCardLivePreview() {
    com.tanush.bouncer.ui.theme.BouncerPreviewTheme {
        val mockMatch = CricketMatch(
            id = "preview-1",
            seriesId = "",
            matchTitle = "India vs Australia",
            matchType = "T20",
            status = "Live",
            teams = listOf(
                CricketMatch.Team("India", "185/4", "", true, null, "IND", "18.2"),
                CricketMatch.Team("Australia", "142/6", "", false, null, "AUS", "15.0")
            ),
            venue = "MCG, Melbourne",
            date = "Today",
            matchTime = "7:30 PM",
            score = null,
            currentStatus = "India need 44 runs from 12 balls",
            info = "Live",
            winProbability = 65.0
        )
        MatchCard(match = mockMatch, onClick = {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MatchCardUpcomingPreview() {
    com.tanush.bouncer.ui.theme.BouncerPreviewTheme {
        val mockMatch = CricketMatch(
            id = "preview-2",
            seriesId = "",
            matchTitle = "England vs South Africa",
            matchType = "ODI",
            status = "Upcoming",
            teams = listOf(
                CricketMatch.Team("England", "", "", false, null, "ENG", ""),
                CricketMatch.Team("South Africa", "", "", false, null, "SA", "")
            ),
            venue = "Lord's, London",
            date = "Tomorrow",
            matchTime = "2:30 PM",
            score = null,
            currentStatus = "",
            info = "1st ODI"
        )
        MatchCard(match = mockMatch, onClick = {})
    }
}
