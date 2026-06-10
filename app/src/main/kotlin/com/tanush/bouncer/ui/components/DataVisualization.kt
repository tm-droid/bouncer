package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary

@Composable
private fun TrendIndicator(trend: Float) {
    val colors = BouncerDesignSystem.colors
    val color = if (trend > 0) colors.statusSuccess else colors.statusLive
    val symbol = if (trend > 0) "↑" else "↓"
    
    Text(
        text = "$symbol ${String.format("%.1f", kotlin.math.abs(trend))}%",
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    showPercentage: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progressAnimation"
    )
    
    Column(modifier = modifier) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (showPercentage) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = hierarchy.secondary.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.glassBorder, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                hierarchy.secondary.secondary,
                                hierarchy.secondary.primary,
                                hierarchy.secondary.primary
                            )
                        ),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
fun CircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: androidx.compose.ui.unit.Dp = 100.dp
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "circularProgress"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.toPx() - strokeWidth) / 2
            val centerX = size.toPx() / 2
            val centerY = size.toPx() / 2
            
            // Background circle
            drawCircle(
                color = colors.glassBorder,
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Progress arc
            val sweepAngle = 360f * animatedProgress
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        hierarchy.secondary.secondary,
                        hierarchy.secondary.primary,
                        hierarchy.secondary.primary,
                        hierarchy.secondary.secondary
                    )
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(centerX - radius, centerY - radius)
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = hierarchy.secondary.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            label?.let {
                Text(
                    text = it,
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val max = maxValue ?: data.maxOfOrNull { it.second } ?: 1f
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, value) ->
            val animatedHeight by animateFloatAsState(
                targetValue = (value / max).coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "barHeight$label"
            )
            
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedHeight)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    hierarchy.secondary.secondary,
                                    hierarchy.secondary.primary,
                                    hierarchy.secondary.primary
                                )
                            ),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun RadarChart(
    data: Map<String, Float>,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Canvas(modifier = modifier.size(200.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = min(size.width, size.height) / 2 - 40.dp.toPx()
        val dataPoints = data.entries.toList()
        val angleStep = 360f / dataPoints.size
        
        // Draw background web
        for (i in 1..5) {
            val webPath = Path()
            val webRadius = radius * i / 5
            dataPoints.forEachIndexed { index, _ ->
                val angle = Math.toRadians((angleStep * index - 90).toDouble())
                val x = centerX + webRadius * cos(angle).toFloat()
                val y = centerY + webRadius * sin(angle).toFloat()
                
                if (index == 0) {
                    webPath.moveTo(x, y)
                } else {
                    webPath.lineTo(x, y)
                }
            }
            webPath.close()
            
            drawPath(
                path = webPath,
                color = GlassBorder,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        
        // Draw data polygon
        val dataPath = Path()
        dataPoints.forEachIndexed { index, (_, value) ->
            val angle = Math.toRadians((angleStep * index - 90).toDouble())
            val normalizedValue = (value / maxValue).coerceIn(0f, 1f)
            val x = centerX + radius * normalizedValue * cos(angle).toFloat()
            val y = centerY + radius * normalizedValue * sin(angle).toFloat()
            
            if (index == 0) {
                dataPath.moveTo(x, y)
            } else {
                dataPath.lineTo(x, y)
            }
        }
        dataPath.close()
        
        drawPath(
            path = dataPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    hierarchy.secondary.secondary.copy(alpha = 0.4f),
                    hierarchy.secondary.primary.copy(alpha = 0.2f)
                ),
                center = Offset(centerX, centerY),
                radius = radius
            )
        )
        
        drawPath(
            path = dataPath,
            color = hierarchy.secondary.primary,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun MiniSparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(40.dp)
    ) {
        if (data.size < 2) return@Canvas
        
        val maxValue = data.maxOrNull() ?: 1f
        val minValue = data.minOrNull() ?: 0f
        val range = maxValue - minValue
        if (range == 0f) return@Canvas
        
        val path = Path()
        val stepX = size.width / (data.size - 1)
        
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val normalizedValue = (value - minValue) / range
            val y = size.height - (normalizedValue * size.height)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
