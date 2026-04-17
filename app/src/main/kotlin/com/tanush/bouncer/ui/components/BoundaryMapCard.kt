package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import io.sentry.Sentry

/**
 * Boundary data
 */
data class BoundaryMapData(
    val playerName: String? = null,
    val teamName: String? = null,
    val boundaries: List<BoundaryHit>,
    val totalFours: Int,
    val totalSixes: Int
)

data class BoundaryHit(
    val angle: Float, // 0-360 degrees from straight
    val distance: Float, // 0-1 normalized (1 = over the ropes)
    val isSix: Boolean,
    val runs: Int = if (isSix) 6 else 4,
    val batsmanName: String? = null,
    val overNumber: Float? = null
)

/**
 * Premium Boundary Map Card
 * Shows boundary locations around the cricket field
 */
@Composable
fun BoundaryMapCard(
    data: BoundaryMapData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boundary")
    
    // Glow pulse for sixes
    val sixGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sixGlow"
    )
    
    // Subtle rotation for the field
    val fieldRotation by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fieldRotation"
    )

    val textMeasurer = rememberTextMeasurer()

    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BOUNDARY MAP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = hierarchy.secondary.primary
                    )
                    data.playerName?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    } ?: data.teamName?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
                
                // Boundary counts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BoundaryCountBadge(
                        count = data.totalFours,
                        isSix = false
                    )
                    BoundaryCountBadge(
                        count = data.totalSixes,
                        isSix = true
                    )
                }
            }
            
            // Cricket field with boundary markers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val outerRadius = size.minDimension / 2 - 16.dp.toPx()
                    val innerRadius = outerRadius * 0.4f
                    val pitchWidth = 16.dp.toPx()
                    val pitchHeight = outerRadius * 0.35f
                    
                    rotate(fieldRotation, pivot = Offset(centerX, centerY)) {
                        // Outer boundary rope
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1a472a).copy(alpha = 0.6f),
                                    Color(0xFF0d2818).copy(alpha = 0.8f)
                                ),
                                center = Offset(centerX, centerY),
                                radius = outerRadius
                            ),
                            radius = outerRadius,
                            center = Offset(centerX, centerY)
                        )
                        
                        // Rope
                        drawCircle(
                            color = colors.textPrimary.copy(alpha = 0.5f),
                            radius = outerRadius,
                            center = Offset(centerX, centerY),
                            style = Stroke(3.dp.toPx())
                        )
                        
                        // 30-yard circle
                        drawCircle(
                            color = colors.textPrimary.copy(alpha = 0.25f),
                            radius = innerRadius,
                            center = Offset(centerX, centerY),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10f, 10f)
                                )
                            )
                        )
                        
                        // Pitch rectangle
                        val pitchPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = centerX - pitchWidth / 2,
                                    top = centerY - pitchHeight / 2,
                                    right = centerX + pitchWidth / 2,
                                    bottom = centerY + pitchHeight / 2,
                                    cornerRadius = CornerRadius(3.dp.toPx())
                                )
                            )
                        }
                        
                        drawPath(
                            path = pitchPath,
                            color = Color(0xFFD4A574).copy(alpha = 0.7f)
                        )
                        
                        drawPath(
                            path = pitchPath,
                            color = colors.textPrimary.copy(alpha = 0.5f),
                            style = Stroke(1.dp.toPx())
                        )
                        
                        // Crease lines
                        drawLine(
                            color = colors.textPrimary.copy(alpha = 0.6f),
                            start = Offset(centerX - 20.dp.toPx(), centerY - pitchHeight * 0.35f),
                            end = Offset(centerX + 20.dp.toPx(), centerY - pitchHeight * 0.35f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = colors.textPrimary.copy(alpha = 0.6f),
                            start = Offset(centerX - 20.dp.toPx(), centerY + pitchHeight * 0.35f),
                            end = Offset(centerX + 20.dp.toPx(), centerY + pitchHeight * 0.35f),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // Zone labels (simplified)
                        val zones = listOf(
                            0f to "Straight",
                            90f to "Point",
                            180f to "Fine Leg",
                            270f to "Midwicket"
                        )
                        
                        zones.forEach { (angle, _) ->
                            val labelAngle = Math.toRadians((angle - 90).toDouble())
                            val labelRadius = outerRadius + 8.dp.toPx()
                            val labelX = centerX + (cos(labelAngle) * labelRadius).toFloat()
                            val labelY = centerY + (sin(labelAngle) * labelRadius).toFloat()
                            
                            drawCircle(
                                color = colors.textPrimary.copy(alpha = 0.3f),
                                radius = 3.dp.toPx(),
                                center = Offset(labelX, labelY)
                            )
                        }
                    }
                    
                    // Draw boundary hits
                    data.boundaries.forEach { boundary ->
                        // Convert angle (0 = straight, clockwise)
                        val radianAngle = Math.toRadians((boundary.angle - 90).toDouble())
                        val distance = outerRadius * boundary.distance.coerceIn(0.3f, 0.95f)
                        val x = centerX + (cos(radianAngle) * distance).toFloat()
                        val y = centerY + (sin(radianAngle) * distance).toFloat()
                        
                        val boundaryColor = if (boundary.isSix) hierarchy.secondary.primary else colors.statusInfo
                        val glowIntensity = if (boundary.isSix) sixGlow else 0.7f
                        
                        // Glow effect
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    boundaryColor.copy(alpha = 0.4f * glowIntensity),
                                    boundaryColor.copy(alpha = 0.1f * glowIntensity),
                                    Color.Transparent
                                ),
                                center = Offset(x, y),
                                radius = if (boundary.isSix) 20.dp.toPx() else 14.dp.toPx()
                            ),
                            radius = if (boundary.isSix) 20.dp.toPx() else 14.dp.toPx(),
                            center = Offset(x, y)
                        )
                        
                        // Main marker
                        drawCircle(
                            color = boundaryColor,
                            radius = if (boundary.isSix) 8.dp.toPx() else 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                        
                        // Inner highlight
                        drawCircle(
                            color = colors.textPrimary.copy(alpha = 0.6f),
                            radius = if (boundary.isSix) 4.dp.toPx() else 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                        
                        // Line to boundary (for sixes)
                        if (boundary.isSix && boundary.distance > 0.9f) {
                            val lineEndAngle = Math.toRadians((boundary.angle - 90).toDouble())
                            val lineEndX = centerX + (cos(lineEndAngle) * outerRadius).toFloat()
                            val lineEndY = centerY + (sin(lineEndAngle) * outerRadius).toFloat()
                            
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        hierarchy.secondary.primary.copy(alpha = 0.1f),
                                        hierarchy.secondary.primary.copy(alpha = 0.5f)
                                    ),
                                    start = Offset(x, y),
                                    end = Offset(lineEndX, lineEndY)
                                ),
                                start = Offset(x, y),
                                end = Offset(lineEndX, lineEndY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoundaryLegend(color = colors.statusInfo, label = "Fours")
                Spacer(modifier = Modifier.width(24.dp))
                BoundaryLegend(color = hierarchy.secondary.primary, label = "Sixes")
            }
            
            // Zone distribution
            val zoneCounts = calculateZoneCounts(data.boundaries)
            if (zoneCounts.isNotEmpty()) {
                ZoneDistribution(zoneCounts = zoneCounts)
            }
        }
    }
}

@Composable
private fun BoundaryCountBadge(
    count: Int,
    isSix: Boolean,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val color = if (isSix) hierarchy.secondary.primary else StatusInfo
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSix) "6️⃣" else "4️⃣",
                fontSize = 14.sp
            )
            Text(
                text = "$count",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun BoundaryLegend(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
                .border(
                    width = 2.dp,
                    color = colors.textPrimary.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
    }
}

private fun calculateZoneCounts(boundaries: List<BoundaryHit>): Map<String, Int> {
    try {
        val zones = mutableMapOf<String, Int>()
    
        boundaries.forEach { boundary ->
            val zone = when {
                boundary.angle in 315f..360f || boundary.angle in 0f..45f -> "Straight"
                boundary.angle in 45f..90f -> "Cover"
                boundary.angle in 90f..135f -> "Point"
                boundary.angle in 135f..180f -> "Third Man"
                boundary.angle in 180f..225f -> "Fine Leg"
                boundary.angle in 225f..270f -> "Square Leg"
                boundary.angle in 270f..315f -> "Midwicket"
                else -> "Other"
            }
            zones[zone] = (zones[zone] ?: 0) + 1
        }
    
        return zones
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

@Composable
private fun ZoneDistribution(
    zoneCounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val total = zoneCounts.values.sum().toFloat()
    val sortedZones = zoneCounts.entries.sortedByDescending { it.value }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ZONE DISTRIBUTION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = colors.textSecondary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sortedZones.take(4).forEach { (zone, count) ->
                val percentage = (count / total * 100).toInt()
                
                ZoneItem(
                    zoneName = zone,
                    count = count,
                    percentage = percentage,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ZoneItem(
    zoneName: String,
    count: Int,
    percentage: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colors.glassBorder.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$percentage%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = hierarchy.secondary.primary
            )
            Text(
                text = zoneName,
                fontSize = 9.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = "($count)",
                fontSize = 8.sp,
                color = colors.textSecondary.copy(alpha = 0.7f)
            )
        }
    }
}
