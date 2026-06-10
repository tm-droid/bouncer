package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.GoldLight

/**
 * Cricket ball loading animation
 */
@Composable
fun CricketBallLoader(
    size: Dp = 60.dp,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val statusLive = colors.statusLive
    val infiniteTransition = rememberInfiniteTransition(label = "cricketBall")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                translationY = -bounce * 20f
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCricketBall(size = size, statusLive = statusLive)
        }
    }
}

private fun DrawScope.drawCricketBall(size: Dp, statusLive: Color) {
    val ballRadius = size.toPx() / 2
    val center = Offset(ballRadius, ballRadius)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                statusLive,
                Color(0xFFA01818),
                Color(0xFF6B0F0F)
            ),
            center = Offset(ballRadius * 0.7f, ballRadius * 0.5f),
            radius = ballRadius
        ),
        radius = ballRadius,
        center = center
    )

    drawPath(
        path = cricketSeamPath(center, ballRadius),
        color = Color.White,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    val shineCenter = Offset(ballRadius * 0.6f, ballRadius * 0.4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = shineCenter,
            radius = ballRadius * 0.4f
        ),
        radius = ballRadius * 0.4f,
        center = shineCenter
    )
}

private fun cricketSeamPath(center: Offset, ballRadius: Float): Path {
    val points = 12
    return Path().apply {
        for (i in 0 until points) {
            val angle = Math.toRadians((i * 360.0 / points) + 45)
            val r = if (i % 2 == 0) ballRadius * 0.6f else ballRadius * 0.8f
            val x = center.x + (cos(angle) * r).toFloat()
            val y = center.y + (sin(angle) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
