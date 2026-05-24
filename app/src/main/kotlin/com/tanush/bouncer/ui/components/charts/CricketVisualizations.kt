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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

// region Data models

data class EnhancedShotData(
    val id: String,
    val angle: Float,
    val distance: Float,
    val runs: Int,
    val velocity: Float = 0.5f,
    val isWicket: Boolean = false,
    val shotType: String = "",
    val over: Int = 0,
    val ball: Int = 0
)

data class BowlingZone(
    val length: String,
    val line: String,
    val count: Int,
    val runs: Int = 0,
    val wickets: Int = 0
)

data class ManhattanOverData(
    val over: Int,
    val runs: Int,
    val wickets: Int = 0,
    val extras: Int = 0,
    val isSelected: Boolean = false
)

// endregion

// region Shared helpers

private fun sectorNameFor(angle: Double): String = when {
    angle in 315.0..360.0 || angle in 0.0..45.0 -> "Straight"
    angle in 45.0..90.0 -> "Cover"
    angle in 90.0..135.0 -> "Point"
    angle in 135.0..180.0 -> "Third Man"
    angle in 180.0..225.0 -> "Fine Leg"
    angle in 225.0..270.0 -> "Square Leg"
    angle in 270.0..315.0 -> "Mid Wicket"
    else -> "Unknown"
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun shotEndOffset(centerX: Float, centerY: Float, angleDeg: Float, distance: Float): Offset {
    val angleRad = Math.toRadians(angleDeg.toDouble() - 90)
    return Offset(
        centerX + (cos(angleRad) * distance).toFloat(),
        centerY + (sin(angleRad) * distance).toFloat()
    )
}

// endregion

// region Enhanced Wagon Wheel

@Composable
fun EnhancedWagonWheel(
    shots: List<EnhancedShotData>,
    batsmanName: String = "Batsman",
    modifier: Modifier = Modifier,
    showTrailAnimation: Boolean = true,
    onSectorSelected: ((String, List<EnhancedShotData>) -> Unit)? = null,
    onShotSelected: ((EnhancedShotData) -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    @Suppress("UNUSED_VARIABLE")
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
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            WagonWheelHeader(batsmanName, shots, hierarchy.secondary.primary)
            Spacer(modifier = Modifier.height(16.dp))

            WagonWheelCanvasBox(
                shots = shots,
                selectedShot = selectedShot,
                showTrailAnimation = showTrailAnimation,
                trailAlpha = trailAlpha,
                pulseScale = pulseScale,
                primaryColor = hierarchy.secondary.primary,
                onShotTap = {
                    selectedShot = it
                    onShotSelected?.invoke(it)
                },
                onSectorTap = { name, sectorShots ->
                    selectedSector = name
                    onSectorSelected?.invoke(name, sectorShots)
                }
            )

            selectedShot?.let { shot ->
                Spacer(modifier = Modifier.height(12.dp))
                SelectedShotInfo(shot)
            }
        }
    }
}

@Composable
private fun WagonWheelHeader(
    batsmanName: String,
    shots: List<EnhancedShotData>,
    accentColor: Color
) {
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
            Text(text = batsmanName, fontSize = 12.sp, color = accentColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBadge(shots.count { it.runs == 4 }.toString(), "4s", StatusInfo)
            StatBadge(shots.count { it.runs == 6 }.toString(), "6s", accentColor)
        }
    }
}

@Composable
private fun WagonWheelCanvasBox(
    shots: List<EnhancedShotData>,
    selectedShot: EnhancedShotData?,
    showTrailAnimation: Boolean,
    trailAlpha: Float,
    pulseScale: Float,
    primaryColor: Color,
    onShotTap: (EnhancedShotData) -> Unit,
    onSectorTap: (String, List<EnhancedShotData>) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .border(
                2.dp,
                Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.4f),
                        GlassBorder,
                        primaryColor.copy(alpha = 0.6f),
                        GlassBorder
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
                            onShotTap(clickedShot)
                        } else {
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90
                            val normalizedAngle = if (angle < 0) angle + 360 else angle
                            val sectorName = sectorNameFor(normalizedAngle)
                            val sectorShots = shots.filter { shot ->
                                val shotAngle = (shot.angle + 90 + 360) % 360
                                sectorNameFor(shotAngle.toDouble()) == sectorName
                            }
                            onSectorTap(sectorName, sectorShots)
                        }
                    }
                }
        ) {
            drawWagonWheel(
                shots = shots,
                selectedShot = selectedShot,
                showTrailAnimation = showTrailAnimation,
                trailAlpha = trailAlpha,
                pulseScale = pulseScale,
                primaryColor = primaryColor
            )
        }
    }
}

private fun DrawScope.drawWagonWheel(
    shots: List<EnhancedShotData>,
    selectedShot: EnhancedShotData?,
    showTrailAnimation: Boolean,
    trailAlpha: Float,
    pulseScale: Float,
    primaryColor: Color
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val maxRadius = min(centerX, centerY) * 0.85f
    val center = Offset(centerX, centerY)

    drawWagonWheelBackground(center, maxRadius, primaryColor)
    drawPitchRect(centerX, centerY)

    shots.forEach { shot ->
        drawShot(
            shot = shot,
            center = center,
            centerX = centerX,
            centerY = centerY,
            maxRadius = maxRadius,
            isSelected = shot == selectedShot,
            showTrailAnimation = showTrailAnimation,
            trailAlpha = trailAlpha,
            pulseScale = pulseScale,
            primaryColor = primaryColor
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primaryColor, primaryColor),
            center = center
        ),
        radius = 10.dp.toPx(),
        center = center
    )
}

private fun DrawScope.drawWagonWheelBackground(center: Offset, maxRadius: Float, primaryColor: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1E4D0F), Color(0xFF2A5F18), Color(0xFF1A4510)),
            center = center
        ),
        radius = maxRadius + 15.dp.toPx(),
        center = center
    )

    val centerX = center.x
    val centerY = center.y
    listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { angle ->
        val angleRad = Math.toRadians(angle.toDouble() - 90)
        val endX = centerX + (cos(angleRad) * maxRadius).toFloat()
        val endY = centerY + (sin(angleRad) * maxRadius).toFloat()
        drawLine(
            color = Color.White.copy(alpha = 0.1f),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 1.dp.toPx()
        )
    }

    listOf(0.33f, 0.66f, 1f).forEach { ratio ->
        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = maxRadius * ratio,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }

    drawCircle(
        color = primaryColor.copy(alpha = 0.6f),
        radius = maxRadius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawPitchRect(centerX: Float, centerY: Float) {
    val pitchWidth = 18.dp.toPx()
    val pitchHeight = 45.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF8B5A2B), Color(0xFF6B4423))
        ),
        topLeft = Offset(centerX - pitchWidth / 2, centerY - pitchHeight / 2),
        size = Size(pitchWidth, pitchHeight),
        cornerRadius = CornerRadius(2.dp.toPx())
    )
}

private fun shotVelocityColor(shot: EnhancedShotData, primaryColor: Color): Color = when {
    shot.isWicket -> StatusLive
    shot.runs == 6 -> primaryColor
    shot.runs == 4 -> StatusInfo
    else -> Color(
        red = lerp(StatusInfo.red, ChartOrange.red, shot.velocity),
        green = lerp(StatusInfo.green, ChartOrange.green, shot.velocity),
        blue = lerp(StatusInfo.blue, ChartOrange.blue, shot.velocity),
        alpha = 1f
    )
}

private fun DrawScope.shotLineWidth(runs: Int): Float = when (runs) {
    6 -> 4.dp.toPx()
    4 -> 3.5.dp.toPx()
    else -> 2.5.dp.toPx()
}

private fun DrawScope.shotDotRadius(runs: Int): Float = when (runs) {
    6 -> 8.dp.toPx()
    4 -> 7.dp.toPx()
    else -> 5.dp.toPx()
}

@Suppress("LongParameterList")
private fun DrawScope.drawShot(
    shot: EnhancedShotData,
    center: Offset,
    centerX: Float,
    centerY: Float,
    maxRadius: Float,
    isSelected: Boolean,
    showTrailAnimation: Boolean,
    trailAlpha: Float,
    pulseScale: Float,
    primaryColor: Color
) {
    val angleRad = Math.toRadians(shot.angle.toDouble() - 90)
    val distance = shot.distance * maxRadius
    val endPoint = shotEndOffset(centerX, centerY, shot.angle, distance)
    val velocityColor = shotVelocityColor(shot, primaryColor)
    val lineWidth = shotLineWidth(shot.runs)

    if (showTrailAnimation && shot.runs >= 4) {
        val trailPoints = 5
        for (i in 1..trailPoints) {
            val ratio = i.toFloat() / trailPoints
            val trailX = centerX + (cos(angleRad) * distance * ratio).toFloat()
            val trailY = centerY + (sin(angleRad) * distance * ratio).toFloat()
            drawCircle(
                color = velocityColor.copy(alpha = trailAlpha * (1 - ratio) * 0.5f),
                radius = 6.dp.toPx() * (1 - ratio),
                center = Offset(trailX, trailY)
            )
        }
    }

    if (shot.runs >= 4) {
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, velocityColor.copy(alpha = 0.4f)),
                start = center,
                end = endPoint
            ),
            start = center,
            end = endPoint,
            strokeWidth = lineWidth * 3
        )
    }

    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(velocityColor.copy(alpha = 0.4f), velocityColor),
            start = center,
            end = endPoint
        ),
        start = center,
        end = endPoint,
        strokeWidth = lineWidth,
        cap = StrokeCap.Round
    )

    val dotRadius = shotDotRadius(shot.runs)
    if (isSelected) {
        drawCircle(
            color = primaryColor,
            radius = dotRadius * pulseScale + 4.dp.toPx(),
            center = endPoint,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(velocityColor, velocityColor.copy(alpha = 0.7f)),
            center = endPoint
        ),
        radius = dotRadius,
        center = endPoint
    )
}

@Composable
private fun SelectedShotInfo(shot: EnhancedShotData) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val accentColor = when {
        shot.isWicket -> StatusLive
        shot.runs == 6 -> hierarchy.secondary.primary
        else -> StatusInfo
    }
    val label = when {
        shot.isWicket -> "OUT"
        shot.runs == 6 -> "SIX"
        shot.runs == 4 -> "FOUR"
        else -> "${shot.runs}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
            Text(text = "Over ${shot.over}.${shot.ball}", fontSize = 11.sp, color = TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Speed: ${(shot.velocity * 100).toInt()}%",
                fontSize = 11.sp,
                color = ChartOrange
            )
            Box(
                modifier = Modifier
                    .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}

// endregion

// region Pitch Map

private fun lineForRelX(relX: Float): String = when {
    relX < 0.33f -> "offside"
    relX < 0.66f -> "middle"
    else -> "legside"
}

private fun lengthForRelY(relY: Float): String = when {
    relY < 0.25f -> "short"
    relY < 0.5f -> "good"
    relY < 0.75f -> "full"
    else -> "yorker"
}

private fun heatColorForLength(length: String): Color = when (length) {
    "yorker" -> Yorker
    "full" -> FullLength
    "good" -> GoodLength
    "short" -> ShortPitch
    else -> StatusInfo
}

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
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Pitch Map", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
                            val relX = (offset.x - pitchLeft) / pitchWidth
                            val relY = (offset.y - pitchTop) / pitchHeight
                            if (relX in 0f..1f && relY in 0f..1f) {
                                val line = lineForRelX(relX)
                                val length = lengthForRelY(relY)
                                val zone = zones.find { it.length == length && it.line == line }
                                selectedZone = zone
                                zone?.let { onZoneSelected?.invoke(it) }
                            }
                        }
                    }
            ) {
                drawPitchMap(zones, selectedZone, showHeatMap, textMeasurer)
            }

            Spacer(modifier = Modifier.height(12.dp))
            PitchMapLegend()

            selectedZone?.let { zone ->
                Spacer(modifier = Modifier.height(12.dp))
                ZoneStatsRow(zone)
            }
        }
    }
}

private fun DrawScope.drawPitchMap(
    zones: List<BowlingZone>,
    selectedZone: BowlingZone?,
    showHeatMap: Boolean,
    textMeasurer: TextMeasurer
) {
    val pitchWidth = size.width * 0.6f
    val pitchHeight = size.height * 0.9f
    val pitchLeft = (size.width - pitchWidth) / 2
    val pitchTop = size.height * 0.05f

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF8B5A2B), Color(0xFF6B4423), Color(0xFF8B5A2B))
        ),
        topLeft = Offset(pitchLeft, pitchTop),
        size = Size(pitchWidth, pitchHeight),
        cornerRadius = CornerRadius(8.dp.toPx())
    )

    val zoneWidth = pitchWidth / 3
    val zoneHeight = pitchHeight / 4
    val lengths = listOf("short", "good", "full", "yorker")
    val lines = listOf("offside", "middle", "legside")
    val maxCount = zones.maxOfOrNull { it.count } ?: 1

    lengths.forEachIndexed { yIndex, length ->
        lines.forEachIndexed { xIndex, line ->
            val zoneX = pitchLeft + xIndex * zoneWidth
            val zoneY = pitchTop + yIndex * zoneHeight
            val zone = zones.find { it.length == length && it.line == line }
            drawPitchZone(
                zone = zone,
                length = length,
                maxCount = maxCount,
                isSelected = zone == selectedZone,
                showHeatMap = showHeatMap,
                topLeft = Offset(zoneX, zoneY),
                cellSize = Size(zoneWidth, zoneHeight),
                textMeasurer = textMeasurer
            )
        }
    }

    drawPitchCreaseAndStumps(pitchLeft, pitchTop, pitchWidth, pitchHeight)
}

@Suppress("LongParameterList")
private fun DrawScope.drawPitchZone(
    zone: BowlingZone?,
    length: String,
    maxCount: Int,
    isSelected: Boolean,
    showHeatMap: Boolean,
    topLeft: Offset,
    cellSize: Size,
    textMeasurer: TextMeasurer
) {
    if (zone != null && showHeatMap) {
        val intensity = zone.count.toFloat() / maxCount
        drawRect(
            color = heatColorForLength(length).copy(alpha = intensity * 0.6f),
            topLeft = topLeft,
            size = cellSize
        )
    }

    drawRect(
        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.2f),
        topLeft = topLeft,
        size = cellSize,
        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
    )

    zone?.let {
        val textResult = textMeasurer.measure(
            text = it.count.toString(),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            textLayoutResult = textResult,
            color = TextPrimary,
            topLeft = Offset(
                topLeft.x + (cellSize.width - textResult.size.width) / 2,
                topLeft.y + (cellSize.height - textResult.size.height) / 2
            )
        )
    }
}

private fun DrawScope.drawPitchCreaseAndStumps(
    pitchLeft: Float,
    pitchTop: Float,
    pitchWidth: Float,
    pitchHeight: Float
) {
    val creaseY = pitchTop + pitchHeight * 0.85f
    drawLine(
        color = Color.White.copy(alpha = 0.8f),
        start = Offset(pitchLeft, creaseY),
        end = Offset(pitchLeft + pitchWidth, creaseY),
        strokeWidth = 2.dp.toPx()
    )

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

@Composable
private fun PitchMapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LengthLegendItem("Yorker", Yorker)
        LengthLegendItem("Full", FullLength)
        LengthLegendItem("Good", GoodLength)
        LengthLegendItem("Short", ShortPitch)
    }
}

@Composable
private fun ZoneStatsRow(zone: BowlingZone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        ZoneStat(zone.count.toString(), "Balls", TextPrimary)
        ZoneStat(zone.runs.toString(), "Runs", ChartGreen)
        ZoneStat(zone.wickets.toString(), "Wickets", StatusLive)
    }
}

@Composable
private fun ZoneStat(value: String, label: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, color = valueColor)
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
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
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}

// endregion

// region Manhattan graph

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
    val groupFactor = if (team2Data != null) 2.5f else 1.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ManhattanHeader(team1Name, team2Name, team1Color, team2Color, team2Data != null)
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(team1Data, team2Data) {
                        detectTapGestures { offset ->
                            val padding = 40.dp.toPx()
                            val chartWidth = size.width - padding * 2
                            val barWidth = chartWidth / (team1Data.size * groupFactor)
                            val groupWidth = barWidth * (if (team2Data != null) 2f else 1f)
                            val tappedOver = team1Data.indices.find { index ->
                                val x = padding + index * (barWidth * groupFactor)
                                offset.x >= x && offset.x <= x + groupWidth
                            }
                            selectedOver = tappedOver ?: -1
                            tappedOver?.let { over ->
                                onOverSelected?.invoke(over, team1Data[over], team2Data?.getOrNull(over))
                            }
                        }
                    }
            ) {
                drawManhattan(
                    team1Data = team1Data,
                    team2Data = team2Data,
                    team1Color = team1Color,
                    team2Color = team2Color,
                    selectedOver = selectedOver,
                    maxRuns = maxRuns,
                    groupFactor = groupFactor,
                    textMeasurer = textMeasurer
                )
            }

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
private fun ManhattanHeader(
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    hasTeam2: Boolean
) {
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
            if (hasTeam2) LegendItem(team2Name, team2Color)
        }
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawManhattan(
    team1Data: List<ManhattanOverData>,
    team2Data: List<ManhattanOverData>?,
    team1Color: Color,
    team2Color: Color,
    selectedOver: Int,
    maxRuns: Int,
    groupFactor: Float,
    textMeasurer: TextMeasurer
) {
    val padding = 40.dp.toPx()
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2
    val barWidth = chartWidth / (team1Data.size * groupFactor)
    val barSpacing = barWidth * 0.25f

    drawManhattanGridLines(padding, chartHeight, maxRuns, textMeasurer)

    team1Data.forEachIndexed { index, data ->
        val x = padding + index * (barWidth * groupFactor)
        val isSelected = index == selectedOver

        drawManhattanBar(
            x = x,
            runs = data.runs,
            wickets = data.wickets,
            color = team1Color,
            selectedColor = GoldPrimary,
            isSelected = isSelected,
            barWidth = barWidth,
            chartHeight = chartHeight,
            padding = padding,
            maxRuns = maxRuns
        )

        team2Data?.getOrNull(index)?.let { team2Over ->
            drawManhattanBar(
                x = x + barWidth + barSpacing,
                runs = team2Over.runs,
                wickets = team2Over.wickets,
                color = team2Color,
                selectedColor = GoldPrimary.copy(alpha = 0.7f),
                isSelected = isSelected,
                barWidth = barWidth,
                chartHeight = chartHeight,
                padding = padding,
                maxRuns = maxRuns
            )
        }

        if (index % 5 == 0 || index == team1Data.lastIndex) {
            drawManhattanOverLabel(index, x, barWidth, padding, textMeasurer)
        }
    }
}

private fun DrawScope.drawManhattanGridLines(
    padding: Float,
    chartHeight: Float,
    maxRuns: Int,
    textMeasurer: TextMeasurer
) {
    for (i in 0..4) {
        val y = padding + chartHeight * i / 4
        drawLine(
            color = GlassBorder,
            start = Offset(padding, y),
            end = Offset(size.width - padding, y),
            strokeWidth = 1.dp.toPx()
        )
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
}

@Suppress("LongParameterList")
private fun DrawScope.drawManhattanBar(
    x: Float,
    runs: Int,
    wickets: Int,
    color: Color,
    selectedColor: Color,
    isSelected: Boolean,
    barWidth: Float,
    chartHeight: Float,
    padding: Float,
    maxRuns: Int
) {
    val barHeight = (runs.toFloat() / maxRuns) * chartHeight
    val y = padding + chartHeight - barHeight
    val barColor = if (isSelected) selectedColor else color
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(barColor, barColor.copy(alpha = 0.7f))
        ),
        topLeft = Offset(x, y),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    for (w in 0 until wickets) {
        drawCircle(
            color = StatusLive,
            radius = 3.dp.toPx(),
            center = Offset(x + barWidth / 2, y - 6.dp.toPx() - w * 8.dp.toPx())
        )
    }
}

private fun DrawScope.drawManhattanOverLabel(
    index: Int,
    x: Float,
    barWidth: Float,
    padding: Float,
    textMeasurer: TextMeasurer
) {
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
        Text(text = name, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun TeamOverColumn(name: String, data: ManhattanOverData, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = name, fontSize = 10.sp, color = color)
        Text(text = "${data.runs} runs", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
        if (data.wickets > 0) {
            Text(text = "${data.wickets}W", color = StatusLive, fontSize = 10.sp)
        }
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
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Over $over",
            fontWeight = FontWeight.Bold,
            color = hierarchy.secondary.primary,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TeamOverColumn(team1Name, team1Data, team1Color)
            team2Data?.let { TeamOverColumn(team2Name, it, team2Color) }
        }
    }
}

// endregion

// region Win probability gauge

@Composable
fun WinProbabilityGaugeAnimated(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    confidenceBand: Float = 0.1f,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
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
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WIN PROBABILITY",
                color = hierarchy.secondary.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            GaugeArcWithLabel(
                animatedProbability = animatedProbability,
                pulseAlpha = pulseAlpha,
                team1Name = team1Name,
                team1Color = team1Color,
                confidenceBand = confidenceBand
            )

            Spacer(modifier = Modifier.height(20.dp))

            GaugeProbabilityBar(
                animatedProbability = animatedProbability,
                team1Name = team1Name,
                team2Name = team2Name,
                team1Color = team1Color,
                team2Color = team2Color
            )
        }
    }
}

@Composable
private fun GaugeArcWithLabel(
    animatedProbability: Float,
    pulseAlpha: Float,
    team1Name: String,
    team1Color: Color,
    confidenceBand: Float
) {
    Box(
        modifier = Modifier.size(220.dp, 120.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGaugeArc(animatedProbability, pulseAlpha, team1Color, confidenceBand)
        }

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
}

private fun DrawScope.drawGaugeArc(
    animatedProbability: Float,
    pulseAlpha: Float,
    team1Color: Color,
    confidenceBand: Float
) {
    val strokeWidth = 16.dp.toPx()
    val radius = size.width / 2 - strokeWidth
    val center = Offset(size.width / 2, size.height)
    val arcTopLeft = Offset(strokeWidth, size.height - radius)
    val arcSize = Size(radius * 2, radius * 2)

    drawArc(
        color = GlassSurface,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    val bandStart = ((animatedProbability - confidenceBand).coerceIn(0f, 1f)) * 180f
    val bandEnd = ((animatedProbability + confidenceBand).coerceIn(0f, 1f)) * 180f

    drawArc(
        color = team1Color.copy(alpha = 0.2f),
        startAngle = 180f + bandStart,
        sweepAngle = bandEnd - bandStart,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Butt)
    )

    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(team1Color, team1Color.copy(alpha = 0.8f)),
            center = center
        ),
        startAngle = 180f,
        sweepAngle = 180f * animatedProbability,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    drawGaugeNeedle(center, radius, animatedProbability, pulseAlpha, team1Color)
}

private fun DrawScope.drawGaugeNeedle(
    center: Offset,
    radius: Float,
    animatedProbability: Float,
    pulseAlpha: Float,
    team1Color: Color
) {
    val needleAngle = 180f + (180f * animatedProbability)
    val needleRadians = Math.toRadians(needleAngle.toDouble())
    val needleLength = radius + 10.dp.toPx()
    val needleEnd = Offset(
        center.x + (needleLength * cos(needleRadians)).toFloat(),
        center.y + (needleLength * sin(needleRadians)).toFloat()
    )

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

    drawCircle(color = Surface1, radius = 12.dp.toPx(), center = center)
    drawCircle(color = team1Color, radius = 6.dp.toPx(), center = center)
}

@Composable
private fun GaugeProbabilityBar(
    animatedProbability: Float,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = team1Name.uppercase(), fontSize = 10.sp, color = team1Color, fontWeight = FontWeight.Bold)
            Text(
                text = "${(animatedProbability * 100).toInt()}%",
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )
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
            Text(
                text = "${((1 - animatedProbability) * 100).toInt()}%",
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// endregion

// region Run rate chart

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
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            RunRateHeader(currentColor, requiredColor, projectedColor, projectedRunRates != null)
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                drawRunRateChart(
                    currentRunRates = currentRunRates,
                    requiredRunRates = requiredRunRates,
                    projectedRunRates = projectedRunRates,
                    currentColor = currentColor,
                    requiredColor = requiredColor,
                    projectedColor = projectedColor,
                    animatedProgress = animatedProgress,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

@Composable
private fun RunRateHeader(
    currentColor: Color,
    requiredColor: Color,
    projectedColor: Color,
    hasProjected: Boolean
) {
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
            if (hasProjected) LegendItem("Projected", projectedColor)
        }
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawRunRateChart(
    currentRunRates: List<Float>,
    requiredRunRates: List<Float>,
    projectedRunRates: List<Float>?,
    currentColor: Color,
    requiredColor: Color,
    projectedColor: Color,
    animatedProgress: Float,
    textMeasurer: TextMeasurer
) {
    val padding = 40.dp.toPx()
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2

    val allRates = currentRunRates + requiredRunRates + (projectedRunRates ?: emptyList())
    val maxRate = (allRates.maxOrNull() ?: 10f) * 1.1f
    val minRate = 0f
    val range = maxRate - minRate

    drawRunRateGrid(padding, chartHeight, maxRate, range, textMeasurer)

    drawRunRateLine(currentRunRates, currentColor, padding, chartWidth, chartHeight, minRate, range, animatedProgress)
    drawRunRateLine(requiredRunRates, requiredColor, padding, chartWidth, chartHeight, minRate, range, animatedProgress)
    projectedRunRates?.let {
        drawRunRateLine(it, projectedColor, padding, chartWidth, chartHeight, minRate, range, animatedProgress, isDashed = true)
    }
}

private fun DrawScope.drawRunRateGrid(
    padding: Float,
    chartHeight: Float,
    maxRate: Float,
    range: Float,
    textMeasurer: TextMeasurer
) {
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
}

@Suppress("LongParameterList")
private fun DrawScope.drawRunRateLine(
    data: List<Float>,
    color: Color,
    padding: Float,
    chartWidth: Float,
    chartHeight: Float,
    minRate: Float,
    range: Float,
    animatedProgress: Float,
    isDashed: Boolean = false
) {
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

    val stroke = if (isDashed) {
        Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    } else {
        Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
    drawPath(path = path, color = color, style = stroke)
}

// endregion

// region Pitch heat map

@Composable
fun PitchHeatMap(
    heatData: List<List<Float>>,
    title: String = "Pitch Heat Map",
    modifier: Modifier = Modifier,
    lowColor: Color = StatusInfo,
    highColor: Color = StatusLive.copy(alpha = 0.8f),
    onZoneTapped: ((Int, Int, Float) -> Unit)? = null
) {
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
                drawHeatMap(heatData, selectedCell, lowColor, highColor, textMeasurer)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HeatMapLegend(lowColor, highColor)
        }
    }
}

private fun DrawScope.drawHeatMap(
    heatData: List<List<Float>>,
    selectedCell: Pair<Int, Int>?,
    lowColor: Color,
    highColor: Color,
    textMeasurer: TextMeasurer
) {
    val pitchWidth = size.width * 0.7f
    val pitchHeight = size.height * 0.9f
    val pitchLeft = (size.width - pitchWidth) / 2
    val pitchTop = size.height * 0.05f

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF6B4423), Color(0xFF8B5A2B), Color(0xFF6B4423))
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
            drawHeatMapCell(
                row = row,
                col = col,
                value = value,
                maxValue = maxValue,
                isSelected = selectedCell == Pair(row, col),
                topLeft = Offset(pitchLeft + col * cellWidth, pitchTop + row * cellHeight),
                cellSize = Size(cellWidth, cellHeight),
                lowColor = lowColor,
                highColor = highColor,
                textMeasurer = textMeasurer
            )
        }
    }

    drawHeatMapAxisLabels(pitchLeft, pitchTop, pitchWidth, pitchHeight, cellWidth, cellHeight, textMeasurer)
}

@Suppress("LongParameterList", "UNUSED_PARAMETER")
private fun DrawScope.drawHeatMapCell(
    row: Int,
    col: Int,
    value: Float,
    maxValue: Float,
    isSelected: Boolean,
    topLeft: Offset,
    cellSize: Size,
    lowColor: Color,
    highColor: Color,
    textMeasurer: TextMeasurer
) {
    val intensity = if (maxValue > 0) value / maxValue else 0f
    val cellColor = Color(
        red = lerp(lowColor.red, highColor.red, intensity),
        green = lerp(lowColor.green, highColor.green, intensity),
        blue = lerp(lowColor.blue, highColor.blue, intensity),
        alpha = 0.3f + intensity * 0.5f
    )

    drawRect(color = cellColor, topLeft = topLeft, size = cellSize)
    drawRect(
        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.15f),
        topLeft = topLeft,
        size = cellSize,
        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
    )

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
                topLeft.x + (cellSize.width - textResult.size.width) / 2,
                topLeft.y + (cellSize.height - textResult.size.height) / 2
            )
        )
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawHeatMapAxisLabels(
    pitchLeft: Float,
    pitchTop: Float,
    pitchWidth: Float,
    pitchHeight: Float,
    cellWidth: Float,
    cellHeight: Float,
    textMeasurer: TextMeasurer
) {
    listOf("Short", "Good", "Full", "Yorker").forEachIndexed { index, label ->
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

    listOf("Off", "Mid", "Leg").forEachIndexed { index, label ->
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

@Composable
private fun HeatMapLegend(lowColor: Color, highColor: Color) {
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
                    Brush.horizontalGradient(colors = listOf(lowColor, highColor))
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "High", fontSize = 10.sp, color = TextSecondary)
    }
}

// endregion

// region Small shared composables

@Composable
private fun StatBadge(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 9.sp, color = TextSecondary)
    }
}

// endregion
