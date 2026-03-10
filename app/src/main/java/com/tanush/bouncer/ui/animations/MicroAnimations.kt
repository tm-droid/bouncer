package com.tanush.bouncer.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

// Color Constants
private val GoldPrimary = Color(0xFFD4AF37)
private val BackgroundBase = Color(0xFF0F1115)
private val StatusLive = Color(0xFFFF3B30)

// a) AttentionDot - small pulsing dot indicator
@Composable
fun AttentionDot(
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary,
    size: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "attention_dot")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(color)
    )
}

// b) ProgressRing - circular progress indicator
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = GoldPrimary,
    backgroundColor: Color = Color(0xFF2A2A2A)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress_ring"
    )
    
    Canvas(modifier = modifier.size(size)) {
        val strokeWidthPx = strokeWidth.toPx()
        val diameter = this.size.minDimension
        val radius = (diameter - strokeWidthPx) / 2
        val topLeft = Offset(
            (this.size.width - diameter) / 2 + strokeWidthPx / 2,
            (this.size.height - diameter) / 2 + strokeWidthPx / 2
        )
        val arcSize = Size(diameter - strokeWidthPx, diameter - strokeWidthPx)
        
        // Background circle
        drawArc(
            color = backgroundColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
        
        // Progress arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}

// c) TypingIndicator - three bouncing dots
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary,
    dotSize: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_indicator")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { translationY = offsetY }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// d) BreathingGlow - subtle glow that breathes in/out
@Composable
fun BreathingGlow(
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary,
    maxRadius: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_glow")
    
    val radiusMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    Canvas(modifier = modifier.size(maxRadius * 2)) {
        val radius = maxRadius.toPx() * radiusMultiplier
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

// e) SwipeHint - subtle horizontal oscillation hint
@Composable
fun SwipeHint(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var cycleCount by remember { mutableStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_hint")
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint_offset"
    )
    
    LaunchedEffect(offsetX) {
        if (offsetX < -9f) {
            cycleCount++
        }
    }
    
    if (cycleCount < 4) {
        Box(
            modifier = modifier.graphicsLayer {
                translationX = offsetX
            }
        ) {
            content()
        }
    }
}

// f) TabSwitchIndicator - animated underline for tabs
@Composable
fun TabSwitchIndicator(
    selectedIndex: Int,
    tabCount: Int,
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary,
    height: Dp = 3.dp
) {
    val animatedPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 400f
        ),
        label = "tab_position"
    )
    
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val tabWidth = size.width / tabCount
        val indicatorWidth = tabWidth * 0.6f
        val startX = tabWidth * animatedPosition + (tabWidth - indicatorWidth) / 2
        
        drawRoundRect(
            color = color,
            topLeft = Offset(startX, 0f),
            size = Size(indicatorWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(height.toPx() / 2)
        )
    }
}

// g) LivePulse - live match indicator pulse
@Composable
fun LivePulse(
    modifier: Modifier = Modifier,
    color: Color = StatusLive,
    dotSize: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )
    
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )
    
    Box(
        modifier = modifier.size(dotSize * 3),
        contentAlignment = Alignment.Center
    ) {
        // Expanding ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = color,
                radius = (dotSize.toPx() / 2) * ringScale,
                center = center,
                alpha = ringAlpha,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // Center dot
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// h) ContentRevealClip - circular reveal animation
@Composable
fun ContentRevealClip(
    isRevealed: Boolean,
    modifier: Modifier = Modifier,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "reveal_progress"
    )
    
    Box(
        modifier = modifier.drawBehind {
            clipPath(
                path = Path().apply {
                    val maxRadius = sqrt(
                        size.width.pow(2) + size.height.pow(2)
                    )
                    val currentRadius = maxRadius * progress
                    addOval(
                        Rect(
                            center = Offset(
                                size.width * centerX,
                                size.height * centerY
                            ),
                            radius = currentRadius
                        )
                    )
                }
            ) {
                drawRect(color = Color.Transparent)
            }
        }
    ) {
        if (progress > 0f) {
            content()
        }
    }
}

// i) NumberTicker - individual digit roll animation
@Composable
fun NumberTicker(
    targetNumber: Int,
    modifier: Modifier = Modifier,
    textColor: Color = GoldPrimary,
    fontSize: Int = 24
) {
    val digits = targetNumber.toString().map { it.digitToInt() }
    
    Row(modifier = modifier) {
        digits.forEach { digit ->
            SingleDigitTicker(
                targetDigit = digit,
                textColor = textColor,
                fontSize = fontSize
            )
        }
    }
}

@Composable
private fun SingleDigitTicker(
    targetDigit: Int,
    textColor: Color,
    fontSize: Int
) {
    val animatedDigit by animateFloatAsState(
        targetValue = targetDigit.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 200f
        ),
        label = "digit_roll"
    )
    
    Box(
        modifier = Modifier
            .height((fontSize * 1.5).dp)
            .width(fontSize.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val digitHeight = size.height / 10
            val offset = -animatedDigit * digitHeight
            
            clipRect {
                for (i in 0..9) {
                    val y = offset + i * digitHeight + digitHeight / 2
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            i.toString(),
                            size.width / 2,
                            y + fontSize / 2,
                            android.graphics.Paint().apply {
                                color = textColor.toArgb()
                                textSize = fontSize.toFloat()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                        )
                    }
                }
            }
        }
    }
}
