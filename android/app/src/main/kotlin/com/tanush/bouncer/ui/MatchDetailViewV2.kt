package com.tanush.bouncer.ui

import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import com.tanush.bouncer.data.history.ArchivedInfo
import com.tanush.bouncer.data.history.PastFeedSource
import com.tanush.bouncer.data.history.isCricsheetOnly
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.MatchFollowSheet
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.StaleDataBanner
import com.tanush.bouncer.ui.components.v2.ReminderBottomSheet
import com.tanush.bouncer.ui.components.premium.ScorecardTab
import com.tanush.bouncer.ui.screens.matchdetail.v2.CommentaryTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.HighlightsTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.InfoTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.GraphsTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.LiveTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchPhase
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTabContent
import com.tanush.bouncer.ui.screens.matchdetail.v2.SquadsTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.SummaryTabV2
import com.tanush.bouncer.ui.screens.matchdetail.v2.hasGraphs
import com.tanush.bouncer.ui.screens.matchdetail.v2.matchPhaseOf
import com.tanush.bouncer.ui.screens.matchdetail.v2.matchTabsFor
import com.tanush.bouncer.ui.screens.matchdetail.v2.pageFor
import com.tanush.bouncer.ui.screens.matchdetail.v2.rememberHeaderCollapse
import com.tanush.bouncer.ui.screens.matchdetail.v2.rememberOverviewDerivedData
import com.tanush.bouncer.ui.screens.matchdetail.v2.titleRes
import com.tanush.bouncer.viewmodels.mergedProbabilityHistory
import com.tanush.bouncer.viewmodels.probabilityHistoryFrom
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import com.tanush.bouncer.ui.screens.matchdetail.v2.buildOverStrip
import com.tanush.bouncer.viewmodels.HighlightsViewModel
import com.tanush.bouncer.viewmodels.MatchDetailViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.bouncerScreenBackground
import com.tanush.bouncer.ui.components.BatsmanMilestoneCard
import com.tanush.bouncer.ui.components.ShareCardData
import com.tanush.bouncer.ui.components.ShareCardGenerator
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.screens.matchdetail.v2.meaningfulRequiredRunRate

/**
 * The match screen: the fixed score header, then the tabs [matchTabsFor] offers for the match's
 * state — Info first for a fixture, Live while it is on, Summary once it is over.
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
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }
    val haptics = rememberHapticFeedback()
    val vm: MatchDetailViewModel = viewModel(
        key = MatchDetailViewModel.storeKey(match.id),
        factory = MatchDetailViewModel.factory(match),
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val streamingCommentary by vm.streamingCommentary.collectAsStateWithLifecycle()
    val commentaryEventPages by vm.commentaryEvents.pages.collectAsStateWithLifecycle()

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
        match.withArchivedInfo(uiState.pastInfo).copy(
            currentStatus = liveStatus,
            venue = match.venue.ifBlank { uiState.liveVenue.orEmpty() },
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

    // The whole match's series where the server recorded one, not only this screen's time open.
    val probabilityHistory = remember(uiState.scorecard, uiState.probabilityHistory, match.teams) {
        mergedProbabilityHistory(probabilityHistoryFrom(uiState.scorecard, match.teams), uiState.probabilityHistory)
    }
    // Live and Summary narrate the lines; the per-ball event verdict is the commentary tab's
    // filter input, so it is not threaded through them.
    val commentaryText = remember(uiState.commentary) { uiState.commentary.map { it.text } }
    val phase = matchPhaseOf(liveMatch)
    // Derived once for the three tabs that read it: Live, Summary and Graphs. This recomposes on
    // every ball of a live match, and every list transform in it is remembered against its inputs.
    val derived = rememberOverviewDerivedData(
        match = liveMatch,
        scorecard = uiState.scorecard,
        matchFlow = uiState.matchFlow,
        isMatchActive = phase == MatchPhase.IN_PLAY,
        probabilityHistory = probabilityHistory,
        commentary = commentaryText,
    )
    val tabs = matchTabsFor(
        phase,
        MatchTabContent(
            scorecard = uiState.scorecardLoading || uiState.scorecard?.innings?.isNotEmpty() == true,
            commentary = uiState.commentaryLoading || uiState.commentary.isNotEmpty(),
            graphs = hasGraphs(liveMatch, uiState.scorecard, derived, probabilityHistory),
            squads = uiState.squads != null,
            highlights = highlights.isNotEmpty(),
        ),
    )
    // The selection is a tab, and the pager is rebuilt on it whenever the set changes (Graphs
    // arriving with the first plotted over, a match starting or ending under the reader): an index
    // carried across would land on whichever tab now sits there. See pageFor.
    var selectedTab by rememberSaveable { mutableStateOf<MatchTab?>(null) }
    val pagerState = key(tabs) { rememberPagerState(initialPage = pageFor(tabs, selectedTab)) { tabs.size } }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> tabs.getOrNull(page)?.let { selectedTab = it } }
    }
    val collapse = rememberHeaderCollapse()
    // A new tab starts with the hero showing: its own list is at the top.
    LaunchedEffect(pagerState.currentPage) { collapse.expand() }
    val openCommentary: () -> Unit = {
        val page = tabs.indexOf(MatchTab.COMMENTARY)
        if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
    }
    val openInfo: () -> Unit = {
        val page = tabs.indexOf(MatchTab.INFO)
        if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
    }

    val battingTeam = liveMatch.teams.firstOrNull { it.isBatting }
    // The selected tab takes the batting side's colour when the registry knows it; otherwise the
    // gold every other selected tab in the app uses, not the neutral an unknown side resolves to.
    val battingInk = if (TeamColorRegistry.isKnown(battingTeam?.name)) rememberTeamInk(battingTeam?.name) else colors.primaryText
    val tabAccentColor by animateColorAsState(
        targetValue = battingInk,
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
                            if (hapticEnabled) haptics.click()
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
                                        team1FullName = t1?.name,
                                        team2FullName = t2?.name,
                                    )
                                    val bitmap = ShareCardGenerator.generateShareBitmap(
                                        data,
                                        typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_bold),
                                    )
                                    ShareCardGenerator.shareBitmap(context, bitmap)
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
                            Icon(Icons.Default.Share, stringResource(R.string.common_share), tint = colors.onSurface)
                        }
                    }
                    // A Cricsheet-only match is long over and has no id any alert could name.
                    if (!match.isCricsheetOnly) {
                        IconButton(onClick = {
                            if (hapticEnabled) haptics.click()
                            showReminderSheet = true
                        }) {
                            Icon(
                                Icons.Default.Alarm,
                                stringResource(R.string.common_set_reminder),
                                tint = colors.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            if (hapticEnabled) haptics.click()
                            showNotifPrefsSheet = true
                        }) {
                            Icon(
                                imageVector = if (isFollowing) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = stringResource(
                                    if (isFollowing) R.string.match_stop_live_updates else R.string.match_follow_live_updates
                                ),
                                tint = if (isFollowing) colors.primaryText else colors.onSurface,
                            )
                        }
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
            Column(modifier = Modifier.fillMaxSize().nestedScroll(collapse.connection)) {
            // The score header is a sibling of the pager, so its strip is on screen for every tab.
            // The commentary tab does not repeat it: the over card at the head of the feed already
            // states the same over, with the same chips.
            val overStrip = remember(uiState.commentary, uiState.recentBalls, uiState.recentBallsOver) {
                buildOverStrip(uiState.commentary, uiState.recentBalls, uiState.recentBallsOver)
            }

            MatchDetailHeader(
                match = liveMatch,
                situation = uiState.situation,
                lastUpdatedMs = uiState.lastUpdatedMs,
                recentBalls = overStrip?.balls.orEmpty(),
                collapse = collapse,
            )

            // Live-data failure: static match info below still renders; this offers the retry.
            if (uiState.loadError != null) {
                StaleDataBanner(
                    // Unknown claims nothing: "offline" is only said when a throwable proved it.
                    kind = uiState.loadErrorKind.takeIf { it != FailureKind.Unknown } ?: FailureKind.Server,
                    savedAtMs = null,
                    onRetry = vm::retryInitialLoad,
                )
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
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = stringResource(tab.titleRes(uiState.squads?.hasPlayingXi == true)),
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
                when (tabs.getOrNull(page) ?: return@HorizontalPager) {
                    MatchTab.LIVE -> LiveTabV2(
                        match = liveMatch,
                        batsmen = uiState.batsmen,
                        bowler = uiState.bowler,
                        scorecard = uiState.scorecard,
                        matchFlow = uiState.matchFlow,
                        derived = derived,
                        streamingCommentary = streamingCommentary,
                        onOpenCommentary = openCommentary,
                    )
                    MatchTab.SUMMARY -> SummaryTabV2(
                        match = liveMatch,
                        scorecard = uiState.scorecard,
                        hasCommentary = MatchTab.COMMENTARY in tabs,
                        onOpenCommentary = openCommentary,
                    )
                    MatchTab.SCORECARD -> ScorecardTab(
                        scorecard = uiState.scorecard,
                        isLoading = uiState.scorecardLoading,
                        // The screen's phase, not the list's status: after the toss the status line
                        // reads "India opt to bowl" for the whole first innings.
                        isLive = phase == MatchPhase.IN_PLAY,
                        squads = uiState.squads,
                    )
                    MatchTab.COMMENTARY -> CommentaryTabV2(
                        commentary = uiState.commentary,
                        matchStatus = liveMatch.currentStatus,
                        isLoading = uiState.commentaryLoading,
                        failure = uiState.commentaryErrorKind.takeIf { uiState.commentaryError != null },
                        onRetry = vm::retryInitialLoad,
                        onOpenInfo = openInfo.takeIf { MatchTab.INFO in tabs },
                        hasOlder = uiState.hasOlderCommentary,
                        isLoadingOlder = uiState.olderCommentaryLoading,
                        onLoadOlder = vm::loadOlderCommentary,
                        // Cricsheet's feed is the whole match, already loaded; the table holds no
                        // events for it, so the chips filter what is here instead of asking.
                        eventPages = if (uiState.pastFeedSource != null) emptyMap() else commentaryEventPages,
                        onOpenEvents = vm.commentaryEvents::open.takeIf { uiState.pastFeedSource == null },
                        onLoadOlderEvents = vm.commentaryEvents::loadOlder.takeIf { uiState.pastFeedSource == null },
                        sourceCaption = when (uiState.pastFeedSource) {
                            PastFeedSource.CRICSHEET -> stringResource(R.string.commentary_source_cricsheet)
                            PastFeedSource.ARCHIVE_DELIVERIES -> archiveDeliveriesCaption(uiState.pastFeedPartial)
                            else -> null
                        },
                    )
                    MatchTab.GRAPHS -> GraphsTabV2(
                        match = liveMatch,
                        scorecard = uiState.scorecard,
                        derived = derived,
                        probabilityHistory = probabilityHistory,
                        requiredRunRate = meaningfulRequiredRunRate(uiState.liveRequiredRunRate),
                    )
                    MatchTab.SQUADS -> SquadsTabV2(
                        match = liveMatch,
                        squads = uiState.squads,
                        impactSubs = uiState.matchFlow?.impactSubs.orEmpty(),
                        teamNews = uiState.preMatchData?.teamNews,
                    )
                    // The live entry, not the one this screen opened with: Info's Test day and toss
                    // rows read the status line.
                    MatchTab.INFO -> InfoTabV2(
                        match = liveMatch,
                        preMatchData = uiState.preMatchData,
                        headToHead = uiState.headToHead,
                        archivedInfo = uiState.pastInfo,
                    )
                    MatchTab.HIGHLIGHTS -> HighlightsTabV2(highlights = highlights)
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

/** The caption under a backfilled match's ball-by-ball lines, with a note for an innings the source only partly has. */
@Composable
private fun archiveDeliveriesCaption(partial: List<Int>): String {
    val base = stringResource(R.string.commentary_source_archive_deliveries)
    if (partial.isEmpty()) return base
    return base + " " + stringResource(R.string.commentary_source_partial, partial.joinToString(", "))
}

/** The match with the toss and umpires a backfilled card knows, where the list entry had none. */
private fun CricketMatch.withArchivedInfo(info: ArchivedInfo?): CricketMatch {
    if (info == null) return this
    return copy(
        tossWinner = tossWinner ?: info.tossWinner,
        tossDecision = tossDecision ?: info.tossDecision.takeIf { info.tossWinner != null },
        umpire1 = umpire1 ?: info.umpires.getOrNull(0),
        umpire2 = umpire2 ?: info.umpires.getOrNull(1),
    )
}
