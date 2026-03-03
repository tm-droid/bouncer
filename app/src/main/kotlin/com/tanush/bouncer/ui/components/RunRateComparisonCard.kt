package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.max
import kotlin.math.min

/**
 * Run rate data for each over
 */
data class OverRunRate(
    val over: Int,
    val team1Runs: Int,
    val team2Runs: Int?,
    val team1Wickets: Int = 0,
    val team2Wickets: Int? = null
)

/**
 * Premium Run Rate Comparison Chart
 * Shows run rate progression for both teams across overs
 */
@Composable
fun RunRateComparisonCard(
    overData: List<OverRunRate>,
    team1Name: String,
    team2Name: String,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo,
    requiredRunRate: Double? = null,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "rrShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // Calculate cumulative runs for run rate
    val team1Cumulative = overData.runningFold(0) { acc, data -> acc + data.team1Runs }.drop(1)
    val team2Cumulative = overData.mapNotNull { it.team2Runs }.runningFold(0) { acc, runs -> acc + runs }.drop(1)
    
    val team1RunRates = team1Cumulative.mapIndexed { index, total ->
        if (index == 0) total.toFloat() else total.toFloat() / (index + 1)
    }
    val team2RunRates = team2Cumulative.mapIndexed { index, total ->
        if (index == 0) total.toFloat() else total.toFloat() / (index + 1)
    }
    
    val maxRunRate = max(
        team1RunRates.maxOrNull() ?: 0f,
        team2RunRates.maxOrNull() ?: 0f
    ).coerceAtLeast(requiredRunRate?.toFloat() ?: 6f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(24.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = colors.goldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "RUN RATE COMPARISON",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = team1Color, label = team1Name)
                Spacer(modifier = Modifier.width(24.dp))
                LegendDot(color = team2Color, label = team2Name)
                if (requiredRunRate != null) {
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendDot(color = colors.statusLive, label = "Required (${String.format("%.2f", requiredRunRate)})", dashed = true)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val width = size.width
                    val height = size.height
                    val chartPadding = 40.dp.toPx()
                    val chartWidth = width - chartPadding
                    val chartHeight = height - 30.dp.toPx()
                    
                    val maxOvers = overData.size.coerceAtLeast(1)
                    val xStep = chartWidth / maxOvers
                    
                    // Draw grid lines
                    val gridLines = 5
                    for (i in 0..gridLines) {
                        val y = chartHeight - (chartHeight * i / gridLines)
                        drawLine(
                            color = colors.glassBorder.copy(alpha = 0.2f),
                            start = Offset(chartPadding, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // Y-axis labels
                        val runRate = maxRunRate * i / gridLines
                    }
                    
                    // Draw required run rate line
                    if (requiredRunRate != null) {
                        val y = chartHeight - (chartHeight * (requiredRunRate.toFloat() / maxRunRate))
                        val dashPath = Path().apply {
                            moveTo(chartPadding, y)
                            lineTo(width, y)
                        }
                        drawPath(
                            path = dashPath,
                            color = colors.statusLive.copy(alpha = 0.7f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                        )
                    }
                    
                    // Draw Team 2 line (if available)
                    if (team2RunRates.isNotEmpty()) {
                        val team2Path = Path()
                        team2RunRates.forEachIndexed { index, runRate ->
                            val x = chartPadding + (index + 1) * xStep
                            val y = chartHeight - (chartHeight * (runRate / maxRunRate))
                            if (index == 0) {
                                team2Path.moveTo(x, y)
                            } else {
                                team2Path.lineTo(x, y)
                            }
                        }
                        
                        // Draw area under curve
                        val team2AreaPath = Path().apply {
                            addPath(team2Path)
                            lineTo(chartPadding + team2RunRates.size * xStep, chartHeight)
                            lineTo(chartPadding + xStep, chartHeight)
                            close()
                        }
                        drawPath(
                            path = team2AreaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    team2Color.copy(alpha = 0.2f),
                                    team2Color.copy(alpha = 0.02f)
                                )
                            )
                        )
                        
                        // Draw line
                        drawPath(
                            path = team2Path,
                            color = team2Color,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Draw points
                        team2RunRates.forEachIndexed { index, runRate ->
                            val x = chartPadding + (index + 1) * xStep
                            val y = chartHeight - (chartHeight * (runRate / maxRunRate))
                            drawCircle(team2Color, 4.dp.toPx(), Offset(x, y))
                            drawCircle(colors.surface1, 2.dp.toPx(), Offset(x, y))
                        }
                    }
                    
                    // Draw Team 1 line
                    if (team1RunRates.isNotEmpty()) {
                        val team1Path = Path()
                        team1RunRates.forEachIndexed { index, runRate ->
                            val x = chartPadding + (index + 1) * xStep
                            val y = chartHeight - (chartHeight * (runRate / maxRunRate))
                            if (index == 0) {
                                team1Path.moveTo(x, y)
                            } else {
                                team1Path.lineTo(x, y)
                            }
                        }
                        
                        // Draw area under curve
                        val team1AreaPath = Path().apply {
                            addPath(team1Path)
                            lineTo(chartPadding + team1RunRates.size * xStep, chartHeight)
                            lineTo(chartPadding + xStep, chartHeight)
                            close()
                        }
                        drawPath(
                            path = team1AreaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    team1Color.copy(alpha = 0.25f),
                                    team1Color.copy(alpha = 0.02f)
                                )
                            )
                        )
                        
                        // Draw line with glow
                        drawPath(
                            path = team1Path,
                            color = team1Color.copy(alpha = 0.4f),
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = team1Path,
                            color = team1Color,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Draw points
                        team1RunRates.forEachIndexed { index, runRate ->
                            val x = chartPadding + (index + 1) * xStep
                            val y = chartHeight - (chartHeight * (runRate / maxRunRate))
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(team1Color, team1Color.copy(alpha = 0.5f))
                                ),
                                radius = 5.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(colors.surface1, 2.5.dp.toPx(), Offset(x, y))
                        }
                    }
                    
                    // X-axis labels (overs)
                    val labelInterval = max(1, maxOvers / 5)
                }
                
                // Overlay labels
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(36.dp)
                ) {
                    for (i in 5 downTo 0) {
                        val value = maxRunRate * i / 5
                        Text(
                            String.format("%.1f", value),
                            color = colors.textTertiary,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // X-axis labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labelCount = min(6, overData.size)
                val interval = max(1, overData.size / labelCount)
                (1..overData.size step interval).forEach { over ->
                    Text(
                        "$over",
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Text(
                "Overs",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current run rates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RunRateIndicator(
                    label = team1Name,
                    runRate = team1RunRates.lastOrNull() ?: 0f,
                    color = team1Color
                )
                RunRateIndicator(
                    label = team2Name,
                    runRate = team2RunRates.lastOrNull() ?: 0f,
                    color = team2Color
                )
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
    dashed: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dashed) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .drawBehind {
                        drawLine(
                            color = color,
                            start = Offset.Zero,
                            end = Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
        Text(
            label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RunRateIndicator(
    label: String,
    runRate: Float,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = colors.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
        ) {
            Text(
                String.format("%.2f", runRate),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Compact run rate display for match cards
 */
@Composable
fun CompactRunRateDisplay(
    currentRunRate: Float,
    requiredRunRate: Float?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Current RR
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "CRR",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                String.format("%.2f", currentRunRate),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Required RR
        if (requiredRunRate != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "RRR",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    String.format("%.2f", requiredRunRate),
                    color = if (requiredRunRate > currentRunRate) colors.statusLive else colors.statusSuccess,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Run rate trend indicator with arrow
 */
@Composable
fun RunRateTrendIndicator(
    previousRunRate: Float,
    currentRunRate: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val trend = currentRunRate - previousRunRate
    val trendColor = when {
        trend > 0.5f -> colors.statusSuccess
        trend < -0.5f -> colors.statusLive
        else -> colors.textTertiary
    }
    val trendIcon = when {
        trend > 0.5f -> Icons.AutoMirrored.Filled.TrendingUp
        trend < -0.5f -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            trendIcon,
            contentDescription = null,
            tint = trendColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            String.format("%.2f", currentRunRate),
            color = trendColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
