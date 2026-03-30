package com.tanush.bouncer.ui.screens

import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.network.SquadScraper
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.ui.components.GlassHeader
import com.tanush.bouncer.ui.components.EmptyStateView
import com.tanush.bouncer.ui.components.glass.BallEvent
import com.tanush.bouncer.ui.components.glass.BallsRow
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardHero
import com.tanush.bouncer.ui.components.glass.GlassCardLarge
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.components.glass.GlassSegmentedControlWithIcons
import com.tanush.bouncer.ui.components.glass.LiveIndicator
import com.tanush.bouncer.ui.components.glass.SegmentItem
import com.tanush.bouncer.ui.components.glass.StatPill
import com.tanush.bouncer.ui.components.glass.TeamFlagBackground
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.services.LiveActivityService
import com.tanush.bouncer.ui.components.bouncerShimmerBrush
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.etchedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Tab Enum & Segment Items
// ─────────────────────────────────────────────────────────────────────────────

private enum class DetailTab { LIVE, SCORECARD, LINEUPS, OVERS, INFO }

// ─────────────────────────────────────────────────────────────────────────────
//  Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BouncerMatchDetailScreen(
    matchId: String,
    viewModel: LiveActivityViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens

    val matches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val match = remember(matches, matchId) { matches.find { it.id == matchId } }

    // ── State ──────────────────────────────────────────────────────────────
    var scorecard by remember { mutableStateOf<Scorecard?>(null) }
    var squads by remember { mutableStateOf<Pair<List<SquadScraper.Player>, List<SquadScraper.Player>>?>(null) }
    var ballByBall by remember { mutableStateOf<CricBuzzScraper.MatchDetails?>(null) }
    var ballByBallLoading by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var squadsLoading by remember { mutableStateOf(true) }
    var squadsError by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val scraper = remember { CricBuzzScraper() }
    val pagerState = rememberPagerState(pageCount = { DetailTab.entries.size })
    val liveTabLabel = stringResource(R.string.tab_live)
    val scorecardTabLabel = stringResource(R.string.tab_scorecard)
    val lineupsTabLabel = stringResource(R.string.tab_lineups)
    val oversTabLabel = stringResource(R.string.tab_overs)
    val infoTabLabel = stringResource(R.string.tab_info)
    val tabSegmentItems = remember(
        liveTabLabel,
        scorecardTabLabel,
        lineupsTabLabel,
        oversTabLabel,
        infoTabLabel
    ) {
        listOf(
            SegmentItem(liveTabLabel) { tint -> Icon(Icons.Outlined.LiveTv, null, Modifier.size(14.dp), tint) },
            SegmentItem(scorecardTabLabel) { tint -> Icon(Icons.AutoMirrored.Outlined.Assignment, null, Modifier.size(14.dp), tint) },
            SegmentItem(lineupsTabLabel) { tint -> Icon(Icons.Outlined.Groups, null, Modifier.size(14.dp), tint) },
            SegmentItem(oversTabLabel) { tint -> Icon(Icons.Outlined.SportsCricket, null, Modifier.size(14.dp), tint) },
            SegmentItem(infoTabLabel) { tint -> Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint) }
        )
    }

    // Sync pager <-> segment control
    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    // ── Data Fetching ──────────────────────────────────────────────────────
    LaunchedEffect(matchId) {
        // Fetch scorecard
        launch {
            try {
                scorecard = scraper.fetchScorecard(matchId)
            } catch (_: Exception) { }
            isLoading = false
        }
        // Fetch squads
        launch {
            try {
                squads = SquadScraper.fetchSquads(matchId)
            } catch (e: Exception) {
                Log.d("MatchDetail", "Failed to fetch squads", e)
                squadsError = true
            }
            squadsLoading = false
        }
        // Fetch ball-by-ball
        launch {
            try {
                ballByBall = scraper.fetchBallByBallAndBatsmen(matchId)
            } catch (e: Exception) { Log.d("MatchDetail", "Failed to fetch ball-by-ball", e) }
            ballByBallLoading = false
        }
    }

    // Auto-refresh for live matches
    LaunchedEffect(matchId, match?.isLive) {
        if (match?.isLive != true) return@LaunchedEffect
        while (true) {
            delay(8_000L)
            try {
                ballByBall = scraper.fetchBallByBallAndBatsmen(matchId)
            } catch (e: Exception) { Log.d("MatchDetail", "Live refresh: ball-by-ball failed", e) }
            try {
                scorecard = scraper.fetchScorecard(matchId)
            } catch (e: Exception) { Log.d("MatchDetail", "Live refresh: scorecard failed", e) }
        }
    }

    // Keep Live Activity service aligned with match liveness and user preferences.
    LaunchedEffect(match?.id, match?.isLive) {
        val current = match ?: return@LaunchedEffect
        val prefs = NotificationPrefsManager.getInstance(context).settings.value
        if (!prefs.liveActivityEnabled) {
            LiveActivityService.stop(context)
            return@LaunchedEffect
        }

        if (current.isLive) {
            LiveActivityService.start(
                context = context,
                matchId = current.id,
                style = prefs.liveActivityStyle,
                frequency = prefs.liveActivityUpdateFrequency.toUpdateFrequency()
            )
        } else {
            LiveActivityService.stop(context)
        }
    }

    // ── Share callback ─────────────────────────────────────────────────────
    val onShare: () -> Unit = {
        match?.let { m ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildShareText(m))
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Match"))
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    GlassBloomBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (match == null) {
                GlassHeader(
                    title = "",
                    onBackClick = onBackClick
                )
                EmptyStateView(
                    title = stringResource(R.string.state_no_matches_title),
                    message = stringResource(R.string.state_no_matches_desc),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimens.xxl)
                )
                return@GlassBloomBackground
            }

            // Header row
            GlassHeader(
                title = match.matchTitle,
                onBackClick = onBackClick,
                actions = {
                    GlassIconButton(
                        icon = Icons.Outlined.Share,
                        onClick = onShare
                    )
                }
            )

            // Score hero card
            MatchScoreHero(match = match)

            Spacer(modifier = Modifier.height(dimens.spacing.sm))

            // Tab bar
            GlassSegmentedControlWithIcons(
                items = tabSegmentItems,
                selectedIndex = selectedTabIndex,
                onSelect = { selectedTabIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.tabBarHeight)
                    .padding(horizontal = dimens.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(dimens.spacing.sm))

            // Pager content — weight(1f) ensures it takes remaining space in the Column
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { page ->
                // Fade content in/out based on page offset for smooth tab transitions
                val pageOffset = (
                    (pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction
                )
                val contentAlpha = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f) * 0.4f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    when (DetailTab.entries[page]) {
                    DetailTab.LIVE -> LiveTab(
                        match = match,
                        ballByBall = ballByBall,
                        isLoading = ballByBallLoading,
                        modifier = Modifier.fillMaxSize()
                    )
                    DetailTab.SCORECARD -> ScorecardTabWrapper(
                        scorecard = scorecard,
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxSize()
                    )
                    DetailTab.LINEUPS -> LineupsTab(
                        match = match,
                        squads = squads,
                        isLoading = squadsLoading,
                        hasError = squadsError,
                        onRetry = {
                            squadsLoading = true
                            squadsError = false
                            scope.launch {
                                try {
                                    squads = SquadScraper.fetchSquads(matchId)
                                } catch (_: Exception) {
                                    squadsError = true
                                }
                                squadsLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    DetailTab.OVERS -> OversTimelineTab(
                        recentBalls = ballByBall?.recentBalls
                            ?: match.recentBalls
                            ?: match.score?.recentBalls
                                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                            ?: emptyList(),
                        currentOver = match.teams.firstOrNull { it.isBatting }?.overs,
                        bowlerName = (ballByBall?.bowler ?: match.currentBowler)?.name,
                        modifier = Modifier.fillMaxSize()
                    )
                    DetailTab.INFO -> InfoTab(
                        match = match,
                        modifier = Modifier.fillMaxSize()
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    icon: ImageVector? = null,
    isGold: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier
            .width(if (isGold) 190.dp else 160.dp)
            .height(72.dp)
            .background(
                if (isGold) colors.goldPrimary.copy(alpha = 0.08f)
                else colors.glassSurface
            ),
        shape = RoundedCornerShape(12.dp),
        borderOpacity = if (isGold) 0.4f else 0.15f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacing.sm),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Title + Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = colors.goldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = title.uppercase(),
                    style = typography.labelSmall,
                    color = if (isGold) colors.goldPrimary else colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 9.sp
                )
            }

            // Body: Value + Subtitle/Progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = value,
                        style = if (isGold) typography.bodyMedium else typography.bodyLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (progress == null) {
                        Text(
                            text = subtitle,
                            style = typography.labelSmall,
                            color = colors.textTertiary,
                            fontSize = 9.sp
                        )
                    }
                }

                if (progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(colors.goldPrimary)
                                .shadow(4.dp, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchScoreHero(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)

    // Team colors for split-flag background
    val team1Color = remember(team1?.acronym) {
        TeamPrimaryColors.getTeamColor(team1?.acronym ?: "")
    }
    val team2Color = remember(team2?.acronym) {
        TeamPrimaryColors.getTeamColor(team2?.acronym ?: "")
    }

    // Breathing animation for live matches
    val pulseAlpha = if (match.isLive) {
        val infiniteTransition = rememberInfiniteTransition(label = "heroBreathing")
        infiniteTransition.animateFloat(
            initialValue = 0.06f,
            targetValue = 0.16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        ).value
    } else {
        0.10f
    }

    // Extract target from status (e.g. "need 156 runs")
    val targetRuns = remember(match.currentStatus) {
        val regex = Regex("""need\s+(\d+)\s+run""", RegexOption.IGNORE_CASE)
        regex.find(match.currentStatus)?.groupValues?.getOrNull(1)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenHorizontal),
        contentPadding = PaddingValues(0.dp),
        borderOpacity = 0.20f
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val halfWidth = size.width / 2f
                    // Left half: team1 color gradient
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                team1Color.copy(alpha = pulseAlpha),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = halfWidth
                        ),
                        topLeft = Offset.Zero,
                        size = Size(halfWidth, size.height)
                    )
                    // Right half: team2 color gradient
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                team2Color.copy(alpha = pulseAlpha)
                            ),
                            startX = halfWidth,
                            endX = size.width
                        ),
                        topLeft = Offset(halfWidth, 0f),
                        size = Size(halfWidth, size.height)
                    )
                }
                .padding(dimens.spacing.md)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (team1 != null) {
                        TeamScoreColumn(
                            team = team1,
                            modifier = Modifier.weight(1f),
                            isLive = match.isLive
                        )
                    }

                    // Gold accent divider + live indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
                        modifier = Modifier.padding(horizontal = dimens.spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(52.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            colors.goldPrimary.copy(alpha = 0.5f),
                                            colors.goldPrimary.copy(alpha = 0.8f),
                                            colors.goldPrimary.copy(alpha = 0.5f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        if (match.isLive) {
                            LiveIndicator()
                        }
                    }

                    if (team2 != null) {
                        TeamScoreColumn(
                            team = team2,
                            modifier = Modifier.weight(1f),
                            isLive = match.isLive
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacing.sm))

                // Target / RRR pill badge
                if (targetRuns != null || match.score?.requiredRunRate != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.goldPrimary.copy(alpha = 0.12f))
                            .border(
                                width = 1.dp,
                                color = colors.goldPrimary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        val pillText = when {
                            targetRuns != null -> "Target: $targetRuns"
                            match.score?.requiredRunRate != null -> "RRR: ${match.score.requiredRunRate}"
                            else -> ""
                        }
                        Text(
                            text = pillText,
                            style = typography.matchStatus,
                            color = colors.goldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(dimens.spacing.xs))
                }

                // Match status
                Text(
                    text = match.currentStatus,
                    style = typography.labelSmall,
                    color = colors.goldPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TeamScoreColumn(
    team: CricketMatch.Team,
    modifier: Modifier = Modifier,
    isLive: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    // Subtle breathing for the batting team's score when live
    val scoreAlpha = if (isLive && team.isBatting) {
        val infiniteTransition = rememberInfiniteTransition(label = "scoreBreathing")
        infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scoreAlpha"
        ).value
    } else {
        1f
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.xs)
    ) {
        TeamLogoCircle(
            teamCode = team.acronym,
            logoUrl = team.logoUrl,
            size = 52.dp
        )
        Text(
            text = team.acronym.ifBlank { team.name.take(3).uppercase() },
            style = typography.labelLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        if (team.score.isNotBlank()) {
            Text(
                text = team.score,
                style = typography.scoreDisplay,
                color = if (team.isBatting) colors.textPrimary else colors.textSecondary,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .graphicsLayer(alpha = scoreAlpha)
                    .etchedText()
            )
            Text(
                text = "(${team.overs} ov)",
                style = typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 0: Live
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveTab(
    match: CricketMatch,
    ballByBall: CricBuzzScraper.MatchDetails?,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val score = match.score
    val batsmen = ballByBall?.batsmen ?: match.scrapedBatsmen ?: score?.batsmen?.map { b ->
        ScrapedBatsman(b.name, b.runs.toString(), b.balls.toString(),
            b.fours.toString(), b.sixes.toString(),
            String.format("%.1f", b.strikeRate), false)
    } ?: emptyList()
    val bowler = ballByBall?.bowler ?: match.currentBowler
    val recentBalls = ballByBall?.recentBalls ?: match.recentBalls
        ?: score?.recentBalls?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        ?: emptyList()

    // Derive dynamic insight data from match state
    val approachingInfo = remember(batsmen) {
        batsmen
            .mapNotNull { batter ->
                val runs = batter.runs.toIntOrNull() ?: return@mapNotNull null
                if (runs <= 0) return@mapNotNull null
                val nextMilestone = ((runs / 50) + 1) * 50
                val remaining = nextMilestone - runs
                val progress = runs.toFloat() / nextMilestone
                val shortName = batter.name.split(" ").lastOrNull() ?: batter.name
                Triple("$shortName $nextMilestone", "$remaining to go", progress)
            }
            .maxByOrNull { it.third } // closest to milestone (highest progress)
    }

    val partnershipRuns = remember(batsmen) { batsmen.sumOf { it.runs.toIntOrNull() ?: 0 } }
    val partnershipBalls = remember(batsmen) { batsmen.sumOf { it.balls.toIntOrNull() ?: 0 } }

    val lastWicketScoreRegex = remember { Regex("""(\d+)\s*\((\d+)\)""") }
    val lastWicketValue = remember(score?.lastWicket) {
        score?.lastWicket?.let { lw ->
            val m = lastWicketScoreRegex.find(lw)
            if (m != null) lw.substring(0, m.range.first).trim().take(15)
            else lw.take(15)
        } ?: "—"
    }
    val lastWicketSubtitle = remember(score?.lastWicket) {
        score?.lastWicket?.let { lastWicketScoreRegex.find(it)?.value } ?: ""
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = dimens.screenHorizontal,
            vertical = dimens.md
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        // Horizontal Stats Row as per _19
        item(key = "horizontal_stats") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = dimens.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)
            ) {
                // Approaching Card
                InsightStatCard(
                    title = "APPROACHING",
                    value = approachingInfo?.first ?: "—",
                    subtitle = approachingInfo?.second ?: "No data",
                    progress = approachingInfo?.third ?: 0f,
                    icon = Icons.Outlined.LiveTv,
                    isGold = approachingInfo != null
                )

                // Partnership Card
                InsightStatCard(
                    title = "PARTNERSHIP",
                    value = if (batsmen.isNotEmpty()) "$partnershipRuns" else "N/A",
                    subtitle = if (batsmen.isNotEmpty()) "($partnershipBalls balls)" else "",
                    isGold = false
                )

                // Last Wicket Card
                InsightStatCard(
                    title = "LAST WKT",
                    value = lastWicketValue,
                    subtitle = lastWicketSubtitle,
                    isGold = false
                )
            }
        }

        // Loading shimmer placeholders when data hasn't arrived yet
        val hasNoLiveData = batsmen.isEmpty() && bowler == null && recentBalls.isEmpty()
        if (hasNoLiveData && isLoading) {
            item(key = "shimmer_recent") {
                LiveShimmerCard(height = 64.dp, label = "RECENT")
            }
            item(key = "shimmer_batting") {
                LiveShimmerCard(height = 88.dp, label = "BATTING")
            }
            item(key = "shimmer_batter") {
                LiveShimmerBatterCard()
            }
            item(key = "shimmer_bowling") {
                LiveShimmerCard(height = 88.dp, label = "BOWLING")
            }
        } else if (hasNoLiveData && !isLoading) {
            item(key = "empty_live") {
                EmptyStateView(
                    icon = Icons.Outlined.SportsCricket,
                    title = "No live data",
                    message = "Ball-by-ball data is not available for this match yet.",
                    modifier = Modifier.padding(vertical = dimens.xxl)
                )
            }
        }

        // Recent balls
        if (recentBalls.isNotEmpty()) {
            item(key = "recent_balls") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "RECENT",
                            style = typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(dimens.sm))
                        BallsRow(
                            events = recentBalls.map { parseBallEvent(it) },
                            maxBalls = recentBalls.size.coerceAtMost(12)
                        )
                    }
                }
            }
        }

        // Run rate info
        if (score?.currentRunRate != null || score?.requiredRunRate != null) {
            item(key = "run_rate") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.sm)
                ) {
                    score.currentRunRate?.let { crr ->
                        StatPill(
                            label = "CRR",
                            value = crr,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    score.requiredRunRate?.let { rrr ->
                        StatPill(
                            label = "RRR",
                            value = rrr,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Batsmen
        if (batsmen.isNotEmpty()) {
            item(key = "batsmen_header") {
                Text(
                    text = "BATTING",
                    style = typography.labelSmall,
                    color = colors.goldPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            val striker = batsmen.firstOrNull { it.isStriker } ?: batsmen.firstOrNull()
            if (striker != null) {
                item(key = "featured_batter") {
                    FeaturedBatterCard(batter = striker)
                }
            }
            val others = batsmen.filter { it != striker }
            items(others, key = { "bat_${it.name}" }) { batter ->
                NonStrikerCard(batter = batter)
            }
        }

        // Bowler
        if (bowler != null) {
            item(key = "bowler_header") {
                Text(
                    text = "BOWLING",
                    style = typography.labelSmall,
                    color = colors.goldPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = dimens.sm)
                )
            }
            item(key = "bowler") {
                GlassBowlerCard(bowler = bowler)
            }
        }

        // Last wicket
        if (score?.lastWicket != null && score.lastWicket.isNotBlank()) {
            item(key = "last_wicket") {
                GlassCardSmall(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "LAST WICKET",
                            style = typography.labelSmall,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(dimens.xs))
                        Text(
                            text = score.lastWicket,
                            style = typography.bodyMedium,
                            color = colors.statusLive
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(dimens.xxl)) }
    }
}

@Composable
private fun FeaturedBatterCard(
    batter: ScrapedBatsman,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = Color(0xFF800000),
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height)
                )
            },
        shape = RoundedCornerShape(24.dp),
        borderOpacity = 0.2f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left part: Photo and Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Photo with STR badge
                Box(
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF800000).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = batter.name.take(1).uppercase(),
                            style = typography.headingSmall,
                            color = BouncerDesignSystem.colors.textSecondary
                        )
                    }
                    if (batter.isStriker) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF800000))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "STR",
                                style = typography.labelSmall.copy(fontSize = 8.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Name and SR
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = batter.name,
                            style = typography.headingSmall.copy(fontSize = 18.sp),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Top scorer",
                            tint = colors.goldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "SR ${batter.strikeRate}",
                        style = typography.labelSmall,
                        color = colors.textTertiary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Right part: Large score
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = batter.runs,
                    style = typography.headingLarge.copy(fontSize = 40.sp),
                    color = colors.goldPrimary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.etchedText()
                )
                Text(
                    text = "(${batter.balls})",
                    style = typography.bodySmall,
                    color = colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = typography.statValue,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = typography.labelSmall,
            color = colors.textMuted
        )
    }
}

@Composable
private fun NonStrikerCard(
    batter: ScrapedBatsman,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCardSmall(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = batter.name,
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (batter.howOut != null && batter.howOut.isNotBlank()) {
                    Text(
                        text = batter.howOut,
                        style = typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
            Text(
                text = "${batter.runs} (${batter.balls})",
                style = typography.statValue,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GlassBowlerCard(
    bowler: ScrapedBowler,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = bowler.name,
                style = typography.headingSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(dimens.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn("Overs", bowler.overs)
                StatColumn("Maidens", bowler.maidens)
                StatColumn("Runs", bowler.runs)
                StatColumn("Wkts", bowler.wickets)
                StatColumn("Econ", bowler.economy)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Live Tab Shimmer Placeholders
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveShimmerCard(
    height: Dp,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val shimmer = bouncerShimmerBrush()

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = label,
                style = typography.labelSmall,
                color = colors.textMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height - 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmer)
            )
        }
    }
}

@Composable
private fun LiveShimmerBatterCard(modifier: Modifier = Modifier) {
    val shimmer = bouncerShimmerBrush()

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        borderOpacity = 0.2f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(shimmer)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmer)
                    )
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmer)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1: Scorecard Wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScorecardTabWrapper(
    scorecard: Scorecard?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    ScorecardTab(
        scorecard = scorecard,
        isLoading = isLoading,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 2: Lineups (Glass-converted Squads)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LineupsTab(
    match: CricketMatch,
    squads: Pair<List<SquadScraper.Player>, List<SquadScraper.Player>>?,
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = BouncerDesignSystem.dimens

    when {
        isLoading -> {
            LineupsLoadingState(modifier = modifier)
        }
        hasError || squads == null -> {
            EmptyStateView(
                icon = Icons.Outlined.Groups,
                title = "Squads unavailable",
                message = "Could not load squad information for this match.",
                actionLabel = if (hasError) "Retry" else null,
                onAction = if (hasError) onRetry else null,
                modifier = modifier.padding(dimens.xxl)
            )
        }
        else -> {
            val (team1, team2) = squads
            val teamNames = match.teams.map { it.name }
            val teamAcronyms = match.teams.map { it.acronym }

            LineupsContent(
                team1Name = teamNames.getOrNull(0) ?: "Team 1",
                team1Code = teamAcronyms.getOrNull(0) ?: "",
                team1Players = team1,
                team2Name = teamNames.getOrNull(1) ?: "Team 2",
                team2Code = teamAcronyms.getOrNull(1) ?: "",
                team2Players = team2,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun LineupsLoadingState(modifier: Modifier = Modifier) {
    val dimens = BouncerDesignSystem.dimens

    Column(
        modifier = modifier.padding(dimens.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(dimens.md)
    ) {
        Spacer(modifier = Modifier.height(dimens.lg))
        repeat(8) {
            GlassCardSmall(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) { }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 3: Overs (Placeholder)
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 4: Info
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoTab(
    match: CricketMatch,
    modifier: Modifier = Modifier
) {
    val dimens = BouncerDesignSystem.dimens

    if (match.info.isBlank() && match.venue.isBlank()) {
        EmptyStateView(
            icon = Icons.Outlined.Info,
            title = "Match Info",
            message = "Match information is not available yet.",
            modifier = modifier.padding(dimens.xxl)
        )
    } else {
        MatchInfoContent(
            match = match,
            modifier = modifier
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared stat cells (internal for ScorecardTab)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun StatHeaderCell(label: String, modifier: Modifier = Modifier) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Text(
        text = label,
        style = typography.labelSmall,
        color = colors.textMuted,
        textAlign = TextAlign.End,
        modifier = modifier
    )
}

@Composable
internal fun StatValueCell(
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Text(
        text = value,
        style = typography.statValue,
        color = if (highlight) colors.goldPrimary else colors.textPrimary,
        fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
        textAlign = TextAlign.End,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ball Event Parser
// ─────────────────────────────────────────────────────────────────────────────

private fun parseBallEvent(ball: String): BallEvent {
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

// ─────────────────────────────────────────────────────────────────────────────
//  Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun buildShareText(match: CricketMatch): String {
    val sb = StringBuilder()
    sb.appendLine("\uD83C\uDFCF ${match.matchTitle}")
    match.teams.forEach { team ->
        if (team.score.isNotBlank()) {
            sb.appendLine("${team.name}: ${team.score}")
        }
    }
    if (match.currentStatus.isNotBlank()) {
        sb.appendLine(match.currentStatus)
    }
    sb.appendLine()
    sb.appendLine("Shared from Bouncer \uD83C\uDFCF")
    return sb.toString()
}

private fun NotificationFrequency.toUpdateFrequency(): com.tanush.bouncer.liveactivity.UpdateFrequency = when (this) {
    NotificationFrequency.EVERY_RUN -> com.tanush.bouncer.liveactivity.UpdateFrequency.EVERY_RUN
    NotificationFrequency.EVERY_OVER -> com.tanush.bouncer.liveactivity.UpdateFrequency.EVERY_OVER
    NotificationFrequency.EVERY_WICKET -> com.tanush.bouncer.liveactivity.UpdateFrequency.EVERY_WICKET
}
