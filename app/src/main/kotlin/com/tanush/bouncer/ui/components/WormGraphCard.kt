package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import io.sentry.Sentry

/**
 * Innings progress data point
 */
data class InningsDataPoint(
    val over: Float,
    val cumulativeRuns: Int,
    val wicketsDown: Int = 0,
    val isWicket: Boolean = false,
    val isBoundary: Boolean = false
)

/**
 * Team innings data for worm graph
 */
data class TeamInnings(
    val teamName: String,
    val teamColor: Color,
    val dataPoints: List<InningsDataPoint>
)

/**
 * MEGA UI UPGRADE: WormGraphCard (v2.0).
 * High-fidelity scoring progression with technical telemetry aesthetics and cinematic animations.
 */
@Composable
fun WormGraphCard(
    innings1: TeamInnings,
    innings2: TeamInnings? = null,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    var selectedPoint by remember { mutableStateOf<Pair<Int, InningsDataPoint>?>(null) }
    val textMeasurer = rememberTextMeasurer()
    
    val allPoints = innings1.dataPoints + (innings2?.dataPoints ?: emptyList())
    val maxRuns = allPoints.maxOfOrNull { it.cumulativeRuns }?.coerceAtLeast(50) ?: 100
    val maxOvers = allPoints.maxOfOrNull { it.over }?.coerceAtLeast(1f) ?: 20f
    
    val graphProgress = remember { Animatable(0f) }
    LaunchedEffect(innings1, innings2) {
        graphProgress.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(GlassBackground)
            .border(
                0.5.dp,
                Brush.linearGradient(listOf(GoldHairline, GlassSurface)),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Header: Technical ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SCORING TRAJECTORY",
                        color = hierarchy.secondary.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "INNINGS PROGRESSION MATRIX",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TechWormLegend(innings1.teamName, innings1.teamColor)
                    innings2?.let { TechWormLegend(it.teamName, it.teamColor) }
                }
            }
            
            // Interaction Display
            AnimatedContent(
                targetState = selectedPoint,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "selectedPoint"
            ) { point ->
                if (point != null) {
                    val team = if (point.first == 0) innings1 else innings2 ?: return@AnimatedContent
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(team.teamColor.copy(alpha = 0.05f))
                            .border(0.5.dp, team.teamColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(team.teamName.uppercase(), color = team.teamColor, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text("OVER ${String.format("%.1f", point.second.over)}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${point.second.cumulativeRuns}/${point.second.wicketsDown}", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("RUNS/WICKETS", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.height(52.dp)) // Placeholder
                }
            }
            
            // Graph Matrix
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassDark)
                    .pointerInput(innings1, innings2) {
                        detectTapGestures { offset ->
                            val xPos = (offset.x / size.width) * maxOvers
                            val closest1 = innings1.dataPoints.minByOrNull { Math.abs(it.over - xPos) }
                            val closest2 = innings2?.dataPoints?.minByOrNull { Math.abs(it.over - xPos) }
                            
                            selectedPoint = when {
                                closest2 != null && Math.abs(closest2.over - xPos) < Math.abs((closest1?.over ?: 100f) - xPos) -> 1 to closest2
                                closest1 != null -> 0 to closest1
                                else -> null
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp)) {
                    val graphWidth = size.width
                    val graphHeight = size.height
                    
                    // Technical Grid
                    for (i in 0..4) {
                        val y = graphHeight * (i / 4f)
                        drawLine(
                            color = GlassBorder,
                            start = Offset(0f, y),
                            end = Offset(graphWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    // Draw Worm Lines
                    innings2?.let { drawTechnicalWorm(it, maxOvers, maxRuns, graphProgress.value) }
                    drawTechnicalWorm(innings1, maxOvers, maxRuns, graphProgress.value)
                    
                    // Wicket Markers
                    drawWicketMarkers(innings1, maxOvers, maxRuns, graphProgress.value)
                    innings2?.let { drawWicketMarkers(it, maxOvers, maxRuns, graphProgress.value) }
                }
            }
            
            // Bottom Stats Summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassBorderLight)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WormStatBlock("MAX SCORE", maxRuns.toString(), hierarchy.secondary.primary)
                    VerticalDivider(modifier = Modifier.height(24.dp), color = GlassBorder)
                    WormStatBlock("TOTAL OVERS", String.format("%.1f", maxOvers), colors.statusInfo)
                    VerticalDivider(modifier = Modifier.height(24.dp), color = GlassBorder)
                    WormStatBlock("WICKETS", allPoints.maxOfOrNull { it.wicketsDown }.toString(), ErrorRed)
                }
            }
        }
    }
}

private fun DrawScope.drawTechnicalWorm(innings: TeamInnings, maxOvers: Float, maxRuns: Int, progress: Float) {
    try {
        val points = innings.dataPoints
        if (points.size < 2) return
    
        val path = Path()
        val fillPath = Path()
    
        points.forEachIndexed { index, point ->
            val x = (point.over / maxOvers) * size.width
            val y = size.height - (point.cumulativeRuns.toFloat() / maxRuns) * size.height
        
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
    
        fillPath.lineTo((points.last().over / maxOvers) * size.width, size.height)
        fillPath.close()
    
        // Ambient Fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(innings.teamColor.copy(alpha = 0.15f * progress), Color.Transparent)
            )
        )
    
        // Precision Line
        drawPath(
            path = path,
            color = innings.teamColor.copy(alpha = 0.8f * progress),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

private fun DrawScope.drawWicketMarkers(innings: TeamInnings, maxOvers: Float, maxRuns: Int, progress: Float) {
    try {
        innings.dataPoints.filter { it.isWicket }.forEach { point ->
            val x = (point.over / maxOvers) * size.width * progress
            val y = size.height - (point.cumulativeRuns.toFloat() / maxRuns) * size.height
        
            drawCircle(
                color = ErrorRed,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = ErrorRed.copy(alpha = 0.2f),
                radius = 8.dp.toPx(),
                center = Offset(x, y)
            )
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

@Composable
private fun TechWormLegend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(name.uppercase().take(8), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun WormStatBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
