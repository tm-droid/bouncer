package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Field placement visualization showing fielder positions
 */

data class FieldPosition(
    val x: Float, // 0-1 normalized
    val y: Float, // 0-1 normalized
    val playerName: String = "",
    val positionName: String = "",
    val isSpecial: Boolean = false // Unusual or tactical position
)

data class FieldPlacementInfo(
    val positions: List<FieldPosition>,
    val formation: FormationType = FormationType.STANDARD,
    val slipsCount: Int = 0,
    val catchersBehind: Int = 0,
    val savingOne: Boolean = false,
    val savingBoundary: Boolean = false,
    val isBowlerFriendly: Boolean = true
)

enum class FormationType(val displayName: String) {
    ATTACKING("Attacking"),
    DEFENSIVE("Defensive"),
    STANDARD("Standard"),
    POWERPLAY("Powerplay"),
    DEATH_OVERS("Death Overs"),
    SPINNER("Spinner Friendly"),
    PACER("Pacer Friendly")
}

@Composable
fun FieldPlacementCard(
    fieldInfo: FieldPlacementInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "field_animation")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )
    
    val formationColor = when (fieldInfo.formation) {
        FormationType.ATTACKING -> colors.statusLive
        FormationType.DEFENSIVE -> colors.statusInfo
        FormationType.POWERPLAY -> hierarchy.secondary.primary
        FormationType.DEATH_OVERS -> colors.statusWarning
        FormationType.SPINNER -> Color(0xFFE040FB)
        FormationType.PACER -> colors.statusSuccess
        FormationType.STANDARD -> hierarchy.secondary.primary
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Field Placement",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Formation type badge
                Surface(
                    color = formationColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = fieldInfo.formation.displayName.uppercase(),
                        color = formationColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Field visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val fieldRadius = minOf(size.width, size.height) / 2 - 15f
                    
                    // Field background
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1A472A).copy(alpha = 0.4f),
                                Color(0xFF1A472A).copy(alpha = 0.2f),
                                Color(0xFF1A472A).copy(alpha = 0.1f)
                            ),
                            center = center,
                            radius = fieldRadius
                        ),
                        radius = fieldRadius,
                        center = center
                    )
                    
                    // Boundary rope
                    drawCircle(
                        color = Color.White.copy(alpha = 0.4f),
                        radius = fieldRadius,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                    
                    // 30 yard circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = fieldRadius * 0.45f,
                        center = center,
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                        )
                    )
                    
                    // Pitch in center
                    val pitchWidth = 16f
                    val pitchHeight = 35f
                    drawRect(
                        color = Color(0xFFD4B896).copy(alpha = 0.5f),
                        topLeft = Offset(center.x - pitchWidth / 2, center.y - pitchHeight / 2),
                        size = Size(pitchWidth, pitchHeight)
                    )
                    
                    // Radar sweep effect
                    val sweepAngle = radarRotation * (PI / 180f).toFloat()
                    val sweepPath = Path().apply {
                        moveTo(center.x, center.y)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                center.x - fieldRadius,
                                center.y - fieldRadius,
                                center.x + fieldRadius,
                                center.y + fieldRadius
                            ),
                            startAngleDegrees = radarRotation,
                            sweepAngleDegrees = 30f,
                            forceMoveTo = false
                        )
                        close()
                    }
                    
                    drawPath(
                        path = sweepPath,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                formationColor.copy(alpha = 0.1f * pulseAlpha),
                                Color.Transparent
                            ),
                            center = center
                        )
                    )
                    
                    // Draw fielder positions
                    fieldInfo.positions.forEach { position ->
                        // Convert normalized coordinates to field coordinates
                        val fieldX = center.x + (position.x - 0.5f) * 2 * fieldRadius
                        val fieldY = center.y + (position.y - 0.5f) * 2 * fieldRadius
                        
                        // Check if within boundary
                        val distFromCenter = kotlin.math.sqrt(
                            (fieldX - center.x).pow(2) + (fieldY - center.y).pow(2)
                        )
                        
                        if (distFromCenter <= fieldRadius) {
                            // Glow for special positions
                            if (position.isSpecial) {
                                drawCircle(
                                    color = formationColor.copy(alpha = 0.4f * pulseAlpha),
                                    radius = 16f,
                                    center = Offset(fieldX, fieldY)
                                )
                            }
                            
                            // Fielder marker
                            drawCircle(
                                color = Color.White,
                                radius = 10f,
                                center = Offset(fieldX, fieldY)
                            )
                            drawCircle(
                                color = if (position.isSpecial) formationColor else colors.statusInfo,
                                radius = 7f,
                                center = Offset(fieldX, fieldY)
                            )
                        }
                    }
                    
                    // Batsman position (center)
                    drawCircle(
                        color = hierarchy.secondary.primary,
                        radius = 8f,
                        center = center
                    )
                    drawCircle(
                        color = hierarchy.secondary.primary,
                        radius = 5f,
                        center = center
                    )
                }
                
                // Position labels overlay
                Text(
                    text = "COVER",
                    color = TextMuted,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                )
                
                Text(
                    text = "SQUARE LEG",
                    color = TextMuted,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                )
                
                Text(
                    text = "LONG ON",
                    color = TextMuted,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
                
                Text(
                    text = "THIRD MAN",
                    color = TextMuted,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Field stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FieldStatBox(
                    label = "Slips",
                    value = "${fieldInfo.slipsCount}",
                    color = colors.statusLive
                )
                FieldStatBox(
                    label = "Behind Wicket",
                    value = "${fieldInfo.catchersBehind}",
                    color = colors.statusInfo
                )
                FieldStatBox(
                    label = "In Circle",
                    value = "${fieldInfo.positions.count { 
                        val dist = sqrt((it.x - 0.5f).pow(2) + (it.y - 0.5f).pow(2))
                        dist < 0.225f // Approximately 30 yard circle
                    }}",
                    color = hierarchy.secondary.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Strategy indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StrategyIndicator(
                    label = "Saving Single",
                    isActive = fieldInfo.savingOne,
                    activeColor = colors.statusSuccess
                )
                StrategyIndicator(
                    label = "Saving Boundary",
                    isActive = fieldInfo.savingBoundary,
                    activeColor = hierarchy.secondary.primary
                )
                StrategyIndicator(
                    label = "Bowler Friendly",
                    isActive = fieldInfo.isBowlerFriendly,
                    activeColor = colors.statusInfo
                )
            }
        }
    }
}

@Composable
private fun FieldStatBox(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StrategyIndicator(
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else TextMuted)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else TextSecondary,
            fontSize = 10.sp
        )
    }
}
