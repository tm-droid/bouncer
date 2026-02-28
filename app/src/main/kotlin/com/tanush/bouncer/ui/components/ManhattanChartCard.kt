package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Over data for Manhattan chart
 */
data class OverData(
    val overNumber: Int,
    val runs: Int,
    val wickets: Int = 0,
    val extras: Int = 0,
    val isActive: Boolean = false,
    val isPowerplay: Boolean = false
)

/**
 * Premium Manhattan Chart Card
 * Shows runs scored per over as a bar chart
 */
@Composable
fun ManhattanChartCard(
    overs: List<OverData>,
    teamName: String,
    teamColor: Color = ShinyGoldDeep,
    modifier: Modifier = Modifier
) {
    var selectedOver by remember { mutableStateOf<OverData?>(null) }
    val maxRuns = (overs.maxOfOrNull { it.runs } ?: 15).coerceAtLeast(6)
    val textMeasurer = rememberTextMeasurer()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = teamColor.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
                        tint = teamColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "MANHATTAN CHART",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = teamColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, teamColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = teamName,
                        color = teamColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Selected over info
            AnimatedVisibility(
                visible = selectedOver != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedOver?.let { over ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = teamColor.copy(alpha = 0.1f),
                        border = BorderStroke(0.5.dp, teamColor.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Over ${over.overNumber}",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (over.isPowerplay) {
                                    Text(
                                        text = "POWERPLAY",
                                        color = ChartBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${over.runs}",
                                    color = teamColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "runs",
                                    color = TextTertiary,
                                    fontSize = 10.sp
                                )
                            }
                            
                            if (over.wickets > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${over.wickets}",
                                        color = ErrorRed,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "wickets",
                                        color = TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            
                            if (over.extras > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${over.extras}",
                                        color = WarningYellow,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "extras",
                                        color = TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(overs) {
                            detectTapGestures { offset ->
                                val barWidth = size.width.toFloat() / overs.size
                                val index = (offset.x / barWidth).toInt().coerceIn(0, overs.size - 1)
                                selectedOver = if (selectedOver == overs[index]) null else overs[index]
                            }
                        }
                ) {
                    val chartHeight = this.size.height - 30.dp.toPx()
                    val chartWidth = this.size.width
                    val barWidth = chartWidth / overs.size
                    val barGap = 3.dp.toPx()
                    
                    // Draw horizontal grid lines
                    for (i in 0..3) {
                        val y = chartHeight - (chartHeight * i / 3f)
                        drawLine(
                            color = CardBorder.copy(alpha = 0.2f),
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                        )
                    }
                    
                    // Draw bars
                    overs.forEachIndexed { index, over ->
                        val barHeight = (over.runs.toFloat() / maxRuns) * chartHeight
                        val x = index * barWidth + barGap
                        val actualBarWidth = barWidth - barGap * 2
                        
                        val isSelected = selectedOver == over
                        
                        // Bar gradient
                        val barColor = when {
                            over.wickets > 0 -> Brush.verticalGradient(
                                colors = listOf(ErrorRed, ErrorRed.copy(alpha = 0.6f)),
                                startY = chartHeight - barHeight,
                                endY = chartHeight
                            )
                            over.runs >= 12 -> Brush.verticalGradient(
                                colors = listOf(BoundarySix, BoundarySix.copy(alpha = 0.6f)),
                                startY = chartHeight - barHeight,
                                endY = chartHeight
                            )
                            over.runs >= 8 -> Brush.verticalGradient(
                                colors = listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.6f)),
                                startY = chartHeight - barHeight,
                                endY = chartHeight
                            )
                            over.isPowerplay -> Brush.verticalGradient(
                                colors = listOf(ChartBlue, ChartBlue.copy(alpha = 0.6f)),
                                startY = chartHeight - barHeight,
                                endY = chartHeight
                            )
                            else -> Brush.verticalGradient(
                                colors = listOf(teamColor, teamColor.copy(alpha = 0.6f)),
                                startY = chartHeight - barHeight,
                                endY = chartHeight
                            )
                        }
                        
                        // Selection highlight
                        if (isSelected) {
                            drawRoundRect(
                                color = teamColor.copy(alpha = 0.1f),
                                topLeft = Offset(x - barGap, 0f),
                                size = Size(actualBarWidth + barGap * 2, chartHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                        
                        // Bar
                        drawRoundRect(
                            brush = barColor,
                            topLeft = Offset(x, chartHeight - barHeight),
                            size = Size(actualBarWidth, barHeight),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        
                        // Wicket indicator dot
                        if (over.wickets > 0) {
                            drawCircle(
                                color = ErrorRed,
                                radius = 4.dp.toPx(),
                                center = Offset(x + actualBarWidth / 2, chartHeight - barHeight - 8.dp.toPx())
                            )
                        }
                        
                        // Draw over number labels (every 5 overs or if selected)
                        if (over.overNumber % 5 == 0 || isSelected || over.overNumber == 1) {
                            val labelStyle = TextStyle(
                                color = if (isSelected) teamColor else TextTertiary,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            val textResult = textMeasurer.measure("${over.overNumber}", labelStyle)
                            drawText(
                                textResult,
                                topLeft = Offset(x + actualBarWidth / 2 - textResult.size.width / 2, chartHeight + 8.dp.toPx())
                            )
                        }
                    }
                    
                    // Draw baseline
                    drawLine(
                        color = CardBorder.copy(alpha = 0.5f),
                        start = Offset(0f, chartHeight),
                        end = Offset(chartWidth, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chart legend
            ManhattanLegend()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary stats
            ManhattanSummary(overs = overs, teamColor = teamColor)
        }
    }
}

/**
 * Manhattan chart legend
 */
@Composable
private fun ManhattanLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendBar(color = BoundarySix, label = "12+ runs")
        LegendBar(color = SuccessGreen, label = "8-11 runs")
        LegendBar(color = ErrorRed, label = "Wicket")
    }
}

/**
 * Legend bar item
 */
@Composable
private fun LegendBar(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 10.sp
        )
    }
}

/**
 * Manhattan summary stats
 */
@Composable
private fun ManhattanSummary(overs: List<OverData>, teamColor: Color) {
    val totalRuns = overs.sumOf { it.runs }
    val totalWickets = overs.sumOf { it.wickets }
    val avgRunRate = if (overs.isNotEmpty()) totalRuns.toFloat() / overs.size else 0f
    val highestScoring = overs.maxByOrNull { it.runs }
    val powerplayRuns = overs.filter { it.isPowerplay }.sumOf { it.runs }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f).copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalRuns/${totalWickets}",
                    color = teamColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Score",
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.2f", avgRunRate),
                    color = ChartBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Run Rate",
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }
            
            highestScoring?.let { over ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${over.runs}",
                        color = BoundarySix,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Best (Ov ${over.overNumber})",
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            
            if (powerplayRuns > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$powerplayRuns",
                        color = SuccessGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Powerplay",
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

/**
 * Mini Manhattan for compact displays
 */
@Composable
fun MiniManhattan(
    overs: List<OverData>,
    teamColor: Color = ShinyGoldDeep,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)
) {
    Canvas(modifier = modifier) {
        val maxRuns = (overs.maxOfOrNull { it.runs } ?: 12).coerceAtLeast(6)
        val chartHeight = this.size.height
        val chartWidth = this.size.width
        val barWidth = chartWidth / overs.size.coerceAtLeast(1)
        val barGap = 1.dp.toPx()
        
        overs.forEachIndexed { index, over ->
            val barHeight = (over.runs.toFloat() / maxRuns) * chartHeight
            val x = index * barWidth + barGap
            val actualBarWidth = barWidth - barGap * 2
            
            val color = when {
                over.wickets > 0 -> ErrorRed
                over.runs >= 12 -> BoundarySix
                over.runs >= 8 -> SuccessGreen
                else -> teamColor
            }
            
            drawRoundRect(
                color = color,
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(actualBarWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}
