package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val BallRed = Color(0xFFCC3333)
private val BallRedLight = Color(0xFFDD5555)
private const val BALL_SIZE_DP = 32
private const val SPIN_DURATION_MS = 1200
private const val DISMISS_DURATION_MS = 200

/**
 * Cricket ball pull-to-refresh indicator.
 *
 * While pulling: ball scales 0→1 and rotates 0°→180° proportional to [pullProgress].
 * While refreshing: continuous 360° rotation at 1200ms per revolution.
 * On dismiss: scales to 0 with EaseOutExpo.
 * Reduced motion: no rotation — static ball with alpha pulse during refresh.
 */
@Composable
fun CricketBallPullRefreshIndicator(
    isRefreshing: Boolean,
    pullProgress: Float,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()

    // --- Scale ---
    val targetScale = when {
        isRefreshing -> 1f
        pullProgress > 0f -> min(pullProgress, 1f)
        else -> 0f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (!isRefreshing && pullProgress == 0f) {
            tween(DISMISS_DURATION_MS, easing = EaseOutExpo)
        } else {
            snap()
        },
        label = "ball_scale"
    )

    // --- Rotation ---
    val pullRotation = if (reducedMotion) 0f else pullProgress.coerceIn(0f, 1f) * 180f

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SPIN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation"
    )

    // --- Reduced-motion alpha pulse ---
    val pulseAlpha = 0.55f

    val rotation = when {
        reducedMotion -> 0f
        isRefreshing -> refreshRotation
        else -> pullRotation
    }
    val alpha = if (reducedMotion && isRefreshing) pulseAlpha else 1f

    if (animatedScale > 0.01f) {
        Canvas(
            modifier = modifier
                .size(BALL_SIZE_DP.dp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    rotationZ = rotation
                    this.alpha = alpha
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 2.dp.toPx()

            // Ball body — radial gradient for depth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(BallRedLight, BallRed),
                    center = center.copy(
                        x = center.x - radius * 0.2f,
                        y = center.y - radius * 0.2f
                    )
                ),
                radius = radius,
                center = center
            )

            // Seam — curved arc path
            val seamPath = Path().apply {
                val startAngle = 30f
                val endAngle = 150f
                moveTo(
                    center.x + radius * 0.6f * cos(startAngle * PI.toFloat() / 180f),
                    center.y + radius * 0.6f * sin(startAngle * PI.toFloat() / 180f)
                )
                for (angle in startAngle.toInt()..endAngle.toInt() step 5) {
                    val rad = angle * PI.toFloat() / 180f
                    val r = radius * 0.6f * (1f + 0.1f * sin(angle * 6f * PI.toFloat() / 180f))
                    lineTo(center.x + r * cos(rad), center.y + r * sin(rad))
                }
            }
            drawPath(
                path = seamPath,
                color = Color.White.copy(alpha = 0.8f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Shine highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                    center = center.copy(
                        x = center.x - radius * 0.4f,
                        y = center.y - radius * 0.4f
                    ),
                    radius = radius * 0.45f
                ),
                radius = radius * 0.45f,
                center = center.copy(
                    x = center.x - radius * 0.3f,
                    y = center.y - radius * 0.3f
                )
            )
        }
    }
}

// ── Previews ──

@Preview(name = "Pull 50%", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewPullHalf() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        CricketBallPullRefreshIndicator(
            isRefreshing = false,
            pullProgress = 0.5f
        )
    }
}

@Preview(name = "Refreshing", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewRefreshing() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        CricketBallPullRefreshIndicator(
            isRefreshing = true,
            pullProgress = 1f
        )
    }
}

private val EaseOutExpo: Easing = Easing { t ->
    if (t == 1f) 1f else 1f - 2.0.pow(-10.0 * t).toFloat()
}
