package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.GoldLight

private const val WICKET_FALL_DURATION_MS = 2000L

@Composable
private fun EmptyStateIcon(
    icon: ImageVector,
    pulseScale: Float,
    glowAlpha: Float,
    floatOffset: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                translationY = floatOffset
            }
            .size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.4f),
                            hierarchy.secondary.primary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = hierarchy.secondary.primary.copy(alpha = 0.3f)
                )
                .background(
                    Brush.linearGradient(colors = listOf(GlassSurface, GlassSurface)),
                    CircleShape
                )
                .border(
                    2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            hierarchy.secondary.secondary.copy(alpha = 0.5f),
                            hierarchy.secondary.primary.copy(alpha = 0.3f)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = hierarchy.secondary.primary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * Premium action button with gold gradient
 */
@Composable
fun PremiumActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "buttonShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val backgroundBrush = actionButtonBrush(enabled, shimmerOffset, hierarchy, colors)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        ActionButtonContent(text = text, icon = icon, enabled = enabled, colors = colors)
    }
}

@Composable
private fun actionButtonBrush(
    enabled: Boolean,
    shimmerOffset: Float,
    hierarchy: ColorHierarchy,
    colors: BouncerColors
): Brush {
    if (enabled) {
        return Brush.linearGradient(
            colors = listOf(
                hierarchy.secondary.primary,
                hierarchy.secondary.primary,
                hierarchy.secondary.primary
            ),
            start = Offset(shimmerOffset * 100, 0f),
            end = Offset((shimmerOffset + 1) * 100, 0f)
        )
    }
    return Brush.linearGradient(
        colors = listOf(
            colors.glassBorder,
            colors.glassBorder.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun ActionButtonContent(
    text: String,
    icon: ImageVector?,
    enabled: Boolean,
    colors: BouncerColors
) {
    val contentColor = if (enabled) colors.backgroundBase else colors.textTertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = text.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Cricket ball loading animation
 */
@Composable
fun CricketBallLoader(
    size: Dp = 60.dp,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val statusLive = colors.statusLive
    val infiniteTransition = rememberInfiniteTransition(label = "cricketBall")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                translationY = -bounce * 20f
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCricketBall(size = size, statusLive = statusLive)
        }
    }
}

private fun DrawScope.drawCricketBall(size: Dp, statusLive: Color) {
    val ballRadius = size.toPx() / 2
    val center = Offset(ballRadius, ballRadius)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                statusLive,
                Color(0xFFA01818),
                Color(0xFF6B0F0F)
            ),
            center = Offset(ballRadius * 0.7f, ballRadius * 0.5f),
            radius = ballRadius
        ),
        radius = ballRadius,
        center = center
    )

    drawPath(
        path = cricketSeamPath(center, ballRadius),
        color = Color.White,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    val shineCenter = Offset(ballRadius * 0.6f, ballRadius * 0.4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = shineCenter,
            radius = ballRadius * 0.4f
        ),
        radius = ballRadius * 0.4f,
        center = shineCenter
    )
}

private fun cricketSeamPath(center: Offset, ballRadius: Float): Path {
    val points = 12
    return Path().apply {
        for (i in 0 until points) {
            val angle = Math.toRadians((i * 360.0 / points) + 45)
            val r = if (i % 2 == 0) ballRadius * 0.6f else ballRadius * 0.8f
            val x = center.x + (cos(angle) * r).toFloat()
            val y = center.y + (sin(angle) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * Wicket falling animation for dismissals
 */
@Composable
fun WicketFallingAnimation(
    isActive: Boolean,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (isActive && !hasStarted) {
            hasStarted = true
            kotlinx.coroutines.delay(WICKET_FALL_DURATION_MS)
            onComplete()
        }
    }

    val fallRotation by animateFloatAsState(
        targetValue = if (isActive) 90f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "wicketFall"
    )

    if (!isActive) return

    Canvas(modifier = modifier.size(100.dp)) {
        drawWicket(fallRotation = fallRotation)
    }
}

private fun DrawScope.drawWicket(fallRotation: Float) {
    val stumpWidth = 8.dp.toPx()
    val stumpHeight = 80.dp.toPx()
    val bailWidth = 20.dp.toPx()
    val bailHeight = 6.dp.toPx()
    val pivotY = size.height - stumpHeight * 0.3f

    drawStump(
        xFraction = 0.25f,
        rotationDeg = -fallRotation * 0.3f,
        pivotY = pivotY,
        stumpWidth = stumpWidth,
        stumpHeight = stumpHeight
    )
    drawStump(
        xFraction = 0.5f,
        rotationDeg = 0f,
        pivotY = pivotY,
        stumpWidth = stumpWidth,
        stumpHeight = stumpHeight
    )
    drawStump(
        xFraction = 0.75f,
        rotationDeg = fallRotation * 0.5f,
        pivotY = pivotY,
        stumpWidth = stumpWidth,
        stumpHeight = stumpHeight
    )

    drawBail(
        pivotXFraction = 0.4f,
        topLeftXFraction = 0.3f,
        rotationDeg = fallRotation * 2f,
        bailLift = fallRotation / 90f * 50f,
        stumpHeight = stumpHeight,
        bailWidth = bailWidth,
        bailHeight = bailHeight
    )
    drawBail(
        pivotXFraction = 0.6f,
        topLeftXFraction = 0.55f,
        rotationDeg = -fallRotation * 1.5f,
        bailLift = fallRotation / 90f * 30f,
        stumpHeight = stumpHeight,
        bailWidth = bailWidth,
        bailHeight = bailHeight
    )
}

private fun DrawScope.drawStump(
    xFraction: Float,
    rotationDeg: Float,
    pivotY: Float,
    stumpWidth: Float,
    stumpHeight: Float
) {
    val x = size.width * xFraction
    val draw = {
        drawRoundRect(
            color = GoldPrimary,
            topLeft = Offset(x - stumpWidth / 2, size.height - stumpHeight),
            size = GeometrySize(stumpWidth, stumpHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
    if (rotationDeg == 0f) {
        draw()
    } else {
        rotate(degrees = rotationDeg, pivot = Offset(x, pivotY)) { draw() }
    }
}

private fun DrawScope.drawBail(
    pivotXFraction: Float,
    topLeftXFraction: Float,
    rotationDeg: Float,
    bailLift: Float,
    stumpHeight: Float,
    bailWidth: Float,
    bailHeight: Float
) {
    rotate(
        degrees = rotationDeg,
        pivot = Offset(size.width * pivotXFraction, size.height - stumpHeight - bailHeight)
    ) {
        drawRoundRect(
            color = GoldLight,
            topLeft = Offset(
                size.width * topLeftXFraction,
                size.height - stumpHeight - bailHeight - bailLift
            ),
            size = GeometrySize(bailWidth, bailHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

/**
 * Premium stat card with animated counter
 */
@Composable
fun AnimatedStatCard(
    value: Int,
    label: String,
    icon: ImageVector,
    accentColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "statValue"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "statCard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.4f + shimmerOffset * 0.2f),
                    colors.glassBorder.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCardIcon(icon = icon, accentColor = accentColor)
            Text(
                text = animatedValue.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = (-1).sp
            )
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatCardIcon(icon: ImageVector, accentColor: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(accentColor.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Trophy/medal component for achievements
 */
@Composable
fun TrophyBadge(
    tier: TrophyTier,
    size: Dp = 64.dp,
    unlocked: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "trophy")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val color = trophyColor(tier, hierarchy)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (unlocked) {
            Box(
                modifier = Modifier
                    .size(size)
                    .blur(8.dp)
                    .background(color.copy(alpha = 0.4f), CircleShape)
            )
        }

        TrophyInnerCircle(
            size = size,
            color = color,
            unlocked = unlocked,
            shimmerOffset = shimmerOffset,
            colors = colors
        )
    }
}

@Composable
private fun TrophyInnerCircle(
    size: Dp,
    color: Color,
    unlocked: Boolean,
    shimmerOffset: Float,
    colors: BouncerColors
) {
    val brush = trophyBrush(unlocked, color, shimmerOffset, colors)
    val borderColor = if (unlocked) color.copy(alpha = 0.8f) else colors.glassBorder.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(size * 0.85f)
            .background(brush, CircleShape)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = if (unlocked) Color.White else colors.textTertiary,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

private fun trophyColor(tier: TrophyTier, hierarchy: ColorHierarchy): Color = when (tier) {
    TrophyTier.BRONZE -> Color(0xFFCD7F32)
    TrophyTier.SILVER -> Color(0xFFC0C0C0)
    TrophyTier.GOLD -> hierarchy.secondary.primary
    TrophyTier.PLATINUM -> Color(0xFFE5E4E2)
    TrophyTier.DIAMOND -> Color(0xFFB9F2FF)
}

private fun trophyBrush(
    unlocked: Boolean,
    color: Color,
    shimmerOffset: Float,
    colors: BouncerColors
): Brush {
    if (unlocked) {
        return Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = 0.8f),
                color,
                color.copy(alpha = 0.6f)
            ),
            start = Offset(shimmerOffset * 50, 0f),
            end = Offset((shimmerOffset + 1) * 50, 50f)
        )
    }
    val locked = colors.glassBorder
    return Brush.linearGradient(
        colors = listOf(
            locked.copy(alpha = 0.4f),
            locked.copy(alpha = 0.2f)
        )
    )
}

enum class TrophyTier {
    BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
}

/**
 * Animated progress ring
 */
@Composable
fun AnimatedProgressRing(
    progress: Float,
    size: Dp = 80.dp,
    strokeWidth: Dp = 8.dp,
    backgroundColor: Color = BouncerDesignSystem.colors.glassBorder.copy(alpha = 0.3f),
    progressColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    content: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

            drawArc(
                color = backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        progressColor.copy(alpha = 0.5f),
                        progressColor,
                        progressColor.copy(alpha = 0.8f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = stroke
            )
        }

        content()
    }
}
