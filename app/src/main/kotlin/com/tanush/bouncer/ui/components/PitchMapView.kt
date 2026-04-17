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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import io.sentry.Sentry

/**
 * Data class representing a ball delivery on the pitch
 */
data class PitchDelivery(
    val id: String,
    val x: Float,        // 0-1 range, where 0.5 is middle stump line
    val y: Float,        // 0-1 range, where 0 is bowler end, 1 is batsman end
    val result: DeliveryResult,
    val speed: Float? = null,
    val bowlerName: String? = null,
    val over: Int? = null,
    val ball: Int? = null
)

enum class DeliveryResult {
    DOT,
    SINGLE,
    TWO,
    THREE,
    FOUR,
    SIX,
    WICKET,
    WIDE,
    NO_BALL,
    LEG_BYE,
    BYE
}

/**
 * Premium Pitch Map visualization showing ball pitch locations
 */
@Composable
fun PitchMapView(
    deliveries: List<PitchDelivery>,
    modifier: Modifier = Modifier,
    onDeliveryClick: ((PitchDelivery) -> Unit)? = null,
    showLegend: Boolean = true,
    title: String = "Pitch Map"
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    var selectedDelivery by remember { mutableStateOf<PitchDelivery?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pitchAnim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
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
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.3).sp
                )
                
                Text(
                    text = "${deliveries.size} balls",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = hierarchy.secondary.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Capture colors for Canvas scope
            val dotBallColor = colors.textMuted
            val chartBlueColor = colors.statusInfo
            val boundaryFourColor = colors.statusInfo
            val boundarySixColor = colors.statusWarning
            val wicketRedColor = colors.statusLive
            val chartPurpleColor = colors.statusInfo
            val selectionColor = hierarchy.secondary.primary
            
            // Pitch Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                hierarchy.secondary.primary.copy(alpha = 0.3f + shimmerOffset * 0.2f),
                                colors.glassBorder,
                                hierarchy.secondary.primary.copy(alpha = 0.2f)
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(deliveries) {
                            detectTapGestures { offset ->
                                // Find clicked delivery
                                val clicked = deliveries.find { delivery ->
                                    val dx = offset.x - delivery.x * size.width
                                    val dy = offset.y - delivery.y * size.height
                                    sqrt(dx * dx + dy * dy) < 24.dp.toPx()
                                }
                                selectedDelivery = clicked
                                clicked?.let { onDeliveryClick?.invoke(it) }
                            }
                        }
                ) {
                    val pitchWidth = size.width
                    val pitchHeight = size.height
                    
                    // Pitch background - grass texture effect
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A3D0C),
                                Color(0xFF234F12),
                                Color(0xFF1A3D0C),
                                Color(0xFF234F12),
                                Color(0xFF1A3D0C)
                            )
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                    
                    // Grass lines effect
                    val lineSpacing = 20.dp.toPx()
                    var currentY = 0f
                    while (currentY < pitchHeight) {
                        drawLine(
                            color = Color(0xFF2A5F18).copy(alpha = 0.3f),
                            start = Offset(0f, currentY),
                            end = Offset(pitchWidth, currentY),
                            strokeWidth = 1.dp.toPx()
                        )
                        currentY += lineSpacing
                    }
                    
                    // Wicket areas (brown strip)
                    val stripWidth = pitchWidth * 0.35f
                    val stripLeft = (pitchWidth - stripWidth) / 2
                    
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF6B4423),
                                Color(0xFF8B5A2B),
                                Color(0xFF6B4423),
                                Color(0xFF8B5A2B),
                                Color(0xFF6B4423)
                            )
                        ),
                        topLeft = Offset(stripLeft, 0f),
                        size = Size(stripWidth, pitchHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    
                    // Crease lines
                    val creaseColor = Color(0xFFE5E5E5)
                    val creaseStroke = 2.dp.toPx()
                    
                    // Bowling crease (top)
                    val bowlingCreaseY = pitchHeight * 0.08f
                    drawLine(
                        color = creaseColor,
                        start = Offset(stripLeft - 20.dp.toPx(), bowlingCreaseY),
                        end = Offset(stripLeft + stripWidth + 20.dp.toPx(), bowlingCreaseY),
                        strokeWidth = creaseStroke
                    )
                    
                    // Popping crease (top)
                    val poppingCreaseTopY = pitchHeight * 0.15f
                    drawLine(
                        color = creaseColor.copy(alpha = 0.7f),
                        start = Offset(stripLeft - 30.dp.toPx(), poppingCreaseTopY),
                        end = Offset(stripLeft + stripWidth + 30.dp.toPx(), poppingCreaseTopY),
                        strokeWidth = creaseStroke * 0.8f
                    )
                    
                    // Batting crease (bottom)
                    val battingCreaseY = pitchHeight * 0.92f
                    drawLine(
                        color = creaseColor,
                        start = Offset(stripLeft - 20.dp.toPx(), battingCreaseY),
                        end = Offset(stripLeft + stripWidth + 20.dp.toPx(), battingCreaseY),
                        strokeWidth = creaseStroke
                    )
                    
                    // Popping crease (bottom)
                    val poppingCreaseBottomY = pitchHeight * 0.85f
                    drawLine(
                        color = creaseColor.copy(alpha = 0.7f),
                        start = Offset(stripLeft - 30.dp.toPx(), poppingCreaseBottomY),
                        end = Offset(stripLeft + stripWidth + 30.dp.toPx(), poppingCreaseBottomY),
                        strokeWidth = creaseStroke * 0.8f
                    )
                    
                    // Stumps at both ends
                    val stumpWidth = 3.dp.toPx()
                    val stumpHeight = 12.dp.toPx()
                    val stumpSpacing = 10.dp.toPx()
                    val centerX = pitchWidth / 2
                    
                    // Bowler's stumps
                    for (i in -1..1) {
                        drawRoundRect(
                            color = creaseColor,
                            topLeft = Offset(centerX + i * stumpSpacing - stumpWidth / 2, bowlingCreaseY - stumpHeight),
                            size = Size(stumpWidth, stumpHeight),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }
                    
                    // Batsman's stumps
                    for (i in -1..1) {
                        drawRoundRect(
                            color = creaseColor,
                            topLeft = Offset(centerX + i * stumpSpacing - stumpWidth / 2, battingCreaseY),
                            size = Size(stumpWidth, stumpHeight),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }
                    
                    // Length zones (subtle overlay)
                    val zones = listOf(
                        Triple(0.15f to 0.35f, FullLength, "Full"),
                        Triple(0.35f to 0.55f, GoodLength, "Good"),
                        Triple(0.55f to 0.75f, ShortPitch, "Short"),
                        Triple(0.75f to 0.85f, Yorker.copy(alpha = 0.15f), "Back")
                    )
                    
                    zones.forEach { (range, color, _) ->
                        drawRect(
                            color = color.copy(alpha = 0.08f),
                            topLeft = Offset(stripLeft, pitchHeight * range.first),
                            size = Size(stripWidth, pitchHeight * (range.second - range.first))
                        )
                    }
                    
                    // Draw deliveries
                    deliveries.forEach { delivery ->
                        val x = delivery.x * pitchWidth
                        val y = delivery.y * pitchHeight
                        val isSelected = delivery == selectedDelivery
                        val baseRadius = 8.dp.toPx()
                        val radius = if (isSelected) baseRadius * pulseScale else baseRadius
                        
                        val color = when (delivery.result) {
                            DeliveryResult.DOT -> dotBallColor
                            DeliveryResult.SINGLE, DeliveryResult.TWO, DeliveryResult.THREE -> chartBlueColor
                            DeliveryResult.FOUR -> boundaryFourColor
                            DeliveryResult.SIX -> boundarySixColor
                            DeliveryResult.WICKET -> wicketRedColor
                            DeliveryResult.WIDE, DeliveryResult.NO_BALL -> WarningOrange
                            DeliveryResult.LEG_BYE, DeliveryResult.BYE -> chartPurpleColor
                        }
                        
                        // Glow effect
                        if (delivery.result != DeliveryResult.DOT) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.4f),
                                        Color.Transparent
                                    ),
                                    center = Offset(x, y)
                                ),
                                radius = radius * 2.5f,
                                center = Offset(x, y)
                            )
                        }
                        
                        // Main dot
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color,
                                    color.copy(alpha = 0.8f)
                                ),
                                center = Offset(x, y)
                            ),
                            radius = radius,
                            center = Offset(x, y)
                        )
                        
                        // Highlight
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = radius * 0.4f,
                            center = Offset(x - radius * 0.2f, y - radius * 0.2f)
                        )
                        
                        // Selection ring
                        if (isSelected) {
                            drawCircle(
                                color = selectionColor,
                                radius = radius + 4.dp.toPx(),
                                center = Offset(x, y),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
            
            // Selected delivery info
            selectedDelivery?.let { delivery ->
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.08f).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = delivery.bowlerName ?: "Unknown Bowler",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (delivery.over != null && delivery.ball != null) {
                            Text(
                                text = "Over ${delivery.over}.${delivery.ball}",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delivery.speed?.let { speed ->
                            Text(
                                text = "${speed.toInt()} km/h",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = hierarchy.secondary.primary
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(
                                    getResultColor(delivery.result, colors).copy(alpha = 0.2f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = getResultText(delivery.result),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = getResultColor(delivery.result, colors)
                            )
                        }
                    }
                }
            }
            
            // Legend
            if (showLegend) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem(color = colors.textMuted, label = "Dot")
                    LegendItem(color = colors.statusInfo, label = "Runs")
                    LegendItem(color = colors.statusInfo, label = "4s")
                    LegendItem(color = colors.statusWarning, label = "6s")
                    LegendItem(color = colors.statusLive, label = "W")
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    val colors = BouncerDesignSystem.colors
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
            fontSize = 11.sp,
            color = colors.textSecondary
        )
    }
}

private fun getResultColor(result: DeliveryResult, colors: BouncerColors): Color = when (result) {
    DeliveryResult.DOT -> colors.textMuted
    DeliveryResult.SINGLE, DeliveryResult.TWO, DeliveryResult.THREE -> colors.statusInfo
    DeliveryResult.FOUR -> colors.statusInfo
    DeliveryResult.SIX -> colors.statusWarning
    DeliveryResult.WICKET -> colors.statusLive
    DeliveryResult.WIDE, DeliveryResult.NO_BALL -> WarningOrange
    DeliveryResult.LEG_BYE, DeliveryResult.BYE -> colors.statusInfo
}

private fun getResultText(result: DeliveryResult): String = when (result) {
    DeliveryResult.DOT -> "DOT"
    DeliveryResult.SINGLE -> "1"
    DeliveryResult.TWO -> "2"
    DeliveryResult.THREE -> "3"
    DeliveryResult.FOUR -> "FOUR"
    DeliveryResult.SIX -> "SIX"
    DeliveryResult.WICKET -> "OUT"
    DeliveryResult.WIDE -> "WD"
    DeliveryResult.NO_BALL -> "NB"
    DeliveryResult.LEG_BYE -> "LB"
    DeliveryResult.BYE -> "B"
}
