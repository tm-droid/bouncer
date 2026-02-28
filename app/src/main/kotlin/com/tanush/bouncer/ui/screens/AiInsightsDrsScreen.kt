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
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*

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
    
    Scaffold(
        containerColor = Color(0xFF141208), // Dark charcoal-olive base from mockup
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SportsCricket, null, tint = ShinyGoldDeep)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BOUNCER", color = ShinyGoldDeep, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, null, tint = Color.White)
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

            // Verdict Module
            // TODO: Get real DRS decision outcome from match event stream when available in models
            VerdictModule(isOut = true)

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
                .background(Color(0xFF0F0F0F).copy(alpha = 0.9f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {},
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text("FULL ANALYSIS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Button(
                onClick = {},
                modifier = Modifier.weight(2f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShinyGoldDeep),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        // Abstract neural background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ShinyGoldDeep.copy(alpha = 0.15f), Color.Transparent)
                ),
                radius = size.maxDimension / 1.5f,
                center = Offset(size.width * 0.8f, size.height * 0.2f)
            )
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = ShinyGoldDeep, modifier = Modifier.size(16.dp))
                Text("AI ANALYSIS", color = ShinyGoldDeep.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Text("Match Critical Analysis", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            
            // TODO: Generate this insight dynamically based on real-time match state/telemetry from match data
            Text(
                text = "Spin rate on last delivery increased by 15%. High probability of LBW on current trajectory.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        // Bottom glow line
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(ShinyGoldDeep, Color.Transparent)))
        )
    }
}

@Composable
private fun DrsVisualizationCard(match: CricketMatch) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1A1912))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
    ) {
        Box(modifier = Modifier.height(260.dp).fillMaxWidth()) {
            // TODO: Implement actual 3D rendering or high-fidelity path drawing based on ball tracking data from telemetry
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height * 0.8f)
                    quadraticTo(size.width * 0.5f, size.height * 0.4f, size.width, size.height * 0.5f)
                }
                drawPath(
                    path = path,
                    color = Color(0xFF00F3FF),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }

            // Overlay UI
            Surface(
                modifier = Modifier.padding(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF00F3FF).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF00F3FF), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REVIEW IN PROGRESS", color = Color(0xFF00F3FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // TODO: Link these to actual ball tracking results from match stream telemetry
                DrsMetricsRow("PITCHING", "IN LINE", Color(0xFF00F3FF))
                DrsMetricsRow("IMPACT", "IN LINE", Color(0xFF00F3FF))
                DrsMetricsRow("WICKETS", "HITTING", ShinyGoldDeep)
            }
        }
        
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("DRS Visualization", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            // TODO: Generate descriptive summary from real trajectory analysis from telemetry
            Text("Projected path indicates ball hitting top of leg stump.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun DrsMetricsRow(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VerdictModule(isOut: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isOut) "OUT" else "NOT OUT",
            color = ShinyGoldDeep,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.graphicsLayer {
                shadowElevation = 20f
            }
        )
        Text(
            text = "UMPIRE'S DECISION OVERTURNED",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun DeliveryMetricsSection(match: CricketMatch) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DELIVERY METRICS", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Icon(Icons.Default.BarChart, null, tint = Color.White.copy(alpha = 0.3f))
        }
        
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // TODO: Replace with real per-ball telemetry data (Impact speed, turn, engine confidence) from match telemetry
            item { MetricCard(Icons.Default.Speed, "Impact Speed", "142", "kph") }
            item { MetricCard(Icons.Default.RotateRight, "Turn Degree", "2.1°", "") }
            item { MetricCard(Icons.Default.Psychology, "Confidence", "98%", "") }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, label: String, value: String, unit: String) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ShinyGoldDeep, modifier = Modifier.size(18.dp))
            }
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PitchMapMiniCard(match: CricketMatch) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Map, null, tint = Color.White.copy(alpha = 0.4f))
            }
            Column {
                Text("Pitch Map", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                // TODO: Map ball impact coordinates to pitch length classifications (e.g., Good Length, Full, Short) from telemetry
                Text("Good Length", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
    }
}
