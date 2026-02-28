package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.tanush.bouncer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteTierAiStatsScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = BlackBackground,
        topBar = { EliteStatsTopBar(match, onBackClick) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Ring System
        Canvas(modifier = Modifier.size(200.dp)) {
            drawCircle(
                color = ShinyGoldDeep.copy(alpha = 0.1f),
                radius = size.minDimension / 2,
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = ShinyGoldDeep.copy(alpha = 0.3f),
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
                color = ShinyGoldDeep.copy(alpha = 0.6f), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                letterSpacing = 2.sp
            )
            Text(
                "94.2", 
                color = Color.White, 
                fontSize = 64.sp, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.shadow(20.dp, spotColor = ShinyGoldDeep.copy(alpha = 0.3f))
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.TrendingUp, null, tint = ShinyGoldDeep, modifier = Modifier.size(14.dp))
                Text("+15.4 vs Avg", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun EliteInsightPill(match: CricketMatch) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, ShinyGoldDeep.copy(alpha = 0.2f), Color.Transparent)))
            .padding(1.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null, tint = ShinyGoldDeep, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Kohli's impact is highest in the last 10 overs.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ValueAddedChartSection() {
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Value Added", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Run contribution per over", color = TextSecondary, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("+42", color = ShinyGoldDeep, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("RUNS", color = ShinyGoldDeep.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = size.width / 7
                val heights = listOf(0.35f, 0.2f, 0.85f, 0.55f, 0.15f)
                
                // Grid lines
                for (i in 0..4) {
                    val y = size.height * (i / 4f)
                    drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y))
                }

                heights.forEachIndexed { index, h ->
                    val x = index * barWidth + (barWidth * 0.2f)
                    drawRect(
                        brush = Brush.verticalGradient(listOf(ShinyGoldDeep, Color.Transparent)),
                        topLeft = Offset(x, size.height * (1 - h)),
                        size = Size(barWidth * 0.6f, size.height * h)
                    )
                }
                
                // Projected Bar
                val projX = 5 * barWidth + (barWidth * 0.2f)
                drawRect(
                    color = Color.White.copy(alpha = 0.1f),
                    topLeft = Offset(projX, size.height * 0.4f),
                    size = Size(barWidth * 0.6f, size.height * 0.6f)
                )
            }
        }
    }
}

@Composable
private fun EliteDuelMetricsRow(match: CricketMatch) {
    Row(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DuelPlayerCard(
            label = "Batter",
            name = "V. Kohli",
            value = "82",
            unit = "Runs",
            color = ShinyGoldDeep,
            modifier = Modifier.weight(1f)
        )
        DuelPlayerCard(
            label = "Bowler",
            name = "P. Cummins",
            value = "2-45",
            unit = "Figures",
            color = ErrorRed,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DuelPlayerCard(label: String, name: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(label.uppercase(), color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(unit, color = TextTertiary, fontSize = 8.sp)
                }
            }
            // Micro Sparkline Bar
            Row(modifier = Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(0.2f, 0.4f, 1f, 0.3f, 0.5f, 0.2f).forEach { h ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(h).background(color.copy(alpha = 0.4f + h * 0.6f)))
                }
            }
        }
    }
}

@Composable
private fun ElitePredictiveModule() {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(ShinyGoldDeep))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Psychology, null, tint = ShinyGoldDeep, modifier = Modifier.size(18.dp))
                    Text("AI PREDICTION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Surface(color = ShinyGoldDeep.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, ShinyGoldDeep.copy(alpha = 0.2f))) {
                    Text("Confidence: 89%", color = ShinyGoldDeep, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("High probability of a short ball strategy targeting the body.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Based on field placement shift to leg side.", color = TextSecondary, fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WICKET", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("12%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EliteStatsTopBar(match: CricketMatch, onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                Text(
                    "${match.teams.getOrNull(0)?.acronym ?: ""} vs ${match.teams.getOrNull(1)?.acronym ?: ""}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "42.3 Overs",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        },
        actions = {
            Surface(
                color = ErrorRed.copy(alpha = 0.1f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, ErrorRed.copy(alpha = 0.2f)),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(ErrorRed, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE", color = ErrorRed, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
