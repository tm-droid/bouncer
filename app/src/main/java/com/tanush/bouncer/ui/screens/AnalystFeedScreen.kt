package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.animations.staggeredFadeIn
import com.tanush.bouncer.ui.components.glass.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import kotlin.math.absoluteValue

@Composable
fun AnalystFeedScreen(
    viewModel: LiveActivityViewModel,
    onBackClick: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val matches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var selectedFormat by remember { mutableStateOf("All") }
    val formats = listOf("All", "T20", "ODI", "Test")

    val filteredMatches = remember(matches, selectedFormat) {
        if (selectedFormat == "All") matches
        else matches.filter { it.matchType.contains(selectedFormat, ignoreCase = true) }
    }

    val liveMatches = remember(filteredMatches) { filteredMatches.filter { it.isLive } }
    val upcomingMatches = remember(filteredMatches) { filteredMatches.filter { !it.isLive } }

    GlassBloomBackground(modifier = modifier.fillMaxSize()) {
        androidx.compose.animation.Crossfade(
            targetState = isLoading && matches.isEmpty(),
            animationSpec = tween(500),
            label = "analystFeedLoading"
        ) { isInitialLoading ->
            if (isInitialLoading) {
                AnalystFeedSkeleton()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = dimens.screenHorizontal,
                        end = dimens.screenHorizontal,
                        top = dimens.lg,
                        bottom = dimens.xxxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
                ) {
                    // ── Header ──
                    item(key = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                onClick = onBackClick,
                                contentDescription = "Back"
                            )
                            Spacer(modifier = Modifier.width(dimens.md))
                            Text(
                                text = "ANALYST FEED",
                                style = typography.headingSmall,
                                color = colors.goldPrimary,
                                letterSpacing = 2.sp,
                                modifier = Modifier.weight(1f)
                            )
                            GlassIconButton(
                                icon = Icons.Filled.FilterList,
                                onClick = { /* filter action */ },
                                contentDescription = "Filter"
                            )
                        }
                    }

                    // ── Format Filter Chips ──
                    item(key = "chips") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(dimens.sm)
                        ) {
                            formats.forEach { format ->
                                GlassFilterChip(
                                    text = format,
                                    selected = selectedFormat == format,
                                    onClick = { selectedFormat = format }
                                )
                            }
                        }
                    }

                    // ── Real-Time Section ──
                    item(key = "live_header") {
                        SectionHeader(
                            title = "REAL-TIME ANALYSIS",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            showLiveDot = liveMatches.isNotEmpty()
                        )
                    }

                    if (liveMatches.isEmpty()) {
                        item(key = "no_live") {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(dimens.xl)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No live matches",
                                        style = typography.headingSmall,
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(dimens.sm))
                                    Text(
                                        text = "Check back when matches are in progress",
                                        style = typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "live_stack") {
                            LiveMatchCardStack(
                                liveMatches = liveMatches,
                                onMatchClick = onMatchClick,
                                isRefreshing = isRefreshing || isLoading
                            )
                        }
                    }

                    // ── Upcoming Section ──
                    if (upcomingMatches.isNotEmpty()) {
                        item(key = "upcoming_header") {
                            SectionHeader(
                                title = "UPCOMING MATCHES",
                                icon = Icons.Filled.Schedule
                            )
                        }

                        itemsIndexed(
                            items = upcomingMatches,
                            key = { _, match -> "upcoming_${match.id}" }
                        ) { index, match ->
                            UpcomingMatchCard(
                                match = match,
                                onClick = { onMatchClick(match.id) },
                                isRefreshing = isRefreshing || isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalystFeedSkeleton() {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenHorizontal, vertical = dimens.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        // Header skeleton
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.glassSurface).shimmerEffect())
            Spacer(modifier = Modifier.width(dimens.md))
            Box(modifier = Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(dimens.radius.xs)).background(colors.glassSurface).shimmerEffect())
        }
        
        // Chips skeleton
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
            repeat(4) {
                Box(modifier = Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(colors.glassSurface).shimmerEffect())
            }
        }

        // Section header
        Box(modifier = Modifier.width(180.dp).height(20.dp).clip(RoundedCornerShape(dimens.radius.xs)).background(colors.glassSurface).shimmerEffect())
        
        // Large card skeleton
        Box(modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(dimens.radius.xl)).background(colors.glassSurface).shimmerEffect())
        
        // Another section header
        Box(modifier = Modifier.width(150.dp).height(20.dp).clip(RoundedCornerShape(dimens.radius.xs)).background(colors.glassSurface).shimmerEffect())
        
        // Small cards
        repeat(2) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(dimens.radius.lg)).background(colors.glassSurface).shimmerEffect())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    showLiveDot: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BouncerDesignSystem.dimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(BouncerDesignSystem.dimens.iconSize.md)
        )
        Text(
            text = title,
            style = typography.labelMedium,
            color = colors.goldPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        if (showLiveDot) {
            Spacer(modifier = Modifier.width(BouncerDesignSystem.dimens.spacing.xs))
            LivePulse(size = BouncerDesignSystem.dimens.spacing.sm)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Live Match Card Stack
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveMatchCardStack(
    liveMatches: List<CricketMatch>,
    onMatchClick: (String) -> Unit,
    isRefreshing: Boolean = false
) {
    val dimens = BouncerDesignSystem.dimens

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background peek card (second match)
        if (liveMatches.size > 1) {
            val peekMatch = liveMatches[1]
            GlassCardSmall(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacing.md)
                    .graphicsLayer {
                        translationY = dimens.spacing.md.toPx()
                        scaleX = 0.95f
                        alpha = if (isRefreshing) 0.3f else 0.5f
                    }
                    .clickable(onClick = if (isRefreshing) ({}) else ({ onMatchClick(peekMatch.id) }))
            ) {
                PeekMatchContent(match = peekMatch)
            }
        }

        // Primary featured card
        val primary = liveMatches.first()
        GlassCardHero(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMatchClick(primary.id) }
                .graphicsLayer {
                    alpha = if (isRefreshing) 0.7f else 1f
                }
        ) {
            LiveAnalysisCard(match = primary, onClick = { onMatchClick(primary.id) })
        }
    }

    // Additional live matches beyond the first two
    if (liveMatches.size > 2) {
        Column {
            liveMatches.drop(2).forEach { match ->
                Spacer(modifier = Modifier.height(dimens.cardSpacing))
                LiveAnalysisCard(
                    match = match,
                    onClick = { onMatchClick(match.id) },
                    isRefreshing = isRefreshing
                )
            }
        }
    }
}

@Composable
private fun PeekMatchContent(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
        ) {
            TeamLogoCircle(
                teamCode = team1?.acronym ?: "T1",
                logoUrl = team1?.logoUrl,
                size = BouncerDesignSystem.dimens.ballIndicatorSize,
                showBorder = false,
                showGlow = false
            )
            Text(
                text = "${team1?.acronym ?: "T1"} vs ${team2?.acronym ?: "T2"}",
                style = typography.labelMedium,
                color = colors.textSecondary
            )
        }
        LiveIndicator()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Live Analysis Card (Primary)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveAnalysisCard(
    match: CricketMatch,
    onClick: () -> Unit,
    isRefreshing: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val team1Color = team1?.let { TeamPrimaryColors.getTeamColor(it.acronym) } ?: colors.goldPrimary
    val team2Color = team2?.let { TeamPrimaryColors.getTeamColor(it.acronym) } ?: colors.goldPrimary

    GlassCardLarge(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (isRefreshing) ({}) else onClick)
            .graphicsLayer {
                alpha = if (isRefreshing) 0.6f else 1f
            },
        useGoldBorder = true
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top row: match type badge + series + live indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
                ) {
                    MatchTypeBadge(matchType = match.matchType)
                    Text(
                        text = match.matchTitle,
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = BouncerDesignSystem.dimens.matchCardMinHeight)
                    )
                }
                LiveIndicator()
            }

            Spacer(modifier = Modifier.height(dimens.lg))

            // Team scores section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
                ) {
                    TeamLogoCircle(
                        teamCode = team1?.acronym ?: "T1",
                        logoUrl = team1?.logoUrl,
                        size = BouncerDesignSystem.dimens.playerAvatarSize
                    )
                    Column {
                        Text(
                            text = team1?.acronym?.ifBlank { team1.name } ?: "T1",
                            style = typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = team1?.score ?: "",
                            style = typography.headingSmall,
                            color = colors.textPrimary
                        )
                        if (team1?.overs?.isNotBlank() == true && team1.overs != "0.0") {
                            Text(
                                text = "(${team1.overs} ov)",
                                style = typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                // VS separator
                Text(
                    text = "vs",
                    style = typography.labelSmall,
                    color = colors.textMuted
                )

                // Team 2
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
                ) {
                    TeamLogoCircle(
                        teamCode = team2?.acronym ?: "T2",
                        logoUrl = team2?.logoUrl,
                        size = BouncerDesignSystem.dimens.playerAvatarSize
                    )
                    Column {
                        Text(
                            text = team2?.acronym?.ifBlank { team2.name } ?: "T2",
                            style = typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = team2?.score ?: "",
                            style = typography.headingSmall,
                            color = colors.textPrimary
                        )
                        if (team2?.overs?.isNotBlank() == true && team2.overs != "0.0") {
                            Text(
                                text = "(${team2.overs} ov)",
                                style = typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
// ... [Lines omitted for brevity in call, but I will include them in real tool call]


            Spacer(modifier = Modifier.height(dimens.md))

            // Recent balls
            val ballEvents = remember(match.recentBalls) {
                match.recentBalls?.map { parseBall(it) } ?: emptyList()
            }
            if (ballEvents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
                ) {
                    Text(
                        text = "LAST 6",
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                    BallsRow(events = ballEvents)
                }
                Spacer(modifier = Modifier.height(dimens.md))
            }

            // Win probability bar
            val team1Prob = match.winProbability.toInt()
            
            DualProgressBar(
                team1Progress = team1Prob / 100f,
                team1Color = team1Color,
                team2Color = team2Color,
                height = BouncerDesignSystem.dimens.spacing.xs
            )
            Spacer(modifier = Modifier.height(BouncerDesignSystem.dimens.spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${team1?.acronym ?: "T1"} ${team1Prob}%",
                    style = typography.labelSmall,
                    color = team1Color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${100 - team1Prob}% ${team2?.acronym ?: "T2"}",
                    style = typography.labelSmall,
                    color = team2Color,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(dimens.md))

            // Status text
            if (match.currentStatus.isNotBlank()) {
                Text(
                    text = match.currentStatus,
                    style = typography.bodySmall,
                    color = colors.goldPrimary.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Match Type Badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchTypeBadge(matchType: String) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val shape = RoundedCornerShape(BouncerDesignSystem.dimens.radius.sm)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(colors.goldPrimary.copy(alpha = 0.15f))
            .padding(
                horizontal = BouncerDesignSystem.dimens.spacing.sm,
                vertical = BouncerDesignSystem.dimens.spacing.xs
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = matchType.uppercase(),
            style = typography.labelSmall,
            color = colors.goldPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Upcoming Match Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UpcomingMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    isRefreshing: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (isRefreshing) ({}) else onClick)
            .graphicsLayer {
                alpha = if (isRefreshing) 0.6f else 1f
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Match title
            Text(
                text = match.matchTitle,
                style = typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(dimens.md))

            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Team logos side by side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.md)
                ) {
                    TeamLogoCircle(
                        teamCode = team1?.acronym ?: "T1",
                        logoUrl = team1?.logoUrl,
                        size = BouncerDesignSystem.dimens.chipHeight
                    )
                    Text(
                        text = "${team1?.acronym?.ifBlank { team1.name } ?: "T1"} vs ${team2?.acronym?.ifBlank { team2.name } ?: "T2"}",
                        style = typography.headingSmall,
                        color = colors.textPrimary
                    )
                    TeamLogoCircle(
                        teamCode = team2?.acronym ?: "T2",
                        logoUrl = team2?.logoUrl,
                        size = BouncerDesignSystem.dimens.chipHeight
                    )
                }

                // Match type badge
                MatchTypeBadge(matchType = match.matchType)
            }

            Spacer(modifier = Modifier.height(dimens.md))

            // Time & Venue row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.md)
            ) {
                // Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = colors.goldPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(BouncerDesignSystem.dimens.iconSize.sm)
                    )
                    Text(
                        text = match.matchTime.ifBlank { match.date },
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                }

                // Venue
                if (match.venue.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.xs),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stadium,
                            contentDescription = null,
                            tint = colors.goldPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(BouncerDesignSystem.dimens.iconSize.sm)
                        )
                        Text(
                            text = match.venue,
                            style = typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ball Parser
// ─────────────────────────────────────────────────────────────────────────────

private fun parseBall(ball: String): BallEvent {
    val b = ball.trim().lowercase()
    return when {
        b == "w" || b.startsWith("wk") -> BallEvent.WICKET
        b == "wd" || b.contains("wide") -> BallEvent.WIDE
        b == "nb" || b.contains("no ball") -> BallEvent.NO_BALL
        b == "6" -> BallEvent.SIX
        b == "4" -> BallEvent.FOUR
        b == "3" -> BallEvent.THREE
        b == "2" -> BallEvent.DOUBLE
        b == "1" -> BallEvent.SINGLE
        b == "0" || b == "." -> BallEvent.DOT
        else -> b.toIntOrNull()?.let {
            when (it) {
                0 -> BallEvent.DOT
                1 -> BallEvent.SINGLE
                2 -> BallEvent.DOUBLE
                3 -> BallEvent.THREE
                4 -> BallEvent.FOUR
                6 -> BallEvent.SIX
                else -> BallEvent.DOT
            }
        } ?: BallEvent.DOT
    }
}
