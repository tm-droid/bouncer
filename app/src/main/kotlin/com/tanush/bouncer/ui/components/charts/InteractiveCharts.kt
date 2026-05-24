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
import androidx.compose.ui.text.TextMeasurer
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
        modifier = modifier.pointerInput(dataPoints) {
            detectTapGestures(
                onPress = { offset ->
                    touchPosition = offset
                    val nearest = findNearestPoint(dataPoints, offset, size.width.toFloat(), size.height.toFloat())
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

private fun findNearestPoint(
    dataPoints: List<ChartDataPoint>,
    offset: Offset,
    width: Float,
    height: Float
): ChartDataPoint? = dataPoints.minByOrNull { point ->
    val px = point.x * width
    val py = (1 - point.y) * height
    val dx = offset.x - px
    val dy = offset.y - py
    dx * dx + dy * dy
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
        animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing),
        label = "lineDrawAnimation"
    )

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
            drawAnimatedLineChart(
                dataPoints = dataPoints,
                lineColor = lineColor,
                fillGradient = fillGradient,
                showDataPoints = showDataPoints,
                showGrid = showGrid,
                strokeWidth = strokeWidth,
                animatedProgress = animatedProgress,
                selected = selected,
                selectionStrokeColor = hierarchy.secondary.primary
            )
        }

        selected?.let { point ->
            DataPointTooltip(
                label = point.label,
                value = point.value,
                color = lineColor,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }
    }
}

private fun DrawScope.drawAnimatedLineChart(
    dataPoints: List<ChartDataPoint>,
    lineColor: Color,
    fillGradient: Boolean,
    showDataPoints: Boolean,
    showGrid: Boolean,
    strokeWidth: Float,
    animatedProgress: Float,
    selected: ChartDataPoint?,
    selectionStrokeColor: Color
) {
    val padding = 40.dp.toPx()
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2

    val minY = dataPoints.minOfOrNull { it.y } ?: 0f
    val maxY = dataPoints.maxOfOrNull { it.y } ?: 1f
    val yRange = if (maxY == minY) 1f else maxY - minY

    if (showGrid) drawHorizontalGrid(padding, chartHeight, 5)

    val points = dataPoints.mapIndexed { index, point ->
        val x = padding + (chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1))
        val normalizedY = (point.y - minY) / yRange
        val y = padding + chartHeight * (1 - normalizedY)
        Offset(x, y)
    }

    if (points.isEmpty()) return

    val animatedPointCount = (points.size * animatedProgress).toInt().coerceAtLeast(1)
    val path = buildSmoothPath(points, animatedPointCount)

    if (fillGradient && animatedPointCount > 1) {
        drawLineChartFill(path, points, animatedPointCount, padding, lineColor)
    }

    drawLineChartStrokes(path, lineColor, strokeWidth)

    if (showDataPoints) {
        drawLineChartPoints(
            points = points,
            dataPoints = dataPoints,
            animatedPointCount = animatedPointCount,
            lineColor = lineColor,
            selected = selected,
            selectionStrokeColor = selectionStrokeColor
        )
    }
}

private fun DrawScope.drawHorizontalGrid(padding: Float, chartHeight: Float, gridLines: Int) {
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

private fun buildSmoothPath(points: List<Offset>, animatedPointCount: Int): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until animatedPointCount.coerceAtMost(points.size)) {
        val prev = points[i - 1]
        val curr = points[i]
        val controlX1 = prev.x + (curr.x - prev.x) / 3
        val controlX2 = prev.x + (curr.x - prev.x) * 2 / 3
        path.cubicTo(controlX1, prev.y, controlX2, curr.y, curr.x, curr.y)
    }
    return path
}

private fun DrawScope.drawLineChartFill(
    path: Path,
    points: List<Offset>,
    animatedPointCount: Int,
    padding: Float,
    lineColor: Color
) {
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

private fun DrawScope.drawLineChartStrokes(path: Path, lineColor: Color, strokeWidth: Float) {
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0.5f))
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
}

private fun DrawScope.drawLineChartPoints(
    points: List<Offset>,
    dataPoints: List<ChartDataPoint>,
    animatedPointCount: Int,
    lineColor: Color,
    selected: ChartDataPoint?,
    selectionStrokeColor: Color
) {
    for (i in 0 until animatedPointCount.coerceAtMost(points.size)) {
        val point = points[i]
        val isSelected = selected == dataPoints.getOrNull(i)
        val pointRadius = if (isSelected) 8.dp.toPx() else 5.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lineColor.copy(alpha = 0.4f), Color.Transparent),
                center = point,
                radius = pointRadius * 2
            ),
            radius = pointRadius * 2,
            center = point
        )
        drawCircle(color = Surface1, radius = pointRadius, center = point)
        drawCircle(color = lineColor, radius = pointRadius - 2.dp.toPx(), center = point)

        if (isSelected) {
            drawCircle(
                color = selectionStrokeColor,
                radius = pointRadius + 4.dp.toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx())
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
                    val tappedIndex = data.indices.find { index ->
                        val x = spacing + index * (barWidth + spacing)
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

        data.forEachIndexed { index, (label, value) ->
            val x = spacing + index * (barWidth + spacing)
            val animatedHeight = animatedValues[index].value * availableHeight
            val y = size.height - bottomPadding - animatedHeight
            val isSelected = index == selectedIndex
            val currentColor = if (isSelected) GoldPrimary else barColor

            drawBar(x, y, barWidth, animatedHeight, currentColor)
            if (isSelected) {
                drawBarSelectionStroke(x, y, barWidth, animatedHeight, hierarchy.secondary.primary)
            }
            if (showValues && animatedValues[index].value > 0.1f) {
                drawBarValueLabel(textMeasurer, value, x, y, barWidth)
            }
            if (showLabels) {
                drawBarBottomLabel(textMeasurer, label, x, barWidth, bottomPadding)
            }
        }
    }
}

private fun DrawScope.drawBar(
    x: Float,
    y: Float,
    barWidth: Float,
    animatedHeight: Float,
    currentColor: Color
) {
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(currentColor.copy(alpha = 0.4f), Color.Transparent)
        ),
        topLeft = Offset(x - 4.dp.toPx(), y),
        size = Size(barWidth + 8.dp.toPx(), animatedHeight + 8.dp.toPx()),
        cornerRadius = CornerRadius(8.dp.toPx())
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(currentColor, currentColor.copy(alpha = 0.7f))
        ),
        topLeft = Offset(x, y),
        size = Size(barWidth, animatedHeight),
        cornerRadius = CornerRadius(6.dp.toPx())
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
        ),
        topLeft = Offset(x, y),
        size = Size(barWidth * 0.4f, animatedHeight),
        cornerRadius = CornerRadius(6.dp.toPx())
    )
}

private fun DrawScope.drawBarSelectionStroke(
    x: Float,
    y: Float,
    barWidth: Float,
    animatedHeight: Float,
    strokeColor: Color
) {
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
        size = Size(barWidth + 4.dp.toPx(), animatedHeight + 4.dp.toPx()),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawBarValueLabel(
    textMeasurer: TextMeasurer,
    value: Float,
    x: Float,
    y: Float,
    barWidth: Float
) {
    val textResult = textMeasurer.measure(
        text = value.toInt().toString(),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

private fun DrawScope.drawBarBottomLabel(
    textMeasurer: TextMeasurer,
    label: String,
    x: Float,
    barWidth: Float,
    bottomPadding: Float
) {
    val textResult = textMeasurer.measure(
        text = label,
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
        animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing),
        label = "sparklineAnimation"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(40.dp)) {
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
        val path = buildSparklinePath(points, animatedPointCount)

        if (showFill) drawSparklineFill(path, points, animatedPointCount, padding, lineColor)

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (showEndDot && animatedPointCount > 0) {
            drawSparklineEndDot(points[animatedPointCount - 1], lineColor)
        }
    }
}

private fun buildSparklinePath(points: List<Offset>, animatedPointCount: Int): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until animatedPointCount.coerceAtMost(points.size)) {
        val prev = points[i - 1]
        val curr = points[i]
        val controlX1 = prev.x + (curr.x - prev.x) / 2
        path.cubicTo(controlX1, prev.y, controlX1, curr.y, curr.x, curr.y)
    }
    return path
}

private fun DrawScope.drawSparklineFill(
    path: Path,
    points: List<Offset>,
    animatedPointCount: Int,
    padding: Float,
    lineColor: Color
) {
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
            colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
        )
    )
}

private fun DrawScope.drawSparklineEndDot(lastPoint: Offset, lineColor: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lineColor.copy(alpha = 0.4f), Color.Transparent),
            center = lastPoint
        ),
        radius = 8.dp.toPx(),
        center = lastPoint
    )
    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = lastPoint)
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
        animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing),
        label = "areaChartAnimation"
    )

    var selectedSeriesIndex by remember { mutableStateOf(-1) }
    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(dataSeries) {
                detectTapGestures { offset ->
                    val result = findNearestSeriesPoint(
                        dataSeries = dataSeries,
                        offset = offset,
                        canvasWidth = size.width.toFloat(),
                        canvasHeight = size.height.toFloat(),
                        paddingPx = 40.dp.toPx(),
                        thresholdPx = 50.dp.toPx()
                    )
                    selectedSeriesIndex = result.seriesIndex
                    selectedPoint = result.point
                    if (result.seriesIndex >= 0) {
                        onSeriesSelected?.invoke(result.seriesIndex, result.point)
                    }
                }
            }
    ) {
        drawAreaChartContent(
            dataSeries = dataSeries,
            colors = colors,
            showGrid = showGrid,
            animatedProgress = animatedProgress,
            selectedSeriesIndex = selectedSeriesIndex,
            selectedPoint = selectedPoint,
            selectionStrokeColor = hierarchy.secondary.primary
        )
    }

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

private data class NearestSeriesResult(val seriesIndex: Int, val point: ChartDataPoint?)

private fun findNearestSeriesPoint(
    dataSeries: List<List<ChartDataPoint>>,
    offset: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    paddingPx: Float,
    thresholdPx: Float
): NearestSeriesResult {
    val chartWidth = canvasWidth - paddingPx * 2
    val chartHeight = canvasHeight - paddingPx * 2

    val allPoints = dataSeries.flatten()
    val minY = allPoints.minOfOrNull { it.y } ?: 0f
    val maxY = allPoints.maxOfOrNull { it.y } ?: 1f
    val yRange = if (maxY == minY) 1f else maxY - minY

    var nearestDistance = Float.MAX_VALUE
    var nearestSeries = -1
    var nearestPoint: ChartDataPoint? = null
    val thresholdSq = thresholdPx * thresholdPx

    dataSeries.forEachIndexed { seriesIndex, series ->
        series.forEachIndexed { index, point ->
            val x = paddingPx + (chartWidth * index / (series.size - 1).coerceAtLeast(1))
            val normalizedY = (point.y - minY) / yRange
            val y = paddingPx + chartHeight * (1 - normalizedY)
            val dx = offset.x - x
            val dy = offset.y - y
            val distance = dx * dx + dy * dy
            if (distance < nearestDistance && distance < thresholdSq) {
                nearestDistance = distance
                nearestSeries = seriesIndex
                nearestPoint = point
            }
        }
    }
    return NearestSeriesResult(nearestSeries, nearestPoint)
}

private fun DrawScope.drawAreaChartContent(
    dataSeries: List<List<ChartDataPoint>>,
    colors: List<Color>,
    showGrid: Boolean,
    animatedProgress: Float,
    selectedSeriesIndex: Int,
    selectedPoint: ChartDataPoint?,
    selectionStrokeColor: Color
) {
    val padding = 40.dp.toPx()
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2

    val allPoints = dataSeries.flatten()
    val minY = allPoints.minOfOrNull { it.y } ?: 0f
    val maxY = allPoints.maxOfOrNull { it.y } ?: 1f
    val yRange = if (maxY == minY) 1f else maxY - minY

    if (showGrid) drawHorizontalGrid(padding, chartHeight, 4)

    dataSeries.forEachIndexed { seriesIndex, series ->
        if (series.isEmpty()) return@forEachIndexed
        drawAreaSeries(
            series = series,
            color = colors.getOrElse(seriesIndex) { ChartCyan },
            isSelected = seriesIndex == selectedSeriesIndex,
            selectedPoint = selectedPoint,
            padding = padding,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            minY = minY,
            yRange = yRange,
            animatedProgress = animatedProgress,
            selectionStrokeColor = selectionStrokeColor
        )
    }
}

private fun DrawScope.drawAreaSeries(
    series: List<ChartDataPoint>,
    color: Color,
    isSelected: Boolean,
    selectedPoint: ChartDataPoint?,
    padding: Float,
    chartWidth: Float,
    chartHeight: Float,
    minY: Float,
    yRange: Float,
    animatedProgress: Float,
    selectionStrokeColor: Color
) {
    val points = series.mapIndexed { index, point ->
        val x = padding + (chartWidth * index / (series.size - 1).coerceAtLeast(1))
        val normalizedY = (point.y - minY) / yRange
        val y = padding + chartHeight * (1 - normalizedY)
        Offset(x, y)
    }

    val animatedPointCount = (points.size * animatedProgress).toInt().coerceAtLeast(1)
    val linePath = buildSparklinePath(points, animatedPointCount)

    val fillPath = Path().apply {
        addPath(linePath)
        val lastPoint = points[(animatedPointCount - 1).coerceAtMost(points.lastIndex)]
        lineTo(lastPoint.x, size.height - padding)
        lineTo(points[0].x, size.height - padding)
        close()
    }

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = if (isSelected) 0.5f else 0.3f),
                color.copy(alpha = 0.05f)
            )
        )
    )

    drawPath(
        path = linePath,
        color = color,
        style = Stroke(
            width = (if (isSelected) 3f else 2f).dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    if (isSelected) {
        drawAreaSeriesPoints(
            points = points,
            series = series,
            animatedPointCount = animatedPointCount,
            color = color,
            selectedPoint = selectedPoint,
            selectionStrokeColor = selectionStrokeColor
        )
    }
}

private fun DrawScope.drawAreaSeriesPoints(
    points: List<Offset>,
    series: List<ChartDataPoint>,
    animatedPointCount: Int,
    color: Color,
    selectedPoint: ChartDataPoint?,
    selectionStrokeColor: Color
) {
    for (i in 0 until animatedPointCount.coerceAtMost(points.size)) {
        val point = points[i]
        val dataPoint = series.getOrNull(i)
        val isPointSelected = dataPoint == selectedPoint

        drawCircle(color = Surface1, radius = 5.dp.toPx(), center = point)
        drawCircle(color = color, radius = 3.dp.toPx(), center = point)

        if (isPointSelected) {
            drawCircle(
                color = selectionStrokeColor,
                radius = 8.dp.toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx())
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
            Text(text = label, color = TextSecondary, fontSize = 10.sp)
            Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
