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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    
    val ringBrush = Brush.sweepGradient(
        colors = listOf(
            hierarchy.secondary.secondary,
            hierarchy.secondary.primary,
            hierarchy.secondary.primary,
            hierarchy.secondary.secondary
        )
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawProgressRing(size.toPx(), animatedProgress, ringBrush, colors.glassBorder)
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

private fun DrawScope.drawProgressRing(sizePx: Float, progress: Float, ringBrush: Brush, trackColor: Color) {
    val strokeWidth = 8.dp.toPx()
    val radius = (sizePx - strokeWidth) / 2
    val centerX = sizePx / 2
    val centerY = sizePx / 2

    // Background circle
    drawCircle(
        color = trackColor,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Progress arc
    drawArc(
        brush = ringBrush,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        size = Size(radius * 2, radius * 2),
        topLeft = Offset(centerX - radius, centerY - radius)
    )
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
