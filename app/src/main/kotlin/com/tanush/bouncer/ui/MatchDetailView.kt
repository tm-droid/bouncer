package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.*
import com.tanush.bouncer.models.FallOfWicket as ModelFallOfWicket
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.premium.*
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.ui.components.celebrations.*
import com.tanush.bouncer.ui.components.ToastType
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import io.sentry.Sentry
import io.sentry.Breadcrumb
import io.sentry.SentryLevel
import com.tanush.bouncer.utils.parseRuns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailView(
    match: CricketMatch,
    onBackClick: () -> Unit,
    onForecastClick: (String) -> Unit = {},
    onArClick: () -> Unit = {},
    onOpenCommentary: () -> Unit = {},
    onOpenScorecard: () -> Unit = {},
    onOpenAnalysis: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var batsmen by remember { mutableStateOf<List<ScrapedBatsman>>(emptyList()) }
    var bowler by remember { mutableStateOf<ScrapedBowler?>(null) }
    var recentBalls by remember { mutableStateOf<List<String>>(emptyList()) }
    var scorecard by remember { mutableStateOf<Scorecard?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var commentary by remember { mutableStateOf(listOf("Synchronizing Elite Data...")) }

    var liveTeam1Score by remember { mutableStateOf(match.teams.getOrNull(0)?.score ?: "") }
    var liveTeam2Score by remember { mutableStateOf(match.teams.getOrNull(1)?.score ?: "") }
    var liveTeam1Overs by remember { mutableStateOf(match.teams.getOrNull(0)?.overs ?: "") }
    var liveTeam2Overs by remember { mutableStateOf(match.teams.getOrNull(1)?.overs ?: "") }
    var liveCurrentRunRate by remember { mutableStateOf(match.score?.currentRunRate) }
    var liveRequiredRunRate by remember { mutableStateOf(match.score?.requiredRunRate) }

    val scraper = remember { CricBuzzScraper() }
    val commentaryManager = remember { LiveCommentaryManager() }
    val pagerState = rememberPagerState(pageCount = { 5 })

    val context = LocalContext.current
    var unlockedAchievements by remember { mutableStateOf(AchievementKind.loadUnlocked(context)) }
    var showAchievementToast by remember { mutableStateOf(false) }
    var latestAchievementTitle by remember { mutableStateOf("") }
    var latestAchievementDesc by remember { mutableStateOf("") }
    var showCenturyCelebration by remember { mutableStateOf(false) }
    var centuryCelebrationPlayer by remember { mutableStateOf("") }
    var centuryCelebrationRuns by remember { mutableIntStateOf(100) }
    var centuryCelebrationBalls by remember { mutableStateOf<Int?>(null) }
    var showFiveWicketCelebration by remember { mutableStateOf(false) }
    var fiveWicketBowlerName by remember { mutableStateOf("") }
    var fiveWicketCount by remember { mutableIntStateOf(5) }
    var fiveWicketRuns by remember { mutableIntStateOf(0) }
    var fiveWicketOvers by remember { mutableStateOf("") }
    var showConfettiRain by remember { mutableStateOf(false) }
    val hapticPlayer = rememberHapticPlayer()

    fun unlockAchievement(kind: AchievementKind, title: String, desc: String) {
        try {
            if (!unlockedAchievements.contains(kind)) {
                val wasNew = AchievementKind.unlock(context, kind)
                if (wasNew) {
                    unlockedAchievements = AchievementKind.loadUnlocked(context)
                    latestAchievementTitle = title
                    latestAchievementDesc = desc
                    showAchievementToast = true
                    showConfettiRain = true
                    hapticPlayer.play(CricketHapticEvent.MILESTONE)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun checkForAchievements() {
        try {
            for (batsman in batsmen) {
                val runs = batsman.runs.toIntOrNull() ?: 0
                val balls = batsman.balls.toIntOrNull()
                if (runs >= 30) unlockAchievement(AchievementKind.RUNS_30, "30 Runs!", "${batsman.name} scored 30 runs")
                if (runs >= 50) unlockAchievement(AchievementKind.RUNS_50, "Fifty!", "${batsman.name} scored a Fifty")
                if (runs >= 75) unlockAchievement(AchievementKind.RUNS_75, "75 Runs!", "${batsman.name} scored 75 runs")
                if (runs >= 100) {
                    val wasAlreadyUnlocked = unlockedAchievements.contains(AchievementKind.RUNS_100)
                    unlockAchievement(AchievementKind.RUNS_100, "Century!", "${batsman.name} scored a Century")
                    if (!wasAlreadyUnlocked) {
                        showCenturyCelebration = true
                        centuryCelebrationPlayer = batsman.name
                        centuryCelebrationRuns = runs
                        centuryCelebrationBalls = balls
                    }
                }
                if (runs >= 150) unlockAchievement(AchievementKind.RUNS_150, "150 Runs!", "${batsman.name} scored 150 runs")
                if (runs >= 200) unlockAchievement(AchievementKind.RUNS_200, "Double Century!", "${batsman.name} scored a Double Century")
                if (runs >= 250) unlockAchievement(AchievementKind.RUNS_250, "250 Runs!", "${batsman.name} scored 250 runs")
                if (runs >= 300) unlockAchievement(AchievementKind.RUNS_300, "Triple Century!", "${batsman.name} scored a Triple Century")
            }
            bowler?.let { b ->
                val wickets = b.wickets.toIntOrNull() ?: 0
                if (wickets >= 3) unlockAchievement(AchievementKind.WICKETS_3, "Three-for!", "${b.name} took 3 wickets")
                if (wickets >= 4) unlockAchievement(AchievementKind.WICKETS_4, "Four-for!", "${b.name} took 4 wickets")
                if (wickets >= 5) {
                    val wasAlreadyUnlocked = unlockedAchievements.contains(AchievementKind.WICKETS_5)
                    unlockAchievement(AchievementKind.WICKETS_5, "Five-for!", "${b.name} took 5 wickets")
                    if (!wasAlreadyUnlocked) {
                        showFiveWicketCelebration = true
                        fiveWicketBowlerName = b.name
                        fiveWicketCount = wickets
                        fiveWicketRuns = b.runs.toIntOrNull() ?: 0
                        fiveWicketOvers = b.overs
                    }
                }
                if (wickets >= 10) unlockAchievement(AchievementKind.WICKETS_10, "10 Wickets!", "${b.name} took 10 wickets")
            }
            for (team in match.teams) {
                if (parseRuns(team.score) >= 200) unlockAchievement(AchievementKind.TEAM_200, "200+ Total!", "${team.name} scored 200+ runs")
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    LaunchedEffect(match.id) {
        while (true) {
            try {
                isRefreshing = true
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

                checkForAchievements()

                val keyNames = batsmen.map { it.name }.toMutableList()
                bowler?.let { keyNames.add(it.name) }

                commentary = commentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/${match.id}",
                    keyNames = keyNames
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "match_detail_view.refresh"
                    message = "Live data refresh failed"
                    level = SentryLevel.WARNING
                    setData("matchId", match.id)
                    setData("error", e.message ?: "Unknown")
                })
                Sentry.captureException(e)
            } finally {
                isRefreshing = false
            }
            delay(8000)
        }
    }

    Scaffold(
        containerColor = BgBase,
        topBar = { MatchDetailHeader_Extracted(
            match = match,
            onBackClick = onBackClick,
            liveTeam1Score = liveTeam1Score,
            liveTeam2Score = liveTeam2Score,
            liveTeam1Overs = liveTeam1Overs,
            liveTeam2Overs = liveTeam2Overs,
            liveCurrentRunRate = liveCurrentRunRate,
            liveRequiredRunRate = liveRequiredRunRate
        ) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                EliteTabRow(selectedTab, { selectedTab = it })

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = true,
                    contentPadding = PaddingValues(top = Spacing.sm)
                ) { page ->
                    when (page) {
                        0 -> OverviewTab_Extracted(match, batsmen, bowler, recentBalls, commentary, liveCurrentRunRate, liveRequiredRunRate)
                        1 -> ScorecardTab_Extracted(scorecard)
                        2 -> CommentaryTab_Extracted(
                            commentary = commentary,
                            recentBalls = recentBalls,
                            onOpenCommentary = onOpenCommentary,
                            onOpenScorecard = onOpenScorecard,
                            onOpenAnalysis = onOpenAnalysis
                        )
                        3 -> StatisticsTab_Extracted(match, scorecard)
                        4 -> VenueTab_Extracted(match)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                AchievementToast(
                    title = latestAchievementTitle,
                    description = latestAchievementDesc,
                    isVisible = showAchievementToast,
                    type = ToastType.ACHIEVEMENT,
                    onDismiss = { showAchievementToast = false }
                )
            }

            if (showConfettiRain) {
                ConfettiRain(
                    modifier = Modifier.fillMaxSize(),
                    particleCount = 100,
                    duration = 3000L,
                    isActive = true,
                    onComplete = { showConfettiRain = false }
                )
            }

            if (showCenturyCelebration) {
                CenturyExplosion(
                    playerName = centuryCelebrationPlayer,
                    runs = centuryCelebrationRuns,
                    balls = centuryCelebrationBalls,
                    isVisible = true,
                    onDismiss = { showCenturyCelebration = false }
                )
            }

            if (showFiveWicketCelebration) {
                FiveWicketHaulCelebration(
                    bowlerName = fiveWicketBowlerName,
                    wickets = fiveWicketCount,
                    runs = fiveWicketRuns,
                    overs = fiveWicketOvers,
                    isVisible = true,
                    onDismiss = { showFiveWicketCelebration = false }
                )
            }
        }
    }
}

@Composable
private fun EliteTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Summary", "Scorecard", "Commentary", "Stats", "Info")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs)
            .clip(RoundedCornerShape(CornerRadius.pill))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                BorderWidth.hairline,
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(CornerRadius.pill)
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedTab == index
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Gold400.copy(alpha = 0.22f) else Color.Transparent,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "tabBackground"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(CornerRadius.pill))
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab,
                    color = if (isSelected) Gold400 else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TechnicalQuickAction(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(GlassSurface)
            .border(
                BorderWidth.hairline,
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentColor.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(CornerRadius.md)
            )
            .clickable { }
            .padding(Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(IconSize.xs))
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = LetterSpacing.relaxed)
        }
    }
}

@Composable
fun MicroStatCard(
    modifier: Modifier = Modifier,
    label: String,
    name: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.lg))
            .background(GlassSurface)
            .border(
                BorderWidth.hairline,
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.20f),
                        accentColor.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(CornerRadius.lg)
            )
            .padding(Spacing.md)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Icon(icon, null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(value, color = accentColor, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                Text(subValue, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
fun EliteImpactHeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderWidth.hairline, Gold400.copy(alpha = 0.15f), RoundedCornerShape(CornerRadius.md)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QueryStats, null, tint = Gold400, modifier = Modifier.size(14.dp))
                Text("Match Momentum", color = Gold400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("94.2", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = StatusSuccess.copy(alpha = 0.1f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                    Text("+15.4 vs session avg", color = StatusSuccess, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EliteAiInsightsTab(match: CricketMatch) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            PremiumWinProbabilityHero(
                team1Name = match.teams.getOrNull(0)?.acronym ?: "T1",
                team1Probability = (match.calculateHeuristicProbability() / 100.0).toFloat().coerceIn(0.05f, 0.95f)
            )
        }

        item {
            val bowler = match.currentBowler
            val bowlerDesc = if (bowler != null) {
                "${bowler.name} has an economy of ${bowler.economy}. Current bowling figures: ${bowler.wickets}/${bowler.runs}."
            } else {
                "Bowling analysis will update when live ball-by-ball data is available."
            }
            AiAnalysisCard(
                title = "Bowling Threat Analysis",
                description = bowlerDesc,
                highlightText = if (bowler != null) "Economy: ${bowler.economy}" else "Awaiting data",
                type = if ((bowler?.economy?.toDoubleOrNull() ?: 0.0) < 7.0) AnalysisType.WARNING else AnalysisType.NEUTRAL
            )
        }

        item {
            val crr = match.score?.currentRunRate?.toDoubleOrNull()
            val rrr = match.score?.requiredRunRate?.toDoubleOrNull()
            val momentumDesc = if (crr != null && rrr != null) {
                val diff = crr - rrr
                if (diff > 0) "Batting team is ahead of the required rate by ${String.format("%.1f", diff)} runs per over."
                else "Batting team needs to accelerate. Currently ${String.format("%.1f", -diff)} runs per over behind required rate."
            } else {
                "Run rate comparison will update when chase begins."
            }
            AiAnalysisCard(
                title = "Momentum Analysis",
                description = momentumDesc,
                highlightText = if (crr != null) "CRR: ${String.format("%.1f", crr)}" else "Awaiting data",
                type = if (crr != null && rrr != null && crr >= rrr) AnalysisType.POSITIVE else AnalysisType.NEUTRAL
            )
        }

        item {
            val striker = match.scrapedBatsmen?.find { it.isStriker }
            val batsmanDesc = if (striker != null) {
                "${striker.name} is on ${striker.runs} off ${striker.balls} balls (SR: ${striker.strikeRate})."
            } else {
                "Batsman analysis will update when live data is available."
            }
            AiAnalysisCard(
                title = "Batsman Performance",
                description = batsmanDesc,
                highlightText = if (striker != null) "SR: ${striker.strikeRate}" else "Awaiting data",
                type = if ((striker?.strikeRate?.toDoubleOrNull() ?: 0.0) > 130.0) AnalysisType.POSITIVE else AnalysisType.NEUTRAL
            )
        }

        item {
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            if (team1 != null && team2 != null) {
                val prob = (match.calculateHeuristicProbability() / 100.0).toFloat()
                val crr = match.score?.currentRunRate?.toFloatOrNull() ?: 6.0f
                val rrr = match.score?.requiredRunRate?.toFloatOrNull() ?: 6.0f
                val crrTotal = crr + rrr
                val crrPct = if (crrTotal > 0f) (crr / crrTotal * 100f).coerceIn(10f, 90f) else 50f
                TacticalMatrixCard(
                    team1Name = team1.acronym,
                    team2Name = team2.acronym,
                    team1Color = ChartBlue,
                    team2Color = StatusSuccess,
                    metrics = listOf(
                        TacticalMetric("Current Run Rate", crrPct, 100f - crrPct),
                        TacticalMetric("Win Probability", prob * 100f, (1f - prob) * 100f),
                        TacticalMetric("Batting Momentum", prob * 100f, (1f - prob) * 100f),
                        TacticalMetric("Pressure Index", (1f - prob) * 100f, prob * 100f)
                    )
                )
            }
        }

        item {
            val overs = match.teams.firstOrNull { it.isBatting }?.overs ?: ""
            val oversNum = overs.split(".").firstOrNull()?.toIntOrNull() ?: 0
            val phase = when {
                oversNum < 6 -> "Powerplay"
                oversNum < 15 -> "Middle Overs"
                else -> "Death Overs"
            }
            val crr = match.score?.currentRunRate ?: "--"
            val striker = match.scrapedBatsmen?.find { it.isStriker }
            val keyHighlightText = if (striker != null) {
                "${striker.name} on ${striker.runs} off ${striker.balls} balls (SR: ${striker.strikeRate}) in the $phase phase."
            } else {
                "Match is in the $phase phase. Current run rate: $crr."
            }
            MatchInsightsCard(
                matchTitle = match.matchTitle,
                currentPhase = phase,
                insights = listOf(
                    MatchInsightDetail(
                        "Run Rate",
                        crr,
                        if ((match.score?.currentRunRate?.toFloatOrNull() ?: 0f) > 8f) "+High" else "Steady",
                        InsightType.BATTING,
                        if ((match.score?.currentRunRate?.toFloatOrNull() ?: 0f) > (match.score?.requiredRunRate?.toFloatOrNull() ?: 0f)) InsightTrend.POSITIVE else InsightTrend.NEGATIVE
                    ),
                    MatchInsightDetail(
                        "Bowling Economy",
                        match.currentBowler?.economy?.toString() ?: "--",
                        if ((match.currentBowler?.economy?.toDoubleOrNull() ?: 0.0) < 7.0) "Tight" else "Expensive",
                        InsightType.BOWLING,
                        if ((match.currentBowler?.economy?.toDoubleOrNull() ?: 0.0) < 7.0) InsightTrend.POSITIVE else InsightTrend.NEGATIVE
                    )
                ),
                keyHighlight = keyHighlightText
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun EliteBattingTable(inning: Innings) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderWidth.hairline, Gold400.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
    ) {
        Text(
            "BATTING",
            color = Gold400,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("BATTER", modifier = Modifier.weight(3f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("R", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("B", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("SR", modifier = Modifier.weight(1.2f), color = Gold400, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
        }

        inning.batsmen.forEachIndexed { index, batsman ->
            BattingRowItem(batsman = batsman, rowIndex = index)
        }
    }
}

@Composable
fun EliteBowlingTable(inning: Innings) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderWidth.hairline, ChartBlue.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
    ) {
        Text(
            "BOWLING",
            color = ChartBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("BOWLER", modifier = Modifier.weight(3f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("O", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("R", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("W", modifier = Modifier.weight(1f), color = ChartBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
        }

        inning.bowlers.forEachIndexed { index, bowler ->
            BowlingRowItem(bowler = bowler, rowIndex = index)
        }
    }
}

@Composable
fun BowlingRowItem(bowler: BowlerStats, rowIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (rowIndex % 2 == 0) Color.White.copy(alpha = 0.01f) else Color.Transparent)
            .drawBehind {
                val strokeWidth = 0.5.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(color = Color.White.copy(alpha = 0.04f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidth)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(bowler.name, modifier = Modifier.weight(3f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(bowler.overs, modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text(bowler.runs, modifier = Modifier.weight(1f), color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(bowler.wickets, modifier = Modifier.weight(1f), color = Gold400, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EliteFowTable(fows: List<ModelFallOfWicket>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "FALL OF WICKETS",
            color = WicketRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fows.forEach { fow ->
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(BorderWidth.hairline, WicketRed.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${fow.number}-${fow.score}", color = WicketRed, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(fow.batsman, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("(${fow.overs} ov)", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BattingRowItem(batsman: BatsmanStats, rowIndex: Int) {
    val isNotOut = batsman.dismissal.contains("not out", ignoreCase = true) || batsman.dismissal.isBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (rowIndex % 2 == 0 && !isNotOut) Color.White.copy(alpha = 0.01f) else Color.Transparent
            )
            .then(
                if (isNotOut) {
                    Modifier
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Gold400.copy(alpha = 0.6f), Gold400.copy(alpha = 0.2f))
                                ),
                                topLeft = Offset.Zero,
                                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                            )
                        }
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Gold400.copy(alpha = 0.08f), Color.Transparent)
                            )
                        )
                } else {
                    Modifier.drawBehind {
                        val strokeWidth = 0.5.dp.toPx()
                        val y = size.height - strokeWidth / 2
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(3f)) {
            Text(
                batsman.name,
                color = if (isNotOut) Gold400 else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isNotOut && batsman.dismissal.isBlank()) "batting" else batsman.dismissal,
                color = if (isNotOut) Gold400.copy(alpha = 0.5f) else TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            batsman.runs,
            modifier = Modifier.weight(1f),
            color = if (isNotOut) Gold400 else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(batsman.balls, modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(
            batsman.strikeRate,
            modifier = Modifier.weight(1.2f),
            color = if (isNotOut) Gold400 else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

private fun inferPlayerRole(playerName: String, index: Int, totalPlayers: Int): String {
    try {
        return when {
            index == 0 && totalPlayers > 5 -> "BAT"
            index == 1 && totalPlayers > 5 -> "BAT"
            index < totalPlayers / 3 -> "BAT"
            index >= totalPlayers - (totalPlayers / 3) -> "BOWL"
            else -> "ALL"
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

private fun roleBadgeColor(role: String): Color {
    try {
        return when (role.uppercase()) {
            "WK" -> Gold400
            "C" -> Gold400
            "BAT" -> ChartBlue
            "ALL" -> StatusSuccess
            "BOWL" -> Color(0xFF8B5CF6)
            else -> Color.White.copy(alpha = 0.4f)
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

@Composable
fun SplitPlayerRow(
    playerName: String,
    teamColor: Color,
    isLeftSide: Boolean,
    playerIndex: Int,
    totalPlayers: Int
) {
    val role = inferPlayerRole(playerName, playerIndex, totalPlayers)
    val roleColor = roleBadgeColor(role)
    val borderWidth = 2.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = if (isLeftSide) 0f else size.width
                drawLine(
                    color = teamColor.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = borderWidth.toPx()
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (isLeftSide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(teamColor.copy(alpha = 0.10f), CircleShape)
                        .border(BorderWidth.hairline, teamColor.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playerName.take(1).uppercase(),
                        color = teamColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = playerName,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = role,
                        color = roleColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = playerName,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = role,
                        color = roleColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(teamColor.copy(alpha = 0.10f), CircleShape)
                        .border(BorderWidth.hairline, teamColor.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playerName.take(1).uppercase(),
                        color = teamColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (playerIndex < totalPlayers - 1) {
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun MatchStatsOverviewCard(match: CricketMatch, scorecard: Scorecard?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, Gold400.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Total Runs", scorecard?.innings?.sumOf {
                    it.batsmen.sumOf { b -> b.runs.toIntOrNull() ?: 0 }
                }?.toString() ?: "-")
                StatItem("Wickets", scorecard?.innings?.sumOf { it.fallOfWickets.size }?.toString() ?: "-")
                StatItem("Boundaries", scorecard?.innings?.sumOf {
                    it.batsmen.sumOf { b -> (b.fours.toIntOrNull() ?: 0) + (b.sixes.toIntOrNull() ?: 0) }
                }?.toString() ?: "-")
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("CRR", match.score?.currentRunRate ?: "-", Gold400)
                StatItem("RRR", match.score?.requiredRunRate ?: "-", ErrorRed)
                StatItem("Overs", scorecard?.innings?.lastOrNull()?.let {
                    it.batsmen.lastOrNull()?.balls?.let { b ->
                        "${(b.toIntOrNull() ?: 0) / 6}.${(b.toIntOrNull() ?: 0) % 6}"
                    }
                } ?: "-")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BattingLeadersCard(innings: Innings) {
    val topBatsmen = remember(innings.batsmen) {
        innings.batsmen.sortedByDescending { it.runs.toIntOrNull() ?: 0 }.take(3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, ChartBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topBatsmen.forEachIndexed { index, batsman ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    when (index) {
                                        0 -> Gold400
                                        1 -> Color.White.copy(alpha = 0.3f)
                                        else -> Color.White.copy(alpha = 0.1f)
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (index == 0) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(text = batsman.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "SR: ${batsman.strikeRate}", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "${batsman.runs}", color = ChartBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = "(${batsman.balls})", color = TextMuted, fontSize = 10.sp)
                    }
                }

                if (index < topBatsmen.lastIndex) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
fun BowlingLeadersCard(innings: Innings) {
    val topBowlers = remember(innings.bowlers) {
        innings.bowlers.sortedByDescending { it.wickets.toIntOrNull() ?: 0 }.take(3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, StatusSuccess.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topBowlers.forEachIndexed { index, bowler ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    when (index) {
                                        0 -> StatusSuccess
                                        1 -> Color.White.copy(alpha = 0.3f)
                                        else -> Color.White.copy(alpha = 0.1f)
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (index == 0) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(text = bowler.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Eco: ${bowler.economy}", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "${bowler.wickets}/${bowler.runs}", color = StatusSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = "(${bowler.overs} ov)", color = TextMuted, fontSize = 10.sp)
                    }
                }

                if (index < topBowlers.lastIndex) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
fun PartnershipStatsCard(innings: Innings) {
    val topPartnerships = remember(innings.partnerships) {
        innings.partnerships.sortedByDescending { it.runs.toIntOrNull() ?: 0 }.take(3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, Gold400.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topPartnerships.forEachIndexed { index, partnership ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${partnership.player1} & ${partnership.player2}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${partnership.contribution1} · ${partnership.contribution2}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "${partnership.runs}", color = Gold400, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(text = "(${partnership.balls} balls)", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    if (index < topPartnerships.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}

@Composable
fun EliteAiInsightBanner(text: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.lg))
            .background(GlassSurface)
            .border(BorderWidth.hairline, Gold400.copy(alpha = 0.15f), RoundedCornerShape(CornerRadius.lg))
            .padding(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Gold400.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Gold400, modifier = Modifier.size(IconSize.md))
            }

            Column {
                Text(
                    text = "Match Insight",
                    color = Gold400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = LetterSpacing.relaxed
                )
                Text(
                    text = text ?: "Loading match insights...",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun EliteRecentTelemetry(balls: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "RECENT BALLS",
            color = Gold400.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = LetterSpacing.ultraWide
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(balls, key = { index, ball -> "$index-$ball" }) { _, ball ->
                val bgColor = when (ball) {
                    "W" -> WicketRed
                    "4" -> ChartBlue
                    "6" -> StatusSuccess
                    else -> GlassSurface
                }
                val textColor = when (ball) {
                    "0", "." -> TextMuted
                    else -> TextPrimary
                }
                val borderColor = when (ball) {
                    "W" -> Gold400.copy(alpha = 0.40f)
                    "4" -> ChartBlue.copy(alpha = 0.30f)
                    "6" -> StatusSuccess.copy(alpha = 0.30f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(CornerRadius.xs))
                        .background(bgColor)
                        .border(
                            if (ball == "W") 1.dp else BorderWidth.hairline,
                            borderColor,
                            RoundedCornerShape(CornerRadius.xs)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = ball, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
