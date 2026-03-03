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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Batting control data
 */
data class BattingControlData(
    val playerName: String,
    val controlPercentage: Float,
    val shotTypes: List<ShotTypeControl>,
    val falseShotPercentage: Float,
    val mistimingPercentage: Float,
    val middlingPercentage: Float,
    val edgesPercentage: Float
)

data class ShotTypeControl(
    val shotType: String,
    val attempts: Int,
    val controlPercentage: Float
)

/**
 * Premium Batting Control Card
 * Shows shot control visualization with detailed breakdown
 */
@Composable
fun BattingControlCard(
    data: BattingControlData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "control")
    
    // Main gauge animation
    val gaugeAnimation by animateFloatAsState(
        targetValue = data.controlPercentage / 100f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "gaugeAnim"
    )
    
    // Glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    val colors = BouncerDesignSystem.colors
    
    val controlColor = when {
        data.controlPercentage >= 80 -> colors.statusSuccess
        data.controlPercentage >= 60 -> colors.goldPrimary
        data.controlPercentage >= 40 -> colors.statusWarning
        else -> colors.statusLive
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = controlColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BATTING CONTROL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = colors.goldPrimary
                    )
                    Text(
                        text = data.playerName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }
                
                // Control rating badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = controlColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = when {
                            data.controlPercentage >= 80 -> "EXCELLENT"
                            data.controlPercentage >= 60 -> "GOOD"
                            data.controlPercentage >= 40 -> "AVERAGE"
                            else -> "POOR"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = controlColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Main control gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height * 0.7f
                    val radius = minOf(size.width / 2, size.height) * 0.65f
                    val strokeWidth = 24.dp.toPx()
                    
                    // Background arc (semi-circle)
                    drawArc(
                        color = colors.glassBorder.copy(alpha = 0.2f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Gradient sections (red -> yellow -> green)
                    val sections = listOf(
                        colors.statusLive.copy(alpha = 0.3f),
                        colors.statusWarning.copy(alpha = 0.3f),
                        colors.goldPrimary.copy(alpha = 0.3f),
                        colors.statusSuccess.copy(alpha = 0.3f)
                    )
                    
                    sections.forEachIndexed { index, color ->
                        val startAngle = 180f + (index * 45f)
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerX - radius, centerY - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth * 0.5f, cap = StrokeCap.Butt)
                        )
                    }
                    
                    // Animated fill arc
                    val sweepAngle = 180f * gaugeAnimation
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to colors.statusLive,
                                0.25f to colors.statusWarning,
                                0.5f to colors.goldPrimary,
                                0.75f to colors.statusSuccess,
                                1f to colors.statusSuccess
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        startAngle = 180f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Outer glow on control point
                    val needleAngle = Math.toRadians((180 + sweepAngle).toDouble())
                    val needleX = centerX + (radius * cos(needleAngle)).toFloat()
                    val needleY = centerY + (radius * sin(needleAngle)).toFloat()
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                controlColor.copy(alpha = 0.5f * glowPulse),
                                controlColor.copy(alpha = 0.2f * glowPulse),
                                Color.Transparent
                            ),
                            center = Offset(needleX, needleY),
                            radius = 30.dp.toPx()
                        ),
                        radius = 30.dp.toPx(),
                        center = Offset(needleX, needleY)
                    )
                    
                    // Control point
                    drawCircle(
                        color = controlColor,
                        radius = 8.dp.toPx(),
                        center = Offset(needleX, needleY)
                    )
                    
                    drawCircle(
                        color = colors.textPrimary,
                        radius = 4.dp.toPx(),
                        center = Offset(needleX, needleY)
                    )
                    
                    // Center decorative arc
                    drawArc(
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(centerX - radius * 0.4f, centerY - radius * 0.4f),
                        size = Size(radius * 0.8f, radius * 0.8f),
                        style = Stroke(1.dp.toPx())
                    )
                }
                
                // Center percentage display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Text(
                        text = "${data.controlPercentage.toInt()}%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = controlColor
                    )
                    Text(
                        text = "Control Rate",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
            
            // Breakdown stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlBreakdownItem(
                    label = "Middled",
                    percentage = data.middlingPercentage,
                    color = colors.statusSuccess,
                    emoji = "🎯",
                    modifier = Modifier.weight(1f)
                )
                ControlBreakdownItem(
                    label = "Mistimed",
                    percentage = data.mistimingPercentage,
                    color = colors.statusWarning,
                    emoji = "⚡",
                    modifier = Modifier.weight(1f)
                )
                ControlBreakdownItem(
                    label = "Edges",
                    percentage = data.edgesPercentage,
                    color = colors.statusLive.copy(alpha = 0.8f),
                    emoji = "📐",
                    modifier = Modifier.weight(1f)
                )
                ControlBreakdownItem(
                    label = "False",
                    percentage = data.falseShotPercentage,
                    color = colors.statusLive,
                    emoji = "❌",
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Shot type breakdown
            if (data.shotTypes.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SHOT TYPE CONTROL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.textSecondary
                    )
                    
                    data.shotTypes.take(5).forEach { shotType ->
                        ShotTypeControlRow(shotType = shotType)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlBreakdownItem(
    label: String,
    percentage: Float,
    color: Color,
    emoji: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 14.sp
            )
            Text(
                text = "${percentage.toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ShotTypeControlRow(
    shotType: ShotTypeControl,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = shotType.controlPercentage / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "shotProgress"
    )
    
    val colors = BouncerDesignSystem.colors
    
    val shotColor = when {
        shotType.controlPercentage >= 80 -> colors.statusSuccess
        shotType.controlPercentage >= 60 -> colors.goldPrimary
        shotType.controlPercentage >= 40 -> colors.statusWarning
        else -> colors.statusLive
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shot type name
        Text(
            text = shotType.shotType,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.width(80.dp)
        )
        
        // Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(
                    color = colors.glassBorder.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                shotColor.copy(alpha = 0.7f),
                                shotColor
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        
        // Percentage
        Text(
            text = "${shotType.controlPercentage.toInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = shotColor,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
        
        // Attempts count
        Text(
            text = "(${shotType.attempts})",
            fontSize = 10.sp,
            color = colors.textSecondary,
            modifier = Modifier.width(30.dp)
        )
    }
}

/**
 * Compact control indicator
 */
@Composable
fun CompactControlIndicator(
    controlPercentage: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val color = when {
        controlPercentage >= 80 -> colors.statusSuccess
        controlPercentage >= 60 -> colors.goldPrimary
        controlPercentage >= 40 -> colors.statusWarning
        else -> colors.statusLive
    }
    
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🎯",
            fontSize = 10.sp
        )
        Text(
            text = "${controlPercentage.toInt()}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
