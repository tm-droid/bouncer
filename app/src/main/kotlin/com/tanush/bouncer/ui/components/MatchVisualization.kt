package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium wicket fall timeline visualization
 */
@Composable
fun WicketFallTimeline(
    fallOfWickets: List<FallOfWicket>,
    modifier: Modifier = Modifier
) {
    if (fallOfWickets.isEmpty()) return
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "FALL OF WICKETS",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Surface(
                    color = colors.statusInfo.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${fallOfWickets.size} wickets",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = colors.statusInfo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Timeline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                fallOfWickets.forEachIndexed { index, fow ->
                    WicketTimelineItem(
                        fallOfWicket = fow,
                        wicketNumber = index + 1,
                        isLast = index == fallOfWickets.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WicketTimelineItem(
    fallOfWicket: FallOfWicket,
    wicketNumber: Int,
    isLast: Boolean
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "wicketPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(70.dp)
        ) {
            // Wicket number circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { 
                        if (isLast) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.statusInfo, colors.statusInfo.copy(alpha = 0.8f))
                        ),
                        CircleShape
                    )
                    .shadow(6.dp, CircleShape, spotColor = colors.statusInfo.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$wicketNumber",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Score at wicket
            Text(
                fallOfWicket.score,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            
            // Overs
            Text(
                "(${fallOfWicket.overs})",
                color = colors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Batsman name
            Text(
                fallOfWicket.batsman,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
        }
        
        // Connector line
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.statusInfo.copy(alpha = 0.5f),
                                colors.statusInfo.copy(alpha = 0.2f)
                            )
                        ),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

/**
 * Circular run rate visualization
 */
@Composable
fun RunRateGauge(
    currentRate: Float,
    requiredRate: Float,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val density = LocalDensity.current
    val progress = (currentRate / requiredRate).coerceIn(0f, 1.5f)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "gaugeProgress"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "gauge")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    val gaugeColor = when {
        progress >= 1f -> hierarchy.secondary.primary
        progress >= 0.8f -> hierarchy.secondary.primary
        progress >= 0.6f -> StatusWarning
        else -> StatusInfo
    }

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12f
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            // Background arc
            drawArc(
                color = Surface2,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(gaugeColor.copy(alpha = 0.3f), gaugeColor, gaugeColor.copy(alpha = 0.7f)),
                    center = center
                ),
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress.coerceAtMost(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Glow effect
            drawArc(
                color = gaugeColor.copy(alpha = glowPulse * 0.3f),
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress.coerceAtMost(1f),
                useCenter = false,
                topLeft = Offset(center.x - radius - 4, center.y - radius - 4),
                size = Size((radius + 4) * 2, (radius + 4) * 2),
                style = Stroke(width = strokeWidth + 8, cap = StrokeCap.Round)
            )
        }
        
        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                String.format("%.1f", currentRate),
                color = gaugeColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Text(
                "CRR",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Wagon wheel run distribution visualization
 */
@Composable
fun WagonWheelMini(
    runsByDirection: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val density = LocalDensity.current
    val totalRuns = runsByDirection.values.sum().toFloat()
    
    val directions = listOf(
        "Cover" to 315f,
        "Point" to 0f,
        "Third Man" to 45f,
        "Fine Leg" to 135f,
        "Square Leg" to 180f,
        "Midwicket" to 225f,
        "Long On" to 270f,
        "Long Off" to 292.5f
    )

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2 - 10f
            
            // Draw pitch outline
            drawCircle(
                color = Surface2,
                radius = maxRadius,
                center = center,
                style = Stroke(width = 1f)
            )
            
            // Draw concentric circles
            listOf(0.33f, 0.66f).forEach { ratio ->
                drawCircle(
                    color = GlassBorder.copy(alpha = 0.2f),
                    radius = maxRadius * ratio,
                    center = center,
                    style = Stroke(width = 0.5f)
                )
            }
            
            // Draw run lines
            directions.forEach { (direction, angle) ->
                val runs = runsByDirection[direction] ?: 0
                val normalizedRuns = if (totalRuns > 0) runs / totalRuns else 0f
                val lineLength = maxRadius * normalizedRuns.coerceIn(0.1f, 1f)
                
                val radians = Math.toRadians(angle.toDouble())
                val endX = center.x + (cos(radians) * lineLength).toFloat()
                val endY = center.y + (sin(radians) * lineLength).toFloat()
                
                // Line color based on runs
                val lineColor = when {
                    runs > 30 -> hierarchy.secondary.primary
                    runs > 20 -> hierarchy.secondary.primary
                    runs > 10 -> hierarchy.secondary.primary
                    else -> TextTertiary
                }
                
                drawLine(
                    color = lineColor,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                
                // Dot at end
                if (runs > 0) {
                    drawCircle(
                        color = lineColor,
                        radius = 4f,
                        center = Offset(endX, endY)
                    )
                }
            }
            
            // Center dot (batsman position)
            drawCircle(
                color = TextPrimary,
                radius = 4f,
                center = center
            )
        }
    }
}

/**
 * Over-by-over scoring chart
 */
@Composable
fun OverByOverChart(
    overScores: List<Int>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val maxScore = overScores.maxOrNull() ?: 0
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "RUNS PER OVER",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                overScores.forEachIndexed { index, score ->
                    val barHeight = if (maxScore > 0) (score.toFloat() / maxScore) else 0f
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Score label
                        if (score > 0) {
                            Text(
                                "$score",
                                color = if (score >= 10) hierarchy.secondary.primary else colors.textSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Bar
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height((40 * barHeight + 4).dp)
                                .background(
                                    when {
                                        score >= 15 -> Brush.verticalGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.primary.copy(alpha = 0.6f)))
                                        score >= 10 -> Brush.verticalGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.primary))
                                        score >= 6 -> Brush.verticalGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary))
                                        else -> Brush.verticalGradient(listOf(colors.glassBorderHighlight, colors.glassBorderHighlight))
                                    },
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Over numbers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                overScores.forEachIndexed { index, _ ->
                    Text(
                        "${index + 1}",
                        modifier = Modifier.width(16.dp),
                        color = colors.textTertiary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Match phase indicator
 */
@Composable
fun MatchPhaseIndicator(
    currentOver: Int,
    totalOvers: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val phase = when {
        currentOver <= 6 -> "POWERPLAY"
        currentOver <= 15 -> "MIDDLE OVERS"
        currentOver <= totalOvers - 5 -> "BUILD UP"
        else -> "DEATH OVERS"
    }
    
    val phaseColor = when (phase) {
        "POWERPLAY" -> hierarchy.secondary.primary
        "MIDDLE OVERS" -> hierarchy.secondary.primary
        "BUILD UP" -> hierarchy.secondary.primary
        else -> colors.statusInfo
    }
    
    val progress = currentOver.toFloat() / totalOvers

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.glassBorderHighlight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                phase,
                color = phaseColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.glassSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(phaseColor.copy(alpha = 0.6f), phaseColor)),
                            RoundedCornerShape(50)
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Over $currentOver",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "$totalOvers overs",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
