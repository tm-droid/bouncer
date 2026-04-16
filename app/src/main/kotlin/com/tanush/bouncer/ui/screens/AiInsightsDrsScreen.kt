package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.AiAnalysisCard
import com.tanush.bouncer.ui.components.AnalysisType
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.theme.*
import androidx.compose.ui.platform.LocalContext

/**
 * Premium AI Insights & DRS Review Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsDrsScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = BouncerDesignSystem.colors
    val context = LocalContext.current
    
    Scaffold(
        containerColor = colors.backgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SportsCricket, null, tint = colors.goldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BOUNCER", color = colors.goldPrimary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { Toast.makeText(context, "Notifications coming soon", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Notifications, null, tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // AI Neural Banner
            AiNeuralBanner(match)

            // DRS Visualization Card
            DrsVisualizationCard(match)

            // Verdict Module — no DRS event data available on model yet; show awaiting state
            VerdictModule(isOut = false, hasReview = false)

            // Delivery Metrics
            DeliveryMetricsSection(match)

            // Pitch Map Mini Card
            PitchMapMiniCard(match)

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Bottom Action Bar
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.backgroundBase.copy(alpha = 0.9f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { Toast.makeText(context, "Full analysis coming soon", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.glassSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.glassSurface)
            ) {
                Text("FULL ANALYSIS", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Button(
                onClick = { Toast.makeText(context, "DRS review requested", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.weight(2f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.goldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Gavel, null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("REQUEST REVIEW", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AiNeuralBanner(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors

    // Use AiAnalysisCard for the banner
    val bowler = match.currentBowler
    val striker = match.scrapedBatsmen?.firstOrNull { it.isStriker }
    val bowlerEcon = bowler?.economy?.toDoubleOrNull()
    val strikerSR = striker?.strikeRate?.toDoubleOrNull()
    val analysisDesc = when {
        bowlerEcon != null && bowlerEcon < 6.0 -> "${bowler.name} bowling tight (econ ${bowler.economy}). Pressure building on batting side."
        strikerSR != null && strikerSR > 150.0 -> "${striker?.name} striking at ${striker?.strikeRate}. High boundary probability on current trajectory."
        bowler != null -> "${bowler.name} has bowled ${bowler.overs} overs for ${bowler.wickets} wickets."
        else -> "Awaiting delivery data for analysis."
    }
    AiAnalysisCard(
        title = "Match Critical Analysis",
        description = analysisDesc,
        highlightText = "AI ANALYSIS",
        type = AnalysisType.WARNING,
        modifier = Modifier.padding(0.dp)
    )
}

@Composable
private fun DrsVisualizationCard(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        importance = BouncerImportance.CRITICAL
    ) {
        Box(modifier = Modifier.height(260.dp).fillMaxWidth()) {
            // Placeholder trajectory path — replace with ball tracking telemetry when available
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height * 0.8f)
                    quadraticTo(size.width * 0.5f, size.height * 0.4f, size.width, size.height * 0.5f)
                }
                drawPath(
                    path = path,
                    color = colors.statusInfo,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }

            // Overlay UI
            Surface(
                modifier = Modifier.padding(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, colors.statusInfo.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(colors.statusInfo, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REVIEW IN PROGRESS", color = colors.statusInfo, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // DRS metrics — no live ball tracking data available yet; show awaiting state
                DrsMetricsRow("PITCHING", "AWAITING", colors.textMuted)
                DrsMetricsRow("IMPACT", "AWAITING", colors.textMuted)
                DrsMetricsRow("WICKETS", "AWAITING", colors.textMuted)
            }
        }
        
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("DRS Visualization", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            // Summary — no DRS ball tracking integration yet
            Text("Awaiting DRS review data. Ball trajectory will be visualized when a review is triggered.", color = colors.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DrsMetricsRow(label: String, value: String, valueColor: Color) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VerdictModule(isOut: Boolean, hasReview: Boolean = true) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (!hasReview) "NO REVIEW" else if (isOut) "OUT" else "NOT OUT",
            color = if (hasReview) colors.goldPrimary else colors.textMuted,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.graphicsLayer {
                shadowElevation = 20f
            }
        )
        Text(
            text = if (!hasReview) "AWAITING DRS REVIEW" else "UMPIRE'S DECISION OVERTURNED",
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun DeliveryMetricsSection(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DELIVERY METRICS", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Icon(Icons.Default.BarChart, null, tint = colors.textMuted)
        }
        
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Derive metrics from bowler data when available
            val bowlerData = match.currentBowler
            val speedValue = bowlerData?.let { "N/A" } ?: "N/A"
            item { MetricCard(Icons.Default.Speed, "Impact Speed", speedValue, "kph") }
            item { MetricCard(Icons.AutoMirrored.Filled.RotateRight, "Turn Degree", "--", "") }
            val prob = match.calculateHeuristicProbability()
            item { MetricCard(Icons.Default.Psychology, "Confidence", "${prob.toInt()}%", "") }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, label: String, value: String, unit: String) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.width(140.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(32.dp).background(colors.glassSurface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.goldPrimary, modifier = Modifier.size(18.dp))
            }
            Text(label, color = colors.textSecondary, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, color = colors.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PitchMapMiniCard(match: CricketMatch) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).background(colors.glassSurface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Map, null, tint = colors.textMuted)
                }
                Column {
                    Text("Pitch Map", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    // Derive pitch classification from bowler overs/type
                    val bowler = match.currentBowler
                    val pitchLabel = when {
                        bowler == null -> "Awaiting Data"
                        bowler.economy?.toDoubleOrNull()?.let { it < 5.0 } == true -> "Tight Line"
                        bowler.economy?.toDoubleOrNull()?.let { it > 8.0 } == true -> "Full & Wide"
                        else -> "Good Length"
                    }
                    Text(pitchLabel, color = colors.goldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.textMuted)
        }
    }
}
