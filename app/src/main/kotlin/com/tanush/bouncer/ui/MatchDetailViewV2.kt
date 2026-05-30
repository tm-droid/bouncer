package com.tanush.bouncer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.MatchNotificationPrefsSheet
import com.tanush.bouncer.ui.components.v2.MatchStatusType
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.ReminderBottomSheet
import com.tanush.bouncer.ui.components.v2.StatusBadgeV2
import com.tanush.bouncer.ui.components.v2.determineMatchStatus
import com.tanush.bouncer.ui.components.premium.PremiumAiStatisticsTab
import com.tanush.bouncer.ui.components.premium.PremiumScorecardTab
import com.tanush.bouncer.ui.screens.matchdetail.v2.CommentaryTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.HighlightsTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.InfoTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.OverviewTabV2
import com.tanush.bouncer.viewmodels.HighlightsViewModel
import com.tanush.bouncer.viewmodels.MatchDetailViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.ui.theme.rememberMatchTeamPalette
import com.tanush.bouncer.ui.theme.rememberTeamColors
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.components.ShareCardData
import com.tanush.bouncer.ui.components.ShareCardGenerator
import kotlinx.coroutines.launch

/**
 * MatchDetailViewV2 — Clean match detail screen with score header and tabbed content.
 * Tabs: Overview, Scorecard, Commentary, Info
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailViewV2(
    match: CricketMatch,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val context = LocalContext.current
    val vm: MatchDetailViewModel = viewModel(
        key = match.id,
        factory = MatchDetailViewModel.factory(context, match),
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val streamingCommentary by vm.streamingCommentary.collectAsStateWithLifecycle()

    val highlightsViewModel: HighlightsViewModel = viewModel(
        key = match.id,
        factory = HighlightsViewModel.factory(context, match.id),
    )
    val highlights by highlightsViewModel.highlights.collectAsStateWithLifecycle()

    val tabTitles = listOf("Overview", "Scorecard", "Commentary", "Highlights", "Info", "AI")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    var showReminderSheet by remember { mutableStateOf(false) }
    var showNotifPrefsSheet by remember { mutableStateOf(false) }
    var isSharingCard by remember { mutableStateOf(false) }

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val score1 = uiState.liveTeam1Score.ifBlank { team1?.score ?: "" }
    val score2 = uiState.liveTeam2Score.ifBlank { team2?.score ?: "" }
    val overs1 = uiState.liveTeam1Overs.ifBlank { team1?.overs ?: "" }
    val overs2 = uiState.liveTeam2Overs.ifBlank { team2?.overs ?: "" }

    val liveMatch = if (match.teams.size >= 2) {
        match.copy(
            teams = match.teams.mapIndexed { i, team ->
                when (i) {
                    0 -> team.copy(score = score1.ifBlank { team.score }, overs = overs1.ifBlank { team.overs })
                    1 -> team.copy(score = score2.ifBlank { team.score }, overs = overs2.ifBlank { team.overs })
                    else -> team
                }
            },
        )
    } else {
        match
    }

    val battingTeam = liveMatch.teams.firstOrNull { it.isBatting }
    val battingTeamColors = rememberTeamColors(battingTeam?.name)
    val tabAccentColor by animateColorAsState(
        targetValue = battingTeamColors.primary,
        animationSpec = tween(800),
        label = "tabAccent",
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "${team1?.acronym ?: team1?.name ?: ""} vs ${team2?.acronym ?: team2?.name ?: ""}",
                        style = typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isSharingCard) {
                                isSharingCard = true
                                scope.launch {
                                    val t1 = liveMatch.teams.getOrNull(0)
                                    val t2 = liveMatch.teams.getOrNull(1)
                                    val data = ShareCardData(
                                        team1Name = t1?.acronym?.ifEmpty { t1.name.take(3) } ?: "T1",
                                        team2Name = t2?.acronym?.ifEmpty { t2.name.take(3) } ?: "T2",
                                        team1Score = t1?.score ?: "-",
                                        team2Score = t2?.score,
                                        matchStatus = liveMatch.status,
                                        matchType = liveMatch.matchType,
                                        venue = liveMatch.venue,
                                        winProbability = (liveMatch.winProbability / 100.0).toFloat().coerceIn(0f, 1f),
                                        isLive = liveMatch.isLive,
                                    )
                                    val bitmap = ShareCardGenerator.generateShareBitmap(data)
                                    val uri = ShareCardGenerator.saveBitmapForSharing(context, bitmap)
                                    uri?.let { ShareCardGenerator.shareImage(context, it) }
                                    isSharingCard = false
                                }
                            }
                        },
                        enabled = !isSharingCard,
                    ) {
                        if (isSharingCard) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colors.onSurface,
                            )
                        } else {
                            Icon(Icons.Default.Share, "Share", tint = colors.onSurface)
                        }
                    }
                    IconButton(onClick = { showReminderSheet = true }) {
                        Icon(Icons.Default.Alarm, "Reminder", tint = colors.onSurface)
                    }
                    IconButton(onClick = { showNotifPrefsSheet = true }) {
                        Icon(Icons.Default.Notifications, "Notification Preferences", tint = colors.onSurface)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // ── Score Header ──
            ScoreHeaderV2(
                match = match,
                team1 = team1,
                team2 = team2,
                score1 = score1,
                score2 = score2,
                overs1 = overs1,
                overs2 = overs2,
                currentRunRate = uiState.liveCurrentRunRate,
                requiredRunRate = uiState.liveRequiredRunRate,
                recentBalls = uiState.recentBalls,
            )

            // ── Tab Row ──
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                edgePadding = spacing.lg,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = tabAccentColor,
                        )
                    }
                },
                divider = { HorizontalDivider(color = colors.divider) },
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = title,
                                style = typography.labelLarge,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (pagerState.currentPage == index) tabAccentColor else colors.textSecondary,
                            )
                        },
                    )
                }
            }

            // ── Tab Content ──
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val tabTitle = tabTitles.getOrNull(page) ?: return@HorizontalPager
                when (tabTitle) {
                    "Overview" -> OverviewTabV2(
                        match = liveMatch,
                        batsmen = uiState.batsmen,
                        bowler = uiState.bowler,
                        recentBalls = uiState.recentBalls,
                        commentary = uiState.commentary,
                        currentRunRate = uiState.liveCurrentRunRate,
                        requiredRunRate = uiState.liveRequiredRunRate,
                        scorecard = uiState.scorecard,
                        streamingCommentary = streamingCommentary,
                        matchFlow = uiState.matchFlow,
                    )
                    "Scorecard" -> PremiumScorecardTab(scorecard = uiState.scorecard)
                    "Commentary" -> CommentaryTabV2(
                        commentary = uiState.commentary,
                        recentBalls = uiState.recentBalls,
                        matchStatus = match.currentStatus,
                        isLoading = uiState.commentaryLoading,
                    )
                    "Highlights" -> HighlightsTabV2(highlights = highlights)
                    "Info" -> InfoTabV2(match = match, preMatchData = uiState.preMatchData)
                    "AI" -> PremiumAiStatisticsTab(match = liveMatch)
                }
            }
        }

        if (showReminderSheet) {
            ReminderBottomSheet(
                matchId = match.id,
                matchTitle = match.matchTitle,
                matchStartTime = 0L, // Will be populated when available
                onDismiss = { showReminderSheet = false },
            )
        }

        if (showNotifPrefsSheet) {
            MatchNotificationPrefsSheet(
                matchId = match.id,
                matchTitle = match.matchTitle,
                onDismiss = { showNotifPrefsSheet = false },
            )
        }
    }
}

// ── Score Header ──

@Composable
private fun ScoreHeaderV2(
    match: CricketMatch,
    team1: CricketMatch.Team?,
    team2: CricketMatch.Team?,
    score1: String,
    score2: String,
    overs1: String,
    overs2: String,
    currentRunRate: String?,
    requiredRunRate: String?,
    recentBalls: List<String>,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val palette = rememberMatchTeamPalette(team1?.name, team2?.name)
    val headerBrush = Brush.horizontalGradient(
        0f to palette.home.primary.copy(alpha = 0.13f),
        0.5f to colors.surface,
        1f to palette.away.primary.copy(alpha = 0.13f),
    )
    val livePrimaryColor = if (team1?.isBatting == true) palette.home.primary else palette.away.primary

    val statusType = determineMatchStatus(match)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = headerBrush)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        // Context line
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${match.matchTitle} • ${match.matchType}",
                style = typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            StatusBadgeV2(statusType)
        }
        Spacer(Modifier.height(spacing.md))

        // Team 1
        TeamScoreRow(
            teamName = team1?.acronym ?: team1?.name ?: "Team 1",
            logoUrl = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl),
            score = score1,
            overs = overs1,
            isBatting = team1?.isBatting == true,
            teamColor = palette.home.primary,
        )
        Spacer(Modifier.height(spacing.sm))

        // Team 2
        TeamScoreRow(
            teamName = team2?.acronym ?: team2?.name ?: "Team 2",
            logoUrl = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl),
            score = score2,
            overs = overs2,
            isBatting = team2?.isBatting == true,
            teamColor = palette.away.primary,
        )
        Spacer(Modifier.height(spacing.sm))

        // Status line
        if (match.currentStatus.isNotBlank()) {
            Text(
                text = match.currentStatus,
                style = typography.bodySmall,
                color = if (match.isLive) livePrimaryColor else colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
        }

        // Run rates
        if (currentRunRate != null || requiredRunRate != null) {
            Spacer(Modifier.height(spacing.xs))
            Row {
                currentRunRate?.let {
                    Text("CRR: $it", style = typography.labelSmall, color = colors.textSecondary)
                    Spacer(Modifier.width(spacing.lg))
                }
                requiredRunRate?.let {
                    Text("RRR: $it", style = typography.labelSmall, color = colors.textSecondary)
                }
            }
        }

        // Recent balls
        if (recentBalls.isNotEmpty()) {
            Spacer(Modifier.height(spacing.sm))
            RecentBallsStripV2(balls = recentBalls)
        }
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun TeamScoreRow(
    teamName: String,
    logoUrl: String?,
    score: String,
    overs: String,
    isBatting: Boolean,
    teamColor: Color = Color.Unspecified,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val activeColor = if (isBatting && teamColor != Color.Unspecified) teamColor else colors.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(spacing.sm))
        }
        Text(
            text = teamName,
            style = typography.titleMedium,
            color = activeColor,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (score.isNotBlank()) {
            Text(
                text = score,
                style = typography.scoreMedium,
                color = activeColor,
                fontWeight = FontWeight.Bold,
            )
            if (overs.isNotBlank()) {
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = "($overs)",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
