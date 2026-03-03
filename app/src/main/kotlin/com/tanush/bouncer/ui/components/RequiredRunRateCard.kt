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
 * Chase data
 */
data class ChaseData(
    val target: Int,
    val currentScore: Int,
    val wicketsLost: Int,
    val currentOver: Float,
    val totalOvers: Float,
    val currentRunRate: Float,
    val requiredRunRate: Float,
    val runRateHistory: List<RunRatePoint> = emptyList(),
    val projectedScore: Int? = null
)

data class RunRatePoint(
    val over: Float,
    val currentRR: Float,
    val requiredRR: Float
)

/**
 * Premium Required Run Rate Tracker
 * Shows chase progress with dynamic visualization
 */
@Composable
fun RequiredRunRateCard(
    data: ChaseData,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "rrr")
    
    // Pulse for urgency
    val urgencyPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (data.requiredRunRate > 12) 500 else 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "urgency"
    )
    
    // Progress animation
    val progressAnimation by animateFloatAsState(
        targetValue = data.currentScore.toFloat() / data.target,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    val rrrColor = when {
        data.requiredRunRate <= data.currentRunRate + 1 -> colors.statusSuccess
        data.requiredRunRate <= data.currentRunRate + 3 -> colors.goldPrimary
        data.requiredRunRate <= data.currentRunRate + 5 -> colors.statusWarning
        else -> colors.statusLive
    }
    
    val chaseStatus = when {
        data.currentScore >= data.target -> "TARGET ACHIEVED"
        data.wicketsLost >= 10 -> "ALL OUT"
        data.currentOver >= data.totalOvers -> "INNINGS COMPLETE"
        data.requiredRunRate <= data.currentRunRate -> "ON TRACK"
        data.requiredRunRate <= data.currentRunRate + 2 -> "TIGHT CHASE"
        data.requiredRunRate <= data.currentRunRate + 4 -> "ASKING RATE CLIMBING"
        else -> "TOUGH ASK"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = rrrColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            rrrColor.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp),
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
                        text = "CHASE TRACKER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = colors.goldPrimary
                    )
                    Text(
                        text = chaseStatus,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = rrrColor
                    )
                }
                
                // Target display
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.goldPrimary.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TARGET",
                            fontSize = 8.sp,
                            letterSpacing = 1.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "${data.target}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.goldPrimary
                        )
                    }
                }
            }
            
            // Main visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // Circular progress
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2 - 16.dp.toPx()
                    val strokeWidth = 12.dp.toPx()
                    
                    // Background track
                    drawArc(
                        color = GlassBorder.copy(alpha = 0.2f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Progress arc
                    val progressSweep = 270f * progressAnimation
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to colors.statusSuccess.copy(alpha = 0.7f),
                                0.5f to colors.goldPrimary,
                                1f to rrrColor
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        startAngle = 135f,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Current position indicator
                    val endAngle = Math.toRadians((135 + progressSweep).toDouble())
                    val indicatorX = centerX + (radius * cos(endAngle)).toFloat()
                    val indicatorY = centerY + (radius * sin(endAngle)).toFloat()
                    
                    // Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                rrrColor.copy(alpha = 0.5f * urgencyPulse),
                                Color.Transparent
                            ),
                            center = Offset(indicatorX, indicatorY),
                            radius = 20.dp.toPx()
                        ),
                        radius = 20.dp.toPx(),
                        center = Offset(indicatorX, indicatorY)
                    )
                    
                    // Indicator dot
                    drawCircle(
                        color = rrrColor,
                        radius = 8.dp.toPx(),
                        center = Offset(indicatorX, indicatorY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(indicatorX, indicatorY)
                    )
                    
                    // Inner decorative ring
                    drawCircle(
                        color = GlassBorder.copy(alpha = 0.15f),
                        radius = radius * 0.6f,
                        center = Offset(centerX, centerY),
                        style = Stroke(1.dp.toPx())
                    )
                }
                
                // Center content
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${data.currentScore}/${data.wicketsLost}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "(${data.currentOver})",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
            
            // Runs needed
            val runsNeeded = maxOf(0, data.target - data.currentScore)
            val oversRemaining = maxOf(0f, data.totalOvers - data.currentOver)
            val ballsRemaining = (oversRemaining * 6).toInt()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ChaseStatItem(
                    label = "NEED",
                    value = "$runsNeeded",
                    subValue = "runs",
                    color = rrrColor
                )
                
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(colors.glassBorder.copy(alpha = 0.3f))
                )
                
                ChaseStatItem(
                    label = "FROM",
                    value = "$ballsRemaining",
                    subValue = "balls",
                    color = colors.textPrimary
                )
                
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(colors.glassBorder.copy(alpha = 0.3f))
                )
                
                ChaseStatItem(
                    label = "REQ RR",
                    value = String.format("%.2f", data.requiredRunRate),
                    subValue = "per over",
                    color = rrrColor,
                    highlight = true,
                    pulseAlpha = urgencyPulse
                )
            }
            
            // Run rate comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RunRateCompareItem(
                    label = "CURRENT",
                    value = data.currentRunRate,
                    isGood = data.currentRunRate >= data.requiredRunRate,
                    modifier = Modifier.weight(1f)
                )
                RunRateCompareItem(
                    label = "REQUIRED",
                    value = data.requiredRunRate,
                    isGood = false,
                    isRequired = true,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Projected score if available
            data.projectedScore?.let { projected ->
                val willWin = projected >= data.target
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (willWin) colors.statusSuccess else colors.statusLive).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = (if (willWin) colors.statusSuccess else colors.statusLive).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROJECTED SCORE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = colors.textSecondary
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$projected",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (willWin) colors.statusSuccess else colors.statusLive
                            )
                            Text(
                                text = if (willWin) "✓" else "✗",
                                fontSize = 16.sp,
                                color = if (willWin) colors.statusSuccess else colors.statusLive
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaseStatItem(
    label: String,
    value: String,
    subValue: String,
    color: Color,
    highlight: Boolean = false,
    pulseAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = if (highlight) 22.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) color.copy(alpha = 0.7f + (0.3f * pulseAlpha)) else color
        )
        Text(
            text = subValue,
            fontSize = 9.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun RunRateCompareItem(
    label: String,
    value: Float,
    isGood: Boolean,
    isRequired: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = when {
        isRequired -> StatusLive.copy(alpha = 0.8f)
        isGood -> StatusSuccess
        else -> StatusWarning
    }
    
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
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = TextSecondary
            )
            
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = String.format("%.2f", value),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "RPO",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

/**
 * Compact RRR display
 */
@Composable
fun CompactRRRDisplay(
    requiredRunRate: Float,
    currentRunRate: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        requiredRunRate <= currentRunRate -> StatusSuccess
        requiredRunRate <= currentRunRate + 2 -> StatusWarning
        else -> StatusLive
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RRR",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Text(
            text = String.format("%.2f", requiredRunRate),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
