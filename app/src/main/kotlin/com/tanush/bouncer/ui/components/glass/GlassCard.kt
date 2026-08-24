package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GlassOpacity
import kotlin.random.Random
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Core glass-morphism card container.
 * Based on the mockup CSS: background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08); border-radius: 16px;
 *
 * @param modifier Modifier for the card
 * @param shape Shape of the card corners (default 16dp rounded)
 * @param borderOpacity Opacity of the border (0.0-1.0)
 * @param useGoldBorder Whether to use gold accent border instead of white
 * @param showNoiseOverlay Whether to show the subtle noise texture overlay
 * @param showPulse Loading pulse animation (subtle breathing effect)
 * @param contentPadding Padding inside the card
 * @param content Content to display inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = BouncerDesignSystem.shapes.glassCard,
    borderOpacity: Float = GlassOpacity.BORDER,
    useGoldBorder: Boolean = true,
    showNoiseOverlay: Boolean = true,
    showPulse: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(BouncerDesignSystem.dimens.spacing.md),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "glass_pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (showPulse) 0.85f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse"
    )

    // Use the refined gold gradient for premium cards
    val gradientBorder = if (useGoldBorder) {
        colors.goldGradient
    } else {
        Brush.verticalGradient(
            colors = listOf(
                colors.goldLight.copy(alpha = borderOpacity * 0.95f),
                Color.White.copy(alpha = borderOpacity * 0.6f),
                colors.goldDark.copy(alpha = borderOpacity * 0.55f)
            )
        )
    }
    
    Box(
        modifier = modifier
            .graphicsLayer { alpha = alphaPulse }
            .clip(shape)
            .background(colors.glassSurface) // Use the new semantic glass surface
            .border(width = 1.dp, brush = gradientBorder, shape = shape)
    ) {
        // Noise texture overlay
        if (showNoiseOverlay) {
            NoiseOverlay(
                modifier = Modifier.matchParentSize(),
                density = 0.20f,
                opacity = 0.05f
            )
        }
        
        // Content
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Large variant of GlassCard with 24dp padding.
 */
@Composable
fun GlassCardLarge(
    modifier: Modifier = Modifier,
    shape: Shape = BouncerDesignSystem.shapes.glassCardHero,
    borderOpacity: Float = GlassOpacity.BORDER,
    useGoldBorder: Boolean = true,
    showNoiseOverlay: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(BouncerDesignSystem.dimens.spacing.lg),
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = shape,
        borderOpacity = borderOpacity,
        useGoldBorder = useGoldBorder,
        showNoiseOverlay = showNoiseOverlay,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Small variant of GlassCard with 12dp padding.
 */
@Composable
fun GlassCardSmall(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(BouncerDesignSystem.dimens.radius.sm),
    borderOpacity: Float = GlassOpacity.BORDER,
    useGoldBorder: Boolean = false,
    showNoiseOverlay: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(BouncerDesignSystem.dimens.spacing.sm),
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = shape,
        borderOpacity = borderOpacity,
        useGoldBorder = useGoldBorder,
        showNoiseOverlay = showNoiseOverlay,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Gold-bordered highlight box for special indicators (like ball indicators 4/6/W).
 * Based on mockup CSS: background: rgba(212,175,55,0.15); border: 1px solid rgba(212,175,55,0.3); border-radius: 6px;
 *
 * @param modifier Modifier for the box
 * @param cornerRadius Corner radius of the box
 * @param backgroundColor Background color (defaults to gold with low opacity)
 * @param borderOpacity Opacity of the gold border
 * @param content Content to display
 */
@Composable
fun EtchedGoldBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BouncerDesignSystem.dimens.radius.sm,
    backgroundColor: Color? = null,
    borderOpacity: Float = 0.3f,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = BouncerDesignSystem.dimens.spacing.sm,
        vertical = BouncerDesignSystem.dimens.spacing.xs
    ),
    content: @Composable BoxScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val shapes = BouncerDesignSystem.shapes
    val shape = RoundedCornerShape(cornerRadius)
    
    val bgColor = backgroundColor ?: colors.goldPrimary.copy(alpha = 0.15f)
    
    // Inner shadow effect for etched look
    val gradientBg = Brush.linearGradient(
        colors = listOf(
            colors.backgroundBase.copy(alpha = 0.6f),
            Color.White.copy(alpha = 0.05f).copy(alpha = 0.4f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(gradientBg)
            .drawBehind {
                // Inner highlight effect
                drawRect(
                    color = bgColor.copy(alpha = 0.3f),
                    size = size
                )
            }
            .border(
                width = 1.dp,
                color = hierarchy.secondary.primary.copy(alpha = borderOpacity),
                shape = shape
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Hero-style glass card with team gradient background.
 * Used for main match score displays.
 */
@Composable
fun GlassCardHero(
    modifier: Modifier = Modifier,
    leftTeamColor: Color? = null,
    rightTeamColor: Color? = null,
    shape: Shape = BouncerDesignSystem.shapes.glassCardHero,
    showNoiseOverlay: Boolean = true,
    showPulse: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (showPulse) 0.9f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse_hero"
    )

    Box(
        modifier = modifier
            .graphicsLayer { alpha = alphaPulse }
            .clip(shape)
            .background(colors.glassSurface)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        hierarchy.secondary.primary.copy(alpha = 0.3f),
                        hierarchy.secondary.primary.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
    ) {
        // Team color gradient backgrounds
        if (leftTeamColor != null || rightTeamColor != null) {
            Row(modifier = Modifier.matchParentSize()) {
                // Left team background
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    (leftTeamColor ?: colors.goldPrimary).copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        )
                )
                // Right team background
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    (rightTeamColor ?: colors.goldPrimary).copy(alpha = 0.15f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        )
                )
            }
        }
        
        // Premium Etched Gold Inner Border (top only)
        val edgeLift = BouncerThemeV2.colors.highlight.copy(alpha = 0.1f)
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 1.dp.toPx()
            drawLine(
                color = edgeLift,
                start = Offset(20.dp.toPx(), 0f),
                end = Offset(size.width - 20.dp.toPx(), 0f),
                strokeWidth = strokeWidth
            )
        }
        
        // Noise texture overlay
        if (showNoiseOverlay) {
            NoiseOverlay(
                modifier = Modifier.matchParentSize(),
                density = 0.15f,
                opacity = 0.04f
            )
        }
        
        // Content
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Noise texture overlay for glass surfaces.
 * Creates a subtle noise effect using Canvas with random dots.
 *
 * @param modifier Modifier for sizing
 * @param density How dense the noise pattern should be (0.0-1.0)
 * @param opacity Overall opacity of the noise
 */
@Composable
fun NoiseOverlay(
    modifier: Modifier = Modifier,
    density: Float = 0.15f,
    opacity: Float = 0.04f,
    isAnimated: Boolean = true
) {
    // Subtle breathing effect for the noise texture to make it feel alive
    val infiniteTransition = rememberInfiniteTransition(label = "noiseBreathing")
    val breathingOpacity by if (isAnimated) {
        infiniteTransition.animateFloat(
            initialValue = opacity * 0.7f,
            targetValue = opacity * 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "opacity"
        )
    } else {
        remember { mutableFloatStateOf(opacity) }
    }

    // Generate stable noise points based on a seed
    val noisePoints = remember {
        val random = Random(42) // Fixed seed for stable pattern
        List(500) {
            Triple(
                random.nextFloat(),
                random.nextFloat(),
                random.nextFloat() * 0.5f + 0.5f // Brightness variation
            )
        }
    }
    
    val noiseLift = BouncerThemeV2.colors.highlight
    Canvas(modifier = modifier) {
        val pointsToDraw = (size.width * size.height * density * 0.0001f).toInt()
            .coerceIn(50, noisePoints.size)
        
        noisePoints.take(pointsToDraw).forEach { (xRatio, yRatio, brightness) ->
            val x = xRatio * size.width
            val y = yRatio * size.height
            
            drawCircle(
                color = noiseLift.copy(alpha = breathingOpacity * brightness),
                radius = 0.5f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Glass surface with stadium gradient and gold bloom effect.
 * Exactly matches the design system mockup from DESIGN_MAPPING.md.
 */
@Composable
fun GlassBloomBackground(
    modifier: Modifier = Modifier,
    bloomColor: Color? = null,
    bloomOpacity: Float = 0.15f,
    isAnimated: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val bloom = bloomColor ?: colors.goldPrimary
    
    // Breathing effect for the gold bloom to make the background feel alive
    val infiniteTransition = rememberInfiniteTransition(label = "bloomBreathing")
    val currentBloomAlpha by if (isAnimated) {
        infiniteTransition.animateFloat(
            initialValue = bloomOpacity * 0.8f,
            targetValue = bloomOpacity * 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bloomAlpha"
        )
    } else {
        remember { mutableFloatStateOf(bloomOpacity) }
    }
    
    Box(
        modifier = modifier
            .background(Color.Black)
            .drawBehind {
                // 1. Stadium Gradient: circle at 50% 10%
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A), // #1a1a1a 0%
                            Color(0xFF0F1115), // #0f1115 60%
                            Color.Black        // #000 100%
                        ),
                        center = Offset(size.width / 2, size.height * 0.1f),
                        radius = size.height * 1.5f
                    )
                )
                
                // 2. Gold Bloom: Radial ellipse at top
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bloom.copy(alpha = currentBloomAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, 0f),
                        radius = size.width * 0.9f
                    )
                )
            },
        content = {
            NoiseOverlay(modifier = Modifier.matchParentSize(), isAnimated = isAnimated)
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    )
}
