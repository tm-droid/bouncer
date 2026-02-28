package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewScreen(onBackClick: () -> Unit) {
    Scaffold(
        containerColor = BlackBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("ANALYTICS HUB", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = GoldDark)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // Career Radar Chart (Elite Analyst)
            Box(modifier = Modifier.staggeredFadeIn(0)) {
                EliteRadarAnalytics()
            }

            // Performance Leaderboard Rail
            Box(modifier = Modifier.staggeredFadeIn(1)) {
                PerformanceRail()
            }

            // Historical Trends Grid
            Box(modifier = Modifier.staggeredFadeIn(2)) {
                HistoricalTrendsGrid()
            }
        }
    }
}

@Composable
private fun EliteRadarAnalytics() {
    Card(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, GoldHairline)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CAREER PERFORMANCE RADAR", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(32.dp))
            
            CareerRadarChart(
                metrics = listOf(
                    "Batting" to 0.85f,
                    "Bowling" to 0.72f,
                    "Fielding" to 0.94f,
                    "Strategy" to 0.68f,
                    "Pace" to 0.78f
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AnalyticsBadge("Power", "Elite")
                AnalyticsBadge("Timing", "Pro")
                AnalyticsBadge("Spin", "A-Grade")
            }
        }
    }
}

@Composable
fun CareerRadarChart(metrics: List<Pair<String, Float>>) {
    Canvas(modifier = Modifier.size(220.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.width / 2
        val sides = metrics.size
        val angleStep = (2 * PI / sides).toFloat()

        // Draw Radar Web
        for (i in 1..4) {
            val scale = i / 4f
            val path = Path()
            for (j in 0 until sides) {
                val angle = j * angleStep - (PI / 2).toFloat()
                val x = centerX + cos(angle) * radius * scale
                val y = centerY + sin(angle) * radius * scale
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = 0.05f), style = Stroke(width = 1.dp.toPx()))
        }

        // Draw Axis
        for (j in 0 until sides) {
            val angle = j * angleStep - (PI / 2).toFloat()
            drawLine(
                Color.White.copy(alpha = 0.05f),
                Offset(centerX, centerY),
                Offset(centerX + cos(angle) * radius, centerY + sin(angle) * radius)
            )
        }

        // Draw Performance Area
        val perfPath = Path()
        metrics.forEachIndexed { j, metric ->
            val angle = j * angleStep - (PI / 2).toFloat()
            val x = centerX + cos(angle) * radius * metric.second
            val y = centerY + sin(angle) * radius * metric.second
            if (j == 0) perfPath.moveTo(x, y) else perfPath.lineTo(x, y)
        }
        perfPath.close()
        
        drawPath(
            perfPath,
            brush = Brush.radialGradient(listOf(GoldDark.copy(alpha = 0.4f), Color.Transparent)),
        )
        drawPath(
            perfPath,
            color = GoldDark,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun AnalyticsBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PerformanceRail() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("TOP ANALYSTS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 24.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(listOf("Virat Kohli", "Steve Smith", "Kane Williamson")) { player ->
                PlayerCard(player)
            }
        }
    }
}

@Composable
private fun PlayerCard(name: String) {
    Surface(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, GlassBorderLight)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)))
            Spacer(modifier = Modifier.height(12.dp))
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("94.2", color = GoldDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HistoricalTrendsGrid() {
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("MARKET TRENDS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendCard(modifier = Modifier.weight(1f), title = "Avg Score", value = "168.4", trend = "+4.2%")
            TrendCard(modifier = Modifier.weight(1f), title = "Wicket Rate", value = "7.2", trend = "-1.1%")
        }
    }
}

@Composable
private fun TrendCard(modifier: Modifier, title: String, value: String, trend: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, GlassBorderLight)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(trend, color = if (trend.contains("+")) SuccessGreen else ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
