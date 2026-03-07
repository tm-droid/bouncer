package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback

/**
 * Material 3 Enhanced Surface Components
 * 
 * Provides native Android surface styles with:
 * - Proper elevation tinting
 * - Material 3 compliant shadows
 * - Glass morphic effects
 * - Interactive states
 */

/**
 * Enhanced Material 3 card with proper elevation
 */
@Composable
fun Material3Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = Elevation.medium,
    cornerRadius: Dp = CornerRadius.md,
    containerColor: Color = GlassSurface,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "cardScale"
    )
    
    val cardElevation by animateFloatAsState(
        targetValue = if (isPressed) elevation.value * 0.6f else elevation.value,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "cardElevation"
    )
    
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            ),
        onClick = if (onClick != null) {
            {
                haptic.click()
                onClick()
            }
        } else {
            {}
        },
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = colors.textPrimary
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = cardElevation.dp,
            pressedElevation = (cardElevation * 0.6f).dp
        ),
        border = BorderStroke(
            width = BorderWidth.hairline,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassBorder,
                    GlassSurface
                )
            )
        ),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            getElevationTint(cardElevation.dp),
                            Color.Transparent
                        )
                    )
                )
                .padding(Spacing.md)
        ) {
            content()
        }
    }
}

/**
 * Glass morphic surface with blur and transparency
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = CornerRadius.md,
    borderAlpha: Float = 0.15f,
    backgroundAlpha: Float = 0.08f,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 400f
        ),
        label = "glassScale"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = Color.Black.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = backgroundAlpha * 1.3f),
                        Color.White.copy(alpha = backgroundAlpha * 0.7f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = borderAlpha * 0.5f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(
                            bounded = true,
                            color = GlassBorder
                        )
                    ) {
                        haptic.click()
                        onClick()
                    }
                } else Modifier
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            )
    ) {
        content()
    }
}

/**
 * Elevated surface with Material 3 tinting
 */
@Composable
fun ElevatedSurface(
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
    cornerRadius: Dp = 20.dp,
    containerColor: Color = GlassSurface,
    tonalElevation: Dp = elevation,
    shadowElevation: Dp = elevation,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = Color.Black.copy(alpha = 0.3f),
                ambientColor = Color.Black.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = BorderStroke(
            width = BorderWidth.hairline,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassSurface,
                    Color.Transparent
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            getElevationTint(tonalElevation),
                            Color.Transparent
                        )
                    )
                )
        ) {
            content()
        }
    }
}

/**
 * Gradient card with premium look
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gradientColors: List<Color> = listOf(
        GlassSurface,
        Surface1.copy(alpha = 0.5f),
        BackgroundBase
    ),
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 8.dp,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "gradientCardScale"
    )
    
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) elevation * 0.6f else elevation,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(colors = gradientColors)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorder,
                        GlassSurface,
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(
                            bounded = true,
                            color = GlassBorder
                        )
                    ) {
                        haptic.click()
                        onClick()
                    }
                } else Modifier
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            )
    ) {
        content()
    }
}

/**
 * Shimmer placeholder surface for loading states
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    shimmerColors: List<Color> = listOf(
        GlassSurface,
        GlassSurface,
        GlassSurface
    )
) {
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
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassSurface)
            .background(
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = androidx.compose.ui.geometry.Offset(offsetX, 0f),
                    end = androidx.compose.ui.geometry.Offset(offsetX + 500f, 500f)
                )
            )
    )
}

// ── Previews ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun Material3CardPreview() {
    com.tanush.bouncer.ui.theme.BouncerPreviewTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            Material3Card(
                onClick = {},
                elevation = Elevation.medium
            ) {
                androidx.compose.material3.Text(
                    text = "Material 3 Card Preview",
                    modifier = Modifier.padding(16.dp),
                    color = TextPrimary
                )
            }
        }
    }
}
