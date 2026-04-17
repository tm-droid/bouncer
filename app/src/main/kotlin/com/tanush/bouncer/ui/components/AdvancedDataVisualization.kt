package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium win probability dial with animated needle
 */
@Composable
fun WinProbabilityDial(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    modifier: Modifier = Modifier
) {
    val animatedProbability by animateFloatAsState(
        targetValue = team1Probability,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "probability"
    )
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    val infiniteTransition = rememberInfiniteTransition(label = "dial")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "WIN PROBABILITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = hierarchy.secondary.primary
            )
            
            // Dial
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    val center = Offset(size.width / 2, size.height * 0.9f)
                    
                    // Background arc
                    drawArc(
                        color = colors.glassBorder,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Team 1 arc (left side)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to team1Color.copy(alpha = 0.4f),
                                0.25f to team1Color,
                                0.5f to team1Color
                            )
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f * animatedProbability,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Team 2 arc (right side)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0.5f to team2Color,
                                0.75f to team2Color,
                                1f to team2Color.copy(alpha = 0.4f)
                            )
                        ),
                        startAngle = 180f + 180f * animatedProbability,
                        sweepAngle = 180f * (1f - animatedProbability),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Needle
                    val needleAngle = (180f + 180f * animatedProbability) * PI.toFloat() / 180f
                    val needleLength = radius * 0.6f
                    
                    // Needle glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.textPrimary.copy(alpha = glowPulse * 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = 25.dp.toPx()
                        ),
                        radius = 25.dp.toPx(),
                        center = center
                    )
                    
                    // Needle line
                    drawLine(
                        color = colors.textPrimary,
                        start = center,
                        end = Offset(
                            center.x + cos(needleAngle) * needleLength,
                            center.y + sin(needleAngle) * needleLength
                        ),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    // Center dot
                    drawCircle(
                        color = colors.textPrimary,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                }
            }
            
            // Team names and percentages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = team1Name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = team1Color
                    )
                    Text(
                        text = "${(animatedProbability * 100).toInt()}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = team1Color
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = team2Name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = team2Color
                    )
                    Text(
                        text = "${((1f - animatedProbability) * 100).toInt()}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = team2Color
                    )
                }
            }
        }
    }
}

/**
 * Over-by-over run comparison chart
 */
@Composable
fun OverComparisonChart(
    team1Runs: List<Int>,
    team2Runs: List<Int>,
    team1Name: String,
    team2Name: String,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val maxRuns = maxOf(team1Runs.maxOrNull() ?: 0, team2Runs.maxOrNull() ?: 0, 12)
    var selectedOver by remember { mutableStateOf<Int?>(null) }
    val colors = BouncerDesignSystem.colors
    
    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OVER COMPARISON",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = hierarchy.secondary.primary
                )
                
                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(color = team1Color, label = team1Name)
                    LegendItem(color = team2Color, label = team2Name)
                }
            }
            
            // Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val barWidth = size.width.toFloat() / maxOf(team1Runs.size, team2Runs.size)
                            val tappedOver = (offset.x / barWidth).toInt()
                            selectedOver = if (selectedOver == tappedOver) null else tappedOver
                        }
                    }
            ) {
                val barWidth = size.width / maxOf(team1Runs.size, team2Runs.size)
                val barPadding = barWidth * 0.15f
                val actualBarWidth = (barWidth - barPadding * 2) / 2
                
                // Grid lines
                for (i in 0..4) {
                    val y = size.height * (1f - i / 4f)
                    drawLine(
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
                
                // Bars
                val maxOvers = maxOf(team1Runs.size, team2Runs.size)
                for (i in 0 until maxOvers) {
                    val x = i * barWidth + barPadding
                    val isSelected = selectedOver == i
                    
                    // Team 1 bar
                    val team1Height = if (i < team1Runs.size) {
                        (team1Runs[i].toFloat() / maxRuns) * size.height
                    } else 0f
                    
                    if (team1Height > 0) {
                        drawRoundRect(
                            color = team1Color.copy(alpha = if (isSelected) 1f else 0.8f),
                            topLeft = Offset(x, size.height - team1Height),
                            size = Size(actualBarWidth, team1Height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                    
                    // Team 2 bar
                    val team2Height = if (i < team2Runs.size) {
                        (team2Runs[i].toFloat() / maxRuns) * size.height
                    } else 0f
                    
                    if (team2Height > 0) {
                        drawRoundRect(
                            color = team2Color.copy(alpha = if (isSelected) 1f else 0.8f),
                            topLeft = Offset(x + actualBarWidth + 2.dp.toPx(), size.height - team2Height),
                            size = Size(actualBarWidth, team2Height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }
            }
            
            // Selected over info
            AnimatedVisibility(
                visible = selectedOver != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedOver?.let { over ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface1
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Over ${over + 1}", fontSize = 10.sp, color = colors.textMuted)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(team1Name, fontSize = 10.sp, color = team1Color)
                                Text(
                                    "${team1Runs.getOrNull(over) ?: "-"} runs",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(team2Name, fontSize = 10.sp, color = team2Color)
                                Text(
                                    "${team2Runs.getOrNull(over) ?: "-"} runs",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary
        )
    }
}

/**
 * Boundary distribution pie chart
 */
@Composable
fun BoundaryDistributionChart(
    fours: Int,
    sixes: Int,
    dots: Int,
    singles: Int,
    doubles: Int,
    triples: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val total = fours + sixes + dots + singles + doubles + triples
    if (total == 0) return
    val colors = BouncerDesignSystem.colors
    
    val data = listOf(
        ChartSegment("6s", sixes, hierarchy.secondary.primary),
        ChartSegment("4s", fours, colors.statusInfo),
        ChartSegment("3s", triples, colors.statusInfo),
        ChartSegment("2s", doubles, colors.statusInfo),
        ChartSegment("1s", singles, colors.statusSuccess),
        ChartSegment("Dots", dots, DotBall)
    ).filter { it.value > 0 }
    
    var selectedSegment by remember { mutableStateOf<ChartSegment?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pie")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SCORING BREAKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = hierarchy.secondary.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie chart
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation * 0.1f }
                    ) {
                        var startAngle = -90f
                        
                        data.forEach { segment ->
                            val sweepAngle = (segment.value.toFloat() / total) * 360f
                            
                            drawArc(
                                color = segment.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle - 2f,
                                useCenter = true,
                                size = size
                            )
                            
                            startAngle += sweepAngle
                        }
                        
                        // Inner circle for donut effect
                        drawCircle(
                            color = colors.surface1,
                            radius = size.minDimension / 3
                        )
                    }
                    
                    // Center text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = total.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "balls",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }
                
                // Legend
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data.forEach { segment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(segment.color, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "${segment.label}: ${segment.value}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "(${((segment.value.toFloat() / total) * 100).toInt()}%)",
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ChartSegment(
    val label: String,
    val value: Int,
    val color: Color
)

/**
 * Innings progress tracker
 */
@Composable
fun InningsProgressTracker(
    currentOver: Float,
    totalOvers: Int,
    currentRuns: Int,
    projectedScore: Int?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val progress = (currentOver / totalOvers).coerceIn(0f, 1f)
    val colors = BouncerDesignSystem.colors
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "innings")
    val markerPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "marker"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INNINGS PROGRESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = hierarchy.secondary.primary
                )
                
                if (projectedScore != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = hierarchy.secondary.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Projected: $projectedScore",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hierarchy.secondary.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                // Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.glassBorder)
                )
                
                // Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary,
                                    hierarchy.secondary.primary,
                                    hierarchy.secondary.secondary
                                )
                            )
                        )
                )
                
                // Current position marker
                Box(
                    modifier = Modifier
                        .offset(x = (animatedProgress * 300).dp - 12.dp) // Approximate position
                        .size(24.dp)
                        .align(Alignment.CenterStart)
                        .graphicsLayer { scaleX = markerPulse; scaleY = markerPulse }
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        hierarchy.secondary.primary.copy(alpha = 0.5f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.maxDimension
                            )
                        }
                        .background(hierarchy.secondary.primary, CircleShape)
                        .border(2.dp, colors.textPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${currentOver.toInt()}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.backgroundBase
                    )
                }
                
                // Over markers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(0, totalOvers / 4, totalOvers / 2, totalOvers * 3 / 4, totalOvers).forEach { over ->
                        Text(
                            text = over.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted
                        )
                    }
                }
            }
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatPill(label = "Score", value = currentRuns.toString())
                StatPill(label = "Overs", value = String.format("%.1f", currentOver))
                StatPill(label = "Run Rate", value = String.format("%.2f", if (currentOver > 0) currentRuns / currentOver else 0f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = colors.textPrimary
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted
        )
    }
}
