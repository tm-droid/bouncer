package com.tanush.bouncer.ui.components.charts

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Shot data for wagon wheel visualization
 */
data class EnhancedShotData(
    val id: String,
    val angle: Float,           // Angle in degrees (0 = straight)
    val distance: Float,        // 0-1 range
    val runs: Int,
    val velocity: Float = 0.5f, // Shot speed 0-1
    val isWicket: Boolean = false,
    val shotType: String = "",
    val over: Int = 0,
    val ball: Int = 0
)

/**
 * Enhanced Wagon Wheel with animated shot trails and velocity coloring
 */
@Composable
fun EnhancedWagonWheel(
    shots: List<EnhancedShotData>,
    batsmanName: String = "Batsman",
    modifier: Modifier = Modifier,
    showTrailAnimation: Boolean = true,
    onSectorSelected: ((String, List<EnhancedShotData>) -> Unit)? = null,
    onShotSelected: ((EnhancedShotData) -> Unit)? = null
) {
    var selectedSector by remember { mutableStateOf<String?>(null) }
    var selectedShot by remember { mutableStateOf<EnhancedShotData?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "wagonWheelAnim")
    
    val trailAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trailAlpha"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
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
                Column {
                    Text(
                        text = "Enhanced Wagon Wheel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = batsmanName,
                        fontSize = 12.sp,
                        color = ShinyGoldDeep
                    )
                }
                
                // Stats badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shots.filter { it.runs == 4 }.size.let { count ->
                        StatBadge(count.toString(), "4s", BoundaryFour)
                    }
                    shots.filter { it.runs == 6 }.size.let { count ->
                        StatBadge(count.toString(), "6s", BoundarySix)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Wagon Wheel Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        Brush.sweepGradient(
                            colors = listOf(
                                ShinyGoldDeep.copy(alpha = 0.4f),
                                CardBorder,
                                ShinyGoldDeep.copy(alpha = 0.6f),
                                CardBorder
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .pointerInput(shots) {
                            detectTapGestures { offset ->
                                val centerX = size.width / 2
                                val centerY = size.height / 2
                                val maxRadius = min(centerX, centerY) * 0.85f
                                
                                // Check if a shot was clicked
                                val clickedShot = shots.find { shot ->
                                    val angleRad = Math.toRadians(shot.angle.toDouble() - 90)
                                    val distance = shot.distance * maxRadius
                                    val shotX = centerX + (cos(angleRad) * distance).toFloat()
                                    val shotY = centerY + (sin(angleRad) * distance).toFloat()
                                    
                                    val dx = offset.x - shotX
                                    val dy = offset.y - shotY
                                    sqrt(dx * dx + dy * dy) < 25.dp.toPx()
                                }
                                
                                if (clickedShot != null) {
                                    selectedShot = clickedShot
                                    onShotSelected?.invoke(clickedShot)
                                } else {
                                    // Check sector selection
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90
                                    val normalizedAngle = if (angle < 0) angle + 360 else angle
                                    
                                    val sectorName = when {
                                        normalizedAngle in 315.0..360.0 || normalizedAngle in 0.0..45.0 -> "Straight"
                                        normalizedAngle in 45.0..90.0 -> "Cover"
                                        normalizedAngle in 90.0..135.0 -> "Point"
                                        normalizedAngle in 135.0..180.0 -> "Third Man"
                                        normalizedAngle in 180.0..225.0 -> "Fine Leg"
                                        normalizedAngle in 225.0..270.0 -> "Square Leg"
                                        normalizedAngle in 270.0..315.0 -> "Mid Wicket"
                                        else -> "Unknown"
                                    }
                                    
                                    selectedSector = sectorName
                                    val sectorShots = shots.filter { shot ->
                                        val shotAngle = (shot.angle + 90 + 360) % 360
                                        when (sectorName) {
                                            "Straight" -> shotAngle in 315f..360f || shotAngle in 0f..45f
                                            "Cover" -> shotAngle in 45f..90f
                                            "Point" -> shotAngle in 90f..135f
                                            "Third Man" -> shotAngle in 135f..180f
                                            "Fine Leg" -> shotAngle in 180f..225f
                                            "Square Leg" -> shotAngle in 225f..270f
                                            "Mid Wicket" -> shotAngle in 270f..315f
                                            else -> false
                                        }
                                    }
                                    onSectorSelected?.invoke(sectorName, sectorShots)
                                }
                            }
                        }
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val maxRadius = min(centerX, centerY) * 0.85f
                    
                    // Field background
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E4D0F),
                                Color(0xFF2A5F18),
                                Color(0xFF1A4510)
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        radius = maxRadius + 15.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    
                    // Sector dividers
                    val sectors = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
                    sectors.forEach { angle ->
                        val angleRad = Math.toRadians(angle.toDouble() - 90)
                        val endX = centerX + (cos(angleRad) * maxRadius).toFloat()
                        val endY = centerY + (sin(angleRad) * maxRadius).toFloat()
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(centerX, centerY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    // Distance rings
                    listOf(0.33f, 0.66f, 1f).forEach { ratio ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.1f),
                            radius = maxRadius * ratio,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    
                    // Boundary circle
                    drawCircle(
                        color = ShinyGoldDeep.copy(alpha = 0.6f),
                        radius = maxRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // Pitch
                    val pitchWidth = 18.dp.toPx()
                    val pitchHeight = 45.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF8B5A2B),
                                Color(0xFF6B4423)
                            )
                        ),
                        topLeft = Offset(centerX - pitchWidth / 2, centerY - pitchHeight / 2),
                        size = Size(pitchWidth, pitchHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    
                    // Draw shots with velocity-based colors
                    shots.forEach { shot ->
                        val angleRad = Math.toRadians(shot.angle.toDouble() - 90)
                        val distance = shot.distance * maxRadius
                        val endX = centerX + (cos(angleRad) * distance).toFloat()
                        val endY = centerY + (sin(angleRad) * distance).toFloat()
                        
                        val isSelected = shot == selectedShot
                        
                        // Velocity-based color interpolation
                        val velocityColor = when {
                            shot.isWicket -> WicketRed
                            shot.runs == 6 -> BoundarySix
                            shot.runs == 4 -> BoundaryFour
                            else -> Color(
                                red = lerp(ChartBlue.red, ChartOrange.red, shot.velocity),
                                green = lerp(ChartBlue.green, ChartOrange.green, shot.velocity),
                                blue = lerp(ChartBlue.blue, ChartOrange.blue, shot.velocity),
                                alpha = 1f
                            )
                        }
                        
                        val lineWidth = when {
                            shot.runs == 6 -> 4.dp.toPx()
                            shot.runs == 4 -> 3.5.dp.toPx()
                            else -> 2.5.dp.toPx()
                        }
                        
                        // Trail animation for boundaries
                        if (showTrailAnimation && shot.runs >= 4) {
                            val trailPoints = 5
                            for (i in 1..trailPoints) {
                                val ratio = i.toFloat() / trailPoints
                                val trailX = centerX + (cos(angleRad) * distance * ratio).toFloat()
                                val trailY = centerY + (sin(angleRad) * distance * ratio).toFloat()
                                
                                drawCircle(
                                    color = velocityColor.copy(alpha = trailAlpha * (1 - ratio) * 0.5f),
                                    radius = (6.dp.toPx() * (1 - ratio)),
                                    center = Offset(trailX, trailY)
                                )
                            }
                        }
                        
                        // Shot glow
                        if (shot.runs >= 4) {
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        velocityColor.copy(alpha = 0.4f)
                                    ),
                                    start = Offset(centerX, centerY),
                                    end = Offset(endX, endY)
                                ),
                                start = Offset(centerX, centerY),
                                end = Offset(endX, endY),
                                strokeWidth = lineWidth * 3
                            )
                        }
                        
                        // Main shot line
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    velocityColor.copy(alpha = 0.4f),
                                    velocityColor
                                ),
                                start = Offset(centerX, centerY),
                                end = Offset(endX, endY)
                            ),
                            start = Offset(centerX, centerY),
                            end = Offset(endX, endY),
                            strokeWidth = lineWidth,
                            cap = StrokeCap.Round
                        )
                        
                        // End dot
                        val dotRadius = when {
                            shot.runs == 6 -> 8.dp.toPx()
                            shot.runs == 4 -> 7.dp.toPx()
                            else -> 5.dp.toPx()
                        }
                        
                        if (isSelected) {
                            drawCircle(
                                color = ShinyGoldDeep,
                                radius = dotRadius * pulseScale + 4.dp.toPx(),
                                center = Offset(endX, endY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(velocityColor, velocityColor.copy(alpha = 0.7f)),
                                center = Offset(endX, endY)
                            ),
                            radius = dotRadius,
                            center = Offset(endX, endY)
                        )
                    }
                    
                    // Center batsman indicator
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ShinyGoldMid, ShinyGoldDeep),
                            center = Offset(centerX, centerY)
                        ),
                        radius = 10.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                }
            }
            
            // Selected shot info
            selectedShot?.let { shot ->
                Spacer(modifier = Modifier.height(12.dp))
                SelectedShotInfo(shot)
            }
        }
    }
}

@Composable
private fun SelectedShotInfo(shot: EnhancedShotData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = shot.shotType.ifEmpty { "Shot" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "Over ${shot.over}.${shot.ball}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Speed: ${(shot.velocity * 100).toInt()}%",
                fontSize = 11.sp,
                color = ChartOrange
            )
            Box(
                modifier = Modifier
                    .background(
                        when {
                            shot.isWicket -> WicketRed
                            shot.runs == 6 -> BoundarySix
                            shot.runs == 4 -> BoundaryFour
                            else -> ChartBlue
                        }.copy(alpha = 0.2f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when {
                        shot.isWicket -> "OUT"
                        shot.runs == 6 -> "SIX"
                        shot.runs == 4 -> "FOUR"
                        else -> "${shot.runs}"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        shot.isWicket -> WicketRed
                        shot.runs == 6 -> BoundarySix
                        shot.runs == 4 -> BoundaryFour
                        else -> ChartBlue
                    }
                )
            }
        }
    }
}

/**
 * Bowling length data for pitch map
 */
data class BowlingZone(
    val length: String,        // "yorker", "full", "good", "short"
    val line: String,          // "offside", "middle", "legside"
    val count: Int,
    val runs: Int = 0,
    val wickets: Int = 0
)

/**
 * Interactive Pitch Map with touch zones and bowling length heat maps
 */
@Composable
fun InteractivePitchMap(
    zones: List<BowlingZone>,
    modifier: Modifier = Modifier,
    showHeatMap: Boolean = true,
    onZoneSelected: ((BowlingZone) -> Unit)? = null
) {
    var selectedZone by remember { mutableStateOf<BowlingZone?>(null) }
    
    val textMeasurer = rememberTextMeasurer()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Pitch Map",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .pointerInput(zones) {
                        detectTapGestures { offset ->
                            val pitchWidth = size.width * 0.6f
                            val pitchHeight = size.height * 0.9f
                            val pitchLeft = (size.width - pitchWidth) / 2
                            val pitchTop = size.height * 0.05f
                            
                            // Calculate zone from tap
                            val relX = (offset.x - pitchLeft) / pitchWidth
                            val relY = (offset.y - pitchTop) / pitchHeight
                            
                            if (relX in 0f..1f && relY in 0f..1f) {
                                val line = when {
                                    relX < 0.33f -> "offside"
                                    relX < 0.66f -> "middle"
                                    else -> "legside"
                                }
                                val length = when {
                                    relY < 0.25f -> "short"
                                    relY < 0.5f -> "good"
                                    relY < 0.75f -> "full"
                                    else -> "yorker"
                                }
                                
                                val zone = zones.find { it.length == length && it.line == line }
                                selectedZone = zone
                                zone?.let { onZoneSelected?.invoke(it) }
                            }
                        }
                    }
            ) {
                val pitchWidth = size.width * 0.6f
                val pitchHeight = size.height * 0.9f
                val pitchLeft = (size.width - pitchWidth) / 2
                val pitchTop = size.height * 0.05f
                
                // Pitch background
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF8B5A2B),
                            Color(0xFF6B4423),
                            Color(0xFF8B5A2B)
                        )
                    ),
                    topLeft = Offset(pitchLeft, pitchTop),
                    size = Size(pitchWidth, pitchHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                
                // Zone grid
                val zoneWidth = pitchWidth / 3
                val zoneHeight = pitchHeight / 4
                
                val lengths = listOf("short", "good", "full", "yorker")
                val lines = listOf("offside", "middle", "legside")
                
                lengths.forEachIndexed { yIndex, length ->
                    lines.forEachIndexed { xIndex, line ->
                        val zoneX = pitchLeft + xIndex * zoneWidth
                        val zoneY = pitchTop + yIndex * zoneHeight
                        
                        val zone = zones.find { it.length == length && it.line == line }
                        val isSelected = zone == selectedZone
                        
                        if (zone != null && showHeatMap) {
                            val maxCount = zones.maxOfOrNull { it.count } ?: 1
                            val intensity = zone.count.toFloat() / maxCount
                            
                            val heatColor = when (length) {
                                "yorker" -> Yorker
                                "full" -> FullLength
                                "good" -> GoodLength
                                "short" -> ShortPitch
                                else -> ChartBlue
                            }
                            
                            drawRect(
                                color = heatColor.copy(alpha = intensity * 0.6f),
                                topLeft = Offset(zoneX, zoneY),
                                size = Size(zoneWidth, zoneHeight)
                            )
                        }
                        
                        // Zone border
                        drawRect(
                            color = if (isSelected) ShinyGoldDeep else Color.White.copy(alpha = 0.2f),
                            topLeft = Offset(zoneX, zoneY),
                            size = Size(zoneWidth, zoneHeight),
                            style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
                        )
                        
                        // Zone count
                        zone?.let {
                            val countText = it.count.toString()
                            val textResult = textMeasurer.measure(
                                text = countText,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            drawText(
                                textLayoutResult = textResult,
                                color = TextPrimary,
                                topLeft = Offset(
                                    zoneX + (zoneWidth - textResult.size.width) / 2,
                                    zoneY + (zoneHeight - textResult.size.height) / 2
                                )
                            )
                        }
                    }
                }
                
                // Crease lines
                val creaseY = pitchTop + pitchHeight * 0.85f
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(pitchLeft, creaseY),
                    end = Offset(pitchLeft + pitchWidth, creaseY),
                    strokeWidth = 2.dp.toPx()
                )
                
                // Stumps
                val stumpWidth = 3.dp.toPx()
                val stumpHeight = 8.dp.toPx()
                val stumpSpacing = 8.dp.toPx()
                val stumpsX = pitchLeft + pitchWidth / 2 - stumpSpacing
                
                for (i in 0..2) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(stumpsX + i * stumpSpacing, creaseY - stumpHeight),
                        size = Size(stumpWidth, stumpHeight)
                    )
                }
            }
            
            // Length legend
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LengthLegendItem("Yorker", Yorker)
                LengthLegendItem("Full", FullLength)
                LengthLegendItem("Good", GoodLength)
                LengthLegendItem("Short", ShortPitch)
            }
            
            // Selected zone info
            selectedZone?.let { zone ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = zone.count.toString(), fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Balls", fontSize = 10.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = zone.runs.toString(), fontWeight = FontWeight.Bold, color = ChartGreen)
                        Text(text = "Runs", fontSize = 10.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = zone.wickets.toString(), fontWeight = FontWeight.Bold, color = WicketRed)
                        Text(text = "Wickets", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LengthLegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
}

/**
 * Over data for Manhattan graph
 */
data class ManhattanOverData(
    val over: Int,
    val runs: Int,
    val wickets: Int = 0,
    val extras: Int = 0,
    val isSelected: Boolean = false
)

/**
 * Enhanced Manhattan Graph with expandable bars and over-by-over comparison
 */
@Composable
fun ManhattanGraphEnhanced(
    team1Data: List<ManhattanOverData>,
    team2Data: List<ManhattanOverData>? = null,
    team1Name: String = "Team 1",
    team2Name: String = "Team 2",
    team1Color: Color = ChartCyan,
    team2Color: Color = ChartOrange,
    modifier: Modifier = Modifier,
    onOverSelected: ((Int, ManhattanOverData, ManhattanOverData?) -> Unit)? = null
) {
    var selectedOver by remember { mutableStateOf(-1) }
    
    val maxRuns = maxOf(
        team1Data.maxOfOrNull { it.runs } ?: 1,
        team2Data?.maxOfOrNull { it.runs } ?: 0
    )
    
    val textMeasurer = rememberTextMeasurer()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manhattan Graph",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(team1Name, team1Color)
                    if (team2Data != null) {
                        LegendItem(team2Name, team2Color)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(team1Data, team2Data) {
                        detectTapGestures { offset ->
                            val padding = 40.dp.toPx()
                            val chartWidth = size.width - padding * 2
                            val barWidth = chartWidth / (team1Data.size * (if (team2Data != null) 2.5f else 1.5f))
                            
                            val tappedOver = team1Data.indices.find { index ->
                                val x = padding + index * (barWidth * (if (team2Data != null) 2.5f else 1.5f))
                                offset.x >= x && offset.x <= x + barWidth * (if (team2Data != null) 2f else 1f)
                            }
                            
                            selectedOver = tappedOver ?: -1
                            tappedOver?.let { over ->
                                onOverSelected?.invoke(
                                    over,
                                    team1Data[over],
                                    team2Data?.getOrNull(over)
                                )
                            }
                        }
                    }
            ) {
                val padding = 40.dp.toPx()
                val chartWidth = size.width - padding * 2
                val chartHeight = size.height - padding * 2
                
                val barWidth = chartWidth / (team1Data.size * (if (team2Data != null) 2.5f else 1.5f))
                val barSpacing = barWidth * 0.25f
                
                // Grid lines
                for (i in 0..4) {
                    val y = padding + chartHeight * i / 4
                    drawLine(
                        color = GlassBorder,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Y-axis labels
                    val value = maxRuns - (maxRuns * i / 4)
                    val labelResult = textMeasurer.measure(
                        text = value.toString(),
                        style = TextStyle(fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = labelResult,
                        color = TextSecondary,
                        topLeft = Offset(padding - labelResult.size.width - 4.dp.toPx(), y - labelResult.size.height / 2)
                    )
                }
                
                // Draw bars
                team1Data.forEachIndexed { index, data ->
                    val x = padding + index * (barWidth * (if (team2Data != null) 2.5f else 1.5f))
                    val barHeight = (data.runs.toFloat() / maxRuns) * chartHeight
                    val y = padding + chartHeight - barHeight
                    
                    val isSelected = index == selectedOver
                    
                    // Team 1 bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isSelected) ShinyGoldDeep else team1Color,
                                (if (isSelected) ShinyGoldDeep else team1Color).copy(alpha = 0.7f)
                            )
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    
                    // Wicket indicator
                    if (data.wickets > 0) {
                        for (w in 0 until data.wickets) {
                            drawCircle(
                                color = WicketRed,
                                radius = 3.dp.toPx(),
                                center = Offset(x + barWidth / 2, y - 6.dp.toPx() - w * 8.dp.toPx())
                            )
                        }
                    }
                    
                    // Team 2 bar (if exists)
                    team2Data?.getOrNull(index)?.let { team2Over ->
                        val x2 = x + barWidth + barSpacing
                        val barHeight2 = (team2Over.runs.toFloat() / maxRuns) * chartHeight
                        val y2 = padding + chartHeight - barHeight2
                        
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    if (isSelected) ShinyGoldDeep.copy(alpha = 0.7f) else team2Color,
                                    (if (isSelected) ShinyGoldDeep.copy(alpha = 0.7f) else team2Color).copy(alpha = 0.7f)
                                )
                            ),
                            topLeft = Offset(x2, y2),
                            size = Size(barWidth, barHeight2),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                        
                        if (team2Over.wickets > 0) {
                            for (w in 0 until team2Over.wickets) {
                                drawCircle(
                                    color = WicketRed,
                                    radius = 3.dp.toPx(),
                                    center = Offset(x2 + barWidth / 2, y2 - 6.dp.toPx() - w * 8.dp.toPx())
                                )
                            }
                        }
                    }
                    
                    // Over label
                    if (index % 5 == 0 || index == team1Data.lastIndex) {
                        val labelResult = textMeasurer.measure(
                            text = (index + 1).toString(),
                            style = TextStyle(fontSize = 9.sp)
                        )
                        drawText(
                            textLayoutResult = labelResult,
                            color = TextSecondary,
                            topLeft = Offset(
                                x + barWidth / 2 - labelResult.size.width / 2,
                                size.height - padding + 8.dp.toPx()
                            )
                        )
                    }
                }
            }
            
            // Selected over details
            if (selectedOver >= 0) {
                Spacer(modifier = Modifier.height(12.dp))
                OverComparisonCard(
                    over = selectedOver + 1,
                    team1Data = team1Data[selectedOver],
                    team2Data = team2Data?.getOrNull(selectedOver),
                    team1Name = team1Name,
                    team2Name = team2Name,
                    team1Color = team1Color,
                    team2Color = team2Color
                )
            }
        }
    }
}

@Composable
private fun LegendItem(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = name,
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun OverComparisonCard(
    over: Int,
    team1Data: ManhattanOverData,
    team2Data: ManhattanOverData?,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Over $over",
            fontWeight = FontWeight.Bold,
            color = ShinyGoldDeep,
            fontSize = 14.sp
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = team1Name, fontSize = 10.sp, color = team1Color)
                Text(text = "${team1Data.runs} runs", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
                if (team1Data.wickets > 0) {
                    Text(text = "${team1Data.wickets}W", color = WicketRed, fontSize = 10.sp)
                }
            }
            
            team2Data?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = team2Name, fontSize = 10.sp, color = team2Color)
                    Text(text = "${it.runs} runs", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
                    if (it.wickets > 0) {
                        Text(text = "${it.wickets}W", color = WicketRed, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/**
 * Animated Win Probability Gauge with smooth needle transitions
 */
@Composable
fun WinProbabilityGaugeAnimated(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = ShinyGoldDeep,
    team2Color: Color = ChartBlue,
    confidenceBand: Float = 0.1f,
    modifier: Modifier = Modifier
) {
    val animatedProbability by animateFloatAsState(
        targetValue = team1Probability.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "probability"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "gaugePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WIN PROBABILITY",
                color = ShinyGoldDeep,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Gauge
            Box(
                modifier = Modifier.size(220.dp, 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val radius = size.width / 2 - strokeWidth
                    val center = Offset(size.width / 2, size.height)
                    
                    // Background arc
                    drawArc(
                        color = Color.White.copy(alpha = 0.05f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth, size.height - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Confidence band
                    val bandStart = ((animatedProbability - confidenceBand).coerceIn(0f, 1f)) * 180f
                    val bandEnd = ((animatedProbability + confidenceBand).coerceIn(0f, 1f)) * 180f
                    val bandSweep = bandEnd - bandStart
                    
                    drawArc(
                        color = team1Color.copy(alpha = 0.2f),
                        startAngle = 180f + bandStart,
                        sweepAngle = bandSweep,
                        useCenter = false,
                        topLeft = Offset(strokeWidth, size.height - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    
                    // Team 1 arc (probability)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(team1Color, team1Color.copy(alpha = 0.8f)),
                            center = center
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f * animatedProbability,
                        useCenter = false,
                        topLeft = Offset(strokeWidth, size.height - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Needle
                    val needleAngle = 180f + (180f * animatedProbability)
                    val needleRadians = Math.toRadians(needleAngle.toDouble())
                    val needleLength = radius + 10.dp.toPx()
                    val needleEnd = Offset(
                        center.x + (needleLength * cos(needleRadians)).toFloat(),
                        center.y + (needleLength * sin(needleRadians)).toFloat()
                    )
                    
                    // Needle glow
                    drawLine(
                        color = Color.White.copy(alpha = pulseAlpha * 0.3f),
                        start = center,
                        end = needleEnd,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    // Center pivot
                    drawCircle(color = BlackSurface, radius = 12.dp.toPx(), center = center)
                    drawCircle(color = team1Color, radius = 6.dp.toPx(), center = center)
                }
                
                // Center percentage display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "${(animatedProbability * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = team1Name.uppercase(),
                        color = team1Color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Comparison bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = team1Name.uppercase(), fontSize = 10.sp, color = team1Color, fontWeight = FontWeight.Bold)
                    Text(text = "${(animatedProbability * 100).toInt()}%", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Black)
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProbability)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(team1Color, team1Color.copy(alpha = 0.7f))
                                )
                            )
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = team2Name.uppercase(), fontSize = 10.sp, color = team2Color, fontWeight = FontWeight.Bold)
                    Text(text = "${((1 - animatedProbability) * 100).toInt()}%", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * Run Rate Chart comparing required vs current run rate
 */
@Composable
fun RunRateChart(
    currentRunRates: List<Float>,
    requiredRunRates: List<Float>,
    projectedRunRates: List<Float>? = null,
    modifier: Modifier = Modifier,
    currentColor: Color = ChartGreen,
    requiredColor: Color = ChartOrange,
    projectedColor: Color = ChartPurple
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "runRateAnimation"
    )
    
    val textMeasurer = rememberTextMeasurer()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Run Rate Comparison",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem("Current", currentColor)
                    LegendItem("Required", requiredColor)
                    if (projectedRunRates != null) {
                        LegendItem("Projected", projectedColor)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val padding = 40.dp.toPx()
                val chartWidth = size.width - padding * 2
                val chartHeight = size.height - padding * 2
                
                val allRates = currentRunRates + requiredRunRates + (projectedRunRates ?: emptyList())
                val maxRate = (allRates.maxOrNull() ?: 10f) * 1.1f
                val minRate = 0f
                val range = maxRate - minRate
                
                // Grid
                for (i in 0..4) {
                    val y = padding + chartHeight * i / 4
                    drawLine(
                        color = GlassBorder,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    val value = maxRate - (range * i / 4)
                    val labelResult = textMeasurer.measure(
                        text = String.format("%.1f", value),
                        style = TextStyle(fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = labelResult,
                        color = TextSecondary,
                        topLeft = Offset(padding - labelResult.size.width - 4.dp.toPx(), y - labelResult.size.height / 2)
                    )
                }
                
                fun drawRunRateLine(data: List<Float>, color: Color, isDashed: Boolean = false) {
                    if (data.size < 2) return
                    
                    val animatedCount = (data.size * animatedProgress).toInt().coerceAtLeast(2)
                    val path = Path()
                    
                    data.take(animatedCount).forEachIndexed { index, rate ->
                        val x = padding + (chartWidth * index / (data.size - 1))
                        val normalizedY = (rate - minRate) / range
                        val y = padding + chartHeight * (1 - normalizedY)
                        
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            val prevX = padding + (chartWidth * (index - 1) / (data.size - 1))
                            val controlX = prevX + (x - prevX) / 2
                            val prevY = padding + chartHeight * (1 - (data[index - 1] - minRate) / range)
                            path.cubicTo(controlX, prevY, controlX, y, x, y)
                        }
                    }
                    
                    if (isDashed) {
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                        )
                    } else {
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                
                // Draw lines
                drawRunRateLine(currentRunRates, currentColor)
                drawRunRateLine(requiredRunRates, requiredColor)
                projectedRunRates?.let { drawRunRateLine(it, projectedColor, isDashed = true) }
            }
        }
    }
}

/**
 * Pitch Heat Map showing bowling/batting zones with color intensity
 */
@Composable
fun PitchHeatMap(
    heatData: List<List<Float>>,  // 4x3 grid (lengths x lines)
    title: String = "Pitch Heat Map",
    modifier: Modifier = Modifier,
    lowColor: Color = ChartBlue,
    highColor: Color = WicketRed.copy(alpha = 0.8f),
    onZoneTapped: ((Int, Int, Float) -> Unit)? = null
) {
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val textMeasurer = rememberTextMeasurer()
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(heatData) {
                        detectTapGestures { offset ->
                            val pitchWidth = size.width * 0.7f
                            val pitchHeight = size.height * 0.9f
                            val pitchLeft = (size.width - pitchWidth) / 2
                            val pitchTop = size.height * 0.05f
                            
                            val cellWidth = pitchWidth / 3
                            val cellHeight = pitchHeight / 4
                            
                            val col = ((offset.x - pitchLeft) / cellWidth).toInt()
                            val row = ((offset.y - pitchTop) / cellHeight).toInt()
                            
                            if (col in 0..2 && row in 0..3) {
                                selectedCell = Pair(row, col)
                                val value = heatData.getOrNull(row)?.getOrNull(col) ?: 0f
                                onZoneTapped?.invoke(row, col, value)
                            }
                        }
                    }
            ) {
                val pitchWidth = size.width * 0.7f
                val pitchHeight = size.height * 0.9f
                val pitchLeft = (size.width - pitchWidth) / 2
                val pitchTop = size.height * 0.05f
                
                // Pitch background
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6B4423),
                            Color(0xFF8B5A2B),
                            Color(0xFF6B4423)
                        )
                    ),
                    topLeft = Offset(pitchLeft, pitchTop),
                    size = Size(pitchWidth, pitchHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                
                val cellWidth = pitchWidth / 3
                val cellHeight = pitchHeight / 4
                
                val maxValue = heatData.flatten().maxOrNull() ?: 1f
                
                heatData.forEachIndexed { row, rowData ->
                    rowData.forEachIndexed { col, value ->
                        val x = pitchLeft + col * cellWidth
                        val y = pitchTop + row * cellHeight
                        val intensity = if (maxValue > 0) value / maxValue else 0f
                        
                        val isSelected = selectedCell == Pair(row, col)
                        
                        // Interpolate color based on intensity
                        val cellColor = Color(
                            red = lerp(lowColor.red, highColor.red, intensity),
                            green = lerp(lowColor.green, highColor.green, intensity),
                            blue = lerp(lowColor.blue, highColor.blue, intensity),
                            alpha = 0.3f + intensity * 0.5f
                        )
                        
                        drawRect(
                            color = cellColor,
                            topLeft = Offset(x, y),
                            size = Size(cellWidth, cellHeight)
                        )
                        
                        // Cell border
                        drawRect(
                            color = if (isSelected) ShinyGoldDeep else Color.White.copy(alpha = 0.15f),
                            topLeft = Offset(x, y),
                            size = Size(cellWidth, cellHeight),
                            style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
                        )
                        
                        // Value text
                        if (value > 0) {
                            val valueText = if (value >= 10) value.toInt().toString() else String.format("%.1f", value)
                            val textResult = textMeasurer.measure(
                                text = valueText,
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            drawText(
                                textLayoutResult = textResult,
                                color = if (intensity > 0.5f) TextPrimary else TextSecondary,
                                topLeft = Offset(
                                    x + (cellWidth - textResult.size.width) / 2,
                                    y + (cellHeight - textResult.size.height) / 2
                                )
                            )
                        }
                    }
                }
                
                // Length labels
                val lengths = listOf("Short", "Good", "Full", "Yorker")
                lengths.forEachIndexed { index, label ->
                    val textResult = textMeasurer.measure(
                        text = label,
                        style = TextStyle(fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = textResult,
                        color = TextSecondary,
                        topLeft = Offset(
                            pitchLeft - textResult.size.width - 8.dp.toPx(),
                            pitchTop + index * cellHeight + (cellHeight - textResult.size.height) / 2
                        )
                    )
                }
                
                // Line labels
                val lines = listOf("Off", "Mid", "Leg")
                lines.forEachIndexed { index, label ->
                    val textResult = textMeasurer.measure(
                        text = label,
                        style = TextStyle(fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = textResult,
                        color = TextSecondary,
                        topLeft = Offset(
                            pitchLeft + index * cellWidth + (cellWidth - textResult.size.width) / 2,
                            pitchTop + pitchHeight + 8.dp.toPx()
                        )
                    )
                }
            }
            
            // Color scale legend
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Low", fontSize = 10.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(lowColor, highColor)
                            )
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "High", fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

// Helper functions
@Composable
private fun StatBadge(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 9.sp, color = TextSecondary)
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}
