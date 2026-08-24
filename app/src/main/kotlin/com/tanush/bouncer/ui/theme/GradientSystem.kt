package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import io.sentry.Sentry

private object GradientEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Linear = LinearEasing
}

private object GradientDuration {
    const val Short = 150
    const val Medium = 300
    const val Long = 500
    const val Shimmer = 1800
    const val GradientShift = 3000
    const val SlowRotation = 8000
    const val Ambient = 5000
}

private fun repeatTween(
    duration: Int,
    easing: Easing,
    mode: RepeatMode = RepeatMode.Reverse
): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = tween(durationMillis = duration, easing = easing),
    repeatMode = mode
)

private fun radialAlphaColors(color: Color, alpha: Float, midFactor: Float = 0.3f): List<Color> =
    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * midFactor), Color.Transparent)

private inline fun safeDraw(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

object BrandGradients {
    val GoldGradientDark: Brush
        get() = Brush.linearGradient(listOf(GoldLightDark, GoldDark, GoldPrimaryDark, GoldDark))

    fun GoldGradientAlpha(alpha: Float = 0.8f): Brush = Brush.linearGradient(
        listOf(GoldLightDark.copy(alpha = alpha), GoldDark.copy(alpha = alpha), GoldPrimaryDark.copy(alpha = alpha))
    )

    val GoldHorizontal: Brush
        get() = Brush.horizontalGradient(listOf(GoldPrimaryDark, GoldDark, GoldDark))

    val LiveGradient: Brush
        get() = Brush.linearGradient(listOf(LiveBlueBright, LiveBlue, LiveBlueBright.copy(alpha = 0.8f)))

    val SuccessGradient: Brush
        get() = Brush.horizontalGradient(listOf(SuccessGreenBright, SuccessGreen, SuccessGreenMuted))

    val ErrorGradient: Brush
        get() = Brush.horizontalGradient(listOf(ErrorRed, ErrorRedMuted, ErrorRed.copy(alpha = 0.8f)))

    val WarningGradient: Brush
        get() = Brush.horizontalGradient(listOf(WarningYellow, WarningOrange, WarningOrangeMuted))

    val DarkSurfaceGradient: Brush
        get() = Brush.verticalGradient(listOf(BlackElevated, BlackSurface, BlackBackground))

    val CardGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(BlackSurface, CardDarkBlue.copy(alpha = 0.5f), BlackBackground),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )

    val GlassGradient: Brush
        get() = Brush.linearGradient(listOf(GlassWhite, GlassSurfaceDark, GlassDark))

    val ChartGradient: Brush
        get() = Brush.horizontalGradient(listOf(ChartCyan, ChartBlue, ChartPurple))

    val NeonGradient: Brush
        get() = Brush.linearGradient(listOf(NeonSuccess, ChartCyan, LiveBlueBright))

    val SixerGradient: Brush
        get() = Brush.horizontalGradient(listOf(BoundarySix, SixerPurple, ChartPurple))

    val FourGradient: Brush
        get() = Brush.horizontalGradient(listOf(BoundaryFour, ShinyGoldMid, WarningOrange))

    fun teamAccent(teamPrimary: Color, teamSecondary: Color): Brush =
        Brush.horizontalGradient(listOf(teamPrimary, teamSecondary, teamPrimary.copy(alpha = 0.7f)))

    fun teamSurface(teamPrimary: Color, alpha: Float = 0.08f): Brush =
        Brush.verticalGradient(
            listOf(teamPrimary.copy(alpha = alpha), teamPrimary.copy(alpha = alpha * 0.3f), Color.Transparent)
        )
}

@Composable
fun Modifier.animatedGradientShift(
    colors: List<Color> = listOf(
        CardDarkBlue,
        BlackSurface,
        CardDarkBlue.copy(alpha = 0.7f),
        BlackBackground
    ),
    duration: Int = GradientDuration.GradientShift,
    isLive: Boolean = true
): Modifier = composed {
    if (!isLive) {
        return@composed this.background(Brush.linearGradient(colors = colors))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gradientShift")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = repeatTween(duration, GradientEasing.Standard),
        label = "gradientOffset"
    )

    drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(size.width * offset, 0f),
                end = Offset(size.width * (1f - offset), size.height)
            )
        )
    }
}

@Composable
fun Modifier.liveMatchGradient(
    baseColor: Color = CardDarkBlue,
    accentColor: Color = LiveBlue,
    duration: Int = GradientDuration.GradientShift
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "liveGradient")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = repeatTween(duration / 2, GradientEasing.Emphasized),
        label = "liveAlpha"
    )
    val position by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = repeatTween(duration, GradientEasing.Standard),
        label = "livePosition"
    )

    drawBehind {
        drawRect(brush = Brush.verticalGradient(listOf(baseColor, BlackBackground)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = radialAlphaColors(accentColor, alpha),
                center = Offset(size.width * position, size.height * 0.3f),
                radius = size.maxDimension * 0.6f
            )
        )
    }
}

@Composable
fun Modifier.shimmerGradientBorder(
    borderWidth: Dp = 2.dp,
    shimmerColors: List<Color> = listOf(
        Color.Transparent,
        ShinyGoldLight.copy(alpha = 0.5f),
        ShinyGoldMid.copy(alpha = 0.8f),
        ShinyGoldLight.copy(alpha = 0.5f),
        Color.Transparent
    ),
    shape: Shape = RoundedCornerShape(16.dp),
    duration: Int = GradientDuration.Shimmer
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerBorder")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = repeatTween(duration, GradientEasing.Linear, RepeatMode.Restart),
        label = "shimmerProgress"
    )

    drawWithCache {
        val shimmerBrush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(size.width * shimmerProgress, 0f),
            end = Offset(size.width * (shimmerProgress + 0.5f), size.height)
        )
        onDrawBehind {
            drawRoundRect(
                brush = shimmerBrush,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = borderWidth.toPx())
            )
        }
    }
}

@Composable
fun Modifier.goldShimmerBorder(
    borderWidth: Dp = 1.5.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    duration: Int = GradientDuration.Shimmer
): Modifier = shimmerGradientBorder(
    borderWidth = borderWidth,
    shimmerColors = listOf(
        GoldHairline,
        ShinyGoldLight.copy(alpha = 0.4f),
        ShinyGoldMid.copy(alpha = 0.7f),
        ShinyGoldDeep.copy(alpha = 0.6f),
        ShinyGoldLight.copy(alpha = 0.4f),
        GoldHairline
    ),
    shape = shape,
    duration = duration
)

@Composable
fun Modifier.liveShimmerBorder(
    borderWidth: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    duration: Int = GradientDuration.Shimmer
): Modifier = shimmerGradientBorder(
    borderWidth = borderWidth,
    shimmerColors = listOf(
        Color.Transparent,
        LiveBlueBright.copy(alpha = 0.3f),
        LiveBlue.copy(alpha = 0.6f),
        LiveBlueBright.copy(alpha = 0.3f),
        Color.Transparent
    ),
    shape = shape,
    duration = duration
)

fun Modifier.radialGradientSpotlight(
    color: Color = ShinyGoldMid,
    alpha: Float = 0.2f,
    centerX: Float = 0.5f,
    centerY: Float = 0.3f,
    radiusMultiplier: Float = 0.8f
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.5f),
                color.copy(alpha = alpha * 0.15f),
                Color.Transparent
            ),
            center = Offset(size.width * centerX, size.height * centerY),
            radius = size.maxDimension * radiusMultiplier
        )
    )
}

fun Modifier.multiPointRadialGradient(
    points: List<GradientPoint> = defaultGradientPoints()
): Modifier = drawBehind {
    points.forEach { point ->
        safeDraw {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = radialAlphaColors(point.color, point.alpha),
                    center = Offset(size.width * point.centerX, size.height * point.centerY),
                    radius = size.maxDimension * point.radius
                )
            )
        }
    }
}

data class GradientPoint(
    val color: Color,
    val centerX: Float,
    val centerY: Float,
    val radius: Float = 0.5f,
    val alpha: Float = 0.15f
)

fun defaultGradientPoints(): List<GradientPoint> = listOf(
    GradientPoint(ShinyGoldMid, 0.2f, 0.2f, 0.5f, 0.12f),
    GradientPoint(LiveBlueBright, 0.8f, 0.7f, 0.4f, 0.08f)
)

fun Modifier.sweepGradient(
    colors: List<Color> = listOf(
        ShinyGoldDeep,
        ShinyGoldMid,
        ShinyGoldLight,
        ShinyGoldMid,
        ShinyGoldDeep
    ),
    startAngle: Float = 0f,
    progress: Float = 1f
): Modifier = drawBehind {
    val sweepColors = if (progress < 1f) {
        colors.take((colors.size * progress).toInt().coerceAtLeast(2))
    } else {
        colors
    }
    rotate(degrees = startAngle) {
        drawCircle(brush = Brush.sweepGradient(colors = sweepColors))
    }
}

@Composable
fun Modifier.animatedSweepGradient(
    colors: List<Color> = listOf(ShinyGoldDeep, ShinyGoldMid, ShinyGoldLight, Color.Transparent),
    duration: Int = GradientDuration.SlowRotation
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "sweepRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = repeatTween(duration, GradientEasing.Linear, RepeatMode.Restart),
        label = "sweepAngle"
    )
    sweepGradient(colors = colors, startAngle = rotation)
}

fun Modifier.conicGradient(
    segments: List<GradientSegment>,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f
): Modifier = drawBehind {
    val center = Offset(size.width * centerX, size.height * centerY)
    val radius = size.minDimension / 2

    segments.forEachIndexed { index, segment ->
        safeDraw {
            val startAngle = segments.take(index).sumOf { it.weight.toDouble() }.toFloat() * 360f
            val sweepAngle = segment.weight * 360f
            drawArc(
                color = segment.color,
                startAngle = startAngle - 90f,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }
    }
}

data class GradientSegment(val color: Color, val weight: Float)

fun Modifier.gradientMesh(
    meshPoints: List<MeshPoint> = defaultMeshPoints()
): Modifier = drawBehind {
    meshPoints.forEach { point ->
        safeDraw {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = radialAlphaColors(point.color, point.alpha, 0.4f),
                    center = Offset(size.width * point.x, size.height * point.y),
                    radius = size.maxDimension * point.influence
                )
            )
        }
    }
}

data class MeshPoint(
    val x: Float,
    val y: Float,
    val color: Color,
    val alpha: Float = 0.3f,
    val influence: Float = 0.5f
)

fun defaultMeshPoints(): List<MeshPoint> = listOf(
    MeshPoint(0.1f, 0.1f, ShinyGoldMid, 0.15f, 0.6f),
    MeshPoint(0.9f, 0.2f, LiveBlueBright, 0.1f, 0.5f),
    MeshPoint(0.2f, 0.8f, ChartPurple, 0.08f, 0.4f),
    MeshPoint(0.8f, 0.9f, SuccessGreen, 0.06f, 0.35f)
)

fun Modifier.premiumMesh(): Modifier = gradientMesh(
    meshPoints = listOf(
        MeshPoint(0.15f, 0.1f, ShinyGoldLight, 0.12f, 0.5f),
        MeshPoint(0.85f, 0.15f, ShinyGoldMid, 0.1f, 0.45f),
        MeshPoint(0.5f, 0.5f, CardDarkBlue, 0.15f, 0.6f),
        MeshPoint(0.1f, 0.9f, ShinyGoldDeep, 0.08f, 0.4f),
        MeshPoint(0.9f, 0.85f, ShinyGoldBronze, 0.06f, 0.35f)
    )
)

@Composable
fun Modifier.animatedGradientMesh(
    baseMeshPoints: List<MeshPoint> = defaultMeshPoints(),
    movementRange: Float = 0.1f,
    duration: Int = GradientDuration.Ambient
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "meshAnimation")
    val offset by infiniteTransition.animateFloat(
        initialValue = -movementRange,
        targetValue = movementRange,
        animationSpec = repeatTween(duration, GradientEasing.Standard),
        label = "meshOffset"
    )

    val animatedPoints = baseMeshPoints.mapIndexed { index, point ->
        val offsetMultiplier = if (index % 2 == 0) 1f else -1f
        point.copy(
            x = (point.x + offset * offsetMultiplier).coerceIn(0f, 1f),
            y = (point.y + offset * 0.5f * offsetMultiplier).coerceIn(0f, 1f)
        )
    }

    gradientMesh(animatedPoints)
}

fun Modifier.noiseGradient(
    baseColor: Color = BlackSurface,
    noiseAlpha: Float = 0.05f
): Modifier = drawBehind {
    drawRect(color = baseColor)
    val noisePoints = 20
    for (i in 0 until noisePoints) {
        safeDraw {
            val x = (i * 73.0f) % size.width
            val y = (i * 137.0f) % size.height
            drawCircle(
                color = Color.White.copy(alpha = noiseAlpha * ((i % 3 + 1) * 0.33f)),
                radius = 1.5f,
                center = Offset(x, y)
            )
        }
    }
}

fun Modifier.diagonalGradient(
    colors: List<Color>,
    angle: Float = 45f
): Modifier = drawBehind {
    val radians = Math.toRadians(angle.toDouble()).toFloat()
    val length = size.maxDimension * 1.5f
    val centerX = size.width / 2
    val centerY = size.height / 2
    val start = Offset(centerX - length * cos(radians) / 2, centerY - length * sin(radians) / 2)
    val end = Offset(centerX + length * cos(radians) / 2, centerY + length * sin(radians) / 2)
    drawRect(brush = Brush.linearGradient(colors = colors, start = start, end = end))
}

fun Modifier.threePointGradient(
    topColor: Color = CardDarkBlue,
    middleColor: Color = BlackSurface,
    bottomColor: Color = BlackBackground
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(topColor.copy(alpha = 0.4f), Color.Transparent),
            center = Offset(size.width * 0.5f, 0f),
            radius = size.height * 0.6f
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, middleColor.copy(alpha = 0.2f), Color.Transparent)
        )
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, bottomColor.copy(alpha = 0.5f)),
            center = Offset(size.width / 2, size.height),
            radius = size.height * 0.8f
        )
    )
}

@Composable
fun Modifier.premiumScreenGradient(): Modifier = this
    .background(BlackBackground)
    .premiumMesh()
    .diagonalGradient(
        colors = listOf(Color.Transparent, ShinyGoldDeep.copy(alpha = 0.03f), Color.Transparent),
        angle = 135f
    )

@Composable
fun Modifier.liveCardGradient(): Modifier = composed {
    this
        .liveMatchGradient()
        .liveShimmerBorder()
}

@Composable
fun Modifier.premiumCardGradient(): Modifier = composed {
    this
        .background(BrandGradients.CardGradient)
        .goldShimmerBorder()
        .radialGradientSpotlight(color = ShinyGoldLight, alpha = 0.08f, centerY = 0.2f)
}
