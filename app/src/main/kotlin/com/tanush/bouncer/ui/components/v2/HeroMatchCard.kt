package com.tanush.bouncer.ui.components.v2

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.rememberSegmentColors
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlin.math.roundToInt

/**
 * The featured live match at the top of Home: scores, status, recent balls, run rates, win probability.
 *
 * Home only shows it for a live match. It used to feature whatever came first, so the Recent tab
 * opened on a finished match under a `100% / 0%` probability bar, and Upcoming on a pre-match prior
 * drawn exactly like a live reading.
 *
 * Full-bleed on one tonal step up from the background — no card, no radius, no shadow — and the same
 * row grammar as the list, distinguished by the extra live detail it carries.
 */
@Composable
fun HeroMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavourite: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            HeroHeaderRow(match)
            Spacer(Modifier.height(spacing.sm))
            MatchTeamRows(match = match, isLive = match.isLive)
            HeroStatusLine(match)
            val balls = match.recentBalls
            if (!balls.isNullOrEmpty()) {
                Spacer(Modifier.height(spacing.sm))
                RecentBallsStripV2(balls = balls)
            }
            HeroRunRates(match)
            HeroWinProbability(match)
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun HeroHeaderRow(match: CricketMatch) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = matchHeaderText(match),
            style = BouncerThemeV2.typography.bodySmall,
            color = BouncerThemeV2.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (match.isLive) {
            Spacer(Modifier.width(8.dp))
            StatusBadgeV2(statusType = MatchStatusType.Live)
        }
    }
}

@Composable
private fun HeroStatusLine(match: CricketMatch) {
    if (match.currentStatus.isBlank()) return
    Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
    Text(
        text = matchStatusText(match.currentStatus),
        style = BouncerThemeV2.typography.bodyMedium,
        color = BouncerThemeV2.colors.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeroRunRates(match: CricketMatch) {
    val crr = match.score?.currentRunRate
    val rrr = match.score?.requiredRunRate
    if (crr == null && rrr == null) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(BouncerThemeV2.spacing.lg)) {
        crr?.let { Text(text = "CRR $it", style = typography.numericSmall, color = colors.textSecondary) }
        rrr?.let { Text(text = "RRR $it", style = typography.numericSmall, color = colors.textSecondary) }
    }
}

/**
 * Win probability bar. Null means the server has no number for this match — offline, not yet synced,
 * or a format it declines to score — so the bar is omitted entirely rather than drawn at 50/50.
 * A Test's draw share belongs to neither side and gets its own slice, so the two labels deliberately
 * do not add up to 100 on a match that can be drawn.
 */
@Composable
private fun HeroWinProbability(match: CricketMatch) {
    val winProb = match.winProbability
    if (!match.isLive || winProb == null || match.teams.size < 2) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val draw = ((match.winProbabilityDraw ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val home = (winProb / 100.0).toFloat().coerceIn(0f, 1f - draw)
    val away = (1f - draw - home).coerceAtLeast(0f)
    val (homeColor, awayColor) = winProbabilityColors(match)
    val drawColor = colors.textSecondary.copy(alpha = DRAW_ALPHA)
    val homeLabel = match.teams[0].acronym.ifBlank { match.teams[0].name }
    val awayLabel = match.teams[1].acronym.ifBlank { match.teams[1].name }

    Spacer(Modifier.height(BouncerThemeV2.spacing.md))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$homeLabel ${(home * WIN_PROB_PERCENT).roundToInt()}%", style = typography.numericSmall, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text("${(away * WIN_PROB_PERCENT).roundToInt()}% $awayLabel", style = typography.numericSmall, color = colors.textSecondary)
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
                    home to drawColor,
                    (home + draw) to drawColor,
                    (home + draw) to awayColor,
                )
            )
    )
}

/**
 * Team colours only when the registry actually knows both sides — the shared rule, not a copy of
 * it. `rememberSegmentColors` carries the reasoning and the neutral fallback; this card and the
 * match-detail win-probability bar must not drift apart on it.
 */
@Composable
private fun winProbabilityColors(match: CricketMatch): Pair<Color, Color> = rememberSegmentColors(
    match.teams[0].name,
    match.teams[1].name,
    BouncerThemeV2.colors.surface,
    SEGMENT_MIN_CONTRAST,
)

/** Probability shares are stored 0..1 and shown as whole percentages. */
private const val WIN_PROB_PERCENT = 100f

/**
 * Contrast floor for a filled probability segment against the surface it sits on. Lower than a
 * text floor on purpose — a solid block needs far less separation than a glyph.
 */
private const val SEGMENT_MIN_CONTRAST = 2.2f
private const val DRAW_ALPHA = 0.5f
