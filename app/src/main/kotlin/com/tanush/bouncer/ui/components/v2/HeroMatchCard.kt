package com.tanush.bouncer.ui.components.v2

import kotlin.math.roundToInt
import com.tanush.bouncer.ui.theme.liftForContrast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberMatchTeamPalette
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The featured match at the top of Home: scores, recent balls, run rates, win probability.
 *
 * Full-bleed on one tonal step up from the background — no card, no radius, no shadow, and no
 * team-colour gradient wash. It used to be an elevated 16dp-radius card over a three-stop
 * gradient, which made the same match appear twice in two different visual languages (here and
 * again as row one of the list below). It is now the same row grammar as the list, distinguished
 * by the extra live detail it carries and by sitting on `surface` instead of `background`.
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

    val teamPalette = rememberMatchTeamPalette(
        match.teams.getOrNull(0)?.name,
        match.teams.getOrNull(1)?.name,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {

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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (match.isLive) {
                    StatusBadgeV2(statusType = MatchStatusType.Live)
                }
            }

            Spacer(Modifier.height(spacing.md))

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
                )
                Spacer(Modifier.height(spacing.xs))
                TeamRowV2(
                    teamName = team2.name,
                    teamAcronym = team2.acronym,
                    score = team2.score,
                    overs = team2.overs,
                    isBatting = team2.isBatting,
                    logoUrl = team2.logoUrl,
                )
            }

            // Status line. Neutral ink — see MatchCardV2.MatchCardStatusLine.
            if (match.currentStatus.isNotBlank()) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = match.currentStatus,
                    style = typography.titleSmall,
                    color = if (match.currentStatus.contains("won", ignoreCase = true)) {
                        colors.win
                    } else {
                        colors.textSecondary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                            text = "CRR $it",
                            style = typography.numericSmall,
                            color = colors.textSecondary,
                        )
                    }
                    rrr?.let {
                        Text(
                            text = "RRR $it",
                            style = typography.numericSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            // Win probability bar. Null means the server has no number for this match —
            // offline, not yet synced, or a format it declines to score — so the bar is
            // omitted entirely. The old `!= 50.0` guard could not distinguish that from a
            // genuinely even match.
            val winProb = match.winProbability
            if (winProb != null && match.teams.size >= 2) {
                Spacer(Modifier.height(spacing.md))
                // A Test's draw share belongs to neither side; taking the away number as the
                // complement would hand it to them. It gets its own slice instead, so the two
                // labels deliberately do not add up to 100 on a match that can be drawn.
                val draw = ((match.winProbabilityDraw ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                val home = (winProb / 100.0).toFloat().coerceIn(0f, 1f - draw)
                val away = (1f - draw - home).coerceAtLeast(0f)
                // Lifted against the block fill: a team colour that clears 3:1 on the app
                // background can still read as an empty bar here.
                val homeColor = teamPalette.home.primary
                    .liftForContrast(colors.surface, SEGMENT_MIN_CONTRAST)
                val awayColor = teamPalette.away.primary
                    .liftForContrast(colors.surface, SEGMENT_MIN_CONTRAST)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${match.teams[0].acronym} ${(home * WIN_PROB_PERCENT).roundToInt()}%",
                        style = typography.numericSmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(away * WIN_PROB_PERCENT).roundToInt()}% ${match.teams[1].acronym}",
                        style = typography.numericSmall,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                home to homeColor,
                                home to colors.textSecondary.copy(alpha = 0.5f),
                                (home + draw) to colors.textSecondary.copy(alpha = 0.5f),
                                (home + draw) to awayColor,
                            )
                        )
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}
