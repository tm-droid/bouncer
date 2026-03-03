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
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Data class representing a scoring shot for the wagon wheel
 */
data class WagonWheelShot(
    val id: String,
    val angle: Float,      // Angle in degrees (0 = straight, positive = leg side)
    val distance: Float,   // 0-1 range, 1 being max (6)
    val runs: Int,
    val isWicket: Boolean = false,
    val shotType: String? = null,
    val batsmanName: String? = null,
    val over: Int? = null,
    val ball: Int? = null
)

/**
 * Premium Wagon Wheel visualization for batsman scoring areas
 */
@Composable
fun WagonWheelView(
    shots: List<WagonWheelShot>,
    batsmanName: String = "Batsman",
    modifier: Modifier = Modifier,
    onShotClick: ((WagonWheelShot) -> Unit)? = null,
    showStats: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    // Capture colors for Canvas scopes (non-composable)
    val goldPrimaryColor = colors.goldPrimary
    val goldDarkColor = colors.goldDark
    val glassBorderColor = colors.glassBorder
    val statusWarningColor = colors.statusWarning
    val statusLiveColor = colors.statusLive
    val statusSuccessColor = colors.statusSuccess
    val statusInfoColor = colors.statusInfo
    val textMutedColor = colors.textMuted
    
    var selectedShot by remember { mutableStateOf<WagonWheelShot?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "wagonAnim")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.12f),
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
                Column {
                    Text(
                        text = "Wagon Wheel",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = batsmanName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.goldPrimary
                    )
                }
                
                // Quick stats
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBadge(
                        value = shots.filter { it.runs == 4 }.size.toString(),
                        label = "4s",
                        color = colors.statusSuccess
                    )
                    StatBadge(
                        value = shots.filter { it.runs == 6 }.size.toString(),
                        label = "6s",
                        color = colors.statusWarning
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
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
                                colors.goldPrimary.copy(alpha = 0.3f),
                                colors.glassBorder,
                                colors.goldPrimary.copy(alpha = 0.5f),
                                colors.glassBorder,
                                colors.goldPrimary.copy(alpha = 0.3f)
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
                                
                                // Find clicked shot
                                val clicked = shots.find { shot ->
                                    val shotAngleRad = Math.toRadians(shot.angle.toDouble() - 90)
                                    val maxRadius = min(centerX, centerY) * 0.85f
                                    val shotX = centerX + (cos(shotAngleRad) * maxRadius * shot.distance).toFloat()
                                    val shotY = centerY + (sin(shotAngleRad) * maxRadius * shot.distance).toFloat()
                                    
                                    val dx = offset.x - shotX
                                    val dy = offset.y - shotY
                                    sqrt(dx * dx + dy * dy) < 20.dp.toPx()
                                }
                                selectedShot = clicked
                                clicked?.let { onShotClick?.invoke(it) }
                            }
                        }
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val maxRadius = min(centerX, centerY) * 0.85f
                    
                    // Field background - cricket ground green
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E4D0F),
                                Color(0xFF2A5F18),
                                Color(0xFF1A4510)
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        radius = maxRadius + 20.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    
                    // Grass texture rings
                    for (i in 1..8) {
                        val ringRadius = maxRadius * (i / 8f)
                        drawCircle(
                            color = Color(0xFF2D6A1E).copy(alpha = 0.1f + (i * 0.02f)),
                            radius = ringRadius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    
                    // Boundary circle with glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                goldPrimaryColor.copy(alpha = pulseAlpha * 0.3f)
                            ),
                            center = Offset(centerX, centerY),
                            radius = maxRadius + 15.dp.toPx()
                        ),
                        radius = maxRadius + 15.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    
                    drawCircle(
                        color = goldPrimaryColor.copy(alpha = 0.6f),
                        radius = maxRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // Inner circles (30 yard circle, etc)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = maxRadius * 0.4f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    // Pitch in center
                    val pitchWidth = 20.dp.toPx()
                    val pitchHeight = 50.dp.toPx()
                    
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF8B5A2B),
                                Color(0xFF6B4423),
                                Color(0xFF8B5A2B)
                            )
                        ),
                        topLeft = Offset(centerX - pitchWidth / 2, centerY - pitchHeight / 2),
                        size = Size(pitchWidth, pitchHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                    
                    // Direction sectors (subtle)
                    val sectors = listOf(
                        "Fine Leg" to -45f,
                        "Square Leg" to -90f,
                        "Mid Wicket" to -135f,
                        "Straight" to 180f,
                        "Cover" to 135f,
                        "Point" to 90f,
                        "Third Man" to 45f,
                        "Behind" to 0f
                    )
                    
                    // Draw sector lines
                    sectors.forEach { (_, angle) ->
                        val angleRad = Math.toRadians(angle.toDouble() - 90)
                        val endX = centerX + (cos(angleRad) * maxRadius).toFloat()
                        val endY = centerY + (sin(angleRad) * maxRadius).toFloat()
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(centerX, centerY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    // Draw shots
                    shots.forEach { shot ->
                        val angleRad = Math.toRadians(shot.angle.toDouble() - 90) // -90 to make 0 = straight ahead
                        val distance = shot.distance * maxRadius
                        val endX = centerX + (cos(angleRad) * distance).toFloat()
                        val endY = centerY + (sin(angleRad) * distance).toFloat()
                        
                        val isSelected = shot == selectedShot
                        
                        val color = when {
                            shot.isWicket -> statusLiveColor
                            shot.runs == 6 -> statusWarningColor
                            shot.runs == 4 -> statusSuccessColor
                            shot.runs >= 2 -> statusInfoColor
                            shot.runs == 1 -> statusInfoColor
                            else -> textMutedColor
                        }
                        
                        val lineWidth = when {
                            shot.runs == 6 -> 3.5.dp.toPx()
                            shot.runs == 4 -> 3.dp.toPx()
                            shot.runs >= 2 -> 2.5.dp.toPx()
                            else -> 2.dp.toPx()
                        }
                        
                        // Shot line with glow
                        if (shot.runs >= 4) {
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        color.copy(alpha = 0.4f),
                                        color.copy(alpha = 0.2f)
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
                                    color.copy(alpha = 0.4f),
                                    color,
                                    color
                                ),
                                start = Offset(centerX, centerY),
                                end = Offset(endX, endY)
                            ),
                            start = Offset(centerX, centerY),
                            end = Offset(endX, endY),
                            strokeWidth = lineWidth,
                            cap = StrokeCap.Round
                        )
                        
                        // End point
                        val dotRadius = when {
                            shot.runs == 6 -> 8.dp.toPx()
                            shot.runs == 4 -> 7.dp.toPx()
                            shot.runs >= 2 -> 6.dp.toPx()
                            else -> 5.dp.toPx()
                        }
                        
                        // Glow for boundaries
                        if (shot.runs >= 4) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.5f),
                                        Color.Transparent
                                    ),
                                    center = Offset(endX, endY)
                                ),
                                radius = dotRadius * 3,
                                center = Offset(endX, endY)
                            )
                        }
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color,
                                    color.copy(alpha = 0.7f)
                                ),
                                center = Offset(endX, endY)
                            ),
                            radius = dotRadius,
                            center = Offset(endX, endY)
                        )
                        
                        // Selection indicator
                        if (isSelected) {
                            drawCircle(
                                color = goldPrimaryColor,
                                radius = dotRadius + 5.dp.toPx(),
                                center = Offset(endX, endY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    
                    // Batsman indicator at center
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                goldDarkColor,
                                goldPrimaryColor
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        radius = 12.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.4f),
                        radius = 4.dp.toPx(),
                        center = Offset(centerX - 2.dp.toPx(), centerY - 2.dp.toPx())
                    )
                }
            }
            
            // Selected shot info
            selectedShot?.let { shot ->
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
                            text = shot.shotType ?: "Shot",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (shot.over != null && shot.ball != null) {
                            Text(
                                text = "Over ${shot.over}.${shot.ball}",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                when {
                                    shot.isWicket -> colors.statusLive
                                    shot.runs == 6 -> colors.statusWarning
                                    shot.runs == 4 -> colors.statusSuccess
                                    else -> colors.statusInfo
                                }.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when {
                                shot.isWicket -> "OUT"
                                shot.runs == 6 -> "SIX"
                                shot.runs == 4 -> "FOUR"
                                else -> "${shot.runs} runs"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                shot.isWicket -> colors.statusLive
                                shot.runs == 6 -> colors.statusWarning
                                shot.runs == 4 -> colors.statusSuccess
                                else -> colors.statusInfo
                            }
                        )
                    }
                }
            }
            
            // Stats breakdown
            if (showStats && shots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Zone breakdown
                val legSideRuns = shots.filter { it.angle < 0 }.sumOf { it.runs }
                val offSideRuns = shots.filter { it.angle >= 0 }.sumOf { it.runs }
                val totalRuns = legSideRuns + offSideRuns
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ZoneStatItem(
                        label = "Leg Side",
                        runs = legSideRuns,
                        percentage = if (totalRuns > 0) (legSideRuns * 100) / totalRuns else 0,
                        color = colors.statusWarning
                    )
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(colors.glassBorder)
                    )
                    
                    ZoneStatItem(
                        label = "Off Side",
                        runs = offSideRuns,
                        percentage = if (totalRuns > 0) (offSideRuns * 100) / totalRuns else 0,
                        color = colors.statusInfo
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    value: String,
    label: String,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ZoneStatItem(
    label: String,
    runs: Int,
    percentage: Int,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$runs",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "runs",
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            text = "$percentage%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted
        )
    }
}
