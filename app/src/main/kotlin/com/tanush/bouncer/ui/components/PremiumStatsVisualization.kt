package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

/**
 * Head to head comparison visualization
 */
@Composable
fun HeadToHeadComparison(
    team1Name: String,
    team1Value: Int,
    team2Name: String,
    team2Value: Int,
    statLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val total = team1Value + team2Value
    val team1Ratio = if (total > 0) team1Value.toFloat() / total else 0.5f
    
    val animatedRatio by animateFloatAsState(
        targetValue = team1Ratio,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "h2hRatio"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = colors.glassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = statLabel.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            // Values row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$team1Value",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = hierarchy.secondary.primary
                    )
                    Text(
                        text = team1Name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$team2Value",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textSecondary
                    )
                    Text(
                        text = team2Name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.glassBorder)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Team 1 portion
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedRatio)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Run scoring zones visualization (like wagon wheel simplified)
 */
@Composable
fun ScoringZonesChart(
    zones: List<ScoringZone>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "zones")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zoneRotation"
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassBackground
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SCORING ZONES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = hierarchy.secondary.primary
            )
            
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2 - 20.dp.toPx()
                    
                    // Background circle with subtle grid
                    drawCircle(
                        color = GlassBorder.copy(alpha = 0.3f),
                        radius = radius,
                        style = Stroke(1.dp.toPx())
                    )
                    
                    // Inner circles
                    drawCircle(
                        color = GlassBorder.copy(alpha = 0.2f),
                        radius = radius * 0.66f,
                        style = Stroke(0.5.dp.toPx())
                    )
                    
                    drawCircle(
                        color = GlassBorder.copy(alpha = 0.15f),
                        radius = radius * 0.33f,
                        style = Stroke(0.5.dp.toPx())
                    )
                    
                    // Zone sections
                    val anglePerZone = 360f / zones.size
                    val maxRuns = zones.maxOfOrNull { it.runs } ?: 1
                    
                    zones.forEachIndexed { index, zone ->
                        val startAngle = index * anglePerZone - 90f + rotation * 0.01f
                        val zoneRadius = radius * (zone.runs.toFloat() / maxRuns).coerceIn(0.2f, 1f)
                        
                        // Zone arc
                        drawArc(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    zone.color.copy(alpha = 0.4f),
                                    zone.color.copy(alpha = 0.1f)
                                ),
                                center = Offset(centerX, centerY),
                                radius = zoneRadius
                            ),
                            startAngle = startAngle,
                            sweepAngle = anglePerZone - 2f,
                            useCenter = true,
                            topLeft = Offset(centerX - zoneRadius, centerY - zoneRadius),
                            size = Size(zoneRadius * 2, zoneRadius * 2)
                        )
                        
                        // Zone border
                        drawArc(
                            color = zone.color.copy(alpha = 0.6f),
                            startAngle = startAngle,
                            sweepAngle = anglePerZone - 2f,
                            useCenter = true,
                            topLeft = Offset(centerX - zoneRadius, centerY - zoneRadius),
                            size = Size(zoneRadius * 2, zoneRadius * 2),
                            style = Stroke(1.5.dp.toPx())
                        )
                    }
                    
                    // Center circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Surface2, Surface1)
                        ),
                        radius = 24.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    
                    drawCircle(
                        color = hierarchy.secondary.primary.copy(alpha = 0.5f),
                        radius = 24.dp.toPx(),
                        center = Offset(centerX, centerY),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                zones.take(4).forEach { zone ->
                    ZoneLegendItem(zone = zone)
                }
            }
        }
    }
}

data class ScoringZone(
    val name: String,
    val runs: Int,
    val color: Color
)

@Composable
private fun ZoneLegendItem(zone: ScoringZone) {
    val colors = BouncerDesignSystem.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(zone.color.copy(alpha = 0.7f), CircleShape)
        )
        Column {
            Text(
                text = zone.name,
                fontSize = 9.sp,
                color = colors.textMuted
            )
            Text(
                text = "${zone.runs} runs",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = zone.color
            )
        }
    }
}

/**
 * Run flow chart showing runs per over
 */
@Composable
fun RunFlowChart(
    overs: List<OverRuns>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val maxRuns = overs.maxOfOrNull { it.runs } ?: 10
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RUN FLOW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = hierarchy.secondary.primary
                )
                
                Text(
                    text = "${overs.size} overs",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width / overs.size - 4.dp.toPx()
                    val maxHeight = size.height - 20.dp.toPx()
                    
                    // Draw bars
                    overs.forEachIndexed { index, over ->
                        val barHeight = (over.runs.toFloat() / maxRuns) * maxHeight
                        val x = index * (barWidth + 4.dp.toPx())
                        val y = size.height - barHeight
                        
                        val barColor = when {
                            over.wicket -> StatusLive
                            over.runs >= 15 -> StatusSix
                            over.runs >= 10 -> StatusInfo
                            over.runs >= 6 -> hierarchy.secondary.primary
                            else -> TextSecondary
                        }
                        
                        // Bar
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    barColor,
                                    barColor.copy(alpha = 0.6f)
                                ),
                                startY = y,
                                endY = size.height
                            ),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        
                        // Wicket indicator
                        if (over.wicket) {
                            drawCircle(
                                color = StatusLive,
                                radius = 4.dp.toPx(),
                                center = Offset(x + barWidth / 2, y - 8.dp.toPx())
                            )
                        }
                    }
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(color = StatusSix, text = "15+ runs")
                LegendItem(color = StatusInfo, text = "10-14 runs")
                LegendItem(color = StatusLive, text = "Wicket")
            }
        }
    }
}

data class OverRuns(
    val overNumber: Int,
    val runs: Int,
    val wicket: Boolean = false
)

@Composable
private fun LegendItem(color: Color, text: String) {
    val dsColors = BouncerDesignSystem.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            fontSize = 10.sp,
            color = dsColors.textMuted
        )
    }
}

/**
 * Match comparison radar chart
 */
@Composable
fun MatchComparisonRadar(
    team1Name: String,
    team2Name: String,
    stats: List<RadarComparisonStat>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarGlow"
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassBackground
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "MATCH COMPARISON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = hierarchy.secondary.primary
            )
            
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2 - 30.dp.toPx()
                    val numStats = stats.size
                    val angleStep = 360f / numStats
                    
                    // Draw web
                    for (level in 1..4) {
                        val levelRadius = radius * (level / 4f)
                        val path = Path()
                        
                        for (i in 0 until numStats) {
                            val angle = Math.toRadians((angleStep * i - 90).toDouble())
                            val x = centerX + (cos(angle) * levelRadius).toFloat()
                            val y = centerY + (sin(angle) * levelRadius).toFloat()
                            
                            if (i == 0) path.moveTo(x, y)
                            else path.lineTo(x, y)
                        }
                        path.close()
                        
                        drawPath(
                            path = path,
                            color = GlassBorder.copy(alpha = 0.3f),
                            style = Stroke(1.dp.toPx())
                        )
                    }
                    
                    // Draw axis lines
                    for (i in 0 until numStats) {
                        val angle = Math.toRadians((angleStep * i - 90).toDouble())
                        val x = centerX + (cos(angle) * radius).toFloat()
                        val y = centerY + (sin(angle) * radius).toFloat()
                        
                        drawLine(
                            color = GlassBorder.copy(alpha = 0.2f),
                            start = Offset(centerX, centerY),
                            end = Offset(x, y),
                            strokeWidth = 1.dp.toPx())
                    }
                    
                    // Team 1 polygon
                    val team1Path = Path()
                    stats.forEachIndexed { index, stat ->
                        val angle = Math.toRadians((angleStep * index - 90).toDouble())
                        val statRadius = radius * stat.team1Value.coerceIn(0f, 1f)
                        val x = centerX + (cos(angle) * statRadius).toFloat()
                        val y = centerY + (sin(angle) * statRadius).toFloat()
                        
                        if (index == 0) team1Path.moveTo(x, y)
                        else team1Path.lineTo(x, y)
                    }
                    team1Path.close()
                    
                    // Team 1 fill
                    drawPath(
                        path = team1Path,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                hierarchy.secondary.primary.copy(alpha = 0.3f * glowPulse),
                                hierarchy.secondary.primary.copy(alpha = 0.1f)
                            ),
                            center = Offset(centerX, centerY),
                            radius = radius
                        )
                    )
                    
                    // Team 1 stroke
                    drawPath(
                        path = team1Path,
                        color = hierarchy.secondary.primary,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    
                    // Team 2 polygon
                    val team2Path = Path()
                    stats.forEachIndexed { index, stat ->
                        val angle = Math.toRadians((angleStep * index - 90).toDouble())
                        val statRadius = radius * stat.team2Value.coerceIn(0f, 1f)
                        val x = centerX + (cos(angle) * statRadius).toFloat()
                        val y = centerY + (sin(angle) * statRadius).toFloat()
                        
                        if (index == 0) team2Path.moveTo(x, y)
                        else team2Path.lineTo(x, y)
                    }
                    team2Path.close()
                    
                    // Team 2 fill
                    drawPath(
                        path = team2Path,
                        color = StatusInfo.copy(alpha = 0.15f)
                    )
                    
                    // Team 2 stroke
                    drawPath(
                        path = team2Path,
                        color = StatusInfo.copy(alpha = 0.7f),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    
                    // Data points and labels
                    stats.forEachIndexed { index, stat ->
                        val angle = Math.toRadians((angleStep * index - 90).toDouble())
                        val labelRadius = radius + 16.dp.toPx()
                        val x = centerX + (cos(angle) * labelRadius).toFloat()
                        val y = centerY + (sin(angle) * labelRadius).toFloat()
                        
                        // Team 1 point
                        val point1Radius = radius * stat.team1Value.coerceIn(0f, 1f)
                        val point1X = centerX + (cos(angle) * point1Radius).toFloat()
                        val point1Y = centerY + (sin(angle) * point1Radius).toFloat()
                        
                        drawCircle(
                            color = hierarchy.secondary.primary,
                            radius = 5.dp.toPx(),
                            center = Offset(point1X, point1Y)
                        )
                        
                        // Team 2 point
                        val point2Radius = radius * stat.team2Value.coerceIn(0f, 1f)
                        val point2X = centerX + (cos(angle) * point2Radius).toFloat()
                        val point2Y = centerY + (sin(angle) * point2Radius).toFloat()
                        
                        drawCircle(
                            color = StatusInfo,
                            radius = 5.dp.toPx(),
                            center = Offset(point2X, point2Y)
                        )
                    }
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TeamLegend(name = team1Name, color = hierarchy.secondary.primary)
                TeamLegend(name = team2Name, color = colors.statusInfo)
            }
        }
    }
}

data class RadarComparisonStat(
    val name: String,
    val team1Value: Float, // 0-1 normalized
    val team2Value: Float  // 0-1 normalized
)

@Composable
private fun TeamLegend(name: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color.copy(alpha = 0.3f), CircleShape)
                .border(2.dp, color, CircleShape)
        )
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Bowling economy meter
 */
@Composable
fun EconomyMeter(
    economy: Float,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedEconomy by animateFloatAsState(
        targetValue = economy.coerceIn(0f, 15f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "economy"
    )
    
    val economyColor = when {
        economy <= 4.5f -> colors.statusSuccess
        economy <= 6.0f -> hierarchy.secondary.primary
        economy <= 8.0f -> colors.statusWarning
        economy <= 10.0f -> colors.statusLive.copy(alpha = 0.8f)
        else -> colors.statusLive
    }

    Surface(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = economyColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = colors.glassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ECONOMY RATE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = colors.textMuted
            )
            
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val arcRadius = (size.minDimension - strokeWidth) / 2
                    
                    // Background arc (semi-circle)
                    drawArc(
                        color = GlassBorder,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(arcRadius * 2, arcRadius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Economy arc
                    val normalizedEconomy = (animatedEconomy / 15f).coerceIn(0f, 1f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                StatusSuccess,
                                hierarchy.secondary.primary,
                                StatusWarning,
                                StatusLive
                            ),
                            center = Offset(size.width / 2, size.height / 2)
                        ),
                        startAngle = 180f,
                        sweepAngle = normalizedEconomy * 180f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(arcRadius * 2, arcRadius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 10.dp)
                ) {
                    Text(
                        text = String.format("%.2f", economy),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = economyColor
                    )
                }
            }
            
            // Rating text
            Text(
                text = when {
                    economy <= 4.5f -> "Excellent"
                    economy <= 6.0f -> "Good"
                    economy <= 8.0f -> "Average"
                    economy <= 10.0f -> "Expensive"
                    else -> "Very Expensive"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = economyColor
            )
        }
    }
}
