package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.glass.GlassCardHero
import com.tanush.bouncer.ui.components.glass.LiveIndicator
import com.tanush.bouncer.ui.components.glass.TeamFlagBackground
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.etchedText
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Hero Scorecard Card — the primary visual element at the top of the match detail screen.
 *
 * Replaces a video player with a rich, gradient-backed scorecard featuring:
 * - Team logos prominently displayed with a "vs" divider
 * - Large score and overs display
 * - Live indicator and match type badge
 * - Current status text
 * - Recent balls row
 * - Subtle animated glow effects for live matches
 */
@Composable
fun HeroScorecardCard(
    match: CricketMatch,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val teams = match.teams

    val leftColor = teams.getOrNull(0)?.let { TeamPrimaryColors.getTeamColor(it.acronym) }
    val rightColor = teams.getOrNull(1)?.let { TeamPrimaryColors.getTeamColor(it.acronym) }

    GlassCardHero(
        modifier = modifier.fillMaxWidth(),
        leftTeamColor = leftColor,
        rightTeamColor = rightColor,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Team flag background
            if (leftColor != null && rightColor != null) {
                TeamFlagBackground(
                    leftTeamCode = teams.getOrNull(0)?.acronym ?: "",
                    rightTeamCode = teams.getOrNull(1)?.acronym ?: "",
                    modifier = Modifier.matchParentSize()
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.xl, vertical = dimens.lg),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row — match info + live badge
                HeroTopRow(match = match)

                Spacer(modifier = Modifier.height(dimens.md))

                // Center — team logos, scores
                HeroCenterSection(match = match)

                Spacer(modifier = Modifier.height(dimens.md))

                // Bottom — status + recent balls
                HeroBottomSection(match = match)
            }
        }
    }
}

// ── Top Row ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroTopRow(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Match type + title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.sm),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = match.matchType.uppercase(),
                style = typography.labelSmall,
                color = colors.goldPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = match.matchTitle,
                style = typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Live indicator
        AnimatedVisibility(
            visible = match.isLive,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LiveIndicator(modifier = Modifier.padding(start = dimens.sm))
        }
    }
}

// ── Center Section — Team logos + scores ─────────────────────────────────────

@Composable
private fun HeroCenterSection(match: CricketMatch) {
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Team 1
        if (team1 != null) {
            HeroTeamColumn(
                team = team1,
                modifier = Modifier.weight(1f),
            )
        }

        // VS divider
        Text(
            text = "VS",
            style = typography.headingSmall,
            color = colors.goldPrimary.copy(alpha = 0.5f),
            fontWeight = FontWeight.ExtraLight,
            modifier = Modifier.padding(horizontal = dimens.sm)
        )

        // Team 2
        if (team2 != null) {
            HeroTeamColumn(
                team = team2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroTeamColumn(
    team: CricketMatch.Team,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val teamColor = TeamPrimaryColors.getTeamColor(team.acronym)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.xs),
    ) {
        // Team logo
        TeamLogoCircle(
            teamCode = team.acronym,
            logoUrl = team.logoUrl,
            size = 56.dp,
            showGlow = team.isBatting,
            showBorder = true
        )

        Spacer(modifier = Modifier.height(dimens.xs))

        // Team name
        Text(
            text = team.acronym.ifBlank { team.name.take(3).uppercase() },
            style = typography.labelMedium,
            fontWeight = if (team.isBatting) FontWeight.Bold else FontWeight.Medium,
            color = if (team.isBatting) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        // Score
        if (team.score.isNotBlank()) {
            Text(
                text = team.score,
                style = typography.scoreDisplay,
                color = if (team.isBatting) colors.textPrimary else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.etchedText()
            )

            // Overs
            if (team.overs.isNotBlank() && team.overs != "0.0") {
                Text(
                    text = "${team.overs} ov",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Bottom Section ──────────────────────────────────────────────────────────

@Composable
private fun HeroBottomSection(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Status text
        val statusText = match.currentStatus.ifBlank { match.info }
        if (statusText.isNotBlank()) {
            Text(
                text = statusText.uppercase(),
                style = typography.labelSmall,
                color = if (match.isLive) colors.goldPrimary else colors.textMuted,
                fontWeight = if (match.isLive) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 1.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Recent balls
        val balls = match.recentBalls
            ?: match.score?.recentBalls?.split(",")?.map { it.trim() }
        if (match.isLive && !balls.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(dimens.md))
            // Using global RecentBallsRow but could be refined
            com.tanush.bouncer.ui.components.RecentBallsRow(
                balls = balls,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Animated glow modifier for live matches ─────────────────────────────────

@Composable
private fun Modifier.animatedGlow(): Modifier {
    val colors = BouncerDesignSystem.colors
    val transition = rememberInfiniteTransition(label = "heroGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    return this.drawBehind {
        // Radial glow at center-top
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = glowAlpha),
                    Color.Transparent,
                ),
                center = Offset(size.width / 2f, size.height * 0.3f),
                radius = size.width * 0.6f,
            ),
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun HeroScorecardCardLivePreview() {
    val colors = BouncerDesignSystem.colors
    BouncerTheme(darkTheme = true, dynamicColor = false) {
        HeroScorecardCard(
            match = CricketMatch(
                id = "1",
                seriesId = "s1",
                matchTitle = "ICC CWC: India vs Australia, Final",
                matchType = "ODI",
                status = "Live",
                teams = listOf(
                    CricketMatch.Team(
                        name = "Australia",
                        score = "140/7",
                        status = "batting",
                        isBatting = true,
                        logoUrl = null,
                        acronym = "AUS",
                        overs = "36.1"
                    ),
                    CricketMatch.Team(
                        name = "India",
                        score = "",
                        status = "",
                        isBatting = false,
                        logoUrl = null,
                        acronym = "IND"
                    )
                ),
                venue = "Narendra Modi Stadium",
                date = "2026-02-17",
                matchTime = "2:30 PM",
                score = null,
                currentStatus = "AUS choose to bat • CRR: 3.94",
                info = "",
                recentBalls = listOf("0", "1", "4", "W", "6", "2")
            ),
            modifier = Modifier
                .padding(16.dp)
                .height(BouncerDimens.heroCardHeight),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun HeroScorecardCardCompletedPreview() {
    val colors = BouncerDesignSystem.colors
    BouncerTheme(darkTheme = false, dynamicColor = false) {
        HeroScorecardCard(
            match = CricketMatch(
                id = "2",
                seriesId = "s2",
                matchTitle = "India vs England, 3rd Test",
                matchType = "TEST",
                status = "Complete",
                teams = listOf(
                    CricketMatch.Team(
                        name = "India",
                        score = "432/10 & 285/10",
                        status = "",
                        isBatting = false,
                        logoUrl = null,
                        acronym = "IND",
                        overs = "120.3"
                    ),
                    CricketMatch.Team(
                        name = "England",
                        score = "290/10 & 182/10",
                        status = "",
                        isBatting = false,
                        logoUrl = null,
                        acronym = "ENG",
                        overs = "56.2"
                    )
                ),
                venue = "Wankhede Stadium",
                date = "2026-02-15",
                matchTime = "",
                score = null,
                currentStatus = "India won by 245 runs",
                info = ""
            ),
            modifier = Modifier
                .padding(16.dp)
                .height(BouncerDimens.heroCardHeight),
        )
    }
}
