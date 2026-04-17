package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
 * Powerplay status card with animated timer and run rate comparison
 */

data class PowerplayInfo(
    val currentOver: Float,
    val totalPowerplayOvers: Int = 6,
    val runs: Int,
    val wickets: Int,
    val runRate: Float,
    val isActive: Boolean = true,
    val previousPowerplays: List<PowerplayComparison> = emptyList(),
    val boundaries: Int = 0,
    val dotBalls: Int = 0,
    val projectedScore: Int? = null,
    val powerplayType: PowerplayType = PowerplayType.MANDATORY
)

data class PowerplayComparison(
    val teamName: String,
    val runs: Int,
    val wickets: Int,
    val matchDate: String = ""
)

enum class PowerplayType(val displayName: String, val overs: String) {
    MANDATORY("Powerplay", "1-6"),
    BATTING("Batting PP", "Choice"),
    BOWLING("Bowling PP", "Choice")
}

@Composable
fun PowerplayStatusCard(
    powerplayInfo: PowerplayInfo,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "powerplay_animation")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val sparkRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spark_rotation"
    )
    
    val progress by animateFloatAsState(
        targetValue = powerplayInfo.currentOver / powerplayInfo.totalPowerplayOvers,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    val oversRemaining = powerplayInfo.totalPowerplayOvers - powerplayInfo.currentOver
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    // Capture colors for Canvas/draw scopes (non-composable)
    val statusWarningColor = colors.statusWarning
    val goldDarkColor = hierarchy.secondary.primary
    val statusInfoColor = colors.statusInfo
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box {
            // Animated energy background
            if (powerplayInfo.isActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    // Radiating energy lines
                    for (i in 0 until 8) {
                        val angle = (sparkRotation + i * 45f) * (PI / 180f).toFloat()
                        val lineLength = size.maxDimension * 0.8f
                        
                        val endX = center.x + lineLength * cos(angle)
                        val endY = center.y + lineLength * sin(angle)
                        
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    statusWarningColor.copy(alpha = 0.4f * glowAlpha),
                                    Color.Transparent
                                ),
                                start = center,
                                end = Offset(endX, endY)
                            ),
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 2f
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (powerplayInfo.isActive) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = colors.statusWarning.copy(alpha = glowAlpha),
                                modifier = Modifier
                                    .size(28.dp)
                                    .scale(pulseScale)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sports,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = powerplayInfo.powerplayType.displayName.uppercase(),
                                    color = if (powerplayInfo.isActive) colors.statusWarning else TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                                if (powerplayInfo.isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(statusInfoColor.copy(alpha = glowAlpha))
                                    )
                                }
                            }
                            Text(
                                text = "Overs ${powerplayInfo.powerplayType.overs}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    
                    // Status badge
                    Surface(
                        color = if (powerplayInfo.isActive) colors.statusWarning.copy(alpha = 0.2f) else colors.statusSuccess.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (powerplayInfo.isActive) "ACTIVE" else "COMPLETED",
                            color = if (powerplayInfo.isActive) colors.statusWarning else colors.statusSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Main score display with progress ring
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Progress ring
                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 12f
                            val radius = (size.minDimension - strokeWidth) / 2
                            val center = Offset(size.width / 2, size.height / 2)
                            
                            // Background ring
                            drawCircle(
                                color = GlassBorder,
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )
                            
                            // Progress ring
                            val sweepAngle = 360f * progress
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        statusWarningColor.copy(alpha = 0.5f),
                                        statusWarningColor,
                                        goldDarkColor
                                    ),
                                    center = center
                                ),
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            
                            // Glowing dot at current position
                            if (powerplayInfo.isActive) {
                                val dotAngle = (-90f + sweepAngle) * (PI / 180f).toFloat()
                                val dotX = center.x + radius * cos(dotAngle)
                                val dotY = center.y + radius * sin(dotAngle)
                                
                                drawCircle(
                                    color = goldDarkColor.copy(alpha = glowAlpha),
                                    radius = 12f,
                                    center = Offset(dotX, dotY)
                                )
                                drawCircle(
                                    color = goldDarkColor,
                                    radius = 6f,
                                    center = Offset(dotX, dotY)
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f", powerplayInfo.currentOver),
                                color = TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "overs",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    
                    // Score
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${powerplayInfo.runs}/${powerplayInfo.wickets}",
                            color = hierarchy.secondary.primary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                        Surface(
                            color = when {
                                powerplayInfo.runRate >= 8f -> colors.statusSuccess.copy(alpha = 0.2f)
                                powerplayInfo.runRate >= 6f -> colors.statusWarning.copy(alpha = 0.2f)
                                else -> colors.statusLive.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "RR: ${String.format("%.2f", powerplayInfo.runRate)}",
                                color = when {
                                    powerplayInfo.runRate >= 8f -> colors.statusSuccess
                                    powerplayInfo.runRate >= 6f -> colors.statusWarning
                                    else -> colors.statusLive
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                
                // Overs remaining indicator
                if (powerplayInfo.isActive && oversRemaining > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = colors.statusWarning.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = colors.statusWarning.copy(alpha = glowAlpha),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${String.format("%.1f", oversRemaining)} overs remaining",
                                color = colors.statusWarning,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            if (powerplayInfo.projectedScore != null) {
                                Text(
                                    text = " • Projected: ${powerplayInfo.projectedScore}",
                                    color = hierarchy.secondary.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PowerplayStatBox(
                        label = "BOUNDARIES",
                        value = "${powerplayInfo.boundaries}",
                        color = colors.statusSuccess
                    )
                    PowerplayStatBox(
                        label = "DOT BALLS",
                        value = "${powerplayInfo.dotBalls}",
                        color = TextSecondary
                    )
                    PowerplayStatBox(
                        label = "BALLS LEFT",
                        value = "${((powerplayInfo.totalPowerplayOvers - powerplayInfo.currentOver) * 6).toInt()}",
                        color = colors.statusInfo
                    )
                }
                
                // Historical comparison
                if (powerplayInfo.previousPowerplays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "COMPARISON",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        powerplayInfo.previousPowerplays.take(3).forEach { comparison ->
                            PowerplayComparisonRow(
                                comparison = comparison,
                                currentRuns = powerplayInfo.runs
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerplayStatBox(
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
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun PowerplayComparisonRow(
    comparison: PowerplayComparison,
    currentRuns: Int
) {
    val diff = currentRuns - comparison.runs
    val isAhead = diff >= 0
    val colors = BouncerDesignSystem.colors
    
    Surface(
        color = GlassSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = comparison.teamName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (comparison.matchDate.isNotEmpty()) {
                    Text(
                        text = comparison.matchDate,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${comparison.runs}/${comparison.wickets}",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Surface(
                    color = if (isAhead) colors.statusSuccess.copy(alpha = 0.2f) else colors.statusLive.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isAhead) "+$diff" else "$diff",
                        color = if (isAhead) colors.statusSuccess else colors.statusLive,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
