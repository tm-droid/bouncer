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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteTierAiStatsScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
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
        AiImpactHero(rotation = rotation)

        // Momentum Indicator
        MomentumIndicator(
            momentum = 0.6f,
            team1Name = match.teams.getOrNull(0)?.name ?: "Team 1",
            team2Name = match.teams.getOrNull(1)?.name ?: "Team 2",
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Insight Pill
        EliteInsightPill(match)

        // Value Added Section
        ValueAddedChartSection()

        // Duel Metrics Row
        EliteDuelMetricsRow(match)

        // Predictive Insight Module
        ElitePredictiveModule()
    }
}

@Composable
private fun AiImpactHero(rotation: Float) {
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
                color = colors.goldPrimary.copy(alpha = 0.1f),
                radius = size.minDimension / 2,
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = colors.goldPrimary.copy(alpha = 0.3f),
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
                color = colors.goldPrimary.copy(alpha = 0.6f), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                letterSpacing = 2.sp
            )
            Text(
                "94.2", 
                color = colors.textPrimary, 
                fontSize = 64.sp, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.shadow(20.dp, spotColor = colors.goldPrimary.copy(alpha = 0.3f))
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = colors.goldPrimary, modifier = Modifier.size(14.dp))
                Text("+15.4 vs Avg", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun EliteInsightPill(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, colors.goldPrimary.copy(alpha = 0.2f), Color.Transparent)))
            .padding(1.dp)
            .clip(CircleShape)
            .background(colors.glassSurface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null, tint = colors.goldPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Kohli's impact is highest in the last 10 overs.",
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ValueAddedChartSection() {
    val colors = BouncerDesignSystem.colors
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Value Added", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Run contribution per over", color = colors.textSecondary, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("+42", color = colors.goldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("RUNS", color = colors.goldPrimary.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            BarChart(
                data = listOf(
                    "Ov1" to 0.35f,
                    "Ov2" to 0.2f,
                    "Ov3" to 0.85f,
                    "Ov4" to 0.55f,
                    "Ov5" to 0.15f
                ),
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
    val colors = BouncerDesignSystem.colors
    Row(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DuelPlayerCard(
            label = "Batter",
            name = "V. Kohli",
            value = "82",
            unit = "Runs",
            color = colors.goldPrimary,
            modifier = Modifier.weight(1f)
        )
        DuelPlayerCard(
            label = "Bowler",
            name = "P. Cummins",
            value = "2-45",
            unit = "Figures",
            color = colors.statusLive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DuelPlayerCard(label: String, name: String, value: String, unit: String, color: Color, modifier: Modifier) {
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
                currentRunRate = 7.2f,
                requiredRunRate = 8.5f,
                size = 80.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ElitePredictiveModule() {
    val colors = BouncerDesignSystem.colors

    // Win Probability Ring
    PremiumWinProbabilityRing(
        team1Name = "IND",
        team2Name = "AUS",
        team1Probability = 0.72f,
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
            Box(modifier = Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(colors.goldPrimary))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Psychology, null, tint = colors.goldPrimary, modifier = Modifier.size(18.dp))
                        Text("AI PREDICTION", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Surface(color = colors.goldPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, colors.goldPrimary.copy(alpha = 0.2f))) {
                        Text("Confidence: 89%", color = colors.goldPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("High probability of a short ball strategy targeting the body.", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Based on field placement shift to leg side.", color = colors.textSecondary, fontSize = 10.sp)
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
                            Text("12%", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                    "42.3 Overs",
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
