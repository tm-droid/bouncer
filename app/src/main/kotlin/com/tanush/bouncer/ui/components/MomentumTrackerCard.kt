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
 * Momentum data point for Tracker Card
 */
data class TrackerMomentumPoint(
    val over: Float,
    val momentumValue: Float, // -100 to +100, positive = team1 momentum
    val event: TrackerMomentumEvent? = null
)

data class TrackerMomentumEvent(
    val type: TrackerMomentumEventType,
    val description: String
)

enum class TrackerMomentumEventType {
    WICKET, BOUNDARY_SPREE, DROPPED_CATCH, MILESTONE, COLLAPSE, FIGHTBACK, POWERPLAY
}

data class TrackerMomentumData(
    val team1Name: String,
    val team2Name: String,
    val currentMomentum: Float, // -100 to +100
    val momentumHistory: List<TrackerMomentumPoint>,
    val team1Color: Color = GoldPrimary,
    val team2Color: Color = StatusInfo
)

/**
 * Premium Momentum Tracker Card
 * Shows match momentum swings with animated visualization
 */
@Composable
fun MomentumTrackerCard(
    data: TrackerMomentumData,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "momentum")
    
    // Pulse animation for current momentum
    val pulseFactor by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Wave animation for momentum line
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    
    val currentColor = if (data.currentMomentum >= 0) data.team1Color else data.team2Color
    val momentumIntensity = abs(data.currentMomentum) / 100f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = currentColor.copy(alpha = 0.2f)
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
                        text = "MOMENTUM TRACKER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = hierarchy.secondary.primary
                    )
                    Text(
                        text = "Match Flow Analysis",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
                
                // Current momentum indicator
                MomentumBadge(
                    momentum = data.currentMomentum,
                    team1Name = data.team1Name,
                    team2Name = data.team2Name,
                    team1Color = data.team1Color,
                    team2Color = data.team2Color
                )
            }
            
            // Team labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(data.team1Color, CircleShape)
                    )
                    Text(
                        text = data.team1Name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = data.team1Color
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.team2Name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = data.team2Color
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(data.team2Color, CircleShape)
                    )
                }
            }
            
            // Momentum graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2
                    
                    // Background zones
                    // Team 1 zone (top half)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                data.team1Color.copy(alpha = 0.08f),
                                data.team1Color.copy(alpha = 0.02f),
                                Color.Transparent
                            )
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(width, centerY)
                    )
                    
                    // Team 2 zone (bottom half)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                data.team2Color.copy(alpha = 0.02f),
                                data.team2Color.copy(alpha = 0.08f)
                            )
                        ),
                        topLeft = Offset(0f, centerY),
                        size = Size(width, centerY)
                    )
                    
                    // Center line (neutral)
                    drawLine(
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    
                    // Grid lines
                    listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                        val y = height * fraction
                        drawLine(
                            color = colors.glassBorder.copy(alpha = 0.1f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }
                    
                    if (data.momentumHistory.isNotEmpty()) {
                        val maxOver = data.momentumHistory.maxOfOrNull { it.over } ?: 20f
                        val stepX = width / maxOf(maxOver, 1f)
                        
                        // Create momentum path
                        val momentumPath = Path()
                        val fillPath = Path()
                        
                        data.momentumHistory.forEachIndexed { index, point ->
                            val x = point.over * stepX
                            // Map momentum from -100..+100 to height..0
                            val y = centerY - (point.momentumValue / 100f * centerY * 0.9f)
                            
                            if (index == 0) {
                                momentumPath.moveTo(x, y)
                                fillPath.moveTo(x, centerY)
                                fillPath.lineTo(x, y)
                            } else {
                                // Smooth curve
                                val prevPoint = data.momentumHistory[index - 1]
                                val prevX = prevPoint.over * stepX
                                val prevY = centerY - (prevPoint.momentumValue / 100f * centerY * 0.9f)
                                val controlX = (prevX + x) / 2
                                
                                momentumPath.cubicTo(
                                    controlX, prevY,
                                    controlX, y,
                                    x, y
                                )
                                fillPath.cubicTo(
                                    controlX, prevY,
                                    controlX, y,
                                    x, y
                                )
                            }
                        }
                        
                        // Close fill path
                        val lastX = data.momentumHistory.last().over * stepX
                        fillPath.lineTo(lastX, centerY)
                        fillPath.close()
                        
                        // Draw fill with gradient based on momentum
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    data.team1Color.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    data.team2Color.copy(alpha = 0.3f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )
                        
                        // Draw main line
                        drawPath(
                            path = momentumPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    currentColor.copy(alpha = 0.5f),
                                    currentColor
                                )
                            ),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Draw current position indicator
                        val lastPoint = data.momentumHistory.last()
                        val lastY = centerY - (lastPoint.momentumValue / 100f * centerY * 0.9f)
                        
                        // Pulsing glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    currentColor.copy(alpha = 0.5f * pulseFactor),
                                    currentColor.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                center = Offset(lastX, lastY),
                                radius = 25.dp.toPx() * pulseFactor
                            ),
                            radius = 25.dp.toPx() * pulseFactor,
                            center = Offset(lastX, lastY)
                        )
                        
                        // Current point
                        drawCircle(
                            color = currentColor,
                            radius = 8.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = colors.textPrimary,
                            radius = 4.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                        
                        // Draw key events
                        data.momentumHistory.forEach { point ->
                            point.event?.let { event ->
                                val eventX = point.over * stepX
                                val eventY = centerY - (point.momentumValue / 100f * centerY * 0.9f)
                                
                                val eventColor = when (event.type) {
                                    TrackerMomentumEventType.WICKET -> colors.statusLive
                                    TrackerMomentumEventType.BOUNDARY_SPREE -> hierarchy.secondary.primary
                                    TrackerMomentumEventType.DROPPED_CATCH -> colors.statusWarning
                                    TrackerMomentumEventType.MILESTONE -> colors.statusSuccess
                                    TrackerMomentumEventType.COLLAPSE -> colors.statusLive
                                    TrackerMomentumEventType.FIGHTBACK -> colors.statusSuccess
                                    TrackerMomentumEventType.POWERPLAY -> colors.statusInfo
                                }
                                
                                drawCircle(
                                    color = eventColor.copy(alpha = 0.3f),
                                    radius = 12.dp.toPx(),
                                    center = Offset(eventX, eventY)
                                )
                                
                                drawCircle(
                                    color = eventColor,
                                    radius = 6.dp.toPx(),
                                    center = Offset(eventX, eventY)
                                )
                            }
                        }
                    }
                }
            }
            
            // Momentum scale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "← ${data.team2Name} dominant",
                    fontSize = 9.sp,
                    color = data.team2Color.copy(alpha = 0.7f)
                )
                Text(
                    text = "${data.team1Name} dominant →",
                    fontSize = 9.sp,
                    color = data.team1Color.copy(alpha = 0.7f)
                )
            }
            
            // Current momentum bar
            CurrentMomentumBar(
                momentum = data.currentMomentum,
                team1Color = data.team1Color,
                team2Color = data.team2Color
            )
        }
    }
}

@Composable
private fun MomentumBadge(
    momentum: Float,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val leadingTeam = if (momentum >= 0) team1Name else team2Name
    val color = if (momentum >= 0) team1Color else team2Color
    val intensity = abs(momentum).toInt()
    
    val intensityLabel = when {
        intensity >= 80 -> "DOMINANT"
        intensity >= 60 -> "STRONG"
        intensity >= 40 -> "SLIGHT"
        intensity >= 20 -> "EDGE"
        else -> "BALANCED"
    }
    
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
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (intensity >= 20) {
                Text(
                    text = leadingTeam.take(3).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Text(
                text = intensityLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = if (intensity >= 20) colors.textSecondary else color
            )
        }
    }
}

@Composable
private fun CurrentMomentumBar(
    momentum: Float,
    team1Color: Color,
    team2Color: Color,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val animatedMomentum by animateFloatAsState(
        targetValue = momentum,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "momentumBar"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(
                color = colors.glassBorder.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        // Center marker
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.glassBorder.copy(alpha = 0.5f))
        )
        
        // Momentum indicator
        val indicatorWidth = (abs(animatedMomentum) / 100f) * 0.5f
        val isTeam1 = animatedMomentum >= 0
        
        Box(
            modifier = Modifier
                .fillMaxWidth(indicatorWidth)
                .fillMaxHeight()
                .align(if (isTeam1) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(start = if (!isTeam1) 0.dp else (0.5f * 1f).dp)
                .offset(x = if (isTeam1) (-0.5f * 1f).dp else 0.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (isTeam1) {
                            listOf(team1Color.copy(alpha = 0.3f), team1Color)
                        } else {
                            listOf(team2Color, team2Color.copy(alpha = 0.3f))
                        }
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

/**
 * Compact momentum indicator
 */
@Composable
fun CompactMomentumIndicator(
    momentum: Float,
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val color = if (momentum >= 0) hierarchy.secondary.primary else colors.statusInfo
    val teamName = if (momentum >= 0) team1Name else team2Name
    val intensity = abs(momentum).toInt()
    
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
            text = "📈",
            fontSize = 10.sp
        )
        Text(
            text = "${teamName.take(3)} +$intensity%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
