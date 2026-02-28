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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Line and length classification
 */
enum class DeliveryLine(val label: String, val xRange: ClosedFloatingPointRange<Float>) {
    WIDE_OUTSIDE_OFF("Wide Outside Off", 0.85f..1.0f),
    OUTSIDE_OFF("Outside Off", 0.7f..0.85f),
    OFF_STUMP("Off Stump", 0.55f..0.7f),
    MIDDLE("Middle", 0.45f..0.55f),
    LEG_STUMP("Leg Stump", 0.3f..0.45f),
    OUTSIDE_LEG("Outside Leg", 0.15f..0.3f),
    DOWN_LEG("Down Leg", 0.0f..0.15f)
}

enum class DeliveryLength(val label: String, val yRange: ClosedFloatingPointRange<Float>) {
    FULL_TOSS("Full Toss", 0.0f..0.1f),
    YORKER("Yorker", 0.1f..0.2f),
    FULL("Full", 0.2f..0.35f),
    GOOD("Good Length", 0.35f..0.55f),
    SHORT_PITCH("Short Pitch", 0.55f..0.75f),
    BOUNCER("Bouncer", 0.75f..1.0f)
}

/**
 * Delivery data model
 */
data class Delivery(
    val x: Float, // 0-1, 0 = leg side, 1 = off side (for right-hander)
    val y: Float, // 0-1, 0 = full, 1 = short
    val runs: Int = 0,
    val isWicket: Boolean = false,
    val isBoundary: Boolean = false,
    val isSix: Boolean = false,
    val isDot: Boolean = false,
    val ballNumber: Int = 0
) {
    val line: DeliveryLine
        get() = DeliveryLine.values().find { x in it.xRange } ?: DeliveryLine.MIDDLE
    
    val length: DeliveryLength
        get() = DeliveryLength.values().find { y in it.yRange } ?: DeliveryLength.GOOD
}

/**
 * MEGA UI UPGRADE: PitchMapCard (v2.0).
 * High-fidelity delivery placement visualization with technical broadcast aesthetics.
 */
@Composable
fun PitchMapCard(
    deliveries: List<Delivery>,
    bowlerName: String,
    isRightHandedBatsman: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pitchmap")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f).copy(alpha = 0.8f))
            .border(
                0.5.dp,
                Brush.linearGradient(listOf(GoldHairline, Color.White.copy(alpha = 0.05f))),
                RoundedCornerShape(28.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Technical Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "BALL TRACKING",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = bowlerName.uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ChartBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${deliveries.size} DELIVERIES",
                        color = ChartBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Pitch Matrix
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.6f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pitchWidth = size.width
                    val pitchHeight = size.height
                    
                    // High-fidelity pitch surface
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2D2418),
                                Color(0xFF3D3222),
                                Color(0xFF2D2418)
                            )
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                    
                    // Crease Markings
                    val creaseY = pitchHeight * 0.12f
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(pitchWidth * 0.15f, creaseY),
                        end = Offset(pitchWidth * 0.85f, creaseY),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Stumps Logic
                    val stumpSpacing = 14.dp.toPx()
                    val stumpWidth = 4.dp.toPx()
                    for (i in -1..1) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = Offset(pitchWidth / 2 + i * stumpSpacing - stumpWidth / 2, creaseY - 18.dp.toPx()),
                            size = Size(stumpWidth, 18.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                    
                    // Technical Length Dividers
                    val lengths = listOf(0.15f, 0.35f, 0.6f, 0.85f)
                    lengths.forEach { y ->
                        val lineY = creaseY + (pitchHeight - creaseY) * y
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, lineY),
                            end = Offset(pitchWidth, lineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                        )
                    }
                    
                    // Delivery Visuals
                    deliveries.forEach { delivery ->
                        val xPos = if (isRightHandedBatsman) delivery.x else 1f - delivery.x
                        val dotX = pitchWidth * 0.2f + pitchWidth * 0.6f * xPos
                        val dotY = creaseY + (pitchHeight - creaseY) * delivery.y
                        
                        val markerColor = when {
                            delivery.isWicket -> ErrorRed
                            delivery.isSix -> SixerPurple
                            delivery.isBoundary -> BoundaryFour
                            delivery.isDot -> SuccessGreen
                            else -> Color.White.copy(alpha = 0.3f)
                        }
                        
                        val markerSize = if (delivery.isWicket) 6.dp.toPx() * pulseScale else 4.dp.toPx()
                        
                        if (delivery.isWicket || delivery.isBoundary) {
                            drawCircle(
                                color = markerColor.copy(alpha = 0.2f),
                                radius = markerSize + 8.dp.toPx(),
                                center = Offset(dotX, dotY)
                            )
                        }
                        
                        drawCircle(
                            color = markerColor,
                            radius = markerSize,
                            center = Offset(dotX, dotY)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Tech Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PitchLegendItem(ErrorRed, "WICKET")
                PitchLegendItem(BoundaryFour, "BOUNDARY")
                PitchLegendItem(SuccessGreen, "DOT")
                PitchLegendItem(Color.White.copy(alpha = 0.2f), "OTHER")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Analytics Breakdown
            PitchTechnicalStats(deliveries)
        }
    }
}

@Composable
private fun PitchLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun PitchTechnicalStats(deliveries: List<Delivery>) {
    val wickets = deliveries.count { it.isWicket }
    val dots = deliveries.count { it.isDot }
    val boundaries = deliveries.count { it.isBoundary || it.isSix }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PitchStatBlock("TOTAL", deliveries.size.toString(), Color.White)
            VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
            PitchStatBlock("WICKETS", wickets.toString(), ErrorRed)
            VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
            PitchStatBlock("DOTS", dots.toString(), SuccessGreen)
            VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
            PitchStatBlock("LIMITS", boundaries.toString(), BoundaryFour)
        }
    }
}

@Composable
private fun PitchStatBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun MiniPitchMap(
    deliveries: List<Delivery>,
    modifier: Modifier = Modifier.size(80.dp, 120.dp)
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        drawRoundRect(color = Color(0xFF2D2418), cornerRadius = CornerRadius(8.dp.toPx()))
        deliveries.take(20).forEach { delivery ->
            drawCircle(
                color = if (delivery.isWicket) ErrorRed else Color.White.copy(alpha = 0.3f),
                radius = 2.dp.toPx(),
                center = Offset(size.width * delivery.x, size.height * delivery.y)
            )
        }
    }
}
