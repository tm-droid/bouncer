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

// ==========================================
// PREMIUM GLASSMORPHISM MODIFIERS
// Noise texture, gold glow, and ambient bloom
// ==========================================

/**
 * Modifier extension that draws a subtle noise pattern over content,
 * simulating the SVG fractalNoise overlay from the mockup design system.
 * Uses deterministic pseudo-random dots at ~4% opacity for tactile richness.
 */
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

/**
 * Modifier extension that draws a subtle radial gold gradient behind content,
 * matching the `bg-bloom` ambient glow effect from the mockup design system.
 *
 * @param glowColor Base gold color for the bloom (if null, uses BouncerDesignSystem.GoldPrimary)
 * @param alpha Intensity of the glow (0–1)
 * @param radiusFraction Radius of bloom relative to container diagonal
 * @param centerX Horizontal center (0–1)
 * @param centerY Vertical center (0–1)
 */
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
            val center = Offset(size.width * centerX, size.height * centerY)
            val radius = size.maxDimension * radiusFraction
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        effectiveColor.copy(alpha = alpha),
                        effectiveColor.copy(alpha = alpha * 0.45f),
                        effectiveColor.copy(alpha = alpha * 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                )
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Visual Importance for UI Components
 */
enum class BouncerImportance {
    CRITICAL,   // Live scores, win probability, major alerts (Shiny, Shimmer, Gold)
    STANDARD,   // Tactical info, streaks, key stats (Refined, Subtle borders)
    MINIMAL     // Secondary info, logs, meta-data (Subdued, Dark, Minimalist)
}

/**
 * Sleek Layered Card with adaptive visual hierarchy.
 * Replaces the "bubbly" glass effect with professional precision.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "glassCard")
    
    // Subtle shimmer for CRITICAL cards — slower & lower opacity for premium feel
    val shimmerAlpha by if (importance == BouncerImportance.CRITICAL) {
        infiniteTransition.animateFloat(
            initialValue = 0.02f,
            targetValue = 0.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val borderBrush = when (importance) {
        BouncerImportance.CRITICAL -> Brush.linearGradient(
            colors = listOf(
                BouncerDesignSystem.colors.glassBorder,
                Color.White.copy(alpha = 0.2f),
                BouncerDesignSystem.colors.glassBorder.copy(alpha = 0.3f)
            )
        )
        BouncerImportance.STANDARD -> Brush.verticalGradient(
            colors = listOf(
                hierarchy.dominant.glassBorderHighlight,
                Color.Transparent
            )
        )
        BouncerImportance.MINIMAL -> Brush.verticalGradient(
            colors = listOf(
                hierarchy.dominant.glassBorder,
                Color.Transparent
            )
        )
    }

    val borderWidth = when (importance) {
        BouncerImportance.CRITICAL -> 1.dp
        BouncerImportance.STANDARD -> 0.5.dp
        BouncerImportance.MINIMAL -> 0.5.dp
    }

    val elevation = when (importance) {
        BouncerImportance.CRITICAL -> 12.dp
        BouncerImportance.STANDARD -> 6.dp
        BouncerImportance.MINIMAL -> 2.dp
    }

    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = if (importance == BouncerImportance.CRITICAL) glowColor.copy(alpha = 0.3f) else Color.Black,
                ambientColor = Color.Black.copy(alpha = 0.2f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = when (importance) {
                BouncerImportance.CRITICAL -> GlassBackground
                BouncerImportance.STANDARD -> GlassBackground
                BouncerImportance.MINIMAL -> GlassBackground
            }
        ),
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(borderWidth, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noiseOverlay() // Tactile noise texture from mockup design system
                .drawWithContent {
                    drawContent()
                    if (importance == BouncerImportance.CRITICAL) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = shimmerAlpha), Color.Transparent)
                            )
                        )
                    }
                }
                .background(
                    Brush.verticalGradient(
                        colors = when (importance) {
                            BouncerImportance.CRITICAL -> listOf(Color.White.copy(alpha = 0.04f), Color.Transparent)
                            BouncerImportance.STANDARD -> listOf(Color.White.copy(alpha = 0.02f), Color.Transparent)
                            BouncerImportance.MINIMAL -> listOf(Color.Transparent, Color.Transparent)
                        }
                    )
                ),
            content = content
        )
    }
}

/**
 * Premium sleek surface with precision borders
 */
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
    Box(
        modifier = modifier
            .shadow(
                elevation = if (importance == BouncerImportance.CRITICAL) 8.dp else 2.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = glowColor.copy(alpha = glowIntensity),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (importance == BouncerImportance.MINIMAL) Color.Transparent else hierarchy.dominant.glassBackground)
            .border(
                width = if (importance == BouncerImportance.CRITICAL) 1.dp else 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = when (importance) {
                        BouncerImportance.CRITICAL -> listOf(hierarchy.dominant.glassBorderHighlight, Color.Transparent)
                        BouncerImportance.STANDARD -> listOf(hierarchy.dominant.glassBorder, Color.Transparent)
                        BouncerImportance.MINIMAL -> listOf(hierarchy.dominant.glassBackground, Color.Transparent)
                    }
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        content = content
    )
}

/**
 * Animated score display with a sleeker, more professional look
 */
@Composable
fun AnimatedScoreDisplay(
    score: String,
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    fontSize: Int = 28
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    AnimatedContent(
        targetState = score,
        transitionSpec = {
            (slideInVertically(initialOffsetY = { it }) + fadeIn())
                .togetherWith(slideOutVertically(targetOffsetY = { -it }) + fadeOut())
        },
        modifier = modifier,
        label = "scoreAnimation"
    ) { targetScore ->
        Box(
            modifier = Modifier
                .background(
                    GlassSurface,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    0.5.dp,
                    color.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = targetScore,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

/**
 * Refined Stat Card for high-density data
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    icon: @Composable (() -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    GlassSurface(
        modifier = modifier,
        cornerRadius = 8.dp,
        importance = importance
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.invoke()
            
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = if (importance == BouncerImportance.CRITICAL) color else colors.textPrimary,
                letterSpacing = (-0.5).sp
            )
            
            Text(
                text = title.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Minimalist pulsing indicator
 */
@Composable
fun PulsingGlowCircle(
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "pulseGlow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color.copy(alpha = alpha), CircleShape)
        )
        Box(content = content)
    }
}

/**
 * Sharp Run Rate Indicator
 */
@Composable
fun RunRateIndicator(
    currentRunRate: Float,
    requiredRunRate: Float?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val isPositive = requiredRunRate == null || currentRunRate >= requiredRunRate
    val color = if (isPositive) colors.statusSuccess else colors.statusLive
    
    GlassSurface(
        modifier = modifier.height(56.dp),
        cornerRadius = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RUN RATE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = String.format("%.2f", currentRunRate),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
            }
            
            requiredRunRate?.let { rrr ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "REQUIRED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textTertiary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = String.format("%.2f", rrr),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                }
            }
        }
    }
}

/**
 * Sleek Shimmer loading placeholder
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassSurface)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerTranslate, 0f),
                        end = Offset(shimmerTranslate + 200f, size.height)
                    )
                )
            }
    )
}

/**
 * Precision Gradient Text
 */
@Composable
fun GradientShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 20,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Text(
        text = text,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        style = LocalTextStyle.current.copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    hierarchy.secondary.secondary,
                    hierarchy.secondary.primary,
                    hierarchy.secondary.secondary
                )
            )
        ),
        modifier = modifier
    )
}

// ==========================================
// HERO & LARGE CONTAINER GLASS VARIANTS
// Premium composables matching mockup tokens
// ==========================================

/**
 * GlassHero — Premium hero-tier glass card with heavier blur simulation,
 * inset gold shadow, and 20dp corner radius. Matches "glass-hero" token:
 * 40px blur + inset gold shadow + gold gradient border at 0.25 alpha.
 */
@Composable
fun GlassHero(
    modifier: Modifier = Modifier,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val cornerRadius = 20.dp
    val shape = RoundedCornerShape(cornerRadius)

    val infiniteTransition = rememberInfiniteTransition(label = "glassHero")
    val insetAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroInset"
    )

    Card(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = shape,
                spotColor = glowColor.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.4f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        ),
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.25f),
                    GlassBorder,
                    glowColor.copy(alpha = 0.18f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Inset gold shadow — radial gradient drawn behind content
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = insetAlpha),
                                glowColor.copy(alpha = insetAlpha * 0.4f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.3f),
                            radius = size.maxDimension * 0.7f
                        )
                    )
                }
                // Darker tinted glass background simulating heavier blur
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GlassBackground,
                            Color(0xFF0D0D0D).copy(alpha = 0.15f),
                            GlassBackground
                        )
                    )
                )
                .noiseOverlay(opacity = 0.05f),
            content = content
        )
    }
}

/**
 * GlassContainerLarge — Glass container for scorecard tables with darker bg,
 * 16dp corners, scrollable content, and a gold gradient top border.
 * Matches "glass-container-large" token: heavier blur, table-ready.
 */
@Composable
fun GlassContainerLarge(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val cornerRadius = 16.dp
    val shape = RoundedCornerShape(cornerRadius)
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.3f),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface // Slightly darker glass
        ),
        shape = shape,
        border = BorderStroke(
            width = 0.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassSurface,
                    GlassBackground
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gold gradient top border accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                glowColor.copy(alpha = 0.35f),
                                glowColor.copy(alpha = 0.50f),
                                glowColor.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Scrollable content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollable) Modifier.verticalScroll(scrollState)
                        else Modifier
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.01f)
                            )
                        )
                    )
                    .noiseOverlay(opacity = 0.03f),
                content = content
            )
        }
    }
}

// ==========================================
// ENHANCED GLASSMORPHISM MODIFIERS (v2.0)
// iOS-Inspired Blur Effects & Premium Styling
// ==========================================

/**
 * Modifier extension for iOS-style blur effect (Android 12+)
 * Falls back to semi-transparent overlay on older versions
 */
fun Modifier.glassBlur(
    blurRadius: Dp = 12.dp,
    fallbackAlpha: Float = 0.85f
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    graphicsLayer {
        try {
            renderEffect = RenderEffect.createBlurEffect(
                blurRadius.toPx(),
                blurRadius.toPx(),
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
} else {
    background(GlassBackground)
}

/**
 * Advanced frosted glass card with real blur effect
 * Mimics iOS GlassyMatchView.swift aesthetic
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    blurRadius: Dp = 16.dp,
    borderGlow: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "frostedGlass")
    
    val borderAlpha by if (importance == BouncerImportance.CRITICAL) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderGlow"
        )
    } else {
        remember { mutableFloatStateOf(0.2f) }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (importance == BouncerImportance.CRITICAL) 16.dp else 8.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = borderGlow.copy(alpha = 0.3f),
                ambientColor = Color.Black.copy(alpha = 0.4f)
            )
    ) {
        // Blur layer (Android 12+)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius))
                .glassBlur(blurRadius)
        )
        
        // Content layer with glass effect
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (importance) {
                    BouncerImportance.CRITICAL -> GlassBackground
                    BouncerImportance.STANDARD -> GlassBackground
                    BouncerImportance.MINIMAL -> GlassBackground
                }
            ),
            shape = RoundedCornerShape(cornerRadius),
            border = BorderStroke(
                width = if (importance == BouncerImportance.CRITICAL) 1.5.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderGlow.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = 0.1f),
                        borderGlow.copy(alpha = borderAlpha * 0.5f)
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
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.02f)
                            )
                        )
                    ),
                content = content
            )
        }
    }
}

/**
 * Team-colored glass card that adapts to team colors
 * Provides dynamic theming based on match context
 */
@Composable
fun TeamGlassCard(
    modifier: Modifier = Modifier,
    teamColor: Color,
    cornerRadius: Dp = 16.dp,
    isActive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "teamGlass")
    
    val glowIntensity by if (isActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "teamGlow"
        )
    } else {
        remember { mutableFloatStateOf(0.1f) }
    }

    Card(
        modifier = modifier
            .shadow(
                elevation = if (isActive) 12.dp else 6.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = teamColor.copy(alpha = glowIntensity),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    teamColor.copy(alpha = if (isActive) 0.5f else 0.2f),
                    Color.White.copy(alpha = 0.08f),
                    teamColor.copy(alpha = if (isActive) 0.3f else 0.1f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    // Team color gradient overlay
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                teamColor.copy(alpha = glowIntensity * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = size.maxDimension
                        )
                    )
                },
            content = content
        )
    }
}

/**
 * Neon glow border effect for critical UI elements
 */
@Composable
fun NeonGlowBox(
    modifier: Modifier = Modifier,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    cornerRadius: Dp = 12.dp,
    pulseEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "neonGlow")
    
    val glowAlpha by if (pulseEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowPulse"
        )
    } else {
        remember { mutableFloatStateOf(0.6f) }
    }
    
    val glowSize by if (pulseEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 16f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowSize"
        )
    } else {
        remember { mutableFloatStateOf(12f) }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = glowSize.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = glowColor.copy(alpha = glowAlpha),
                ambientColor = glowColor.copy(alpha = glowAlpha * 0.5f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassSurface)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha),
                        glowColor.copy(alpha = glowAlpha * 0.3f),
                        glowColor.copy(alpha = glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        content = content
    )
}

/**
 * Animated highlight bar for key events
 */
@Composable
fun EventHighlightBar(
    modifier: Modifier = Modifier,
    eventType: EventHighlightType = EventHighlightType.NEUTRAL,
    animated: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val color = when (eventType) {
        EventHighlightType.WICKET -> colors.statusLive
        EventHighlightType.SIX -> colors.statusWarning
        EventHighlightType.FOUR -> colors.statusSuccess
        EventHighlightType.MILESTONE -> hierarchy.secondary.primary
        EventHighlightType.NEUTRAL -> colors.textTertiary
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "eventHighlight")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier
            .width(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .then(
                if (animated) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.5f),
                                    Color.Transparent
                                ),
                                startY = shimmerOffset,
                                endY = shimmerOffset + 100f
                            )
                        )
                    }
                } else Modifier
            )
    )
}

enum class EventHighlightType {
    WICKET, SIX, FOUR, MILESTONE, NEUTRAL
}

/**
 * Morphing shape indicator for match phases
 */
@Composable
fun PhaseIndicator(
    phase: InningsPhaseType,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val color = when (phase) {
        InningsPhaseType.POWERPLAY -> colors.statusSuccess
        InningsPhaseType.MIDDLE_OVERS -> hierarchy.secondary.primary
        InningsPhaseType.DEATH_OVERS -> colors.statusLive
        InningsPhaseType.SUPER_OVER -> colors.statusWarning
    }
    
    val label = when (phase) {
        InningsPhaseType.POWERPLAY -> "POWERPLAY"
        InningsPhaseType.MIDDLE_OVERS -> "MIDDLE OVERS"
        InningsPhaseType.DEATH_OVERS -> "DEATH OVERS"
        InningsPhaseType.SUPER_OVER -> "SUPER OVER"
    }

    Row(
        modifier = modifier
            .background(
                color.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                color.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        
        if (showLabel) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp
            )
        }
    }
}

enum class InningsPhaseType {
    POWERPLAY, MIDDLE_OVERS, DEATH_OVERS, SUPER_OVER
}
