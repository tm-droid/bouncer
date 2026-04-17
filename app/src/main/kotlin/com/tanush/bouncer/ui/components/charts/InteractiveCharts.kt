package com.tanush.bouncer.ui.components.charts

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.abs
import kotlin.math.min

// Chart color palette
val ChartColors = listOf(
    ChartCyan,
    StatusInfo,
    ChartGreen,
    ChartPurple,
    ChartOrange
)

/**
 * Data point for interactive charts
 */
data class ChartDataPoint(
    val x: Float,
    val y: Float,
    val label: String = "",
    val value: String = "",
    val color: Color? = null
)

/**
 * Base composable for touch-interactive data point reveals
 */
@Composable
fun TouchRevealChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    primaryColor: Color = ChartCyan,
    onPointSelected: ((ChartDataPoint?) -> Unit)? = null,
    content: @Composable BoxScope.(selectedPoint: ChartDataPoint?, touchPosition: Offset?) -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    
    Box(
        modifier = modifier
            .pointerInput(dataPoints) {
                detectTapGestures(
                    onPress = { offset ->
                        touchPosition = offset
                        // Find nearest data point
                        val nearest = dataPoints.minByOrNull { point ->
                            val px = point.x * size.width
                            val py = (1 - point.y) * size.height
                            val dx = offset.x - px
                            val dy = offset.y - py
                            dx * dx + dy * dy
                        }
                        if (nearest != null) {
                            val px = nearest.x * size.width
                            val py = (1 - nearest.y) * size.height
                            val distance = kotlin.math.sqrt(
                                (offset.x - px) * (offset.x - px) + (offset.y - py) * (offset.y - py)
                            )
                            if (distance < 60.dp.toPx()) {
                                selectedPoint = nearest
                                onPointSelected?.invoke(nearest)
                            }
                        }
                    },
                    onTap = {
                        selectedPoint = null
                        touchPosition = null
                        onPointSelected?.invoke(null)
                    }
                )
            }
    ) {
        content(selectedPoint, touchPosition)
    }
}

/**
 * Animated line chart with smooth line drawing animation
 */
@Composable
fun AnimatedLineChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartCyan,
    fillGradient: Boolean = true,
    showDataPoints: Boolean = true,
    showGrid: Boolean = true,
    animationDuration: Int = 1500,
    strokeWidth: Float = 3f,
    onPointSelected: ((ChartDataPoint?) -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    if (dataPoints.isEmpty()) return
    
    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = FastOutSlowInEasing
        ),
        label = "lineDrawAnimation"
    )
    
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    
    TouchRevealChart(
        dataPoints = dataPoints,
        modifier = modifier.fillMaxWidth().height(200.dp),
        primaryColor = lineColor,
        onPointSelected = { point ->
            selectedPoint = point
            onPointSelected?.invoke(point)
        }
    ) { selected, _ ->
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 40.dp.toPx()
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2
            
            val minY = dataPoints.minOfOrNull { it.y } ?: 0f
            val maxY = dataPoints.maxOfOrNull { it.y } ?: 1f
            val yRange = if (maxY == minY) 1f else maxY - minY
            
            // Draw grid
            if (showGrid) {
                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = padding + (chartHeight * i / gridLines)
                    drawLine(
                        color = GlassBorder,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            
            // Calculate path points
            val points = dataPoints.mapIndexed { index, point ->
                val x = padding + (chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1))
                val normalizedY = (point.y - minY) / yRange
                val y = padding + chartHeight * (1 - normalizedY)
                Offset(x, y)
            }
            
            // Create animated path
            val animatedPointCount = (points.size * animatedProgress).toInt().coerceAtLeast(1)
            val path = Path()
            
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                
                for (i in 1 until animatedPointCount.coerceAtMost(points.size)) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    
                    // Smooth curve using cubic bezier
                    val controlX1 = prev.x + (curr.x - prev.x) / 3
                    val controlX2 = prev.x + (curr.x - prev.x) * 2 / 3
                    
                    path.cubicTo(
                        controlX1, prev.y,
                        controlX2, curr.y,
                        curr.x, curr.y
                    )
                }
                
                // Draw gradient fill
                if (fillGradient && animatedPointCount > 1) {
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points[animatedPointCount - 1].x, size.height - padding)
                        lineTo(points[0].x, size.height - padding)
                        close()
                    }
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.3f),
                                lineColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
                
                // Draw line with glow
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.3f),
                            lineColor.copy(alpha = 0.5f)
                        )
                    ),
                    style = Stroke(
                        width = (strokeWidth + 4).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = strokeWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                
                // Draw data points
                if (showDataPoints) {
                    for (i in 0 until animatedPointCount.coerceAtMost(points.size)) {
                        val point = points[i]
                        val isSelected = selected == dataPoints.getOrNull(i)
                        val pointRadius = if (isSelected) 8.dp.toPx() else 5.dp.toPx()
                        
                        // Glow effect
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                ),
                                center = point,
                                radius = pointRadius * 2
                            ),
                            radius = pointRadius * 2,
                            center = point
                        )
                        
                        drawCircle(
                            color = Surface1,
                            radius = pointRadius,
                            center = point
                        )
                        
                        drawCircle(
                            color = lineColor,
                            radius = pointRadius - 2.dp.toPx(),
                            center = point
                        )
                        
                        // Draw selection indicator
                        if (isSelected) {
                            drawCircle(
                                color = hierarchy.secondary.primary,
                                radius = pointRadius + 4.dp.toPx(),
                                center = point,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        
        // Tooltip for selected point
        selected?.let { point ->
            DataPointTooltip(
                label = point.label,
                value = point.value,
                color = lineColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }
}

/**
 * Animated bar chart with expanding bars on touch
 */
@Composable
fun AnimatedBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = StatusInfo,
    showLabels: Boolean = true,
    showValues: Boolean = true,
    animationDuration: Int = 800,
    onBarSelected: ((Int, String, Float) -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    if (data.isEmpty()) return
    
    val maxValue = data.maxOfOrNull { it.second } ?: 1f
    var selectedIndex by remember { mutableStateOf(-1) }
    
    val animatedValues = data.mapIndexed { index, (_, value) ->
        animateFloatAsState(
            targetValue = value / maxValue,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = index * 50,
                easing = FastOutSlowInEasing
            ),
            label = "bar$index"
        )
    }
    
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val barWidth = size.width / (data.size * 1.5f)
                    val spacing = barWidth * 0.5f
                    val startX = spacing
                    
                    val tappedIndex = data.indices.find { index ->
                        val x = startX + index * (barWidth + spacing)
                        offset.x >= x && offset.x <= x + barWidth
                    }
                    
                    selectedIndex = tappedIndex ?: -1
                    tappedIndex?.let { idx ->
                        val (label, value) = data[idx]
                        onBarSelected?.invoke(idx, label, value)
                    }
                }
            }
    ) {
        val bottomPadding = if (showLabels) 30.dp.toPx() else 10.dp.toPx()
        val topPadding = if (showValues) 25.dp.toPx() else 10.dp.toPx()
        val availableHeight = size.height - bottomPadding - topPadding
        
        val barWidth = size.width / (data.size * 1.5f)
        val spacing = barWidth * 0.5f
        val startX = spacing
        
        data.forEachIndexed { index, (label, value) ->
            val x = startX + index * (barWidth + spacing)
            val animatedHeight = animatedValues[index].value * availableHeight
            val y = size.height - bottomPadding - animatedHeight
            
            val isSelected = index == selectedIndex
            val currentColor = if (isSelected) GoldPrimary else barColor
            
            // Bar shadow/glow
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        currentColor.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(x - 4.dp.toPx(), y),
                size = Size(barWidth + 8.dp.toPx(), animatedHeight + 8.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            
            // Main bar
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        currentColor,
                        currentColor.copy(alpha = 0.7f)
                    )
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, animatedHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            
            // Highlight gradient
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth * 0.4f, animatedHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            
            // Selection indicator
            if (isSelected) {
                drawRoundRect(
                    color = hierarchy.secondary.primary,
                    topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
                    size = Size(barWidth + 4.dp.toPx(), animatedHeight + 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            // Value label on top
            if (showValues && animatedValues[index].value > 0.1f) {
                val valueText = value.toInt().toString()
                val textResult = textMeasurer.measure(
                    text = valueText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(
                    textLayoutResult = textResult,
                    color = TextPrimary,
                    topLeft = Offset(
                        x + (barWidth - textResult.size.width) / 2,
                        y - textResult.size.height - 4.dp.toPx()
                    )
                )
            }
            
            // Label below
            if (showLabels) {
                val textResult = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                drawText(
                    textLayoutResult = textResult,
                    color = TextSecondary,
                    topLeft = Offset(
                        x + (barWidth - textResult.size.width) / 2,
                        size.height - bottomPadding + 8.dp.toPx()
                    )
                )
            }
        }
    }
}

/**
 * Sparkline graph for compact trend lines
 */
@Composable
fun SparklineGraph(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartGreen,
    showFill: Boolean = true,
    showEndDot: Boolean = true,
    strokeWidth: Float = 2f,
    animationDuration: Int = 1000
) {
    if (data.size < 2) return
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = FastOutSlowInEasing
        ),
        label = "sparklineAnimation"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val padding = 4.dp.toPx()
        val width = size.width - padding * 2
        val height = size.height - padding * 2
        
        val minValue = data.minOrNull() ?: 0f
        val maxValue = data.maxOrNull() ?: 1f
        val range = if (maxValue == minValue) 1f else maxValue - minValue
        
        val points = data.mapIndexed { index, value ->
            val x = padding + (width * index / (data.size - 1))
            val normalizedY = (value - minValue) / range
            val y = padding + height * (1 - normalizedY)
            Offset(x, y)
        }
        
        val animatedPointCount = (points.size * animatedProgress).toInt().coerceAtLeast(2)
        val path = Path()
        
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until animatedPointCount.coerceAtMost(points.size)) {
            val prev = points[i - 1]
            val curr = points[i]
            
            val controlX1 = prev.x + (curr.x - prev.x) / 2
            path.cubicTo(controlX1, prev.y, controlX1, curr.y, curr.x, curr.y)
        }
        
        // Gradient fill
        if (showFill) {
            val lastPoint = points[animatedPointCount - 1]
            val fillPath = Path().apply {
                addPath(path)
                lineTo(lastPoint.x, size.height - padding)
                lineTo(points[0].x, size.height - padding)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
            )
        }
        
        // Line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // End dot
        if (showEndDot && animatedPointCount > 0) {
            val lastPoint = points[animatedPointCount - 1]
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = lastPoint
                ),
                radius = 8.dp.toPx(),
                center = lastPoint
            )
            
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = lastPoint
            )
        }
    }
}

/**
 * Area chart with gradient fill and animations
 */
@Composable
fun AreaChart(
    dataSeries: List<List<ChartDataPoint>>,
    modifier: Modifier = Modifier,
    colors: List<Color> = ChartColors,
    showGrid: Boolean = true,
    showLabels: Boolean = true,
    animationDuration: Int = 1200,
    onSeriesSelected: ((Int, ChartDataPoint?) -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    if (dataSeries.isEmpty() || dataSeries.all { it.isEmpty() }) return
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = FastOutSlowInEasing
        ),
        label = "areaChartAnimation"
    )
    
    var selectedSeriesIndex by remember { mutableStateOf(-1) }
    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }
    
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(dataSeries) {
                detectTapGestures { offset ->
                    val padding = 40.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2
                    
                    val allPoints = dataSeries.flatMap { it }
                    val minY = allPoints.minOfOrNull { it.y } ?: 0f
                    val maxY = allPoints.maxOfOrNull { it.y } ?: 1f
                    val yRange = if (maxY == minY) 1f else maxY - minY
                    
                    var nearestDistance = Float.MAX_VALUE
                    var nearestSeries = -1
                    var nearestDataPoint: ChartDataPoint? = null
                    
                    dataSeries.forEachIndexed { seriesIndex, series ->
                        series.forEachIndexed { index, point ->
                            val x = padding + (chartWidth * index / (series.size - 1).coerceAtLeast(1))
                            val normalizedY = (point.y - minY) / yRange
                            val y = padding + chartHeight * (1 - normalizedY)
                            
                            val dx = offset.x - x
                            val dy = offset.y - y
                            val distance = dx * dx + dy * dy
                            
                            if (distance < nearestDistance && distance < (50.dp.toPx() * 50.dp.toPx())) {
                                nearestDistance = distance
                                nearestSeries = seriesIndex
                                nearestDataPoint = point
                            }
                        }
                    }
                    
                    selectedSeriesIndex = nearestSeries
                    selectedPoint = nearestDataPoint
                    if (nearestSeries >= 0) {
                        onSeriesSelected?.invoke(nearestSeries, nearestDataPoint)
                    }
                }
            }
    ) {
        val padding = 40.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        
        val allPoints = dataSeries.flatMap { it }
        val minY = allPoints.minOfOrNull { it.y } ?: 0f
        val maxY = allPoints.maxOfOrNull { it.y } ?: 1f
        val yRange = if (maxY == minY) 1f else maxY - minY
        
        // Grid
        if (showGrid) {
            for (i in 0..4) {
                val y = padding + (chartHeight * i / 4)
                drawLine(
                    color = GlassBorder,
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        
        // Draw each series (back to front for proper layering)
        dataSeries.forEachIndexed { seriesIndex, series ->
            if (series.isEmpty()) return@forEachIndexed
            
            val color = colors.getOrElse(seriesIndex) { ChartCyan }
            val isSelected = seriesIndex == selectedSeriesIndex
            
            val points = series.mapIndexed { index, point ->
                val x = padding + (chartWidth * index / (series.size - 1).coerceAtLeast(1))
                val normalizedY = (point.y - minY) / yRange
                val y = padding + chartHeight * (1 - normalizedY)
                Offset(x, y)
            }
            
            val animatedPointCount = (points.size * animatedProgress).toInt().coerceAtLeast(1)
            
            // Create path
            val linePath = Path()
            linePath.moveTo(points[0].x, points[0].y)
            
            for (i in 1 until animatedPointCount.coerceAtMost(points.size)) {
                val prev = points[i - 1]
                val curr = points[i]
                val controlX = prev.x + (curr.x - prev.x) / 2
                linePath.cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
            }
            
            // Fill path
            val fillPath = Path().apply {
                addPath(linePath)
                val lastPoint = points[(animatedPointCount - 1).coerceAtMost(points.lastIndex)]
                lineTo(lastPoint.x, size.height - padding)
                lineTo(points[0].x, size.height - padding)
                close()
            }
            
            // Draw fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = if (isSelected) 0.5f else 0.3f),
                        color.copy(alpha = 0.05f)
                    )
                )
            )
            
            // Draw line
            drawPath(
                path = linePath,
                color = color,
                style = Stroke(
                    width = (if (isSelected) 3f else 2f).dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            
            // Draw points for selected series
            if (isSelected) {
                for (i in 0 until animatedPointCount.coerceAtMost(points.size)) {
                    val point = points[i]
                    val dataPoint = series.getOrNull(i)
                    val isPointSelected = dataPoint == selectedPoint
                    
                    drawCircle(
                        color = Surface1,
                        radius = 5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                    
                    if (isPointSelected) {
                        drawCircle(
                            color = hierarchy.secondary.primary,
                            radius = 8.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
    
    // Selected point info
    selectedPoint?.let { point ->
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(GlassBorder)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${point.label}: ${point.value}",
                color = colors.getOrElse(selectedSeriesIndex) { ChartCyan },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Data point tooltip component
 */
@Composable
private fun DataPointTooltip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tooltipPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GlassBorder.copy(alpha = alpha))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
