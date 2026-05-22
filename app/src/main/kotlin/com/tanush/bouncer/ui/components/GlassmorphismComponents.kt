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
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val isCritical = importance == BouncerImportance.CRITICAL
    val shape = RoundedCornerShape(cornerRadius)

    val shimmerAlpha by pulsingFloat(
        enabled = isCritical,
        initial = 0.02f,
        target = 0.08f,
        durationMs = 5000,
        label = "shimmer"
    )

    val borderBrush = when (importance) {
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

    val borderWidth = if (isCritical) 1.dp else 0.5.dp
    val elevation = when (importance) {
        BouncerImportance.CRITICAL -> 12.dp
        BouncerImportance.STANDARD -> 6.dp
        BouncerImportance.MINIMAL -> 2.dp
    }

    Card(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = shape,
            spotColor = if (isCritical) glowColor.copy(alpha = 0.3f) else Color.Black,
            ambientColor = Color.Black.copy(alpha = 0.2f)
        ),
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        shape = shape,
        border = BorderStroke(borderWidth, borderBrush)
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

@Composable
fun AnimatedScoreDisplay(
    score: String,
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    fontSize: Int = 28
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val shape = RoundedCornerShape(8.dp)
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
                .background(GlassSurface, shape)
                .border(0.5.dp, color.copy(alpha = 0.15f), shape)
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

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color.copy(alpha = alpha), CircleShape)
        )
        Box(content = content)
    }
}

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

@Composable
fun GlassHero(
    modifier: Modifier = Modifier,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val shape = RoundedCornerShape(20.dp)

    val insetAlpha by pulsingFloat(
        enabled = true,
        initial = 0.12f,
        target = 0.22f,
        durationMs = 4000,
        label = "heroInset"
    )

    Card(
        modifier = modifier.shadow(
            elevation = 20.dp,
            shape = shape,
            spotColor = glowColor.copy(alpha = 0.35f),
            ambientColor = Color.Black.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
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

@Composable
fun GlassContainerLarge(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val shape = RoundedCornerShape(16.dp)
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.shadow(
            elevation = 10.dp,
            shape = shape,
            spotColor = Color.Black.copy(alpha = 0.3f),
            ambientColor = Color.Black.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = shape,
        border = BorderStroke(
            width = 0.5.dp,
            brush = Brush.linearGradient(colors = listOf(GlassSurface, GlassBackground))
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
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
    val isCritical = importance == BouncerImportance.CRITICAL
    val shape = RoundedCornerShape(cornerRadius)

    val borderAlpha by pulsingFloat(
        enabled = isCritical,
        initial = 0.3f,
        target = 0.6f,
        durationMs = 2000,
        easing = LinearEasing,
        staticValue = 0.2f,
        label = "borderGlow"
    )

    Box(
        modifier = modifier.shadow(
            elevation = if (isCritical) 16.dp else 8.dp,
            shape = shape,
            spotColor = borderGlow.copy(alpha = 0.3f),
            ambientColor = Color.Black.copy(alpha = 0.4f)
        )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .glassBlur(blurRadius)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassBackground),
            shape = shape,
            border = BorderStroke(
                width = if (isCritical) 1.5.dp else 1.dp,
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

@Composable
fun TeamGlassCard(
    modifier: Modifier = Modifier,
    teamColor: Color,
    cornerRadius: Dp = 16.dp,
    isActive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val glowIntensity by pulsingFloat(
        enabled = isActive,
        initial = 0.15f,
        target = 0.35f,
        durationMs = 1500,
        staticValue = 0.1f,
        label = "teamGlow"
    )

    Card(
        modifier = modifier.shadow(
            elevation = if (isActive) 12.dp else 6.dp,
            shape = shape,
            spotColor = teamColor.copy(alpha = glowIntensity),
            ambientColor = Color.Black.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = shape,
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

@Composable
fun NeonGlowBox(
    modifier: Modifier = Modifier,
    glowColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    cornerRadius: Dp = 12.dp,
    pulseEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val shape = RoundedCornerShape(cornerRadius)

    val glowAlpha by pulsingFloat(
        enabled = pulseEnabled,
        initial = 0.4f,
        target = 0.8f,
        durationMs = 1000,
        staticValue = 0.6f,
        label = "glowPulse"
    )

    val glowSize by pulsingFloat(
        enabled = pulseEnabled,
        initial = 8f,
        target = 16f,
        durationMs = 1000,
        staticValue = 12f,
        label = "glowSize"
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = glowSize.dp,
                shape = shape,
                spotColor = glowColor.copy(alpha = glowAlpha),
                ambientColor = glowColor.copy(alpha = glowAlpha * 0.5f)
            )
            .clip(shape)
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
                shape = shape
            ),
        content = content
    )
}

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
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), shape)
            .border(1.dp, color.copy(alpha = 0.3f), shape)
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
