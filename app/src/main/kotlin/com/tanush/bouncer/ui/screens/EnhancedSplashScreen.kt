package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random
import com.tanush.bouncer.ui.theme.GoldLight
import com.tanush.bouncer.ui.theme.GoldPrimary

private const val DEG_TO_RAD = PI.toFloat() / 180f

@Composable
private fun infiniteFloat(
    transition: InfiniteTransition,
    initial: Float,
    target: Float,
    durationMs: Int,
    easing: Easing = LinearEasing,
    mode: RepeatMode = RepeatMode.Restart,
    label: String
) = transition.animateFloat(
    initialValue = initial,
    targetValue = target,
    animationSpec = infiniteRepeatable(animation = tween(durationMs, easing = easing), repeatMode = mode),
    label = label
)

private fun DrawScope.drawSeam(
    center: Offset,
    seamRadius: Float,
    curveUp: Boolean,
    shadowColor: Color,
    shadowWidth: Float,
    mainColor: Color,
    mainWidth: Float
) {
    val dy = if (curveUp) -seamRadius * 0.85f else seamRadius * 0.85f
    val path = Path().apply {
        moveTo(center.x - seamRadius, center.y)
        cubicTo(
            center.x - seamRadius * 0.5f, center.y + dy,
            center.x + seamRadius * 0.5f, center.y + dy,
            center.x + seamRadius, center.y
        )
    }
    drawPath(path, color = shadowColor, style = Stroke(width = shadowWidth, cap = StrokeCap.Round))
    drawPath(path, color = mainColor, style = Stroke(width = mainWidth, cap = StrokeCap.Round))
}

@Composable
fun EnhancedSplashScreen(
    onSplashComplete: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val density = LocalDensity.current

    var phase by remember { mutableIntStateOf(0) }
    var ballAnimationComplete by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var startTransitionOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        phase = 1
        delay(800)
        phase = 2
        ballAnimationComplete = true
        delay(400)
        phase = 3
        showLogo = true
        delay(300)
        showText = true
        delay(600)
        showTagline = true
        delay(800)
        phase = 4
        startTransitionOut = true
        delay(500)
        onSplashComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "enhanced_splash")
    val floodlightAngle by infiniteFloat(infiniteTransition, 0f, 360f, 12000, label = "floodlight_sweep")
    val ballRotation by infiniteFloat(infiniteTransition, 0f, 360f, 2000, label = "ball_rotation")
    val logoYRotation by infiniteFloat(infiniteTransition, -15f, 15f, 3000, FastOutSlowInEasing, RepeatMode.Reverse, "logo_y_rotation")
    val shimmerOffset by infiniteFloat(infiniteTransition, -1f, 2f, 2000, label = "shimmer")
    val glowPulse by infiniteFloat(infiniteTransition, 0.4f, 0.8f, 1500, FastOutSlowInEasing, RepeatMode.Reverse, "glow_pulse")
    val particlePhase by infiniteFloat(infiniteTransition, 0f, 1f, 4000, label = "particles")

    val ballProgress by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ball_trajectory"
    )

    val ballBounce by animateFloatAsState(
        targetValue = if (ballAnimationComplete) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "ball_bounce"
    )

    val logoScale by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "logo_scale"
    )

    val transitionZoom by animateFloatAsState(
        targetValue = if (startTransitionOut) 20f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "transition_zoom"
    )

    val transitionAlpha by animateFloatAsState(
        targetValue = if (startTransitionOut) 0f else 1f,
        animationSpec = tween(400, easing = LinearEasing),
        label = "transition_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBase)
            .graphicsLayer {
                scaleX = transitionZoom
                scaleY = transitionZoom
                alpha = transitionAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPitchBackground(colors)
            drawCrowdSilhouettes(colors, particlePhase)
            drawDustParticles(colors, particlePhase)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFloodlights(colors, floodlightAngle, glowPulse)
        }

        if (phase >= 1 && phase < 3) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBallTrajectory(
                    colors = colors,
                    progress = ballProgress,
                    rotation = ballRotation,
                    bounceOffset = ballBounce * 50f
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(tween(400)) + scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
                )
            ) {
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(200.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary.copy(alpha = glowPulse * 0.4f),
                                    hierarchy.secondary.primary.copy(alpha = glowPulse * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension / 2
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(logoScale)
                            .graphicsLayer {
                                rotationY = logoYRotation
                                cameraDistance = 12f * density.density
                            }
                    ) {
                        draw3DCricketBall(
                            colors = colors,
                            rotation = ballRotation,
                            shimmerOffset = shimmerOffset
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .size(80.dp, 20.dp)
                            .offset(y = 70.dp)
                            .alpha(0.4f)
                    ) {
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            ),
                            size = size
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(visible = showText, enter = fadeIn(tween(300))) {
                LetterByLetterText(text = "BOUNCER", shimmerOffset = shimmerOffset, colors = colors)
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(500)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500)
                )
            ) {
                androidx.compose.material3.Text(
                    text = "Live Cricket. Elevated.",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.sp,
                        fontFamily = InterFontFamily,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.textSecondary,
                                hierarchy.secondary.primary.copy(alpha = 0.7f),
                                colors.textSecondary
                            )
                        )
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = showTagline,
            enter = fadeIn(tween(600)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = "Premium Cricket Experience",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    fontFamily = InterFontFamily,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    text = "v1.0",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFontFamily,
                    color = colors.textMuted.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun DrawScope.drawPitchBackground(colors: BouncerColors) {
    val centerX = size.width / 2
    val centerY = size.height / 2

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A1D22), Color(0xFF141619), colors.backgroundBase),
            center = Offset(centerX, centerY * 0.8f),
            radius = size.width * 1.2f
        )
    )

    val pitchWidth = size.width * 0.15f
    val pitchHeight = size.height * 0.35f
    val pitchTop = centerY - pitchHeight / 2

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A3D1A).copy(alpha = 0.3f),
                Color(0xFF2D3A28).copy(alpha = 0.25f),
                Color(0xFF3D4A35).copy(alpha = 0.2f),
                Color(0xFF2D3A28).copy(alpha = 0.25f),
                Color(0xFF1A3D1A).copy(alpha = 0.3f)
            )
        ),
        topLeft = Offset(centerX - pitchWidth / 2, pitchTop),
        size = Size(pitchWidth, pitchHeight)
    )

    val creaseColor = GoldLight.copy(alpha = 0.15f)
    val creaseStrokeWidth = 1.5.dp.toPx()
    val creaseStartX = centerX - pitchWidth / 2 - 10.dp.toPx()
    val creaseEndX = centerX + pitchWidth / 2 + 10.dp.toPx()
    listOf(pitchTop + pitchHeight * 0.15f, pitchTop + pitchHeight * 0.85f).forEach { creaseY ->
        drawLine(
            color = creaseColor,
            start = Offset(creaseStartX, creaseY),
            end = Offset(creaseEndX, creaseY),
            strokeWidth = creaseStrokeWidth
        )
    }

    val bloomCenter = Offset(centerX, size.height * 0.15f)
    val bloomRadius = size.width * 0.6f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                GoldPrimary.copy(alpha = 0.08f),
                GoldPrimary.copy(alpha = 0.04f),
                Color.Transparent
            ),
            center = bloomCenter,
            radius = bloomRadius
        ),
        radius = bloomRadius,
        center = bloomCenter
    )
}

private fun DrawScope.drawCrowdSilhouettes(colors: BouncerColors, animPhase: Float) {
    val crowdColor = Color(0xFF15171A)
    val crowdHighlight = GoldPrimary.copy(alpha = 0.05f)
    val step = 8.dp.toPx()
    val basePhase = animPhase * 2 * PI.toFloat()

    fun crowdPath(startX: Float, edgeX: Float, dir: Int): Path = Path().apply {
        moveTo(edgeX, size.height * 0.4f)
        lineTo(edgeX, size.height)
        lineTo(startX, size.height)
        var x = startX
        var phase = basePhase
        while (if (dir < 0) x > 0 else x < size.width) {
            val headHeight = size.height * (0.55f + 0.05f * sin(phase + x * 0.02f))
            lineTo(x, headHeight)
            x += step * dir
            phase += 0.5f
        }
        close()
    }

    drawPath(crowdPath(size.width * 0.15f, 0f, -1), color = crowdColor)
    drawPath(crowdPath(size.width * 0.85f, size.width, 1), color = crowdColor)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, crowdHighlight, crowdHighlight.copy(alpha = 0.02f)),
            startY = size.height * 0.5f,
            endY = size.height
        )
    )
}

private fun DrawScope.drawDustParticles(colors: BouncerColors, phase: Float) {
    val random = Random(42)
    repeat(30) { i ->
        val baseX = random.nextFloat() * size.width
        val baseY = random.nextFloat() * size.height
        val particleSize = random.nextFloat() * 2.dp.toPx() + 1.dp.toPx()
        val speed = random.nextFloat() * 0.5f + 0.5f
        val alpha = random.nextFloat() * 0.3f + 0.1f

        val currentPhase = (phase * speed + i * 0.1f) % 1f
        val x = baseX + sin(currentPhase * 2 * PI.toFloat()) * 20.dp.toPx()
        val y = baseY - currentPhase * size.height * 0.3f

        if (y > 0 && y < size.height) {
            drawCircle(
                color = GoldLight.copy(alpha = alpha * (1 - currentPhase)),
                radius = particleSize,
                center = Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawFloodlights(colors: BouncerColors, angle: Float, glowIntensity: Float) {
    val corners = listOf(
        Offset(0f, 0f),
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height),
        Offset(size.width * 0.2f, 0f),
        Offset(size.width * 0.8f, 0f),
        Offset(0f, size.height * 0.3f),
        Offset(size.width, size.height * 0.3f)
    )

    val centerX = size.width / 2
    val centerY = size.height / 2
    val beamLength = size.width * 0.8f
    val spreadDist = beamLength * 0.15f

    corners.forEachIndexed { index, corner ->
        val baseAngle = atan2(centerY - corner.y, centerX - corner.x) / DEG_TO_RAD
        val sweepOffset = sin((angle + index * 45) * DEG_TO_RAD) * 15f
        val currentAngle = baseAngle + sweepOffset
        val rad = currentAngle * DEG_TO_RAD
        val perpRad = (currentAngle + 90) * DEG_TO_RAD

        val endX = corner.x + cos(rad) * beamLength
        val endY = corner.y + sin(rad) * beamLength

        val beamPath = Path().apply {
            moveTo(corner.x, corner.y)
            val px = cos(perpRad) * spreadDist
            val py = sin(perpRad) * spreadDist
            lineTo(endX + px, endY + py)
            lineTo(endX - px, endY - py)
            close()
        }

        val beamAlpha = (0.02f + glowIntensity * 0.02f) * (1f - index * 0.05f)
        drawPath(
            path = beamPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    GoldLight.copy(alpha = beamAlpha),
                    GoldPrimary.copy(alpha = beamAlpha * 0.5f),
                    Color.Transparent
                ),
                center = corner,
                radius = beamLength
            )
        )

        val flareSize = 15.dp.toPx() + glowIntensity * 10.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f + glowIntensity * 0.1f),
                    GoldLight.copy(alpha = 0.08f),
                    Color.Transparent
                )
            ),
            radius = flareSize,
            center = corner
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.3f + glowIntensity * 0.2f),
            radius = 3.dp.toPx(),
            center = corner
        )
    }
}

private fun DrawScope.drawBallTrajectory(
    colors: BouncerColors,
    progress: Float,
    rotation: Float,
    bounceOffset: Float
) {
    val startX = size.width * 1.1f
    val startY = -size.height * 0.1f
    val endX = size.width / 2
    val endY = size.height / 2

    val control1X = size.width * 0.8f
    val control1Y = size.height * 0.1f
    val control2X = size.width * 0.6f
    val control2Y = size.height * 0.6f

    val t = progress
    val t2 = t * t
    val t3 = t2 * t
    val mt = 1 - t
    val mt2 = mt * mt
    val mt3 = mt2 * mt

    val ballX = mt3 * startX + 3 * mt2 * t * control1X + 3 * mt * t2 * control2X + t3 * endX
    val ballY = mt3 * startY + 3 * mt2 * t * control1Y + 3 * mt * t2 * control2Y + t3 * endY + bounceOffset

    if (progress < 1f) {
        val trailPath = Path().apply {
            moveTo(startX, startY)
            cubicTo(control1X, control1Y, control2X, control2Y, ballX, ballY)
        }
        drawPath(
            path = trailPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    GoldPrimary.copy(alpha = 0.2f),
                    GoldPrimary.copy(alpha = 0.4f)
                ),
                start = Offset(startX, startY),
                end = Offset(ballX, ballY)
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    val ballRadius = 25.dp.toPx()
    val ballCenter = Offset(ballX, ballY)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
        ),
        radius = ballRadius * 1.2f,
        center = Offset(ballX + 5.dp.toPx(), ballY + 5.dp.toPx())
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE53935), Color(0xFFC62828), Color(0xFFB71C1C)),
            center = Offset(ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f)
        ),
        radius = ballRadius,
        center = ballCenter
    )

    withTransform({ rotate(rotation, ballCenter) }) {
        drawSeam(
            center = ballCenter,
            seamRadius = ballRadius * 0.8f,
            curveUp = true,
            shadowColor = Color(0xFF5D4037).copy(alpha = 0.6f),
            shadowWidth = 3.dp.toPx(),
            mainColor = Color(0xFFF5F5F5),
            mainWidth = 2.dp.toPx()
        )
    }

    val highlightCenter = Offset(ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
            center = highlightCenter,
            radius = ballRadius * 0.6f
        ),
        radius = ballRadius * 0.6f,
        center = highlightCenter
    )
}

private fun DrawScope.draw3DCricketBall(
    colors: BouncerColors,
    rotation: Float,
    shimmerOffset: Float
) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2
    val shadowCenter = Offset(center.x + 6.dp.toPx(), center.y + 6.dp.toPx())

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
            center = shadowCenter
        ),
        radius = radius,
        center = shadowCenter
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE53935),
                Color(0xFFD32F2F),
                Color(0xFFC62828),
                Color(0xFFB71C1C)
            ),
            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
            radius = radius * 1.5f
        ),
        radius = radius,
        center = center
    )

    rotate(rotation, center) {
        val seamRadius = radius * 0.75f
        drawSeam(
            center = center,
            seamRadius = seamRadius,
            curveUp = true,
            shadowColor = Color(0xFF4E342E).copy(alpha = 0.7f),
            shadowWidth = 4.dp.toPx(),
            mainColor = Color(0xFFFAFAFA),
            mainWidth = 2.5.dp.toPx()
        )

        val stitchCount = 10
        for (i in 0 until stitchCount) {
            val t = i.toFloat() / (stitchCount - 1)
            val x = center.x - radius * 0.75f + (radius * 1.5f * t)
            val offset = sin(t * PI).toFloat() * radius * 0.65f
            val y = center.y - offset

            drawCircle(color = Color(0xFFFAFAFA), radius = 1.8.dp.toPx(), center = Offset(x, y - 5.dp.toPx()))
            drawCircle(color = Color(0xFFFAFAFA), radius = 1.8.dp.toPx(), center = Offset(x, y + 5.dp.toPx()))
        }

        drawSeam(
            center = center,
            seamRadius = seamRadius,
            curveUp = false,
            shadowColor = Color(0xFF4E342E).copy(alpha = 0.5f),
            shadowWidth = 3.dp.toPx(),
            mainColor = Color(0xFFFAFAFA).copy(alpha = 0.6f),
            mainWidth = 2.dp.toPx()
        )
    }

    val mainHighlightCenter = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
            center = mainHighlightCenter,
            radius = radius * 0.55f
        ),
        radius = radius * 0.55f,
        center = mainHighlightCenter
    )

    val shimmerAngle = shimmerOffset * 180f
    val shimmerRad = shimmerAngle * DEG_TO_RAD
    val shimmerCenter = Offset(
        center.x + cos(shimmerRad) * radius * 0.3f,
        center.y + sin(shimmerRad) * radius * 0.3f
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                GoldLight.copy(alpha = 0.3f),
                GoldPrimary.copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = shimmerCenter,
            radius = radius * 0.4f
        ),
        radius = radius * 0.4f,
        center = shimmerCenter
    )

    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                GoldLight.copy(alpha = 0.15f),
                Color.Transparent,
                GoldLight.copy(alpha = 0.1f),
                Color.Transparent
            ),
            center = center
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

@Composable
private fun LetterByLetterText(
    text: String,
    shimmerOffset: Float,
    colors: BouncerColors
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var visibleLetters by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        for (i in text.indices) {
            delay(80)
            visibleLetters = i + 1
        }
    }

    val letterStyle = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 6.sp,
        fontFamily = InterFontFamily
    )

    Row(horizontalArrangement = Arrangement.Center) {
        text.forEachIndexed { index, char ->
            AnimatedVisibility(
                visible = index < visibleLetters,
                enter = fadeIn(tween(150)) + scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                )
            ) {
                Box {
                    androidx.compose.material3.Text(
                        text = char.toString(),
                        style = letterStyle.copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    TextPrimary,
                                    hierarchy.secondary.secondary,
                                    hierarchy.secondary.primary,
                                    colors.etchedGold,
                                    hierarchy.secondary.primary
                                )
                            )
                        )
                    )

                    val letterShimmerOffset = shimmerOffset - index * 0.15f
                    if (letterShimmerOffset in 0f..1f) {
                        androidx.compose.material3.Text(
                            text = char.toString(),
                            style = letterStyle,
                            color = Color.Transparent,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            hierarchy.secondary.secondary.copy(alpha = 0.7f),
                                            Color.White.copy(alpha = 0.9f),
                                            hierarchy.secondary.secondary.copy(alpha = 0.7f),
                                            Color.Transparent
                                        ),
                                        startX = -size.width + letterShimmerOffset * size.width * 3,
                                        endX = letterShimmerOffset * size.width * 3
                                    ),
                                    blendMode = BlendMode.SrcIn
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
