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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.ui.components.v2.MatchNotificationPrefsSheet
import com.tanush.bouncer.ui.components.v2.MatchStatusType
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.ReminderBottomSheet
import com.tanush.bouncer.ui.components.v2.StatusBadgeV2
import com.tanush.bouncer.ui.components.v2.determineMatchStatus
import com.tanush.bouncer.ui.screens.matchdetail.v2.CommentaryTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.InfoTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.OverviewTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.ScorecardTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTimelineTab
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.ShareMatchCardHelper
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

    val tabTitles = listOf("Overview", "Scorecard", "Commentary", "Highlights", "Info")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    // Live data state
    var batsmen by remember { mutableStateOf<List<ScrapedBatsman>>(emptyList()) }
    var bowler by remember { mutableStateOf<ScrapedBowler?>(null) }
    var recentBalls by remember { mutableStateOf<List<String>>(emptyList()) }
    var scorecard by remember { mutableStateOf<Scorecard?>(null) }
    var commentary by remember { mutableStateOf<List<String>>(emptyList()) }
    var commentaryLoading by remember { mutableStateOf(true) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showNotifPrefsSheet by remember { mutableStateOf(false) }

    var liveTeam1Score by remember { mutableStateOf(match.teams.getOrNull(0)?.score ?: "") }
    var liveTeam2Score by remember { mutableStateOf(match.teams.getOrNull(1)?.score ?: "") }
    var liveTeam1Overs by remember { mutableStateOf(match.teams.getOrNull(0)?.overs ?: "") }
    var liveTeam2Overs by remember { mutableStateOf(match.teams.getOrNull(1)?.overs ?: "") }
    var liveCurrentRunRate by remember { mutableStateOf(match.score?.currentRunRate) }
    var liveRequiredRunRate by remember { mutableStateOf(match.score?.requiredRunRate) }

    val scraper = remember { CricBuzzScraper() }
    val commentaryManager = remember { LiveCommentaryManager() }

    // Achievement state
    val context = LocalContext.current
    var unlockedAchievements by remember { mutableStateOf(AchievementKind.loadUnlocked(context)) }

    fun checkAndUnlock(kind: AchievementKind) {
        if (!unlockedAchievements.contains(kind)) {
            AchievementKind.unlock(context, kind)
            unlockedAchievements = AchievementKind.loadUnlocked(context)
        }
    }

    // Polling loop
    LaunchedEffect(match.id) {
        // One-time initial seed: fetch all currently visible commentary before the polling loop starts
        try {
            val url = "https://www.cricbuzz.com/live-cricket-scores/${match.id}"
            val allLines = scraper.fetchLiveCommentary(url)
            if (allLines.isNotEmpty()) {
                commentaryManager.seedHistory(allLines)
                commentary = commentaryManager.getSeededHistory()
            }
        } catch (_: Exception) {
        } finally {
            commentaryLoading = false
        }

        while (true) {
            try {
                val details = scraper.fetchBallByBallAndBatsmen(match.id)
                batsmen = details.batsmen
                bowler = details.bowler
                recentBalls = details.recentBalls

                details.team1Score?.let { liveTeam1Score = it }
                details.team2Score?.let { liveTeam2Score = it }
                details.team1Overs?.let { liveTeam1Overs = it }
                details.team2Overs?.let { liveTeam2Overs = it }
                details.currentRunRate?.let { liveCurrentRunRate = it }
                details.requiredRunRate?.let { liveRequiredRunRate = it }

                scorecard = scraper.fetchScorecard(match.id)

                val keyNames = batsmen.map { it.name }.toMutableList()
                bowler?.let { keyNames.add(it.name) }
                commentary = commentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/${match.id}",
                    keyNames = keyNames,
                )

                // Lightweight achievement checks
                for (b in batsmen) {
                    val runs = b.runs.toIntOrNull() ?: 0
                    if (runs >= 50) checkAndUnlock(AchievementKind.RUNS_50)
                    if (runs >= 100) checkAndUnlock(AchievementKind.RUNS_100)
                }
                bowler?.let { b ->
                    val w = b.wickets.toIntOrNull() ?: 0
                    if (w >= 5) checkAndUnlock(AchievementKind.WICKETS_5)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "match_detail_v2.refresh"
                    message = "Live data refresh failed"
                    level = SentryLevel.WARNING
                    setData("matchId", match.id)
                })
                Sentry.captureException(e)
            }
            delay(8000)
        }
    }

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val score1 = liveTeam1Score.ifBlank { team1?.score ?: "" }
    val score2 = liveTeam2Score.ifBlank { team2?.score ?: "" }
    val overs1 = liveTeam1Overs.ifBlank { team1?.overs ?: "" }
    val overs2 = liveTeam2Overs.ifBlank { team2?.overs ?: "" }

    // Derive a match copy with live team scores so Overview tab (win probability, isUpcoming, etc.)
    // always reflects the most recent data rather than the initial cached snapshot.
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
                    IconButton(onClick = {
                        ShareMatchCardHelper.shareMatchAsImage(
                            context = context,
                            team1 = team1?.name ?: "",
                            team2 = team2?.name ?: "",
                            score1 = score1,
                            score2 = score2,
                            overs1 = overs1,
                            overs2 = overs2,
                            status = match.status,
                            matchTitle = match.matchTitle,
                        )
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = colors.onSurface)
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
                currentRunRate = liveCurrentRunRate,
                requiredRunRate = liveRequiredRunRate,
                recentBalls = recentBalls,
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
                            color = colors.primary,
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
                                color = if (pagerState.currentPage == index) colors.primary else colors.textSecondary,
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
                when (page) {
                    0 -> OverviewTabV2(
                        match = liveMatch,
                        batsmen = batsmen,
                        bowler = bowler,
                        recentBalls = recentBalls,
                        commentary = commentary,
                        currentRunRate = liveCurrentRunRate,
                        requiredRunRate = liveRequiredRunRate,
                        scorecard = scorecard,
                    )
                    1 -> ScorecardTabV2(scorecard = scorecard)
                    2 -> CommentaryTabV2(
                        commentary = commentary,
                        recentBalls = recentBalls,
                        matchStatus = match.currentStatus,
                        isLoading = commentaryLoading,
                    )
                    3 -> MatchTimelineTab(commentary = commentary)
                    4 -> InfoTabV2(match = match)
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

    val statusType = determineMatchStatus(match)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
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
        )
        Spacer(Modifier.height(spacing.sm))

        // Team 2
        TeamScoreRow(
            teamName = team2?.acronym ?: team2?.name ?: "Team 2",
            logoUrl = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl),
            score = score2,
            overs = overs2,
            isBatting = team2?.isBatting == true,
        )
        Spacer(Modifier.height(spacing.sm))

        // Status line
        if (match.currentStatus.isNotBlank()) {
            Text(
                text = match.currentStatus,
                style = typography.bodySmall,
                color = if (match.isLive) colors.primary else colors.textSecondary,
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
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

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
            color = colors.onSurface,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (score.isNotBlank()) {
            Text(
                text = score,
                style = typography.scoreMedium,
                color = colors.onSurface,
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
