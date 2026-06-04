package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.BallPrediction
import com.tanush.bouncer.ui.components.PredictiveBallCard
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * MCP Predictive Forecast Screen - Elite "Masterpiece" Implementation.
 * Matches Mockup Specs: Conic rings, critical alerts, and high-density telemetry.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictiveForecastScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val scrollState = rememberScrollState()
    
    // Derived from mockup data
    val battingTeam = match.teams.firstOrNull { it.isBatting } ?: match.teams.firstOrNull()
    val currentOverStr = battingTeam?.overs ?: "14.0"
    val currentOver = currentOverStr.toDoubleOrNull()?.toInt() ?: 14
    
    val predictions = remember(currentOver, match.scrapedBatsmen, match.currentBowler) {
        val prob = match.calculateHeuristicProbability()
        // Raw probabilities for each ball outcome
        val rawDot = (50 + (100 - prob) * 0.3).coerceIn(20.0, 85.0)
        val rawWicket = ((100 - prob) * 0.4).coerceIn(5.0, 40.0)
        val rawBoundary = (prob * 0.5).coerceIn(10.0, 70.0)
        val rawSingle = (100 - rawDot).coerceIn(15.0, 75.0)
        val rawDouble = (rawDot * 0.7).coerceIn(15.0, 65.0)
        // Normalize so all sum to 100%
        val rawTotal = rawDot + rawSingle + rawWicket + rawDouble + rawDot + rawBoundary
        fun norm(v: Double) = (v / rawTotal * 100).toInt().coerceAtLeast(1)
        listOf(
            BallPrediction("${currentOver + 1}.1", norm(rawDot), "Dot Ball"),
            BallPrediction("${currentOver + 1}.2", norm(rawSingle), "1 Run"),
            BallPrediction("${currentOver + 1}.3", norm(rawWicket), "Wicket", isCritical = rawWicket > 25),
            BallPrediction("${currentOver + 1}.4", norm(rawDouble), "2 Runs"),
            BallPrediction("${currentOver + 1}.5", norm(rawDot), "Dot"),
            BallPrediction("${currentOver + 1}.6", norm(rawBoundary), "Boundary")
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "MCP LIVE ENGINE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = colors.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val pulse = rememberInfiniteTransition(label = "conn_pulse")
                            val alpha by pulse.animateFloat(
                                initialValue = 1f,
                                targetValue = 0.3f,
                                animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                                label = "alpha"
                            )
                            Box(modifier = Modifier.size(6.dp).alpha(alpha).background(hierarchy.secondary.primary, CircleShape).shadow(4.dp, spotColor = hierarchy.secondary.primary))
                            Text(
                                "LIVE CONNECTION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = hierarchy.secondary.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, "Settings", tint = colors.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Depth Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.surface1.copy(alpha = 0.2f), colors.backgroundBase)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Match Score Card (Elite Glass)
                EliteForecastHero(match)

                // Next Over Forecast Section
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Next Over (${currentOver + 1}th) Forecast",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        TextButton(onClick = {}) {
                            Text("View History", color = hierarchy.secondary.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ChevronRight, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(predictions, key = { it.ballNumber }) { prediction ->
                            PredictiveBallCard(prediction)
                        }
                    }
                }

                // Global Confidence & Pitch Stats
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EliteEngineConfidenceCard(
                        modifier = Modifier.weight(1f),
                        confidence = 0.84f,
                        trend = "+2.1%"
                    )
                    ElitePitchAnalysisCard(modifier = Modifier.weight(1f))
                }

                // Map Visualization
                EliteActiveFieldMap()

                // Legal Footer
                Text(
                    text = "Predictions are for informational and entertainment purposes only. Bouncer does not facilitate gambling or betting. Calculations based on historical data and real-time telemetry.",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                
                Spacer(modifier = Modifier.height(120.dp))
            }

            // Fixed Action Bar (Mockup Spec)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.backgroundBase.copy(alpha = 0.9f), colors.backgroundBase)
                        )
                    )
                    .padding(24.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(20.dp, spotColor = hierarchy.secondary.primary.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.goldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Analytics, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("UNLOCK FULL ANALYSIS", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                }
            }
        }
        }
    }
}

@Composable
private fun EliteForecastHero(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val battingTeam = match.teams.firstOrNull { it.isBatting } ?: match.teams.firstOrNull()
    val striker = match.scrapedBatsmen?.find { it.isStriker } ?: match.scrapedBatsmen?.firstOrNull()
    val bowler = match.currentBowler

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("LIVE ${match.matchType.uppercase()} MATCH", color = colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(
                        text = "${match.teams.getOrNull(0)?.acronym ?: "IND"} vs ${match.teams.getOrNull(1)?.acronym ?: "AUS"}",
                        color = colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(battingTeam?.score ?: "--", color = colors.goldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("${battingTeam?.overs ?: "--"} OVERS", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            val totalOvers = when {
                match.matchType.contains("T20", ignoreCase = true) -> 20f
                match.matchType.contains("ODI", ignoreCase = true) -> 50f
                else -> 20f
            }
            val currentOversFloat = (battingTeam?.overs ?: "0").toFloatOrNull() ?: 0f
            val matchProgress = (currentOversFloat / totalOvers).coerceIn(0.01f, 1f)
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(GlassSurface)) {
                Box(modifier = Modifier.fillMaxWidth(matchProgress).fillMaxHeight().background(hierarchy.secondary.primary))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ON STRIKE", color = colors.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(striker?.name ?: "No striker", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${striker?.runs ?: "--"}*", color = colors.goldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Text("SR: ${striker?.strikeRate ?: "--"}", color = colors.textSecondary, fontSize = 10.sp)
                }
                Box(modifier = Modifier.width(1.dp).height(32.dp).background(GlassSurface).align(Alignment.CenterVertically))
                Column(modifier = Modifier.weight(1f)) {
                    Text("BOWLER", color = colors.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bowler?.name ?: "No bowler", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${bowler?.wickets ?: "-"}/${bowler?.runs ?: "-"}", color = colors.goldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Text("ECON: ${bowler?.economy ?: "--"}", color = colors.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun EliteEngineConfidenceCard(modifier: Modifier, confidence: Float, trend: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ENGINE\nCONFIDENCE", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Icon(Icons.Default.Psychology, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(20.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${(confidence * 100).toInt()}%", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(trend, color = colors.statusSuccess, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(GlassSurface)) {
                    Box(modifier = Modifier.fillMaxWidth(confidence).fillMaxHeight().background(hierarchy.secondary.primary))
                }
            }
        }
    }
}

@Composable
private fun ElitePitchAnalysisCard(modifier: Modifier) {
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PITCH\nANALYSIS", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Icon(Icons.Default.Landscape, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PitchTelemetryItem("Humidity", "65%", colors.textPrimary)
                PitchTelemetryItem("Fatigue", "Low", colors.statusSuccess)
                PitchTelemetryItem("Dew", "High", colors.statusLive)
            }
        }
    }
}

@Composable
private fun PitchTelemetryItem(label: String, value: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun EliteActiveFieldMap() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassSurface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = hierarchy.secondary.primary.copy(alpha = 0.03f), radius = size.maxDimension / 2, center = Offset(size.width, size.height / 2))
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ACTIVE FIELD SETUP", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Powerplay Off side focused", color = colors.textSecondary, fontSize = 12.sp)
                }
                Surface(
                    color = GlassSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    onClick = {}
                ) {
                    Text("VIEW MAP", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}
