/* REFERENCE ONLY - DO NOT BUILD
package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.models.*
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.premium.*
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Match Detail View - Elite Tier Dashboard (v3.0).
 * Re-implemented after systematic refinement from High-Fidelity Mockups.
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
    
    // Live data states
    var batsmen by remember { mutableStateOf<List<ScrapedBatsman>>(emptyList()) }
    var bowler by remember { mutableStateOf<ScrapedBowler?>(null) }
    var recentBalls by remember { mutableStateOf<List<String>>(emptyList()) }
    var scorecard by remember { mutableStateOf<Scorecard?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var commentary by remember { mutableStateOf(listOf("Loading commentary...")) }
    
    val scraper = remember { CricBuzzScraper() }
    val commentaryManager = remember { LiveCommentaryManager() }
    
    LaunchedEffect(match.id) {
        while (true) {
            try {
                isRefreshing = true
                val details = scraper.fetchBallByBallAndBatsmen(match.id)
                batsmen = details.batsmen
                bowler = details.bowler
                recentBalls = details.recentBalls
                scorecard = scraper.fetchScorecard(match.id)
                
                val keyNames = mutableListOf<String>()
                details.batsmen.forEach { keyNames.add(it.name) }
                if (details.bowler != null) keyNames.add(details.bowler.name)

                commentary = commentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/${match.id}",
                    keyNames = keyNames
                )
            } catch (e: Exception) {
            } finally {
                isRefreshing = false
            }
            delay(10000)
        }
    }

    Scaffold(
        containerColor = BlackBackground,
        topBar = { EliteMatchDetailHeader(match, onBackClick) },
        bottomBar = { EliteFloatingMatchNav(selectedTab, { selectedTab = it }, onArClick) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Segmented Tab Control (Magazine Layout)
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                edgePadding = 20.dp,
                divider = {},
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .padding(horizontal = 16.dp)
                            .background(ShinyGoldDeep)
                    )
                }
            ) {
                val tabs = listOf("OVERVIEW", "SCORECARD", "STATS", "INFO")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f)
            ) { targetTab ->
                when (targetTab) {
                    0 -> OverviewTab(match, batsmen, bowler, recentBalls, commentary)
                    1 -> ScorecardTab(scorecard)
                    2 -> EliteStatisticsTab(match)
                    3 -> EliteVenueInfoTab(match)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EliteMatchDetailHeader(match: CricketMatch, onBackClick: () -> Unit) {
    val battingTeam = match.teams.firstOrNull { it.isBatting } ?: match.teams.firstOrNull()
    
    Surface(
        color = BlackBackground.copy(alpha = 0.8f),
        modifier = Modifier.blur(BouncerDesign.GlassBlur)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                        Text("ICC WORLD CUP", color = ShinyGoldDeep, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(
                            "${match.teams.getOrNull(0)?.acronym ?: ""} vs ${match.teams.getOrNull(1)?.acronym ?: ""}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, "More", tint = Color.White.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${battingTeam?.acronym ?: ""} ${battingTeam?.score ?: "0/0"}",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "(${battingTeam?.overs ?: "0.0"})",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "CRR: 6.72 • REQ: 7.12",
                    color = ShinyGoldDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    recentBalls: List<String>,
    commentary: List<String>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item { EliteAiCommentaryBanner(commentary.firstOrNull()) }
        item { WinProbabilityGaugeCard(team1 = match.teams.firstOrNull()?.acronym ?: "IND", prob = 0.68f) }
        item { PressureIndexHeatmapCard() }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightGridCard(modifier = Modifier.weight(1f), label = "Projected", value = "215", subValue = "High", icon = Icons.Outlined.QueryStats, progress = 0.85f)
                InsightGridCard(modifier = Modifier.weight(1f), label = "Partnership", value = "45", subValue = "28 balls", icon = Icons.Outlined.Handshake)
            }
        }
        if (recentBalls.isNotEmpty()) {
            item { EliteRecentBalls(recentBalls) }
        }
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
fun WinProbabilityGaugeCard(team1: String, prob: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, GlassBorderLight)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WIN PROBABILITY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(200.dp, 100.dp)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.05f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(ShinyGoldDeep, ShinyGoldMid, ShinyGoldDeep)),
                        startAngle = 180f,
                        sweepAngle = 180f * prob,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    val angle = 180f + (180f * prob)
                    val rad = angle * (PI / 180f).toFloat()
                    val needleLen = 80.dp.toPx()
                    val endX = center.x + cos(rad) * needleLen
                    val endY = center.y + sin(rad) * needleLen
                    
                    drawLine(
                        color = ShinyGoldDeep,
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(ShinyGoldDeep, 6.dp.toPx(), center)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(team1, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${(prob * 100).toInt()}%", color = ShinyGoldDeep, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("OPP", color = TextMuted, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PressureIndexHeatmapCard() {
    Card(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, GlassBorderLight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CoilImage(
                model = "https://images.unsplash.com/photo-1531415074968-036ba1b575da?q=80&w=800",
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(Color.Red.copy(alpha = 0.2f), 60.dp.toPx(), Offset(size.width * 0.4f, size.height * 0.6f))
                        drawCircle(ShinyGoldDeep.copy(alpha = 0.15f), 100.dp.toPx(), Offset(size.width * 0.6f, size.height * 0.4f))
                    }
            )
            
            Column(modifier = Modifier.padding(20.dp).align(Alignment.BottomStart)) {
                Text("PRESSURE INDICES", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("AI-Powered Length Analysis", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ScorecardTab(scorecard: Scorecard?) {
    var selectedInnings by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            InningsSwitcher(
                teams = scorecard?.innings?.map { it.teamName } ?: listOf("IND", "AUS"),
                selectedIndex = selectedInnings,
                onIndexSelected = { selectedInnings = it }
            )
        }

        scorecard?.innings?.getOrNull(selectedInnings)?.let { inning ->
            item { EliteBattingSection(inning) }
            item { EliteFallOfWicketsSection() }
            item { EliteBowlingSection(inning) }
        }
    }
}

@Composable
private fun InningsSwitcher(teams: List<String>, selectedIndex: Int, onIndexSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        teams.forEachIndexed { index, team ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ShinyGoldDeep else Color.Transparent)
                    .clickable { onIndexSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${team.take(3).uppercase()} Inning",
                    color = if (isSelected) Color.Black else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EliteBattingSection(inning: Inning) {
    Column {
        EliteSectionHeader("Batting")
        EliteTableHeader(listOf("Batter" to 3f, "R" to 1f, "B" to 1f, "4s" to 1f, "6s" to 1f, "SR" to 1.2f))
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column {
                inning.batsmen.forEachIndexed { index, batsman ->
                    BattingRow(batsman, isLast = index == inning.batsmen.size - 1)
                }
            }
        }
    }
}

@Composable
private fun BattingRow(batsman: Inning.Batsman, isLast: Boolean) {
    val isStriker = batsman.isBatting
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isStriker) Color.White.copy(alpha = 0.05f) else Color.Transparent)
    ) {
        if (isStriker) {
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(ShinyGoldDeep).align(Alignment.CenterStart))
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(3f)) {
                Text(
                    text = batsman.name + if (isStriker) " *" else "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isStriker) {
                    Text("Batting", color = SuccessGreen, fontSize = 10.sp)
                } else {
                    Text(batsman.dismissalInfo ?: "Not out", color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(batsman.runs.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(batsman.balls.toString(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(batsman.fours.toString(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(batsman.sixes.toString(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(batsman.strikeRate.toString(), color = ShinyGoldDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        }
    }
    if (!isLast) HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun EliteFallOfWicketsSection() {
    Column {
        EliteSectionHeader("Fall of Wickets")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) { i ->
                Surface(
                    modifier = Modifier.width(130.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${i+1}st Wicket", color = TextTertiary, fontSize = 11.sp)
                        Text("45-1", color = ShinyGoldDeep, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("R. Sharma (6.2 ov)", color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun EliteBowlingSection(inning: Inning) {
    Column {
        EliteSectionHeader("Bowling")
        EliteTableHeader(listOf("Bowler" to 3.5f, "O" to 1f, "M" to 1f, "R" to 1f, "W" to 1f, "Econ" to 1.2f))
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column {
                inning.bowlers.forEachIndexed { index, bowler ->
                    BowlingRow(bowler, isLast = index == inning.bowlers.size - 1)
                }
            }
        }
    }
}

@Composable
private fun BowlingRow(bowler: Inning.Bowler, isLast: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(3.5f)) {
            Text(bowler.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            MiniEconomySparkline(modifier = Modifier.width(60.dp).height(12.dp).padding(top = 4.dp))
        }
        Text(bowler.overs.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(bowler.maidens.toString(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(bowler.runs.toString(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(bowler.wickets.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(bowler.economy.toString(), color = ShinyGoldDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
    }
    if (!isLast) HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun EliteTableHeader(cols: List<Pair<String, Float>>) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            cols.forEach { (title, weight) ->
                Text(
                    title,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(weight),
                    textAlign = if (title == "SR" || title == "Econ") TextAlign.End else TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EliteSectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp, start = 16.dp)) {
        Box(modifier = Modifier.size(2.dp, 16.dp).background(ShinyGoldDeep))
        Text(title.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun EliteFloatingMatchNav(selectedTab: Int, onTabSelected: (Int) -> Unit, onArClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.width(320.dp).height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = BlackBackground.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, GlassBorderLight),
            shadowElevation = 24.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Home */ }) { Icon(Icons.Default.Home, null, tint = TextSecondary) }
                IconButton(onClick = { onTabSelected(0) }) { Icon(Icons.Default.Dashboard, null, tint = if (selectedTab == 0) ShinyGoldDeep else TextSecondary) }

                Box(
                    modifier = Modifier.size(48.dp).background(ShinyGoldDeep.copy(alpha = 0.1f), CircleShape).border(1.dp, ShinyGoldDeep, CircleShape).clickable(onClick = onArClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ViewInAr, null, tint = ShinyGoldDeep, modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = { onTabSelected(1) }) { Icon(Icons.Default.Leaderboard, null, tint = if (selectedTab == 1) ShinyGoldDeep else TextSecondary) }
                IconButton(onClick = { onTabSelected(2) }) { Icon(Icons.Default.Analytics, null, tint = if (selectedTab == 2) ShinyGoldDeep else TextSecondary) }
            }
        }
    }
}

@Composable
private fun InsightGridCard(modifier: Modifier, label: String, value: String, subValue: String, icon: ImageVector, progress: Float? = null) {
    Surface(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.05f), modifier = Modifier.size(48.dp).align(Alignment.TopEnd))
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(label.uppercase(), color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        if (progress != null) {
                            Text(subValue, color = ShinyGoldDeep, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                        }
                    }
                    if (progress != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.1f))) {
                            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(ShinyGoldDeep))
                        }
                    } else {
                        Text(subValue, color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EliteRecentBalls(balls: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EliteSectionHeader("Recent Balls")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(balls) { ball ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (ball.contains("4") || ball.contains("6")) ShinyGoldDeep else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, GoldHairline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ball,
                        color = if (ball.contains("4") || ball.contains("6")) BlackPure else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MiniEconomySparkline(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val points = listOf(0.4f, 0.2f, 0.8f, 0.5f, 0.3f, 0.6f)
        val step = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * step
            val y = size.height * (1 - p)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, ShinyGoldDeep, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun EliteStatisticsTab(match: CricketMatch) {
    EliteTierAiStatsScreen(match = match, onBackClick = {})
}
*/
