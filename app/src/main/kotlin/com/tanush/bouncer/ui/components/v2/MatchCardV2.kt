package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Compact match card for lists — series header, two team rows, status line.
 */
@Composable
fun MatchCardV2(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val statusType = determineMatchStatus(match)

    BouncerCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            // Series & format header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${match.matchTitle} • ${match.matchType}",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                StatusBadgeV2(statusType = statusType)
            }

            Spacer(Modifier.height(spacing.md))

            // Team rows
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
                )
                Spacer(Modifier.height(spacing.sm))
                TeamRowV2(
                    teamName = team2.name,
                    teamAcronym = team2.acronym,
                    score = team2.score,
                    overs = team2.overs,
                    isBatting = team2.isBatting,
                    logoUrl = team2.logoUrl,
                )
            }

            // Status line
            if (match.currentStatus.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.sm),
                    color = colors.divider,
                )
                Text(
                    text = match.currentStatus,
                    style = typography.bodySmall,
                    color = if (match.isLive) colors.primary else colors.textSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}
