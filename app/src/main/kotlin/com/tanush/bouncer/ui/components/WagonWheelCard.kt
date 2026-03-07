package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Shot direction regions on the wagon wheel
 */
enum class ShotRegion(val label: String, val startAngle: Float, val endAngle: Float) {
    FINE_LEG("Fine Leg", 180f, 225f),
    BACKWARD_SQUARE_LEG("Backward SQ Leg", 225f, 270f),
    SQUARE_LEG("Square Leg", 270f, 315f),
    MID_WICKET("Mid Wicket", 315f, 360f),
    MID_ON("Mid On", 0f, 45f),
    MID_OFF("Mid Off", 45f, 90f),
    COVER("Cover", 90f, 135f),
    POINT("Point", 135f, 180f)
}

/**
 * Shot data
 */
data class Shot(
    val region: ShotRegion,
    val runs: Int,
    val isBoundary: Boolean = false,
    val isSix: Boolean = false
)

/**
 * MEGA UI UPGRADE: WagonWheelCard (v2.0).
 * Radar-grade shot analytics with ultra-fidelity broadcast aesthetics.
 */
@Composable
fun WagonWheelCard(
    shots: List<Shot>,
    playerName: String,
    totalRuns: Int,
    modifier: Modifier = Modifier
) {
    val runsByRegion = shots.groupBy { it.region }
        .mapValues { (_, shots) -> shots.sumOf { it.runs } }
    val maxRegionRuns = runsByRegion.values.maxOrNull() ?: 1
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassSurface)
            .border(
                0.5.dp,
                Brush.linearGradient(listOf(GoldHairline, GlassSurface)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header - Technical Broadcast Style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SHOT TELEMETRY",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = playerName.uppercase(),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalRuns",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "TOTAL RUNS",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Wagon Wheel Matrix
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Ambient Glow
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .blur(60.dp)
                        .background(ShinyGoldDeep.copy(alpha = 0.05f), CircleShape)
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f - 12.dp.toPx()
                    
                    // Technical Grid Rings
                    listOf(1f, 0.75f, 0.5f, 0.25f).forEach { scale ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.03f),
                            radius = radius * scale,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    
                    // Sector Dividers (Dashed Telemetry Lines)
                    for (region in ShotRegion.values()) {
                        val angleRad = Math.toRadians(region.startAngle.toDouble() - 90)
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = center,
                            end = Offset(
                                center.x + radius * cos(angleRad).toFloat(),
                                center.y + radius * sin(angleRad).toFloat()
                            ),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

                    // Intensity Zones
                    for (region in ShotRegion.values()) {
                        val regionRuns = runsByRegion[region] ?: 0
                        if (regionRuns > 0) {
                            val intensity = (regionRuns.toFloat() / maxRegionRuns).coerceAtLeast(0.15f)
                            val arcRadius = radius * intensity
                            
                            drawArc(
                                brush = Brush.radialGradient(
                                    listOf(ShinyGoldDeep.copy(alpha = 0.2f * intensity), Color.Transparent),
                                    center = center,
                                    radius = arcRadius
                                ),
                                startAngle = region.startAngle - 90f,
                                sweepAngle = (region.endAngle - region.startAngle),
                                useCenter = true,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = Size(arcRadius * 2, arcRadius * 2)
                            )
                        }
                    }
                    
                    // Precision Shot Tracking
                    shots.forEachIndexed { index, shot ->
                        val angleMid = (shot.region.startAngle + shot.region.endAngle) / 2f - 90f
                        val variation = ((index * 31) % 16 - 8)
                        val angleRad = Math.toRadians((angleMid + variation).toDouble())
                        val distFactor = when {
                            shot.isSix -> 0.95f
                            shot.isBoundary -> 0.85f
                            shot.runs >= 2 -> 0.6f + (index % 10) / 60f
                            else -> 0.35f + (index % 10) / 60f
                        }
                        val distance = radius * distFactor
                        
                        val markerColor = when {
                            shot.isSix -> SixerPurple
                            shot.isBoundary -> BoundaryFour
                            shot.runs >= 2 -> SuccessGreen
                            else -> Color.White.copy(alpha = 0.3f)
                        }
                        
                        drawCircle(
                            color = markerColor,
                            radius = (if (shot.isSix || shot.isBoundary) 3.5.dp else 2.dp).toPx(),
                            center = Offset(center.x + distance * cos(angleRad).toFloat(), center.y + distance * sin(angleRad).toFloat())
                        )
                        
                        // Small glow for boundaries
                        if (shot.isSix || shot.isBoundary) {
                            drawCircle(
                                color = markerColor.copy(alpha = 0.2f),
                                radius = 8.dp.toPx(),
                                center = Offset(center.x + distance * cos(angleRad).toFloat(), center.y + distance * sin(angleRad).toFloat())
                            )
                        }
                    }
                }
                
                // Central Core Data
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(0.5.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = shots.size.toString(), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(text = "SHOTS", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Technical Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TechLegendItem(SixerPurple, "SIXES")
                TechLegendItem(BoundaryFour, "FOURS")
                TechLegendItem(SuccessGreen, "RUNS")
                TechLegendItem(TextMuted, "DOTS")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Regional Breakdown
            RegionalSummaryRow(runsByRegion)
        }
    }
}

@Composable
private fun TechLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun RegionalSummaryRow(runsByRegion: Map<ShotRegion, Int>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RegionalStat("OFF SIDE", listOf(ShotRegion.POINT, ShotRegion.COVER, ShotRegion.MID_OFF), runsByRegion)
        VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = GlassBorder)
        RegionalStat("LEG SIDE", listOf(ShotRegion.MID_ON, ShotRegion.MID_WICKET, ShotRegion.SQUARE_LEG), runsByRegion)
        VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = GlassBorder)
        RegionalStat("BEHIND", listOf(ShotRegion.FINE_LEG, ShotRegion.BACKWARD_SQUARE_LEG), runsByRegion)
    }
}

@Composable
private fun RegionalStat(label: String, regions: List<ShotRegion>, runsByRegion: Map<ShotRegion, Int>) {
    val total = regions.sumOf { runsByRegion[it] ?: 0 }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text(total.toString(), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}
