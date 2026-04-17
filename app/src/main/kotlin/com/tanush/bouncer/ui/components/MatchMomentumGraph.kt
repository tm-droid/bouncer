package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Data class for momentum data points
 */
data class MomentumPoint(
    val over: Float,
    val runRate: Float,
    val requiredRunRate: Float? = null,
    val cumulativeRuns: Int,
    val wickets: Int,
    val event: MomentumEvent? = null
)

enum class MomentumEvent {
    WICKET,
    BOUNDARY_FOUR,
    BOUNDARY_SIX,
    FIFTY,
    CENTURY,
    POWERPLAY_END,
    DRINKS,
    NEW_BALL
}

/**
 * MEGA UI UPGRADE: MatchMomentumGraph (v2.1).
 * Ultra-fidelity momentum tracking with broadcast-grade cinematic visuals.
 */
@Composable
fun MatchMomentumGraph(
    team1Data: List<MomentumPoint>,
    team2Data: List<MomentumPoint> = emptyList(),
    team1Name: String = "Team 1",
    team2Name: String = "Team 2",
    team1Color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    team2Color: Color = BouncerDesignSystem.colors.statusInfo,
    modifier: Modifier = Modifier,
    showRequiredRate: Boolean = true,
    title: String = "MATCH MOMENTUM"
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    var selectedPoint by remember { mutableStateOf<Pair<MomentumPoint, Boolean>?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "momentum")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    
    val graphProgress = remember { Animatable(0f) }
    LaunchedEffect(team1Data) {
        graphProgress.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(GlassBackground)
            .border(
                0.5.dp,
                Brush.linearGradient(colors = listOf(GoldHairline, GlassSurface)),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        title,
                        color = hierarchy.secondary.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "LIVE PHASE ANALYSIS",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TechTeamIndicator(team1Name, team1Color)
                    if (team2Data.isNotEmpty()) TechTeamIndicator(team2Name, team2Color)
                }
            }
            
            // Graph Matrix
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassDark)
                    .pointerInput(team1Data, team2Data) {
                        detectTapGestures { offset ->
                            val maxOvers = max(team1Data.lastOrNull()?.over ?: 20f, team2Data.lastOrNull()?.over ?: 20f)
                            val xPos = (offset.x / size.width) * maxOvers
                            val closest = team1Data.minByOrNull { abs(it.over - xPos) }
                            if (closest != null && abs(closest.over - xPos) < 2f) {
                                selectedPoint = closest to true
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp)) {
                    val graphWidth = size.width
                    val graphHeight = size.height
                    
                    val maxOvers = max(team1Data.lastOrNull()?.over ?: 20f, team2Data.lastOrNull()?.over ?: 20f)
                    val maxRR = 15f // Fixed scale for RR comparison
                    
                    // Technical Grid
                    for (i in 0..5) {
                        val y = graphHeight * (i / 5f)
                        drawLine(
                            color = GlassBorder,
                            start = Offset(0f, y),
                            end = Offset(graphWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    // Draw Team 1 Momentum
                    drawTechnicalMomentum(
                        data = team1Data,
                        color = team1Color,
                        maxOvers = maxOvers,
                        maxRR = maxRR,
                        progress = graphProgress.value
                    )
                    
                    // Events Visualization
                    team1Data.filter { it.event == MomentumEvent.WICKET }.forEach { point ->
                        val x = (point.over / maxOvers) * graphWidth * graphProgress.value
                        val y = graphHeight - (point.runRate / maxRR) * graphHeight
                        drawCircle(
                            color = ErrorRed,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = ErrorRed.copy(alpha = 0.2f),
                            radius = 10.dp.toPx() * pulseScale,
                            center = Offset(x, y)
                        )
                    }
                }
            }
            
            // Stats Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MomentumStatMini("AVG RR", String.format("%.2f", if (team1Data.isNotEmpty()) team1Data.map { it.runRate }.average() else 0.0), team1Color)
                VerticalDivider(modifier = Modifier.height(24.dp), color = GlassBorderHighlight)
                MomentumStatMini("MAX RR", String.format("%.2f", team1Data.maxOfOrNull { it.runRate } ?: 0f), colors.goldPrimary)
                VerticalDivider(modifier = Modifier.height(24.dp), color = GlassBorderHighlight)
                MomentumStatMini("WICKETS", team1Data.lastOrNull()?.wickets.toString(), ErrorRed)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTechnicalMomentum(
    data: List<MomentumPoint>,
    color: Color,
    maxOvers: Float,
    maxRR: Float,
    progress: Float
) {
    if (data.isEmpty()) return
    
    val path = Path()
    val fillPath = Path()
    
    data.forEachIndexed { index, point ->
        val x = (point.over / maxOvers) * size.width
        val y = size.height - (point.runRate / maxRR) * size.height
        
        if (index == 0) {
            path.moveTo(x, y)
            fillPath.moveTo(x, size.height)
            fillPath.lineTo(x, y)
        } else {
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }
    
    if (data.isNotEmpty()) {
        fillPath.lineTo((data.last().over / maxOvers) * size.width, size.height)
        fillPath.close()
    }
    
    // Gradient Fill
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.2f * progress), Color.Transparent)
        )
    )
    
    // Main Line
    drawPath(
        path = path,
        color = color.copy(alpha = 0.8f * progress),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

@Composable
private fun TechTeamIndicator(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(name, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MomentumStatMini(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}
