package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.*
import com.tanush.bouncer.models.FallOfWicket as ModelFallOfWicket
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.PowerPlayPhase
import com.tanush.bouncer.ui.components.premium.*
import com.tanush.bouncer.ui.components.premium.MomentumEvent
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Match Detail View
 * Comprehensive match detail screen with live updates, statistics, and analysis.
 * Features: Pitch Analysis, Quick Actions, Predictive Forecasts, Momentum Tracker,
 * Fall of Wickets, Partnership Telemetry, and PowerPlay Analysis.
 * Fixed: Build errors, explicit imports, and refined "Delayed" status UI.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailView(
    match: CricketMatch,
    onBackClick: () -> Unit,
    onForecastClick: (String) -> Unit = {},
    onArClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Live Technical Feeds
    var batsmen by remember { mutableStateOf<List<ScrapedBatsman>>(emptyList()) }
    var bowler by remember { mutableStateOf<ScrapedBowler?>(null) }
    var recentBalls by remember { mutableStateOf<List<String>>(emptyList()) }
    var scorecard by remember { mutableStateOf<Scorecard?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var commentary by remember { mutableStateOf(listOf("Synchronizing Elite Data...")) }
    
    val scraper = remember { CricBuzzScraper() }
    val commentaryManager = remember { LiveCommentaryManager() }
    val pagerState = rememberPagerState(pageCount = { 6 })
    
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
                scorecard = scraper.fetchScorecard(match.id)
                
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
            } finally {
                isRefreshing = false
            }
            delay(8000)
        }
    }

    Scaffold(
        containerColor = BgBase,
        topBar = { EliteMatchDetailHeader(match, onBackClick) },
        bottomBar = { EliteFloatingMatchNav(selectedTab, { selectedTab = it }, onArClick) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background Layer: Obsidian Grid Architecture
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val gridSpacing = 48.dp.toPx()
                        for (x in 0..(size.width / gridSpacing).toInt()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.01f),
                                start = Offset(x * gridSpacing, 0f),
                                end = Offset(x * gridSpacing, size.height),
                                strokeWidth = 1f
                            )
                        }
                        for (y in 0..(size.height / gridSpacing).toInt()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.01f),
                                start = Offset(0f, y * gridSpacing),
                                end = Offset(size.width, y * gridSpacing),
                                strokeWidth = 1f
                            )
                        }
                    }
            )

            Column(modifier = Modifier.fillMaxSize()) {
                EliteTabRow(selectedTab, { selectedTab = it })
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = true,
                    contentPadding = PaddingValues(top = Spacing.sm)
                ) { page ->
                    when (page) {
                        0 -> EliteOverviewTab(match, batsmen, bowler, recentBalls, commentary)
                        1 -> EliteScorecardTab(scorecard)
                        2 -> EliteSquadsTab(match, scorecard)
                        3 -> EliteStatisticsTab(match, scorecard)
                        4 -> EliteAiInsightsTab(match)
                        5 -> EliteVenueInfoTab(match)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EliteMatchDetailHeader(match: CricketMatch, onBackClick: () -> Unit) {
    val battingTeam = match.teams.find { it.isBatting } ?: match.teams.firstOrNull()
    val otherTeam = match.teams.find { !it.isBatting } ?: match.teams.getOrNull(1)
    
    val team1Logo = TeamLogoManager.getBestLogoUrl(battingTeam?.name, battingTeam?.logoUrl)
    val team2Logo = TeamLogoManager.getBestLogoUrl(otherTeam?.name, otherTeam?.logoUrl)
    
    // Parse overs and CRR from score if available
    val overs = battingTeam?.overs ?: ""
    val score = battingTeam?.score ?: "0/0"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1115),
                        Color(0xFF0F1115).copy(alpha = 0.95f),
                        Color.Transparent
                    )
                )
            )
    ) {
        // Top bar: Back, Title, Share
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = match.matchTitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(IconSize.sm))
                }
            },
            actions = {
                IconButton(
                    onClick = { },
                    modifier = Modifier
                        .padding(end = Spacing.xs)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(IconSize.sm))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
        )

        // Glass Score Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .clip(RoundedCornerShape(CornerRadius.lg))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    BorderWidth.hairline,
                    Brush.verticalGradient(
                        colors = listOf(
                            Gold400.copy(alpha = 0.20f),
                            Gold400.copy(alpha = 0.08f)
                        )
                    ),
                    RoundedCornerShape(CornerRadius.lg)
                )
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Badge
            if (match.status.contains("Delayed", ignoreCase = true) || match.status.contains("Interrupted", ignoreCase = true)) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(CornerRadius.xs),
                    border = BorderStroke(BorderWidth.hairline, ErrorRed.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Gavel, null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                        Text("Match Delayed", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Live badge
            if (match.status.contains("live", ignoreCase = true) || match.status.contains("batting", ignoreCase = true) || match.status.contains("bowling", ignoreCase = true)) {
                Surface(
                    color = Color(0xFF22C55E).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(CornerRadius.xs),
                    border = BorderStroke(BorderWidth.hairline, Color(0xFF22C55E).copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Text("LIVE", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            // Teams + Score Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Team 1 (Batting)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    CoilImage(
                        model = team1Logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(
                                if (battingTeam?.isBatting == true) 2.dp else BorderWidth.hairline,
                                if (battingTeam?.isBatting == true) Gold400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = battingTeam?.acronym ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Score Display - Etched Gold Effect
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(2f)
                ) {
                    Text(
                        text = score,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-2).sp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White,
                                    Gold400,
                                    Color(0xFFB08D26)
                                )
                            )
                        )
                    )
                    if (overs.isNotBlank()) {
                        Text(
                            text = "($overs Ov)",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Team 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    CoilImage(
                        model = team2Logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(
                                if (otherTeam?.isBatting == true) 2.dp else BorderWidth.hairline,
                                if (otherTeam?.isBatting == true) Gold400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = otherTeam?.acronym ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Stat Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                if (overs.isNotBlank()) {
                    StatPill(label = "Overs", value = overs)
                }
                // CRR calculation
                val scoreParts = score.split("/", "-")
                val runs = scoreParts.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                val oversNum = overs.toFloatOrNull() ?: 0f
                if (oversNum > 0) {
                    val crr = runs / oversNum
                    StatPill(label = "CRR", value = String.format("%.2f", crr))
                }
                // Target if available from other team
                val otherScore = otherTeam?.score
                if (otherScore != null && otherScore.isNotBlank()) {
                    StatPill(label = "Target", value = otherScore)
                }
            }

            // Match status
            if (match.status.isNotBlank() && !match.status.contains("Delayed", ignoreCase = true)) {
                Text(
                    text = match.status,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.9f), Gold400.copy(alpha = 0.7f))
                        )
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                BorderWidth.hairline,
                Gold400.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EliteTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Live", "Scorecard", "Squads", "Overs", "Stats", "Info")
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs)
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                BorderWidth.hairline,
                Brush.linearGradient(
                    colors = listOf(
                        Gold400.copy(alpha = 0.25f),
                        Gold400.copy(alpha = 0.08f),
                        Gold400.copy(alpha = 0.15f)
                    )
                ),
                RoundedCornerShape(CornerRadius.md)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs.size) { index ->
            val isSelected = selectedTab == index
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "tabAlpha"
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(CornerRadius.sm))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Gold400, Color(0xFFB08D26)),
                                        start = Offset.Zero,
                                        end = Offset(200f, 200f)
                                    )
                                )
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(CornerRadius.sm),
                                    ambientColor = Gold400.copy(alpha = 0.3f),
                                    spotColor = Gold400.copy(alpha = 0.3f)
                                )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabs[index],
                    color = if (isSelected) Color.White else TextSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EliteOverviewTab(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    recentBalls: List<String>,
    commentary: List<String>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Quick Stats Bar - Cricbuzz Style
        item {
            QuickMatchStatsBar(
                currentRunRate = match.score?.currentRunRate ?: "6.42",
                requiredRunRate = match.score?.requiredRunRate,
                partnership = "42(28)",
                lastWicket = "23(18)"
            )
        }
        
        item {
            EliteAiInsightBanner(commentary.firstOrNull())
        }
        
        item {
            EliteImpactHeroCard()
        }

        item {
            EliteForecastCarousel()
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TechnicalQuickAction(
                    modifier = Modifier.weight(1f),
                    label = "FIELD MAP",
                    icon = Icons.Outlined.ZoomOutMap,
                    accentColor = Gold400
                )
                TechnicalQuickAction(
                    modifier = Modifier.weight(1f),
                    label = "HOTSPOTS",
                    icon = Icons.Outlined.LocalFireDepartment,
                    accentColor = ErrorRed
                )
                TechnicalQuickAction(
                    modifier = Modifier.weight(1f),
                    label = "TRAJECTORY",
                    icon = Icons.Outlined.Timeline,
                    accentColor = ChartBlue
                )
            }
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PitchEnvironmentCard(
                    modifier = Modifier.weight(1.2f)
                )
                InsightGridCard(
                    modifier = Modifier.weight(1f),
                    label = "Engine Conf.",
                    value = "84%",
                    subValue = "+2.1% Session",
                    icon = Icons.Default.AutoAwesome,
                    accentColor = Gold400
                )
            }
        }
        
        item {
            PowerPlayStatsCard(
                teamName = match.teams.firstOrNull { it.isBatting }?.name ?: "Batting Team",
                phases = listOf(
                    PowerPlayPhase("PP1", 1, 1, 6, 45, 1, 36, 6, 1, 12),
                    PowerPlayPhase("PP2", 2, 7, 40, 180, 4, 204, 12, 2, 80)
                )
            )
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val striker = batsmen.firstOrNull { it.isStriker } ?: batsmen.firstOrNull()
                striker?.let { 
                    MicroStatCard(
                        modifier = Modifier.weight(1f),
                        label = "STRIKER",
                        name = it.name,
                        value = it.runs,
                        subValue = "(${it.balls})",
                        icon = Icons.Default.SportsCricket,
                        accentColor = Gold400
                    )
                }
                
                bowler?.let { currentBowler ->
                    MicroStatCard(
                        modifier = Modifier.weight(1f),
                        label = "BOWLER",
                        name = currentBowler.name,
                        value = "${currentBowler.wickets}/${currentBowler.runs}",
                        subValue = "(${currentBowler.overs})",
                        icon = Icons.Default.SportsBaseball,
                        accentColor = ChartBlue
                    )
                }
            }
        }

        item {
            EliteMomentumTracker(
                events = listOf(
                    MomentumEvent("18.4", "WICKET", "Bowled · 142kph Yorker", "W", Icons.Default.HeartBroken, WicketRed),
                    MomentumEvent("18.3", "MASSIVE SIX", "Pull Shot · 94 Meters", "6", Icons.Default.Flare, StatusSuccess)
                )
            )
        }
        
        // Win Prediction Gauge - Cricbuzz/ESPNcricinfo Style
        item {
            val team1 = match.teams.firstOrNull()
            val team2 = match.teams.getOrNull(1)
            if (team1 != null && team2 != null) {
                WinPredictionGauge(
                    team1Name = team1.name.take(3).uppercase(),
                    team2Name = team2.name.take(3).uppercase(),
                    team1Probability = 0.58f,
                    team1Color = ChartBlue,
                    team2Color = WicketRed
                )
            }
        }
        
        if (recentBalls.isNotEmpty()) {
            item {
                EliteRecentTelemetry(recentBalls)
            }
        }
        
        item { Spacer(modifier = Modifier.height(100.dp)) }
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                BorderWidth.hairline,
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentColor.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(12.dp)
            )
            .clickable { }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                BorderWidth.hairline,
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.20f),
                        accentColor.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Icon(icon, null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = accentColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subValue, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun EliteImpactHeroCard() {
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
                Text(
                    "Match Momentum",
                    color = Gold400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "94.2",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = StatusSuccess.copy(alpha = 0.1f)
            ) {
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
private fun EliteAiInsightsTab(match: CricketMatch) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            PremiumWinProbabilityHero(
                team1Name = match.teams.getOrNull(0)?.acronym ?: "T1",
                team1Probability = 0.58f
            )
        }
        
        // AI Analysis Cards - Cricbuzz Style Technical Analysis
        item {
            AiAnalysisCard(
                title = "Spin Vulnerability Detected",
                description = "The current batter struggles against left-arm chinaman spin. Dismissal probability spikes in next 2 overs.",
                highlightText = "45% Dismissal Probability",
                type = AnalysisType.WARNING
            )
        }
        
        item {
            AiAnalysisCard(
                title = "Momentum Shift",
                description = "Batting team's powerplay performance is above average vs this bowling lineup.",
                highlightText = "+12 runs above average",
                type = AnalysisType.POSITIVE
            )
        }
        
        item {
            AiAnalysisCard(
                title = "Pitch Degradation",
                description = "Dry patches emerging on good length. Expect increased spin deviation in the second half.",
                highlightText = "15% increase in spin deviation",
                type = AnalysisType.NEUTRAL
            )
        }
        
        // Head to Head Section
        item {
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            if (team1 != null && team2 != null) {
                HeadToHeadPremiumCard(
                    team1 = TeamComparisonData(
                        teamName = team1.name,
                        teamAcronym = team1.acronym,
                        wins = 12,
                        losses = 8,
                        draws = 2,
                        primaryColor = ChartBlue,
                        recentForm = listOf(true, false, true, true, true)
                    ),
                    team2 = TeamComparisonData(
                        teamName = team2.name,
                        teamAcronym = team2.acronym,
                        wins = 8,
                        losses = 12,
                        draws = 2,
                        primaryColor = WicketRed,
                        recentForm = listOf(false, true, false, true, false)
                    ),
                    format = "T20",
                    totalMatches = 22
                )
            }
        }
        
        // Tactical Matrix
        item {
            val team1 = match.teams.getOrNull(0)
            val team2 = match.teams.getOrNull(1)
            if (team1 != null && team2 != null) {
                TacticalMatrixCard(
                    team1Name = team1.acronym,
                    team2Name = team2.acronym,
                    team1Color = ChartBlue,
                    team2Color = StatusSuccess,
                    metrics = listOf(
                        TacticalMetric("Powerplay Dominance", 62f, 38f),
                        TacticalMetric("Death Over Efficiency", 45f, 55f),
                        TacticalMetric("Boundaries Per Match", 70f, 30f),
                        TacticalMetric("Spin Success Rate", 58f, 42f)
                    )
                )
            }
        }
        
        item {
            MatchInsightsCard(
                matchTitle = match.matchTitle,
                currentPhase = "Death Overs",
                insights = listOf(
                    MatchInsightDetail("Spin Impact", "High", "+12% Turn", InsightType.BOWLING, InsightTrend.POSITIVE),
                    MatchInsightDetail("Boundaries", "Low", "-5% RR", InsightType.BATTING, InsightTrend.NEGATIVE)
                ),
                keyHighlight = "Klaasen's 98% aggression rating suggests higher boundary probability in overs 16-20."
            )
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun EliteScorecardTab(scorecard: Scorecard?) {
    if (scorecard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold400)
        }
    } else {
        var selectedInnings by remember { mutableIntStateOf(0) }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            BorderWidth.hairline,
                            Gold400.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp)
                ) {
                    scorecard.innings.forEachIndexed { index, inning ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedInnings == index) Gold400 else Color.Transparent)
                                .clickable { selectedInnings = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                inning.teamName.uppercase(),
                                color = if (selectedInnings == index) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            
            scorecard.innings.getOrNull(selectedInnings)?.let { inning ->
                item {
                    EliteBattingTable(inning)
                }
                
                if (inning.bowlers.isNotEmpty()) {
                    item {
                        EliteBowlingTable(inning)
                    }
                }
                
                if (inning.fallOfWickets.isNotEmpty()) {
                    item {
                        EliteFowTable(inning.fallOfWickets)
                    }
                }
                
                if (inning.partnerships.isNotEmpty()) {
                    item {
                        PartnershipBreakdownCard(
                            teamName = inning.teamName,
                            partnerships = inning.partnerships.map { 
                                PartnershipEntry(
                                    it.player1, it.contribution1.toIntOrNull() ?: 0, 0,
                                    it.player2, it.contribution2.toIntOrNull() ?: 0, 0,
                                    it.runs.toIntOrNull() ?: 0, it.balls.toIntOrNull() ?: 0,
                                    0.0, 0
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EliteBattingTable(inning: Innings) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                BorderWidth.hairline,
                Brush.verticalGradient(
                    colors = listOf(
                        Gold400.copy(alpha = 0.20f),
                        Gold400.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
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
                .background(Color.White.copy(alpha = 0.03f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("BATTER", modifier = Modifier.weight(3f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("R", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("B", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("SR", modifier = Modifier.weight(1.2f), color = Gold400, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
        }
        
        inning.batsmen.forEach { batsman ->
            BattingRowItem(batsman)
        }
    }
}

@Composable
private fun EliteBowlingTable(inning: Innings) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                BorderWidth.hairline,
                Brush.verticalGradient(
                    colors = listOf(
                        ChartBlue.copy(alpha = 0.15f),
                        ChartBlue.copy(alpha = 0.03f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
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
                .background(Color.White.copy(alpha = 0.03f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("BOWLER", modifier = Modifier.weight(3f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("O", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("R", modifier = Modifier.weight(1f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("W", modifier = Modifier.weight(1f), color = ChartBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
        }
        
        inning.bowlers.forEach { bowler ->
            BowlingRowItem(bowler)
        }
    }
}

@Composable
private fun BowlingRowItem(bowler: BowlerStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 0.5.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(color = Color.White.copy(alpha = 0.05f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidth)
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
private fun EliteFowTable(fows: List<ModelFallOfWicket>) {
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
private fun BattingRowItem(batsman: BatsmanStats) {
    val isNotOut = batsman.dismissal.contains("not out", ignoreCase = true) || batsman.dismissal.isBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNotOut) {
                    Modifier
                        .drawBehind {
                            // Gold left inset border for not-out batters
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
                                colors = listOf(
                                    Gold400.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
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

/**
 * Elite Squads Tab - iOS Parity
 * Displays team squads with centered logos and two-column player lists
 */
@Composable
private fun EliteSquadsTab(match: CricketMatch, scorecard: Scorecard?) {
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    
    val team1Logo = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl)
    val team2Logo = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl)
    
    val team1Color = ChartBlue
    val team2Color = ErrorRed
    
    // Extract squad from scorecard batsmen/bowlers
    val team1Players = scorecard?.innings?.getOrNull(0)?.let { inning ->
        val batsmen = inning.batsmen.map { it.name }
        val bowlers = inning.bowlers.map { it.name }
        (batsmen + bowlers).distinct()
    } ?: emptyList()
    
    val team2Players = scorecard?.innings?.getOrNull(1)?.let { inning ->
        val batsmen = inning.batsmen.map { it.name }
        val bowlers = inning.bowlers.map { it.name }
        (batsmen + bowlers).distinct()
    } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === Team Header Bar (glass panel) ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    BorderWidth.hairline,
                    Gold400.copy(alpha = 0.25f),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(team1Color.copy(alpha = 0.15f))
                            .border(BorderWidth.hairline, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CoilImage(
                            model = team1Logo,
                            contentDescription = team1?.name,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Column {
                        Text(
                            text = team1?.acronym ?: "T1",
                            color = team1Color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Playing XI",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp
                        )
                    }
                }
                
                // Center divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )
                
                // Team 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = team2?.acronym ?: "T2",
                            color = team2Color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Playing XI",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(team2Color.copy(alpha = 0.15f))
                            .border(BorderWidth.hairline, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CoilImage(
                            model = team2Logo,
                            contentDescription = team2?.name,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
        
        // === Team name labels above split ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = (team1?.name ?: "Team 1").uppercase(),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = (team2?.name ?: "Team 2").uppercase(),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        
        // === Split Panel Glass Container ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    BorderWidth.hairline,
                    Gold400.copy(alpha = 0.20f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            // Split content
            Row(modifier = Modifier.fillMaxWidth()) {
                // Team 1 Column (left)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    team1Color.copy(alpha = 0.10f),
                                    team1Color.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .padding(vertical = 8.dp)
                ) {
                    if (team1Players.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        team1Players.forEachIndexed { index, player ->
                            SplitPlayerRow(
                                playerName = player,
                                teamColor = team1Color,
                                isLeftSide = true,
                                playerIndex = index,
                                totalPlayers = team1Players.size
                            )
                        }
                    }
                }
                
                // Center Gold Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .then(
                            if (team1Players.isNotEmpty() || team2Players.isNotEmpty()) {
                                val maxCount = maxOf(team1Players.size, team2Players.size, 1)
                                Modifier.height((maxCount * 48).dp)
                            } else {
                                Modifier.height(200.dp)
                            }
                        )
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Gold400.copy(alpha = 0.40f),
                                    Gold400.copy(alpha = 0.50f),
                                    Gold400.copy(alpha = 0.40f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Team 2 Column (right)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    team2Color.copy(alpha = 0.02f),
                                    team2Color.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .padding(vertical = 8.dp)
                ) {
                    if (team2Players.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        team2Players.forEachIndexed { index, player ->
                            SplitPlayerRow(
                                playerName = player,
                                teamColor = team2Color,
                                isLeftSide = false,
                                playerIndex = index,
                                totalPlayers = team2Players.size
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Determines a role badge for a player based on their name heuristics
 * or index position in the squad list.
 */
private fun inferPlayerRole(playerName: String, index: Int, totalPlayers: Int): String {
    val nameLower = playerName.lowercase()
    // Simple heuristics for role detection
    return when {
        index == 0 && totalPlayers > 5 -> "BAT"
        index == 1 && totalPlayers > 5 -> "BAT"
        index < totalPlayers / 3 -> "BAT"
        index >= totalPlayers - (totalPlayers / 3) -> "BOWL"
        else -> "ALL"
    }
}

/**
 * Returns the color for a given role badge.
 */
private fun roleBadgeColor(role: String): Color {
    return when (role.uppercase()) {
        "WK" -> Gold400
        "C" -> Gold400
        "BAT" -> ChartBlue
        "ALL" -> StatusSuccess
        "BOWL" -> Color(0xFF8B5CF6) // Purple
        else -> Color.White.copy(alpha = 0.4f)
    }
}

/**
 * A single player row inside the split-panel lineups view.
 * Left side: avatar + name + role badge with left border.
 * Right side: name + role badge + avatar with right border (mirrored).
 */
@Composable
private fun SplitPlayerRow(
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
                // Team-colored side border
                if (isLeftSide) {
                    drawLine(
                        color = teamColor.copy(alpha = 0.6f),
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = borderWidth.toPx()
                    )
                } else {
                    drawLine(
                        color = teamColor.copy(alpha = 0.6f),
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = borderWidth.toPx()
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (isLeftSide) {
            // Left side: Avatar → Name + Role
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Player avatar circle
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
            // Right side: Name + Role ← Avatar (mirrored)
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
                
                // Player avatar circle
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
    
    // Bottom separator between players
    if (playerIndex < totalPlayers - 1) {
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.padding(horizontal = if (isLeftSide) 8.dp else 8.dp)
        )
    }
}

// SquadPlayerRow removed - replaced by SplitPlayerRow in the split-panel lineups design

/**
 * Elite Statistics Tab - iOS Parity
 * Displays match statistics, batting/bowling averages, and key metrics
 */
@Composable
private fun EliteStatisticsTab(match: CricketMatch, scorecard: Scorecard?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Match Summary Stats
        item {
            Text(
                text = "MATCH STATISTICS",
                color = Gold400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
        }
        
        item {
            MatchStatsOverviewCard(match, scorecard)
        }
        
        // Batting Stats
        scorecard?.innings?.firstOrNull()?.let { innings ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "BATTING LEADERS",
                    color = ChartBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
            
            item {
                BattingLeadersCard(innings)
            }
        }
        
        // Bowling Stats
        scorecard?.innings?.firstOrNull()?.let { innings ->
            if (innings.bowlers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "BOWLING LEADERS",
                        color = StatusSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
                
                item {
                    BowlingLeadersCard(innings)
                }
            }
        }
        
        // Partnership Stats
        scorecard?.innings?.firstOrNull()?.let { innings ->
            if (innings.partnerships.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PARTNERSHIP BREAKDOWN",
                        color = Gold400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
                
                item {
                    PartnershipStatsCard(innings)
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun MatchStatsOverviewCard(match: CricketMatch, scorecard: Scorecard?) {
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
            // Total Runs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Total Runs", scorecard?.innings?.sumOf { 
                    it.batsmen.sumOf { b -> b.runs.toIntOrNull() ?: 0 }
                }?.toString() ?: "-")
                
                StatItem("Wickets", scorecard?.innings?.sumOf { 
                    it.fallOfWickets.size
                }?.toString() ?: "-")
                
                StatItem("Boundaries", scorecard?.innings?.sumOf { 
                    it.batsmen.sumOf { b -> (b.fours.toIntOrNull() ?: 0) + (b.sixes.toIntOrNull() ?: 0) }
                }?.toString() ?: "-")
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            
            // Run Rate Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BattingLeadersCard(innings: Innings) {
    val topBatsmen = innings.batsmen
        .sortedByDescending { it.runs.toIntOrNull() ?: 0 }
        .take(3)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, ChartBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            Text(
                                text = batsman.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "SR: ${batsman.strikeRate}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${batsman.runs}",
                            color = ChartBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "(${batsman.balls})",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
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
private fun BowlingLeadersCard(innings: Innings) {
    val topBowlers = innings.bowlers
        .sortedByDescending { it.wickets.toIntOrNull() ?: 0 }
        .take(3)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, StatusSuccess.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            Text(
                                text = bowler.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Eco: ${bowler.economy}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${bowler.wickets}/${bowler.runs}",
                            color = StatusSuccess,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "(${bowler.overs} ov)",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
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
private fun PartnershipStatsCard(innings: Innings) {
    val topPartnerships = innings.partnerships
        .sortedByDescending { it.runs.toIntOrNull() ?: 0 }
        .take(3)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(BorderWidth.hairline, Gold400.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            Text(
                                text = "${partnership.runs}",
                                color = Gold400,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "(${partnership.balls} balls)",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
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
private fun EliteVenueInfoTab(match: CricketMatch) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "VENUE",
                color = Gold400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
        }
        
        item {
            VenueCard(
                venue = VenueInfo(
                    name = match.venue,
                    city = "Live Telemetry",
                    country = "Global Broadcast",
                    imageUrl = "https://images.unsplash.com/photo-1531415074968-036ba1b575da?q=80&w=800"
                )
            )
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun EliteAiInsightBanner(text: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderWidth.hairline, Gold400.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Gold400.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = Gold400,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column {
                Text(
                    text = "Match Insight",
                    color = Gold400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Recent Balls",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(balls) { ball ->
                val bgColor = when {
                    ball == "W" -> Color(0xFFDC2626) // Red for wickets
                    ball == "4" -> Color(0xFF3B82F6) // Blue for fours
                    ball == "6" -> Color(0xFF10B981) // Emerald for sixes
                    ball == "0" || ball == "." -> Color.White.copy(alpha = 0.06f) // Muted for dots
                    else -> Color.White.copy(alpha = 0.10f) // Dark bg for other runs
                }
                val textColor = when {
                    ball == "W" || ball == "4" || ball == "6" -> Color.White
                    ball == "0" || ball == "." -> TextMuted
                    else -> Color.White
                }
                val borderColor = when {
                    ball == "W" -> Gold400.copy(alpha = 0.40f) // Gold border pulse for wickets
                    ball == "4" -> Color(0xFF3B82F6).copy(alpha = 0.30f)
                    ball == "6" -> Color(0xFF10B981).copy(alpha = 0.30f)
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
                    Text(
                        text = ball,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EliteFloatingMatchNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onArClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Gold top border line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Gold400.copy(alpha = 0.15f),
                            Gold400.copy(alpha = 0.25f),
                            Gold400.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1115).copy(alpha = 0.97f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navItems = listOf(
                Triple(Icons.Outlined.Home, "Home", 0),
                Triple(Icons.Outlined.SportsCricket, "Cricket", 1),
                Triple(Icons.Outlined.Leaderboard, "Leaderboard", 2),
                Triple(Icons.Outlined.Settings, "Settings", 3)
            )

            navItems.forEachIndexed { idx, (icon, label, _) ->
                val isActive = idx == 1 // Cricket tab is active when viewing match detail
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Navigate to section */ }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Subtle glow behind active icon
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Gold400.copy(alpha = 0.15f),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                            )
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isActive) Gold400 else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = label,
                        color = if (isActive) Gold400 else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
