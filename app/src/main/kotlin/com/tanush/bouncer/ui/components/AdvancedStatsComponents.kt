package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium circular win probability indicator with animated arcs
 */
@Composable
fun PremiumWinProbabilityRing(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    size: Dp = 200.dp,
    strokeWidth: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val animatedProbability by animateFloatAsState(
        targetValue = team1Probability,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "probability"
    )
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    val infiniteTransition = rememberInfiniteTransition(label = "ringGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val radius = (size.toPx() - strokePx) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            
            // Outer glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        team1Color.copy(alpha = glowAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius + strokePx * 2
                ),
                radius = radius + strokePx * 2,
                center = center
            )
            
            // Background arc
            drawArc(
                color = Surface2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(strokePx / 2, strokePx / 2),
                size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            
            // Team 1 arc (clockwise from top)
            val team1Angle = animatedProbability * 360f
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        team1Color,
                        team1Color.copy(alpha = 0.7f),
                        team1Color
                    )
                ),
                startAngle = -90f,
                sweepAngle = team1Angle,
                useCenter = false,
                topLeft = Offset(strokePx / 2, strokePx / 2),
                size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            
            // Team 2 arc (remaining)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        team2Color,
                        team2Color.copy(alpha = 0.7f),
                        team2Color
                    )
                ),
                startAngle = -90f + team1Angle,
                sweepAngle = 360f - team1Angle,
                useCenter = false,
                topLeft = Offset(strokePx / 2, strokePx / 2),
                size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            
            // Rotating shimmer highlight
            rotate(rotation, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            TextPrimary.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(strokePx / 2, strokePx / 2),
                    size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                    style = Stroke(width = strokePx + 2, cap = StrokeCap.Round)
                )
            }
        }
        
        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${(animatedProbability * 100).toInt()}%",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = team1Color,
                letterSpacing = (-1).sp
            )
            Text(
                text = team1Name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Premium animated run rate speedometer
 */
@Composable
fun RunRateSpeedometer(
    currentRunRate: Float,
    requiredRunRate: Float = 0f,
    maxRunRate: Float = 15f,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedCRR by animateFloatAsState(
        targetValue = currentRunRate.coerceIn(0f, maxRunRate),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "crr"
    )
    
    val animatedRRR by animateFloatAsState(
        targetValue = requiredRunRate.coerceIn(0f, maxRunRate),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rrr"
    )
    
    val crrColor = when {
        requiredRunRate > 0 && currentRunRate >= requiredRunRate -> StatusSuccess
        requiredRunRate > 0 && currentRunRate < requiredRunRate -> StatusLive
        else -> hierarchy.secondary.primary
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val radius = (size.toPx() - strokeWidth) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            
            // Draw arc segments (tick marks)
            val segments = 15
            val startAngle = 135f
            val sweepAngle = 270f
            
            for (i in 0..segments) {
                val angle = startAngle + (sweepAngle / segments) * i
                val radians = angle * PI / 180
                val innerRadius = radius - strokeWidth / 2 - 8.dp.toPx()
                val outerRadius = radius - strokeWidth / 2
                
                val startX = center.x + (innerRadius * cos(radians)).toFloat()
                val startY = center.y + (innerRadius * sin(radians)).toFloat()
                val endX = center.x + (outerRadius * cos(radians)).toFloat()
                val endY = center.y + (outerRadius * sin(radians)).toFloat()
                
                val segmentColor = if (i <= (animatedCRR / maxRunRate * segments)) {
                    crrColor
                } else {
                    Surface2
                }
                
                drawLine(
                    color = segmentColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            // Draw required run rate indicator
            if (requiredRunRate > 0) {
                val rrrAngle = startAngle + (sweepAngle * (animatedRRR / maxRunRate))
                val radians = rrrAngle * PI / 180
                val indicatorRadius = radius + 8.dp.toPx()
                val indicatorX = center.x + (indicatorRadius * cos(radians)).toFloat()
                val indicatorY = center.y + (indicatorRadius * sin(radians)).toFloat()
                
                drawCircle(
                    color = StatusWarning,
                    radius = 6.dp.toPx(),
                    center = Offset(indicatorX, indicatorY)
                )
            }
            
            // Needle
            val crrAngle = startAngle + (sweepAngle * (animatedCRR / maxRunRate))
            val radians = crrAngle * PI / 180
            val needleLength = radius - 20.dp.toPx()
            val needleX = center.x + (needleLength * cos(radians)).toFloat()
            val needleY = center.y + (needleLength * sin(radians)).toFloat()
            
            drawLine(
                color = crrColor,
                start = center,
                end = Offset(needleX, needleY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Center dot
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(crrColor, crrColor.copy(alpha = 0.5f)),
                    center = center,
                    radius = 12.dp.toPx()
                ),
                radius = 8.dp.toPx(),
                center = center
            )
        }
        
        // Center text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 20.dp)
        ) {
            Text(
                text = String.format("%.2f", animatedCRR),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = crrColor
            )
            Text(
                text = "RUN RATE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Premium match momentum indicator with particle effects
 */
@Composable
fun MomentumIndicator(
    momentum: Float, // -1 to 1, negative = team2, positive = team1
    team1Name: String,
    team2Name: String,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedMomentum by animateFloatAsState(
        targetValue = momentum.coerceIn(-1f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "momentum"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val particleOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleOffset"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = team1Color.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, GlassBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    team1Name,
                    color = team1Color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "MOMENTUM",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    team2Name,
                    color = team2Color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Momentum bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.glassBorderHighlight)
            ) {
                // Center indicator
                val indicatorPosition = (animatedMomentum + 1f) / 2f
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = indicatorPosition)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    team1Color.copy(alpha = 0.3f),
                                    team1Color
                                )
                            )
                        )
                )
                
                // Momentum indicator dot
                Box(
                    modifier = Modifier
                        .offset(x = (indicatorPosition * 100 - 1).dp)
                        .fillMaxHeight()
                        .width(10.dp)
                        .background(
                            if (momentum >= 0) team1Color else team2Color,
                            CircleShape
                        )
                        .align(Alignment.CenterStart)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Momentum description
            val description = when {
                momentum > 0.6f -> "$team1Name dominating"
                momentum > 0.3f -> "$team1Name in control"
                momentum > 0f -> "Slight edge to $team1Name"
                momentum > -0.3f -> "Slight edge to $team2Name"
                momentum > -0.6f -> "$team2Name in control"
                else -> "$team2Name dominating"
            }
            
            Text(
                description,
                color = colors.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Premium over-by-over run comparison chart
 */
@Composable
fun PremiumOverComparisonChart(
    team1Runs: List<Int>,
    team2Runs: List<Int>,
    team1Name: String,
    team2Name: String,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val maxRuns = maxOf(
        team1Runs.maxOrNull() ?: 0,
        team2Runs.maxOrNull() ?: 0,
        10
    )
    val colors = BouncerDesignSystem.colors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Over Comparison",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(team1Name, team1Color)
                    LegendItem(team2Name, team2Color)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val barWidth = size.width / (maxOf(team1Runs.size, team2Runs.size) * 2 + 1)
                val chartHeight = size.height
                
                team1Runs.forEachIndexed { index, runs ->
                    val barHeight = (runs.toFloat() / maxRuns) * chartHeight
                    val x = (index * 2 + 0.5f) * barWidth
                    
                    drawRoundRect(
                        color = team1Color,
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(barWidth * 0.8f, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
                
                team2Runs.forEachIndexed { index, runs ->
                    val barHeight = (runs.toFloat() / maxRuns) * chartHeight
                    val x = (index * 2 + 1.3f) * barWidth
                    
                    drawRoundRect(
                        color = team2Color,
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(barWidth * 0.8f, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(name: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            name,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Premium player impact rating display
 */
@Composable
fun PlayerImpactRating(
    playerName: String,
    impactScore: Float, // 0 to 10
    stats: String,
    avatarColor: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedScore by animateFloatAsState(
        targetValue = impactScore,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "impact"
    )
    val colors = BouncerDesignSystem.colors
    
    val ratingColor = when {
        impactScore >= 8f -> colors.statusSuccess
        impactScore >= 6f -> hierarchy.secondary.primary
        impactScore >= 4f -> colors.statusWarning
        else -> colors.statusLive
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(avatarColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, avatarColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    playerName.take(2).uppercase(),
                    color = avatarColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playerName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stats,
                    color = colors.textTertiary,
                    fontSize = 12.sp
                )
            }
            
            // Impact score
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format("%.1f", animatedScore),
                    color = ratingColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "IMPACT",
                    color = colors.textTertiary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
