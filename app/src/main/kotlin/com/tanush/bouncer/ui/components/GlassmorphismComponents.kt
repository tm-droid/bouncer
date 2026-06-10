package com.tanush.bouncer.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random
import io.sentry.Sentry

private fun reversingSpec(durationMs: Int, easing: Easing = FastOutSlowInEasing) =
    infiniteRepeatable<Float>(animation = tween(durationMs, easing = easing), repeatMode = RepeatMode.Reverse)

@Composable
private fun pulsingFloat(
    enabled: Boolean,
    initial: Float,
    target: Float,
    durationMs: Int,
    easing: Easing = FastOutSlowInEasing,
    staticValue: Float = initial,
    label: String = "pulsingFloat"
): State<Float> {
    return if (enabled) {
        val transition = rememberInfiniteTransition(label = label)
        transition.animateFloat(
            initialValue = initial,
            targetValue = target,
            animationSpec = reversingSpec(durationMs, easing),
            label = label
        )
    } else {
        remember { mutableFloatStateOf(staticValue) }
    }
}

fun Modifier.noiseOverlay(
    opacity: Float = 0.04f,
    density: Float = 0.12f,
    seed: Int = 42
): Modifier = drawWithContent {
    drawContent()
    val stepX = (size.width * density / 10f).coerceAtLeast(3f)
    val stepY = (size.height * density / 10f).coerceAtLeast(3f)
    val rng = Random(seed)
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            if (rng.nextFloat() < 0.45f) {
                val dotAlpha = opacity * (0.3f + rng.nextFloat() * 0.7f)
                drawCircle(
                    color = Color.White.copy(alpha = dotAlpha),
                    radius = 0.5f + rng.nextFloat() * 0.8f,
                    center = Offset(x + rng.nextFloat() * stepX, y + rng.nextFloat() * stepY)
                )
            }
            x += stepX
        }
        y += stepY
    }
}

fun Modifier.goldGlow(
    glowColor: Color? = null,
    alpha: Float = 0.10f,
    radiusFraction: Float = 0.85f,
    centerX: Float = 0.5f,
    centerY: Float = 0.4f
): Modifier = composed {
    val effectiveColor = glowColor ?: BouncerDesignSystem.colors.goldDark
    this.drawBehind {
        try {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        effectiveColor.copy(alpha = alpha),
                        effectiveColor.copy(alpha = alpha * 0.45f),
                        effectiveColor.copy(alpha = alpha * 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * centerX, size.height * centerY),
                    radius = size.maxDimension * radiusFraction
                )
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

enum class BouncerImportance {
    CRITICAL,
    STANDARD,
    MINIMAL
}

@Composable
private fun glassCardBorderBrush(importance: BouncerImportance): Brush {
    val hierarchy = BouncerDesignSystem.hierarchy
    return when (importance) {
        BouncerImportance.CRITICAL -> Brush.linearGradient(
            colors = listOf(
                BouncerDesignSystem.colors.glassBorder,
                Color.White.copy(alpha = 0.2f),
                BouncerDesignSystem.colors.glassBorder.copy(alpha = 0.3f)
            )
        )
        BouncerImportance.STANDARD -> Brush.verticalGradient(
            colors = listOf(hierarchy.dominant.glassBorderHighlight, Color.Transparent)
        )
        BouncerImportance.MINIMAL -> Brush.verticalGradient(
            colors = listOf(hierarchy.dominant.glassBorder, Color.Transparent)
        )
    }
}

private fun glassCardElevation(importance: BouncerImportance): Dp = when (importance) {
    BouncerImportance.CRITICAL -> 12.dp
    BouncerImportance.STANDARD -> 6.dp
    BouncerImportance.MINIMAL -> 2.dp
}

private fun glassCardTintColors(importance: BouncerImportance): List<Color> = when (importance) {
    BouncerImportance.CRITICAL -> listOf(Color.White.copy(alpha = 0.04f), Color.Transparent)
    BouncerImportance.STANDARD -> listOf(Color.White.copy(alpha = 0.02f), Color.Transparent)
    BouncerImportance.MINIMAL -> listOf(Color.Transparent, Color.Transparent)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val isCritical = importance == BouncerImportance.CRITICAL
    val shape = RoundedCornerShape(cornerRadius)

    val shimmerAlpha by pulsingFloat(
        enabled = isCritical,
        initial = 0.02f,
        target = 0.08f,
        durationMs = 5000,
        label = "shimmer"
    )

    Card(
        modifier = modifier.shadow(
            elevation = glassCardElevation(importance),
            shape = shape,
            spotColor = if (isCritical) glowColor.copy(alpha = 0.3f) else Color.Black,
            ambientColor = Color.Black.copy(alpha = 0.2f)
        ),
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        shape = shape,
        border = BorderStroke(if (isCritical) 1.dp else 0.5.dp, glassCardBorderBrush(importance))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noiseOverlay()
                .drawWithContent {
                    drawContent()
                    if (isCritical) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = shimmerAlpha), Color.Transparent)
                            )
                        )
                    }
                }
                .background(Brush.verticalGradient(colors = glassCardTintColors(importance))),
            content = content
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    glowIntensity: Float = 0.1f,
    cornerRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val isCritical = importance == BouncerImportance.CRITICAL
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isCritical) 8.dp else 2.dp,
                shape = shape,
                spotColor = glowColor.copy(alpha = glowIntensity),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(shape)
            .background(if (importance == BouncerImportance.MINIMAL) Color.Transparent else hierarchy.dominant.glassBackground)
            .border(
                width = if (isCritical) 1.dp else 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = when (importance) {
                        BouncerImportance.CRITICAL -> listOf(hierarchy.dominant.glassBorderHighlight, Color.Transparent)
                        BouncerImportance.STANDARD -> listOf(hierarchy.dominant.glassBorder, Color.Transparent)
                        BouncerImportance.MINIMAL -> listOf(hierarchy.dominant.glassBackground, Color.Transparent)
                    }
                ),
                shape = shape
            ),
        content = content
    )
}
