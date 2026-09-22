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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.tanush.bouncer.ui.navigation.LocalOpenSeries
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.CloudOff
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatus
import com.tanush.bouncer.ui.components.v2.AnimatedScoreText
import com.tanush.bouncer.ui.components.v2.MatchFollowSheet
import com.tanush.bouncer.ui.components.v2.MatchStatusType
import com.tanush.bouncer.ui.components.v2.RecentBallsStripV2
import com.tanush.bouncer.ui.components.v2.ReminderBottomSheet
import com.tanush.bouncer.ui.components.v2.StatusBadgeV2
import com.tanush.bouncer.ui.components.v2.determineMatchStatus
import com.tanush.bouncer.ui.components.premium.PremiumAiStatisticsTab
import com.tanush.bouncer.ui.components.premium.ScorecardTab
import com.tanush.bouncer.ui.screens.matchdetail.v2.CommentaryTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.HighlightsTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.InfoTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.OverviewTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.buildOverStrip
import com.tanush.bouncer.viewmodels.HighlightsViewModel
import com.tanush.bouncer.viewmodels.MatchDetailViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.rememberTeamColors
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.CricketEventColors
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.delay
import com.tanush.bouncer.ui.theme.v2.bouncerScreenBackground
import com.tanush.bouncer.ui.components.BatsmanMilestoneCard
import com.tanush.bouncer.ui.components.ShareCardData
import com.tanush.bouncer.ui.components.ShareCardGenerator
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.meaningfulRequiredRunRate

private const val EVENT_FLASH_MILLIS = 1500L

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
        key = MatchDetailViewModel.storeKey(match.id),
        factory = MatchDetailViewModel.factory(context, match),
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val streamingCommentary by vm.streamingCommentary.collectAsStateWithLifecycle()

    // The ViewModel is keyed on the match id, so it keeps the entry it was constructed with while
    // this parameter is recomputed from the matches list on every poll. Handing the newer entry
    // over is what lets a match the server is not writing follow the list instead of freezing on
    // its open-time score — see MatchDetailViewModel.onMatchUpdated.
    LaunchedEffect(match) { vm.onMatchUpdated(match) }

    val highlightsViewModel: HighlightsViewModel = viewModel(
        key = HighlightsViewModel.storeKey(match.id),
        factory = HighlightsViewModel.factory(context, match.id),
    )
    val highlights by highlightsViewModel.highlights.collectAsStateWithLifecycle()

    val tabTitles = listOf(
        R.string.tab_overview,
        R.string.tab_scorecard,
        R.string.tab_commentary,
        R.string.tab_highlights,
        R.string.tab_info,
        R.string.tab_ai,
    )
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    var showReminderSheet by remember { mutableStateOf(false) }
    var showNotifPrefsSheet by remember { mutableStateOf(false) }
    val followedCards by MatchSubscriptionManager.subscriptions.collectAsStateWithLifecycle()
    val followedAlerts by MatchSubscriptionManager.alertPrefs.collectAsStateWithLifecycle()
    val isFollowing = match.id in followedCards || followedAlerts[match.id]?.anyOn == true
    var isSharingCard by remember { mutableStateOf(false) }

    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val score1 = uiState.liveTeam1Score.ifBlank { team1?.score ?: "" }
    val score2 = uiState.liveTeam2Score.ifBlank { team2?.score ?: "" }
    val overs1 = uiState.liveTeam1Overs.ifBlank { team1?.overs ?: "" }
    val overs2 = uiState.liveTeam2Overs.ifBlank { team2?.overs ?: "" }

    // Live status from the server payload supersedes the static status captured at nav time,
    // so the header/badge/result banner update when a match progresses or finishes while open.
    // matchStatus is derived (by lazy) from currentStatus, so overriding it re-derives for free.
    val liveStatus = uiState.liveStatus?.takeIf { it.isNotBlank() } ?: match.currentStatus
    // The stored probability is whatever the match list had; the detail payload is fresher, so it
    // wins when present. A completed match resolves its own from the result string.
    val liveWinProbability = uiState.liveWinProbability ?: match.winProbability
    val liveDrawProbability = uiState.liveDrawProbability ?: match.winProbabilityDraw
    val prematch = uiState.liveWinProbabilityIsPrematch || match.winProbabilityIsPrematch
    val liveMatch = if (match.teams.size >= 2) {
        match.copy(
            currentStatus = liveStatus,
            winProbability = liveWinProbability,
            winProbabilityDraw = liveDrawProbability,
            winProbabilityIsPrematch = prematch,
            teams = match.teams.mapIndexed { i, team ->
                when (i) {
                    0 -> team.copy(score = score1.ifBlank { team.score }, overs = overs1.ifBlank { team.overs })
                    1 -> team.copy(score = score2.ifBlank { team.score }, overs = overs2.ifBlank { team.overs })
                    else -> team
                }
            },
        ).let {
            // A finished match has a result, not a forecast: the draw segment goes with the
            // prediction it belonged to, or a completed Test would keep showing a chance of a draw
            // that has already not happened.
            if (it.isCompleted) {
                it.copy(
                    winProbability = it.resolvedWinProbability(),
                    winProbabilityDraw = null,
                    winProbabilityIsPrematch = false,
                )
            } else {
                it
            }
        }
    } else {
        match.copy(
            currentStatus = liveStatus,
            winProbability = liveWinProbability,
            winProbabilityDraw = liveDrawProbability,
            winProbabilityIsPrematch = prematch,
        )
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
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
                                        winProbability = liveMatch.winProbabilityFraction,
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
                        Icon(
                            imageVector = if (isFollowing) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = stringResource(
                                if (isFollowing) R.string.match_stop_live_updates else R.string.match_follow_live_updates
                            ),
                            tint = if (isFollowing) colors.primaryText else colors.onSurface,
                        )
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
            // The score header is a sibling of the pager, so its strip is on screen for every tab.
            // The commentary tab does not repeat it: the over card at the head of the feed already
            // states the same over, with the same chips.
            val overStrip = remember(uiState.commentary, uiState.recentBalls, uiState.recentBallsOver) {
                buildOverStrip(uiState.commentary, uiState.recentBalls, uiState.recentBallsOver)
            }

            // ── Score Header ──
            ScoreHeaderV2(
                match = liveMatch,
                team1 = team1,
                team2 = team2,
                score1 = score1,
                score2 = score2,
                overs1 = overs1,
                overs2 = overs2,
                currentRunRate = uiState.liveCurrentRunRate,
                requiredRunRate = meaningfulRequiredRunRate(uiState.liveRequiredRunRate),
                recentBalls = overStrip?.balls.orEmpty(),
            )

            // Live-data failure: static match info below still renders; this offers the retry.
            uiState.loadError?.let { message ->
                LiveDataErrorBanner(message = message, onRetry = vm::retryInitialLoad)
            }

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
                                text = stringResource(title),
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
                modifier = Modifier
                    .weight(1f)
                    .bouncerScreenBackground(),
            ) { page ->
                // Dispatching on the resource id rather than on the rendered title: the title is
                // translated now, so matching its text would send every tab to the else branch in
                // any locale but English.
                val tabTitle = tabTitles.getOrNull(page) ?: return@HorizontalPager
                // Overview only narrates the lines; the per-ball event verdict is the commentary
                // tab's filter input, so it is not threaded through the whole overview tree.
                val commentaryText = remember(uiState.commentary) { uiState.commentary.map { it.text } }
                when (tabTitle) {
                    R.string.tab_overview -> OverviewTabV2(
                        match = liveMatch,
                        batsmen = uiState.batsmen,
                        bowler = uiState.bowler,
                        recentBalls = uiState.recentBalls,
                        commentary = commentaryText,
                        currentRunRate = uiState.liveCurrentRunRate,
                        requiredRunRate = meaningfulRequiredRunRate(uiState.liveRequiredRunRate),
                        scorecard = uiState.scorecard,
                        streamingCommentary = streamingCommentary,
                        matchFlow = uiState.matchFlow,
                        probabilityHistory = uiState.probabilityHistory,
                        headToHead = uiState.headToHead,
                    )
                    R.string.tab_scorecard -> ScorecardTab(
                        scorecard = uiState.scorecard,
                        isLoading = uiState.scorecardLoading,
                    )
                    R.string.tab_commentary -> CommentaryTabV2(
                        commentary = uiState.commentary,
                        matchStatus = liveMatch.currentStatus,
                        isLoading = uiState.commentaryLoading,
                        error = uiState.commentaryError,
                        onRetry = vm::retryInitialLoad,
                        hasOlder = uiState.hasOlderCommentary,
                        isLoadingOlder = uiState.olderCommentaryLoading,
                        onLoadOlder = vm::loadOlderCommentary,
                    )
                    R.string.tab_highlights -> HighlightsTabV2(highlights = highlights)
                    R.string.tab_info -> InfoTabV2(match = match, preMatchData = uiState.preMatchData)
                    R.string.tab_ai -> PremiumAiStatisticsTab(match = liveMatch)
                    // Resource ids are non-final under nonTransitiveRClass, so this `when` is a
                    // chain of runtime comparisons, not a verified-exhaustive switch: a tab added
                    // to tabTitles without a branch here rendered a silently blank page.
                    else -> EmptyStateV2(
                        title = stringResource(R.string.tab_unavailable_title),
                        subtitle = stringResource(R.string.tab_unavailable_subtitle),
                    )
                }
            }
            }

            uiState.batsmanMilestone?.let { milestone ->
                BatsmanMilestoneCard(
                    milestoneInfo = milestone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.lg)
                        .align(Alignment.BottomCenter),
                )
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
            MatchFollowSheet(
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

    // Flat. The header used to be a three-stop horizontal gradient tinted with both teams'
    // brand colours at 13% alpha — a wash faint enough to read as nothing in particular and
    // loud enough to make the scores sit on an uneven ground. The scores are the header.

    val statusType = determineMatchStatus(match)

    val flashOverlay = rememberEventFlashOverlay(recentBalls)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .background(flashOverlay)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        // Context line. The teams are already the top bar's title, so this names what the top
        // bar cannot: the series (a link to it, when the match knows its series id) and which match
        // of it this is.
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchContextLine(match = match, onSeriesClick = LocalOpenSeries.current, modifier = Modifier.weight(1f))
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

        // Status line — a decided match gets a designed end moment (Peak-End:
        // the last thing seen of a match is its result, not a stale score).
        MatchStatusLine(match = match)

        // Run rates
        if (currentRunRate != null || requiredRunRate != null) {
            Spacer(Modifier.height(spacing.xs))
            Row {
                currentRunRate?.let {
                    Text(
                        stringResource(R.string.stat_crr_value, it),
                        style = typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.width(spacing.lg))
                }
                requiredRunRate?.let {
                    Text(
                        stringResource(R.string.stat_rrr_value, it),
                        style = typography.labelSmall,
                        color = colors.textSecondary,
                    )
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

/**
 * Peak moment: a wicket / four / six briefly tints the score header in that
 * event's semantic color and fires one haptic — the update should be felt,
 * not just read. Flash is skipped entirely under reduced motion; haptics
 * follow the user's existing haptic setting.
 */
@Composable
private fun rememberEventFlashOverlay(recentBalls: List<String>): Color {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }
    val cricket = BouncerThemeV2.colors.cricket
    var flashTint by remember { mutableStateOf(Color.Unspecified) }
    var prevBalls by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(recentBalls) {
        val prev = prevBalls
        prevBalls = recentBalls
        if (!isNewBall(prev, recentBalls)) return@LaunchedEffect
        val ball = recentBalls.last().trim().uppercase()
        val eventColor = eventFlashColor(ball, cricket) ?: return@LaunchedEffect
        if (hapticEnabled) {
            haptic.performHapticFeedback(
                if (ball.contains("W")) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
        if (!BouncerMotion.reducedMotionEnabled) {
            flashTint = eventColor
            delay(EVENT_FLASH_MILLIS)
            flashTint = Color.Unspecified
        }
    }
    val flashOverlay by animateColorAsState(
        targetValue = if (flashTint == Color.Unspecified) Color.Transparent else flashTint.copy(alpha = 0.15f),
        animationSpec = tween(300),
        label = "eventFlash",
    )
    return flashOverlay
}

/** prev == null is the first composition — never flash on screen open. */
private fun isNewBall(prev: List<String>?, current: List<String>): Boolean =
    prev != null && current.isNotEmpty() && current != prev

private fun eventFlashColor(ball: String, cricket: CricketEventColors): Color? = when {
    ball.contains("W") && !ball.startsWith("WD") -> cricket.wicket
    ball == "6" -> cricket.six
    ball == "4" -> cricket.four
    else -> null
}

@Composable
private fun MatchContextLine(
    match: CricketMatch,
    onSeriesClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val seriesLink = onSeriesClick?.takeIf { match.seriesId.isNotBlank() && match.seriesName.isNotBlank() }
    if (seriesLink == null) {
        Text(
            text = "${match.matchTitle} • ${match.matchType}",
            style = typography.bodySmall,
            color = colors.textSecondary,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { seriesLink(match.seriesId) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = match.seriesName,
                style = typography.bodySmall,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = listOf(match.matchDesc, match.matchType).filter { it.isNotBlank() }.joinToString(" • "),
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun MatchStatusLine(match: CricketMatch) {
    if (match.currentStatus.isBlank()) return
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    if (match.matchStatus == MatchStatus.Completed) {
        MatchResultBanner(text = match.currentStatus)
    } else {
        Text(
            text = match.currentStatus,
            style = typography.bodySmall,
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MatchResultBanner(text: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.win.copy(alpha = 0.12f))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = colors.win,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = text,
            style = typography.bodySmall,
            color = colors.win,
            fontWeight = FontWeight.Bold,
        )
    }
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
    // Neutral for both sides. Batting is carried by weight, not hue — see TeamRowV2.
    val activeColor = colors.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            CoilImage(
                model = logoUrl,
                // Decorative: the team name Text sits directly beside this logo,
                // so a description here would double-announce in TalkBack.
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (score.isNotBlank()) {
            AnimatedScoreText(
                text = score,
                style = typography.scoreMedium,
                color = activeColor,
                fontWeight = FontWeight.Bold,
            )
            if (overs.isNotBlank()) {
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = "($overs)",
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Slim inline banner for live-data failure: static match info below stays usable,
 * this row owns the retry affordance.
 */
@Composable
private fun LiveDataErrorBanner(message: String, onRetry: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = message,
            style = typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry), color = colors.primaryText)
        }
    }
}
