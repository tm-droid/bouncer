package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.glass.BallEvent
import com.tanush.bouncer.ui.components.glass.BallsRow
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.LiveIndicator
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.bouncerClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.utils.parseRuns

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
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val shapes = BouncerDesignSystem.shapes

    // Staggered fade-in animation
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400)
        )
    }

    // Press feedback animation
    var isPressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        scale.animateTo(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = spring(stiffness = 400f)
        )
    }

    // Resolve the batting team's color for the accent border
    val battingTeam = match.teams.find { it.isBatting }
    val battingTeamColor = if (battingTeam != null) {
        TeamPrimaryColors.getTeamColor(battingTeam.acronym.ifBlank { battingTeam.name })
    } else {
        hierarchy.secondary.primary
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = if (isRefreshing) 0.6f else this@graphicsLayer.alpha * alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { if (!isRefreshing) onClick() }
                )
            }
            .drawBehind {
                // Gold accent line on left for live matches
                if (match.isLive) {
                    drawRect(
                        color = hierarchy.secondary.primary,
                        topLeft = Offset.Zero,
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
                // Subtle team color accent — thin 2dp top border at 20% alpha
                drawRect(
                    color = battingTeamColor.copy(alpha = 0.20f),
                    size = Size(size.width, 2.dp.toPx())
                )
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
                        // Gold gradient divider between teams
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            hierarchy.secondary.primary.copy(alpha = 0.3f),
                                            hierarchy.secondary.primary.copy(alpha = 0.5f),
                                            hierarchy.secondary.primary.copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Colored indicator dot for match state
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    match.isLive -> colors.statusLive
                                    statusText.contains("won", ignoreCase = true) -> colors.statusSuccess
                                    statusText.contains("upcoming", ignoreCase = true) -> colors.statusInfo
                                    else -> colors.etchedGold
                                }
                            )
                    )
                    Text(
                        text = statusText,
                        style = typography.bodySmall,
                        color = if (match.isLive) colors.etchedGold else colors.textMuted,
                        fontWeight = if (match.isLive) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
                        events = rawBalls.orEmpty().map { BallEvent.from(it) },
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
            // Match type chip — colored badge per format
            val matchTypeColor = when (match.matchType.uppercase()) {
                "T20", "T20I" -> colors.statusInfo   // Blue
                "ODI" -> colors.statusSuccess          // Green
                "TEST" -> colors.statusLive             // Red
                else -> colors.etchedGold
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(matchTypeColor.copy(alpha = 0.15f))
                    .padding(horizontal = dimens.spacing.sm, vertical = 2.dp)
            ) {
                Text(
                    text = match.matchType.uppercase(),
                    style = typography.labelSmall,
                    color = matchTypeColor,
                    fontWeight = FontWeight.Bold
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
    val hierarchy = BouncerDesignSystem.hierarchy
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
                color = hierarchy.secondary.primary.copy(alpha = 0.8f),
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
            // Extract numeric score for animation
            val numericScore = remember(team.score) {
                parseRuns(team.score)
            }
            
            // Animated score transition
            val animatedScore by animateIntAsState(
                targetValue = numericScore,
                animationSpec = spring(stiffness = 100f),
                label = "scoreAnimation"
            )
            
            // Shimmer effect on score text when updating (for live matches)
            val shimmerAlpha = if (isBatting) {
                val transition = rememberInfiniteTransition(label = "scoreShimmer")
                transition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shimmer"
                ).value
            } else {
                1f
            }
            
            // Reconstruct score text with animated number
            val scoreText = remember(animatedScore, team.score) {
                if (team.score.contains("/")) {
                    val parts = team.score.split("/")
                    "$animatedScore/${parts.getOrNull(1) ?: ""}"
                } else {
                    animatedScore.toString()
                }
            }
            
            Text(
                text = scoreText,
                style = if (isBatting) typography.scoreCompact.copy(fontSize = 20.sp) else typography.scoreCompact,
                fontWeight = if (isBatting) FontWeight.Black else FontWeight.Bold,
                color = if (isBatting) 
                    colors.textPrimary.copy(alpha = shimmerAlpha) 
                else 
                    colors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.graphicsLayer {
                    alpha = shimmerAlpha
                }
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
    val hierarchy = BouncerDesignSystem.hierarchy
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
                                if (batter.isStriker) hierarchy.secondary.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .drawBehind {
                        // Gold-inset left border Spec for striker
                        if (batter.isStriker) {
                            drawRect(
                                color = hierarchy.secondary.primary,
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
                                .background(hierarchy.secondary.primary)
                        )
                    }
                }
                
                Text(
                    text = "${batter.runs}(${batter.balls})",
                    style = typography.labelSmall,
                    color = if (batter.isStriker) hierarchy.secondary.primary else colors.textMuted,
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
