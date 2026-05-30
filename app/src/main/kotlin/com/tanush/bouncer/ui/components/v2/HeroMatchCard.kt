package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberMatchTeamPalette
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Featured hero match card — team-color gradient bg, large scores, recent balls, actions.
 */
@Composable
fun HeroMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavourite: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val shapes = BouncerThemeV2.shapes
    val elevation = BouncerThemeV2.elevation

    val teamPalette = rememberMatchTeamPalette(
        match.teams.getOrNull(0)?.name,
        match.teams.getOrNull(1)?.name,
    )
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            teamPalette.home.primary.copy(alpha = 0.10f),
            teamPalette.away.primary.copy(alpha = 0.05f),
            colors.surface,
        )
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.medium),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(modifier = Modifier.background(gradientBrush)) {
            Column(modifier = Modifier.padding(spacing.lg)) {

                // Series + LIVE badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${match.matchTitle} • ${match.matchType}",
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (match.isLive) {
                        StatusBadgeV2(statusType = MatchStatusType.Live)
                    }
                }

                Spacer(Modifier.height(spacing.lg))

                // Team scores
                if (match.teams.size >= 2) {
                    val team1 = match.teams[0]
                    val team2 = match.teams[1]

                    TeamRowV2(
                        teamName = team1.name,
                        teamAcronym = team1.acronym,
                        score = team1.score,
                        overs = team1.overs,
                        isBatting = team1.isBatting,
                        logoUrl = team1.logoUrl,
                        teamColor = teamPalette.home.primary,
                    )
                    Spacer(Modifier.height(spacing.md))
                    TeamRowV2(
                        teamName = team2.name,
                        teamAcronym = team2.acronym,
                        score = team2.score,
                        overs = team2.overs,
                        isBatting = team2.isBatting,
                        logoUrl = team2.logoUrl,
                        teamColor = teamPalette.away.primary,
                    )
                }

                Spacer(Modifier.height(spacing.md))

                // Status line
                if (match.currentStatus.isNotBlank()) {
                    val battingColor = if (match.teams.getOrNull(0)?.isBatting == true) {
                        teamPalette.home.primary
                    } else {
                        teamPalette.away.primary
                    }
                    Text(
                        text = match.currentStatus,
                        style = typography.titleSmall,
                        color = if (match.isLive) battingColor else colors.textSecondary,
                    )
                }

                // Recent balls
                val balls = match.recentBalls
                if (!balls.isNullOrEmpty()) {
                    Spacer(Modifier.height(spacing.sm))
                    RecentBallsStripV2(balls = balls)
                }

                // Run rates row
                val crr = match.score?.currentRunRate
                val rrr = match.score?.requiredRunRate
                if (crr != null || rrr != null) {
                    Spacer(Modifier.height(spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        crr?.let {
                            Text(
                                text = "CRR: $it",
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        rrr?.let {
                            Text(
                                text = "RRR: $it",
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                // Win probability bar
                val winProb = match.calculateHeuristicProbability()
                if (winProb != 50.0 && match.teams.size >= 2) {
                    Spacer(Modifier.height(spacing.md))
                    HorizontalDivider(color = colors.divider)
                    Spacer(Modifier.height(spacing.sm))
                    val fraction = (winProb / 100.0).toFloat().coerceIn(0.05f, 0.95f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${match.teams[0].acronym} ${winProb.toInt()}%",
                            style = typography.bodySmall,
                            color = teamPalette.home.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${(100 - winProb.toInt())}% ${match.teams[1].acronym}",
                            style = typography.bodySmall,
                            color = teamPalette.away.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    fraction to teamPalette.home.primary,
                                    fraction to teamPalette.away.primary.copy(alpha = 0.7f),
                                )
                            )
                    )
                }
            }
        }
    }
}
