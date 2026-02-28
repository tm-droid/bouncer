package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Economy tracker card showing bowler's economy progression
 */

data class EconomyTrackerInfo(
    val bowlerName: String,
    val currentEconomy: Float,
    val overs: Float,
    val runs: Int,
    val wickets: Int,
    val economyByOver: List<Float>, // Economy rate after each over
    val averageEconomy: Float = 0f,
    val benchmarkEconomy: Float = 6f, // Target economy to beat
    val phasesEconomy: EconomyPhases? = null
)

data class EconomyPhases(
    val powerplayEconomy: Float,
    val middleEconomy: Float,
    val deathEconomy: Float
)

@Composable
fun EconomyTrackerCard(
    economyInfo: EconomyTrackerInfo,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "economy_animation")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val pathProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "path_progress"
    )
    
    val economyColor = when {
        economyInfo.currentEconomy < 5f -> SuccessGreen
        economyInfo.currentEconomy < 7f -> WarningYellow
        economyInfo.currentEconomy < 9f -> Color(0xFFFF8E3C)
        else -> WicketRed
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = economyColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Economy Tracker",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = economyInfo.bowlerName,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Current figures
                Surface(
                    color = economyColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${economyInfo.wickets}/${economyInfo.runs}",
                        color = economyColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Current economy display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Economy gauge
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10f
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)
                        
                        // Background arc
                        drawArc(
                            color = Color.White.copy(alpha = 0.1f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        
                        // Economy arc (scaled 0-12 economy)
                        val economyProgress = (economyInfo.currentEconomy / 12f).coerceIn(0f, 1f)
                        val sweepAngle = 270f * economyProgress
                        
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    SuccessGreen,
                                    WarningYellow,
                                    WicketRed
                                ),
                                center = center
                            ),
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        
                        // Benchmark marker
                        val benchmarkAngle = (135f + 270f * (economyInfo.benchmarkEconomy / 12f)) * (PI / 180f).toFloat()
                        val markerRadius = radius - 15f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            radius = 4f,
                            center = Offset(
                                center.x + markerRadius * cos(benchmarkAngle),
                                center.y + markerRadius * sin(benchmarkAngle)
                            )
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.2f", economyInfo.currentEconomy),
                            color = economyColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ECON",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // Quick stats
                Column {
                    QuickStatRow(label = "Overs", value = String.format("%.1f", economyInfo.overs))
                    Spacer(modifier = Modifier.height(8.dp))
                    QuickStatRow(label = "Target", value = "${economyInfo.benchmarkEconomy}")
                    Spacer(modifier = Modifier.height(8.dp))
                    QuickStatRow(
                        label = "Status", 
                        value = if (economyInfo.currentEconomy <= economyInfo.benchmarkEconomy) "✓" else "✗",
                        valueColor = if (economyInfo.currentEconomy <= economyInfo.benchmarkEconomy) SuccessGreen else WicketRed
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Economy progression chart
            if (economyInfo.economyByOver.isNotEmpty()) {
                Text(
                    text = "ECONOMY PROGRESSION",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val graphWidth = size.width
                        val graphHeight = size.height
                        val padding = 10f
                        
                        val maxEconomy = maxOf(economyInfo.economyByOver.maxOrNull() ?: 10f, 12f)
                        val pointCount = economyInfo.economyByOver.size
                        
                        if (pointCount > 0) {
                            // Benchmark line
                            val benchmarkY = graphHeight - padding - 
                                (graphHeight - 2 * padding) * (economyInfo.benchmarkEconomy / maxEconomy)
                            
                            drawLine(
                                color = Color.White.copy(alpha = 0.3f),
                                start = Offset(padding, benchmarkY),
                                end = Offset(graphWidth - padding, benchmarkY),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                            )
                            
                            // Draw economy line
                            val path = Path()
                            val pointsToShow = (pointCount * pathProgress).toInt().coerceAtLeast(1)
                            
                            economyInfo.economyByOver.take(pointsToShow).forEachIndexed { index, economy ->
                                val x = padding + (graphWidth - 2 * padding) * index / (pointCount - 1).coerceAtLeast(1)
                                val y = graphHeight - padding - (graphHeight - 2 * padding) * (economy / maxEconomy)
                                
                                if (index == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            
                            drawPath(
                                path = path,
                                color = economyColor,
                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            
                            // Draw points
                            economyInfo.economyByOver.take(pointsToShow).forEachIndexed { index, economy ->
                                val x = padding + (graphWidth - 2 * padding) * index / (pointCount - 1).coerceAtLeast(1)
                                val y = graphHeight - padding - (graphHeight - 2 * padding) * (economy / maxEconomy)
                                
                                val pointColor = when {
                                    economy < 5f -> SuccessGreen
                                    economy < 7f -> WarningYellow
                                    economy < 9f -> Color(0xFFFF8E3C)
                                    else -> WicketRed
                                }
                                
                                drawCircle(
                                    color = pointColor,
                                    radius = 5f,
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                }
                
                // Over labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "1", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    Text(
                        text = "${economyInfo.economyByOver.size}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp
                    )
                }
            }
            
            // Phase breakdown
            if (economyInfo.phasesEconomy != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "PHASE ECONOMY",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PhaseEconomyBox(
                        phase = "PP",
                        overs = "1-6",
                        economy = economyInfo.phasesEconomy.powerplayEconomy
                    )
                    PhaseEconomyBox(
                        phase = "Middle",
                        overs = "7-15",
                        economy = economyInfo.phasesEconomy.middleEconomy
                    )
                    PhaseEconomyBox(
                        phase = "Death",
                        overs = "16-20",
                        economy = economyInfo.phasesEconomy.deathEconomy
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStatRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PhaseEconomyBox(
    phase: String,
    overs: String,
    economy: Float
) {
    val phaseColor = when {
        economy < 5f -> SuccessGreen
        economy < 7f -> WarningYellow
        economy < 9f -> Color(0xFFFF8E3C)
        else -> WicketRed
    }
    
    Surface(
        color = phaseColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = phase,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "($overs)",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%.2f", economy),
                color = phaseColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
