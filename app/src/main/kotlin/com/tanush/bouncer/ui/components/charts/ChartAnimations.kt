package com.tanush.bouncer.ui.components.charts

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animation state holder for chart animations
 */
data class ChartAnimationState(
    val progress: Float = 0f,
    val isAnimating: Boolean = false,
    val phase: AnimationPhase = AnimationPhase.IDLE
)

enum class AnimationPhase {
    IDLE, ENTERING, ACTIVE, EXITING
}

/**
 * Line drawing animation path animator
 */
@Composable
fun LineDrawAnimation(
    points: List<Offset>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartCyan,
    strokeWidth: Float = 3f,
    animationDuration: Int = 1500,
    showGlow: Boolean = true,
    onAnimationComplete: (() -> Unit)? = null
) {
    var animationState by remember { mutableStateOf(ChartAnimationState()) }
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = FastOutSlowInEasing
        ),
        finishedListener = {
            animationState = animationState.copy(isAnimating = false, phase = AnimationPhase.ACTIVE)
            onAnimationComplete?.invoke()
        },
        label = "lineDrawProgress"
    )
    
    LaunchedEffect(Unit) {
        animationState = ChartAnimationState(
            progress = 0f,
            isAnimating = true,
            phase = AnimationPhase.ENTERING
        )
    }
    
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        
        val path = Path()
        val animatedPointCount = (points.size * animatedProgress).toInt().coerceAtLeast(1)
        
        path.moveTo(points[0].x, points[0].y)
        
        for (i in 1 until animatedPointCount) {
            val prev = points[i - 1]
            val curr = points[i]
            
            // Smooth bezier curve
            val controlX1 = prev.x + (curr.x - prev.x) / 3
            val controlX2 = prev.x + (curr.x - prev.x) * 2 / 3
            
            path.cubicTo(
                controlX1, prev.y,
                controlX2, curr.y,
                curr.x, curr.y
            )
        }
        
        // Partial segment for smooth animation
        if (animatedPointCount < points.size && animatedProgress > 0) {
            val fraction = (animatedProgress * points.size) - animatedPointCount
            if (fraction > 0 && animatedPointCount > 0) {
                val prev = points[animatedPointCount - 1]
                val curr = points[animatedPointCount]
                val partialX = prev.x + (curr.x - prev.x) * fraction
                val partialY = prev.y + (curr.y - prev.y) * fraction
                path.lineTo(partialX, partialY)
            }
        }
        
        // Glow effect
        if (showGlow) {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.2f),
                        lineColor.copy(alpha = 0.4f)
                    )
                ),
                style = Stroke(
                    width = (strokeWidth + 6).dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        
        // Main line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // Leading point animation
        if (animatedProgress < 1f && animatedPointCount > 0) {
            val leadingIndex = animatedPointCount - 1
            val fraction = (animatedProgress * points.size) - animatedPointCount
            val currentPoint = if (fraction > 0 && animatedPointCount < points.size) {
                val prev = points[leadingIndex]
                val next = points[animatedPointCount]
                Offset(
                    prev.x + (next.x - prev.x) * fraction,
                    prev.y + (next.y - prev.y) * fraction
                )
            } else {
                points[leadingIndex]
            }
            
            // Pulsing leading point
            val pulseScale = 1f + sin(animatedProgress * PI.toFloat() * 4) * 0.3f
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    center = currentPoint
                ),
                radius = 12.dp.toPx() * pulseScale,
                center = currentPoint
            )
            
            drawCircle(
                color = lineColor,
                radius = 5.dp.toPx(),
                center = currentPoint
            )
        }
    }
}

/**
 * Bar expand animation for bar charts
 */
@Composable
fun BarExpandAnimation(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = StatusInfo,
    animationDuration: Int = 800,
    staggerDelay: Int = 50,
    onBarTap: ((Int) -> Unit)? = null
) {
    val maxValue = values.maxOrNull() ?: 1f
    var selectedIndex by remember { mutableStateOf(-1) }
    
    val animatedHeights = values.mapIndexed { index, value ->
        animateFloatAsState(
            targetValue = value / maxValue,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = index * staggerDelay,
                easing = FastOutSlowInEasing
            ),
            label = "barHeight$index"
        )
    }
    
    val animatedScales = values.mapIndexed { index, _ ->
        animateFloatAsState(
            targetValue = if (index == selectedIndex) 1.05f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "barScale$index"
        )
    }
    
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val bottomPadding = 28.dp.toPx()
        val topPadding = 20.dp.toPx()
        val sidePadding = 16.dp.toPx()
        val availableHeight = size.height - bottomPadding - topPadding
        val availableWidth = size.width - sidePadding * 2
        
        val barWidth = availableWidth / (values.size * 1.5f)
        val spacing = barWidth * 0.5f
        
        values.forEachIndexed { index, value ->
            val x = sidePadding + index * (barWidth + spacing)
            val animatedHeight = animatedHeights[index].value * availableHeight
            val scale = animatedScales[index].value
            val y = topPadding + availableHeight - animatedHeight
            
            // Apply scale transform
            val scaledBarWidth = barWidth * scale
            val scaledX = x - (scaledBarWidth - barWidth) / 2
            val scaledY = y - (animatedHeight * (scale - 1))
            val scaledHeight = animatedHeight * scale
            
            // Bar shadow
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        barColor.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(scaledX - 4.dp.toPx(), scaledY + 4.dp.toPx()),
                size = Size(scaledBarWidth + 8.dp.toPx(), scaledHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            
            // Main bar with gradient
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (index == selectedIndex) GoldPrimary else barColor,
                        (if (index == selectedIndex) GoldPrimary else barColor).copy(alpha = 0.7f)
                    )
                ),
                topLeft = Offset(scaledX, scaledY),
                size = Size(scaledBarWidth, scaledHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            
            // Shine effect
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(scaledX, scaledY),
                size = Size(scaledBarWidth * 0.4f, scaledHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            
            // Value label
            if (animatedHeights[index].value > 0.1f) {
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
                        scaledY - textResult.size.height - 4.dp.toPx()
                    )
                )
            }
            
            // Label
            if (labels.getOrNull(index) != null) {
                val labelText = labels[index]
                val textResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                drawText(
                    textLayoutResult = textResult,
                    color = TextSecondary,
                    topLeft = Offset(
                        x + (barWidth - textResult.size.width) / 2,
                        size.height - bottomPadding + 6.dp.toPx()
                    )
                )
            }
        }
    }
}

/**
 * Pie slice animation for rotating chart segments
 */
@Composable
fun PieSliceAnimation(
    slices: List<Pair<Float, Color>>,
    labels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    innerRadiusRatio: Float = 0f,
    animationDuration: Int = 1200,
    onSliceSelected: ((Int) -> Unit)? = null
) {
    var selectedSlice by remember { mutableStateOf(-1) }
    val total = slices.sumOf { it.first.toDouble() }.toFloat()
    
    val animatedAngles = slices.mapIndexed { index, _ ->
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = index * 100,
                easing = FastOutSlowInEasing
            ),
            label = "pieSlice$index"
        )
    }
    
    val rotationAnimation by animateFloatAsState(
        targetValue = 360f,
        animationSpec = tween(
            durationMillis = animationDuration + slices.size * 100,
            easing = FastOutSlowInEasing
        ),
        label = "pieRotation"
    )
    
    val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 - 10.dp.toPx()
            val innerRadius = radius * innerRadiusRatio
            val center = Offset(size.width / 2, size.height / 2)
            
            var startAngle = -90f + rotationAnimation - 360f
            
            slices.forEachIndexed { index, (value, color) ->
                val sweepAngle = (value / total) * 360f * animatedAngles[index].value
                val isSelected = index == selectedSlice
                val sliceRadius = if (isSelected) radius + 8.dp.toPx() else radius
                
                // Draw slice
                if (innerRadiusRatio > 0) {
                    val path = Path().apply {
                        val outerStartX = center.x + sliceRadius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
                        val outerStartY = center.y + sliceRadius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
                        
                        moveTo(outerStartX, outerStartY)
                        
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                center.x - sliceRadius,
                                center.y - sliceRadius,
                                center.x + sliceRadius,
                                center.y + sliceRadius
                            ),
                            startAngleDegrees = startAngle,
                            sweepAngleDegrees = sweepAngle,
                            forceMoveTo = false
                        )
                        
                        val innerEndX = center.x + innerRadius * cos(Math.toRadians((startAngle + sweepAngle).toDouble())).toFloat()
                        val innerEndY = center.y + innerRadius * sin(Math.toRadians((startAngle + sweepAngle).toDouble())).toFloat()
                        lineTo(innerEndX, innerEndY)
                        
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                center.x - innerRadius,
                                center.y - innerRadius,
                                center.x + innerRadius,
                                center.y + innerRadius
                            ),
                            startAngleDegrees = startAngle + sweepAngle,
                            sweepAngleDegrees = -sweepAngle,
                            forceMoveTo = false
                        )
                        
                        close()
                    }
                    
                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color,
                                color.copy(alpha = 0.7f)
                            ),
                            center = center,
                            radius = sliceRadius
                        )
                    )
                    
                    // Border
                    drawPath(
                        path = path,
                        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.2f),
                        style = Stroke(width = if (isSelected) 3.dp.toPx() else 1.dp.toPx())
                    )
                } else {
                    drawArc(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color,
                                color.copy(alpha = 0.7f)
                            ),
                            center = center,
                            radius = sliceRadius
                        ),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        topLeft = Offset(center.x - sliceRadius, center.y - sliceRadius),
                        size = Size(sliceRadius * 2, sliceRadius * 2)
                    )
                }
                
                // Label
                val labelAngle = startAngle + sweepAngle / 2
                val labelRadius = radius * 0.65f
                val labelX = center.x + labelRadius * cos(Math.toRadians(labelAngle.toDouble())).toFloat()
                val labelY = center.y + labelRadius * sin(Math.toRadians(labelAngle.toDouble())).toFloat()
                
                if (sweepAngle > 20 && labels.getOrNull(index) != null) {
                    val percentage = "${((value / total) * 100).toInt()}%"
                    val textResult = textMeasurer.measure(
                        text = percentage,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    drawText(
                        textLayoutResult = textResult,
                        color = TextPrimary,
                        topLeft = Offset(
                            labelX - textResult.size.width / 2,
                            labelY - textResult.size.height / 2
                        )
                    )
                }
                
                startAngle += sweepAngle
            }
        }
        
        // Center content for donut chart
        if (innerRadiusRatio > 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.toInt().toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "Total",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Animated tooltip for data points
 */
@Composable
fun DataPointPopup(
    isVisible: Boolean,
    title: String,
    value: String,
    subtitle: String? = null,
    color: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "popupAlpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "popupScale"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 10f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "popupOffset"
    )
    
    if (alpha > 0) {
        Box(
            modifier = modifier
                .offset(y = offsetY.dp)
                .scale(scale)
                .alpha(alpha)
                .clip(RoundedCornerShape(12.dp))
                .background(GlassBorder)
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

/**
 * Chart legend with animated toggles
 */
@Composable
fun ChartLegend(
    items: List<Pair<String, Color>>,
    selectedItems: Set<Int> = items.indices.toSet(),
    modifier: Modifier = Modifier,
    onItemToggle: ((Int) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (label, color) ->
            LegendItemAnimated(
                label = label,
                color = color,
                isSelected = index in selectedItems,
                onClick = { onItemToggle?.invoke(index) }
            )
        }
    }
}

@Composable
private fun LegendItemAnimated(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.4f,
        animationSpec = tween(200),
        label = "legendAlpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "legendScale"
    )
    
    Row(
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
    }
}

/**
 * Animated axis labels with scaling
 */
@Composable
fun AxisLabels(
    labels: List<String>,
    axis: Axis,
    modifier: Modifier = Modifier,
    animationDuration: Int = 600
) {
    val animatedScales = labels.mapIndexed { index, _ ->
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = index * 50,
                easing = FastOutSlowInEasing
            ),
            label = "axisLabel$index"
        )
    }
    
    val animatedAlphas = labels.mapIndexed { index, _ ->
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = index * 50 + 100,
                easing = FastOutSlowInEasing
            ),
            label = "axisLabelAlpha$index"
        )
    }
    
    when (axis) {
        Axis.X -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .scale(animatedScales[index].value)
                            .alpha(animatedAlphas[index].value)
                    )
                }
            }
        }
        Axis.Y -> {
            Column(
                modifier = modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .scale(animatedScales[index].value)
                            .alpha(animatedAlphas[index].value)
                    )
                }
            }
        }
    }
}

enum class Axis {
    X, Y
}

/**
 * Smooth value transition animation
 */
@Composable
fun AnimatedValue(
    value: Float,
    format: String = "%.1f",
    prefix: String = "",
    suffix: String = "",
    textStyle: TextStyle = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    ),
    color: Color = TextPrimary,
    animationDuration: Int = 800,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = FastOutSlowInEasing
        ),
        label = "animatedValue"
    )
    
    Text(
        text = "$prefix${String.format(format, animatedValue)}$suffix",
        style = textStyle,
        color = color,
        modifier = modifier
    )
}

/**
 * Pulsing indicator for live data
 */
@Composable
fun LiveDataPulse(
    modifier: Modifier = Modifier,
    color: Color = StatusLive,
    size: Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(size * scale)
                .alpha(alpha * 0.5f)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
        )
        
        // Inner dot
        Box(
            modifier = Modifier
                .size(size * 0.5f)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Chart loading skeleton animation
 */
@Composable
fun ChartLoadingSkeleton(
    modifier: Modifier = Modifier,
    barCount: Int = 6
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val barWidth = size.width / (barCount * 2f)
        val spacing = barWidth
        
        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.08f)
            ),
            start = Offset(shimmerTranslate * size.width - 100, 0f),
            end = Offset(shimmerTranslate * size.width + 100, 0f)
        )
        
        for (i in 0 until barCount) {
            val x = spacing / 2 + i * (barWidth + spacing)
            val height = size.height * (0.3f + (i % 3) * 0.2f)
            val y = size.height - height
            
            drawRoundRect(
                brush = shimmerBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
    }
}
