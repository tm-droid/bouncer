package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Native Android UI Polish Utilities
 * 
 * Provides enhanced shadows, gestures, and visual effects
 * for a polished native Android experience
 */

/**
 * Enhanced shadow with color blending for depth
 * More realistic than standard Material elevation
 */
fun Modifier.enhancedShadow(
    elevation: Dp,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    color: Color = Color.Black,
    alpha: Float = 0.15f
): Modifier = drawBehind {
    val shadowColor = color.copy(alpha = alpha)
    val paint = Paint().apply {
        this.color = shadowColor
    }
    
    drawIntoCanvas { canvas ->
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.setShadowLayer(
            elevation.toPx(),
            0f,
            elevation.toPx() * 0.5f,
            shadowColor.toArgb()
        )
    }
}

/**
 * Native Android press effect with scale and haptic
 * Material 3 compliant scale values
 */
fun Modifier.pressEffect(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onPress: () -> Unit = {}
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "pressEffect"
    )
    
    this
        .scale(scale)
        .pointerInput(enabled) {
            if (enabled) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
        }
}

/**
 * Shimmer effect for loading states
 * Material 3 style with smooth animation
 */
fun Modifier.shimmerEffect(
    enabled: Boolean = true,
    colors: List<Color> = listOf(
        Color.White.copy(alpha = 0.02f),
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.02f)
    )
): Modifier = composed {
    if (!enabled) return@composed this
    
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(offsetX, 0f),
                end = Offset(offsetX + 500f, 500f)
            )
        )
    }
}

/**
 * Pulse animation for live indicators
 */
fun Modifier.pulseAnimation(
    enabled: Boolean = true,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    duration: Int = 1500
): Modifier = composed {
    if (!enabled) return@composed this
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    scale(scale)
}

/**
 * Gradient overlay for elevated surfaces
 */
fun Modifier.elevationGradient(
    startAlpha: Float = 0.08f,
    endAlpha: Float = 0.02f
): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = startAlpha),
                Color.White.copy(alpha = endAlpha)
            )
        )
    )
}

/**
 * Glow effect with color
 */
fun Modifier.glowEffect(
    color: Color,
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.25f
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                Color.Transparent
            ),
            center = Offset(size.width / 2, size.height / 2),
            radius = blurRadius.toPx()
        )
    )
}

/**
 * Native swipe-to-dismiss gesture (for future use)
 */
fun Modifier.swipeToDismiss(
    onDismiss: () -> Unit,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    this
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (offsetY > 200f) {
                        onDismiss()
                    }
                    offsetY = 0f
                },
                onVerticalDrag = { _, dragAmount ->
                    offsetY += dragAmount
                }
            )
        }
}

/**
 * Material 3 state layer overlay
 * Shows on hover, press, focus states
 */
@Composable
fun StateLayerColor(
    color: Color,
    state: InteractionState
): Color {
    return when (state) {
        InteractionState.Pressed -> color.copy(alpha = StateLayerOpacity.Pressed)
        InteractionState.Focused -> color.copy(alpha = StateLayerOpacity.Focus)
        InteractionState.Hovered -> color.copy(alpha = StateLayerOpacity.Hover)
        InteractionState.Dragged -> color.copy(alpha = StateLayerOpacity.Dragged)
        InteractionState.Disabled -> color.copy(alpha = StateLayerOpacity.Disabled)
        InteractionState.Default -> Color.Transparent
    }
}

enum class InteractionState {
    Default,
    Pressed,
    Focused,
    Hovered,
    Dragged,
    Disabled
}
