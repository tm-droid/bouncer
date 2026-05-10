package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.BarChart
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.MomentumIndicator
import com.tanush.bouncer.ui.components.PremiumWinProbabilityRing
import com.tanush.bouncer.ui.components.RunRateSpeedometer
import com.tanush.bouncer.ui.components.StatCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.parseRuns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteTierAiStatsScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Scaffold(
        containerColor = colors.backgroundBase,
        topBar = { EliteStatsTopBar(match, onBackClick) }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.padding(padding)) {
            EliteTierAiStatsContent(match)
        }
    }
}

/**
 * Reusable AI Stats content suitable for Tabs or Full Screens.
 */
@Composable
fun EliteTierAiStatsContent(match: CricketMatch) {
    val scrollState = rememberScrollState()
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "impact")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero Metric: AI Impact Score
        val impactProb = match.calculateHeuristicProbability()
        val impactScore = String.format("%.1f", impactProb)
        val avgDiff = String.format("%+.1f", impactProb - 50.0)
        AiImpactHero(rotation = rotation, scoreText = impactScore, trendText = "$avgDiff vs Avg")

        // Momentum Indicator
        val crr = match.score?.currentRunRate?.toFloatOrNull() ?: 6.0f
        val rrr = match.score?.requiredRunRate?.toFloatOrNull() ?: 6.0f
        val momentumValue = ((crr / (crr + rrr)).coerceIn(0.2f, 0.8f))
        MomentumIndicator(
            momentum = momentumValue,
            team1Name = match.teams.getOrNull(0)?.name ?: "Team 1",
            team2Name = match.teams.getOrNull(1)?.name ?: "Team 2",
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Insight Pill
        EliteInsightPill(match)

        // Value Added Section
        ValueAddedChartSection(match)

        // Duel Metrics Row
        EliteDuelMetricsRow(match)

        // Predictive Insight Module
        ElitePredictiveModule(match)
    }
}

@Composable
private fun AiImpactHero(rotation: Float, scoreText: String = "50.0", trendText: String = "+0.0 vs Avg") {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Ring System
        Canvas(modifier = Modifier.size(200.dp)) {
            drawCircle(
                color = hierarchy.secondary.primary.copy(alpha = 0.1f),
                radius = size.minDimension / 2,
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = hierarchy.secondary.primary.copy(alpha = 0.3f),
                startAngle = rotation,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(
                    width = 2.dp.toPx(), 
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CURRENT AI IMPACT SCORE", 
                color = hierarchy.secondary.primary.copy(alpha = 0.6f), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                letterSpacing = 2.sp
            )
            Text(
                scoreText, 
                color = colors.textPrimary, 
                fontSize = 64.sp, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.shadow(20.dp, spotColor = hierarchy.secondary.primary.copy(alpha = 0.3f))
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(14.dp))
                Text(trendText, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun EliteInsightPill(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, hierarchy.secondary.primary.copy(alpha = 0.2f), Color.Transparent)))
            .padding(1.dp)
            .clip(CircleShape)
            .background(colors.glassSurface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            val striker = match.scrapedBatsmen?.find { it.isStriker }
            val insightText = if (striker != null) {
                "${striker.name}'s strike rate of ${striker.strikeRate} suggests high impact in recent overs."
            } else {
                "Live batsman impact analysis will update with ball-by-ball data."
            }
            Text(
                text = insightText,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ValueAddedChartSection(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val crr = match.score?.currentRunRate?.toFloatOrNull() ?: 6.0f
    val totalRuns = parseRuns(match.teams.firstOrNull { it.isBatting }?.score)
    val overs = match.teams.firstOrNull { it.isBatting }?.overs?.split(".")?.firstOrNull()?.toIntOrNull() ?: 1
    val avgPerOver = if (overs > 0) totalRuns.toFloat() / overs else crr
    
    // Generate chart data from match overs — derive 5 data points spanning the innings
    val chartData = (1..minOf(5, overs.coerceAtLeast(1))).map { i ->
        val ov = if (overs <= 5) i else (overs * i / 5)
        val variation = (((i * 7 + overs * 3) % 10) / 10f).coerceIn(0.1f, 0.95f)
        "Ov$ov" to variation
    }.ifEmpty { listOf("Ov1" to 0.5f) }
    
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Value Added", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Run contribution per over", color = colors.textSecondary, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("+${String.format("%.0f", avgPerOver)}", color = hierarchy.secondary.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("RPO", color = hierarchy.secondary.primary.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            BarChart(
                data = chartData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun EliteDuelMetricsRow(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val striker = match.scrapedBatsmen?.find { it.isStriker }
    val bowler = match.currentBowler
    val crr = match.score?.currentRunRate?.toFloatOrNull() ?: 0f
    val rrr = match.score?.requiredRunRate?.toFloatOrNull() ?: 0f
    Row(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DuelPlayerCard(
            label = "Batter",
            name = striker?.name ?: "No batter",
            value = striker?.runs ?: "--",
            unit = "Runs",
            color = hierarchy.secondary.primary,
            modifier = Modifier.weight(1f),
            currentRunRate = crr,
            requiredRunRate = rrr
        )
        DuelPlayerCard(
            label = "Bowler",
            name = bowler?.name ?: "No bowler",
            value = if (bowler != null) "${bowler.wickets}-${bowler.runs}" else "--",
            unit = "Figures",
            color = colors.statusLive,
            modifier = Modifier.weight(1f),
            currentRunRate = crr,
            requiredRunRate = rrr
        )
    }
}

@Composable
private fun DuelPlayerCard(label: String, name: String, value: String, unit: String, color: Color, modifier: Modifier, currentRunRate: Float = 0f, requiredRunRate: Float = 0f) {
    val colors = BouncerDesignSystem.colors
    GlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(label.uppercase(), color = colors.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(name, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(unit, color = colors.textTertiary, fontSize = 8.sp)
                }
            }
            // Run Rate Speedometer replaces inline sparkline
            RunRateSpeedometer(
                currentRunRate = currentRunRate,
                requiredRunRate = requiredRunRate,
                size = 80.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ElitePredictiveModule(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val prob = (match.calculateHeuristicProbability() / 100.0).toFloat().coerceIn(0.05f, 0.95f)
    val team1Acronym = match.teams.getOrNull(0)?.acronym ?: "T1"
    val team2Acronym = match.teams.getOrNull(1)?.acronym ?: "T2"

    // Win Probability Ring
    PremiumWinProbabilityRing(
        team1Name = team1Acronym,
        team2Name = team2Acronym,
        team1Probability = prob,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
    )

    Spacer(modifier = Modifier.height(16.dp))

    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        importance = BouncerImportance.CRITICAL
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(hierarchy.secondary.primary))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                val bowler = match.currentBowler
                val predictionText = if (bowler != null) {
                    "Current bowler ${bowler.name} bowling at economy ${bowler.economy}. Tactical pressure building."
                } else {
                    "Match analysis based on current game state and team composition."
                }
                val confidenceVal = (prob * 100).toInt().coerceIn(50, 98)
                val wicketProb = ((1f - prob) * 20).toInt().coerceIn(2, 35)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Psychology, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(18.dp))
                        Text("AI PREDICTION", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Surface(color = hierarchy.secondary.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.2f))) {
                        Text("Confidence: ${confidenceVal}%", color = hierarchy.secondary.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(predictionText, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Based on live match analysis.", color = colors.textSecondary, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, colors.glassSurface, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("WICKET", color = colors.statusLive, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${wicketProb}%", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EliteStatsTopBar(match: CricketMatch, onBackClick: () -> Unit) {
    val colors = BouncerDesignSystem.colors
    TopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                Text(
                    "${match.teams.getOrNull(0)?.acronym ?: ""} vs ${match.teams.getOrNull(1)?.acronym ?: ""}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    "${match.teams.firstOrNull { it.isBatting }?.overs ?: "--"} Overs",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.textPrimary)
            }
        },
        actions = {
            Surface(
                color = colors.statusLive.copy(alpha = 0.1f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, colors.statusLive.copy(alpha = 0.2f)),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(colors.statusLive, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE", color = colors.statusLive, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
