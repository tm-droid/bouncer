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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay

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
    glowColor: Color = ShinyGoldDeep,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glassCard")
    
    // Subtle shimmer for CRITICAL cards only
    val shimmerAlpha by if (importance == BouncerImportance.CRITICAL) {
        infiniteTransition.animateFloat(
            initialValue = 0.05f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
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
                GoldHairline,
                Color.White.copy(alpha = 0.2f),
                GoldHairline.copy(alpha = 0.3f)
            )
        )
        BouncerImportance.STANDARD -> Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.05f)
            )
        )
        BouncerImportance.MINIMAL -> Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
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
                BouncerImportance.CRITICAL -> Color.White.copy(alpha = 0.07f)
                BouncerImportance.STANDARD -> Color.White.copy(alpha = 0.05f)
                BouncerImportance.MINIMAL -> Color.White.copy(alpha = 0.03f)
            }
        ),
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(borderWidth, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
    glowColor: Color = ShinyGoldDeep,
    glowIntensity: Float = 0.1f,
    cornerRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = if (importance == BouncerImportance.CRITICAL) 8.dp else 2.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = glowColor.copy(alpha = glowIntensity),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (importance == BouncerImportance.MINIMAL) Color.Transparent else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (importance == BouncerImportance.CRITICAL) 1.dp else 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = when (importance) {
                        BouncerImportance.CRITICAL -> listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                        BouncerImportance.STANDARD -> listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                        BouncerImportance.MINIMAL -> listOf(Color.White.copy(alpha = 0.03f), Color.Transparent)
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
    color: Color = ShinyGoldDeep,
    fontSize: Int = 28
) {
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
                    Color.White.copy(alpha = 0.03f),
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
    color: Color = ShinyGoldDeep,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    icon: @Composable (() -> Unit)? = null
) {
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
                color = if (importance == BouncerImportance.CRITICAL) color else TextPrimary,
                letterSpacing = (-0.5).sp
            )
            
            Text(
                text = title.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
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
    color: Color = ShinyGoldDeep,
    content: @Composable BoxScope.() -> Unit
) {
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
    val isPositive = requiredRunRate == null || currentRunRate >= requiredRunRate
    val color = if (isPositive) SuccessGreen else ErrorRed
    
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
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = String.format("%.2f", currentRunRate),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }
            
            requiredRunRate?.let { rrr ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "REQUIRED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
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
            .background(Color.White.copy(alpha = 0.05f))
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
    Text(
        text = text,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        style = LocalTextStyle.current.copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    ShinyGoldLight,
                    ShinyGoldDeep,
                    ShinyGoldLight
                )
            )
        ),
        modifier = modifier
    )
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
        renderEffect = RenderEffect.createBlurEffect(
            blurRadius.toPx(),
            blurRadius.toPx(),
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    }
} else {
    background(Color.White.copy(alpha = 0.05f))
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
    borderGlow: Color = ShinyGoldDeep,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    content: @Composable ColumnScope.() -> Unit
) {
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
                    BouncerImportance.CRITICAL -> Color.White.copy(alpha = 0.07f)
                    BouncerImportance.STANDARD -> Color.White.copy(alpha = 0.05f)
                    BouncerImportance.MINIMAL -> Color.White.copy(alpha = 0.03f)
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
    glowColor: Color = ShinyGoldDeep,
    cornerRadius: Dp = 12.dp,
    pulseEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
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
            .background(Color.White.copy(alpha = 0.05f))
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
    val color = when (eventType) {
        EventHighlightType.WICKET -> ErrorRed
        EventHighlightType.SIX -> SixerPurple
        EventHighlightType.FOUR -> BoundaryFour
        EventHighlightType.MILESTONE -> ShinyGoldDeep
        EventHighlightType.NEUTRAL -> TextTertiary
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
    val color = when (phase) {
        InningsPhaseType.POWERPLAY -> SuccessGreen
        InningsPhaseType.MIDDLE_OVERS -> ShinyGoldDeep
        InningsPhaseType.DEATH_OVERS -> ErrorRed
        InningsPhaseType.SUPER_OVER -> SixerPurple
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
