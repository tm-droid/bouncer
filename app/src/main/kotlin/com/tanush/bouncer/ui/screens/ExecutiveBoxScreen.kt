package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.logic.MatchSimulator
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.components.stats.WinProbabilityGauge
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match Analytics Screen
 * Strategic dashboard with "What-If" simulation capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutiveBoxScreen(
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    // Simulation States
    var baseProbability by remember { mutableFloatStateOf(0.72f) }
    var simulatedProbability by remember { mutableFloatStateOf(0.72f) }
    var lastSimResult by remember { mutableStateOf<MatchSimulator.SimResult?>(null) }
    var isSimulating by remember { mutableStateOf(false) }

    val displayProbability by animateFloatAsState(
        targetValue = simulatedProbability,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "magic_prob"
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                        Text("MATCH ANALYTICS", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(if (isSimulating) WarningOrange else colors.statusSuccess, CircleShape).shadow(4.dp, CircleShape, spotColor = if (isSimulating) WarningOrange else colors.statusSuccess))
                            Text(if (isSimulating) "Simulation Active" else "Live Data", color = if (isSimulating) WarningOrange else colors.statusSuccess, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Interactive Probability Gauge - CRITICAL
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WinProbabilityGauge(
                        team1Name = "INDIA",
                        team2Name = "AUSTRALIA",
                        team1Probability = displayProbability,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (isSimulating) {
                        TextButton(onClick = { 
                            simulatedProbability = baseProbability
                            isSimulating = false
                            lastSimResult = null
                        }) {
                            Text("RESET TO LIVE FEED", color = colors.goldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // 2. Tactical Simulation Console - STANDARD
            item {
                TacticalSimulationConsole(
                    onSimulate = { event ->
                        isSimulating = true
                        val result = MatchSimulator.simulateEvent(
                            currentProb = simulatedProbability,
                            event = event,
                            wicketsDown = 4,
                            target = 280,
                            oversRemaining = 12.4f
                        )
                        simulatedProbability = result.newProbability
                        lastSimResult = result
                    }
                )
            }

            // 3. AI Strategic Briefing / Simulation Impact - ADAPTIVE
            item {
                AnimatedContent(targetState = lastSimResult, label = "sim_impact") { result ->
                    if (result != null) {
                        SimulationImpactCard(result)
                    } else {
                        StrategyBriefingCard()
                    }
                }
            }

            // 4. Strategic Event Log - HEADER
            item {
                Text(
                    text = "STRATEGIC EVENT LOG",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }

            // 5. Telemetry Events - MINIMAL
            items(2) { index ->
                TelemetryEventItem(
                    time = "18.4 OV",
                    title = if (index == 0) "MOMENTUM SHIFT" else "BOUNDARY IMPACT",
                    body = if (index == 0) "Win probability increased by 8.2% after Kohli's aggressive sweep." else "Boundary adds pressure to the bowling unit. RRR now below 7.0.",
                    type = if (index == 0) EventType.MOMENTUM else EventType.IMPACT
                )
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
        }
    }
}

@Composable
private fun TacticalSimulationConsole(onSimulate: (MatchSimulator.SimulatedEvent) -> Unit) {
    val colors = BouncerDesignSystem.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "TACTICAL SIMULATION",
            color = colors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SimButton(Modifier.weight(1f), "WICKET", Icons.Default.Gavel, colors.statusLive) { onSimulate(MatchSimulator.SimulatedEvent.WICKET) }
            SimButton(Modifier.weight(1f), "SIX", Icons.Default.FlashOn, colors.goldPrimary) { onSimulate(MatchSimulator.SimulatedEvent.SIX) }
            SimButton(Modifier.weight(1f), "DOT", Icons.Default.Block, colors.textTertiary) { onSimulate(MatchSimulator.SimulatedEvent.DOT) }
        }
    }
}

@Composable
private fun SimButton(modifier: Modifier, label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GlassSurface)
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SimulationImpactCard(result: MatchSimulator.SimResult) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        importance = BouncerImportance.STANDARD,
        cornerRadius = 14.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.QueryStats, null, tint = if (result.impactScore > 0) colors.statusSuccess else colors.statusLive, modifier = Modifier.size(18.dp))
                Text("IMPACT ANALYSIS", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = result.summary,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Simulation reflects historical data patterns for high-pressure chases.",
                color = colors.textTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StrategyBriefingCard() {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        importance = BouncerImportance.CRITICAL,
        cornerRadius = 14.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = colors.goldPrimary, modifier = Modifier.size(18.dp))
                    Text("STRATEGY BRIEFING", color = colors.goldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(colors.statusSuccess.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("OPTIMIZED", color = colors.statusSuccess, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "India should target the next 2 overs of spin. Australia's field suggested a defensive mindset. High boundary probability square of the wicket.",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )
        }
    }
}

enum class EventType { MOMENTUM, IMPACT }

@Composable
private fun TelemetryEventItem(time: String, title: String, body: String, type: EventType) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .height(IntrinsicSize.Min), // FIX OVERLAP: Ensure row matches height of its content
            verticalAlignment = Alignment.Top, 
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(), // FIX OVERLAP: Allow column to span full height
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(time, color = colors.goldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .weight(1f) // FIX OVERLAP: Dynamic height divider
                        .padding(vertical = 4.dp)
                        .background(GlassBorder)
                )
            }
            
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    title, 
                    color = if (type == EventType.MOMENTUM) ChartBlue else colors.statusSuccess, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Black, 
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    body, 
                    color = colors.textSecondary, 
                    fontSize = 12.sp, 
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun ResourceCard(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    val colors = BouncerDesignSystem.colors
    GlassSurface(
        modifier = modifier,
        cornerRadius = 10.dp,
        importance = BouncerImportance.STANDARD
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = colors.goldPrimary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Column {
                Text(label, color = colors.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(value, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
