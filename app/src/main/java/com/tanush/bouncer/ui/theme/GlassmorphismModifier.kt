package com.tanush.bouncer.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a glassmorphism (frosted glass) effect to a composable.
 */
fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.05f),
    borderColor: Color = Color(0xFFD4AF37).copy(alpha = 0.12f),
    borderWidth: Dp = 1.dp,
    blurRadius: Float = 35f,
    showNoise: Boolean = true
): Modifier = this
    .clip(shape)
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                renderEffect = android.graphics.RenderEffect
                    .createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
    )
    .background(backgroundColor, shape)
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor.copy(alpha = 0.3f),
                borderColor.copy(alpha = 0.05f),
            ),
        ),
        shape = shape,
    )
    .then(
        if (showNoise) {
            Modifier.drawWithCache {
                onDrawWithContent {
                    drawContent()
                    for (i in 0 until 15) {
                        val x = (i * 73.0f) % size.width
                        val y = (i * 137.0f) % size.height
                        drawCircle(
                            color = borderColor.copy(alpha = 0.03f),
                            radius = 1f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        } else Modifier
    )

/**
 * Convenience bouncer-specific glass modifier.
 */
@Composable
fun Modifier.bouncerGlass(
    shape: Shape = BouncerDesignSystem.shapes.glassCard,
    useGoldBorder: Boolean = false
): Modifier {
    val colors = BouncerDesignSystem.colors
    return this.glassmorphism(
        shape = shape,
        backgroundColor = colors.glassSurface,
        borderColor = if (useGoldBorder) colors.goldPrimary else colors.goldPrimary.copy(alpha = 0.15f),
        blurRadius = 35f
    )
}

/**
 * Premium clickable modifier with scale-down and gold-tinted ripple.
 */
@Composable
fun Modifier.bouncerClickable(
    enabled: Boolean = true,
    showRipple: Boolean = true,
    scaleOnPress: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = BouncerDesignSystem.colors

    val scale by animateFloatAsState(
        targetValue = if (isPressed && scaleOnPress) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = if (showRipple) ripple(color = colors.goldPrimary.copy(alpha = 0.15f)) else null,
            enabled = enabled,
            onClick = onClick
        )
}
