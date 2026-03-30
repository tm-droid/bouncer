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

/**
 * Enhanced Premium Animated Splash Screen for Bouncer Cricket App
 * 
 * Features:
 * - Cricket ball trajectory animation with bezier curve path
 * - Stadium floodlights effect with sweeping spotlights
 * - Dynamic pitch texture background with crowd silhouettes
 * - 3D cricket ball logo with rotating seam and gold shimmer
 * - Letter-by-letter text reveal with golden shimmer
 * - Dramatic zoom-through transition effect
 * 
 * Total duration: ~3 seconds
 */
@Composable
fun EnhancedSplashScreen(
    onSplashComplete: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val density = LocalDensity.current
    
    // Animation phase states
    var phase by remember { mutableIntStateOf(0) }
    // 0 = Initial (floodlights start)
    // 1 = Ball entering from top-right
    // 2 = Ball bouncing at center
    // 3 = Logo and text reveal
    // 4 = Transition out (zoom through)
    
    var ballAnimationComplete by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var startTransitionOut by remember { mutableStateOf(false) }
    
    // Launch sequenced animation phases
    LaunchedEffect(Unit) {
        delay(100)
        phase = 1 // Start ball trajectory
        delay(800) // Ball trajectory duration
        phase = 2 // Ball bouncing
        ballAnimationComplete = true
        delay(400) // Bounce settle time
        phase = 3
        showLogo = true
        delay(300)
        showText = true
        delay(600) // Text animation
        showTagline = true
        delay(800)
        phase = 4
        startTransitionOut = true
        delay(500) // Transition out
        onSplashComplete()
    }
    
    // ===============================================
    // Infinite animations for continuous effects
    // ===============================================
    val infiniteTransition = rememberInfiniteTransition(label = "enhanced_splash")
    
    // Floodlight sweep animation (0 to 360 degrees)
    val floodlightAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "floodlight_sweep"
    )
    
    // Ball rotation for seam effect
    val ballRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ball_rotation"
    )
    
    // 3D Y-axis rotation for logo
    val logoYRotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_y_rotation"
    )
    
    // Gold shimmer sweep
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )
    
    // Dust particle animation
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )
    
    // ===============================================
    // Ball trajectory animation (bezier curve)
    // ===============================================
    val ballProgress by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ball_trajectory"
    )
    
    // Ball bounce animation
    val ballBounce by animateFloatAsState(
        targetValue = if (ballAnimationComplete) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ball_bounce"
    )
    
    // Logo scale with bounce
    val logoScale by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )
    
    // Transition zoom effect
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
    
    // ===============================================
    // Main container
    // ===============================================
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
        // Layer 1: Dynamic pitch background with crowd silhouettes
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPitchBackground(colors)
            drawCrowdSilhouettes(colors, particlePhase)
            drawDustParticles(colors, particlePhase)
        }
        
        // Layer 2: Stadium floodlights effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFloodlights(colors, floodlightAngle, glowPulse)
        }
        
        // Layer 3: Ball trajectory path (visible during phase 1)
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
        
        // Layer 4: Main content (logo and text)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D Cricket Ball Logo
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(tween(400)) + scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow effect behind ball
                    Canvas(modifier = Modifier.size(200.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = glowPulse * 0.4f),
                                    colors.goldDark.copy(alpha = glowPulse * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension / 2
                        )
                    }
                    
                    // 3D Cricket Ball with rotation
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
                    
                    // Shadow beneath ball
                    Canvas(
                        modifier = Modifier
                            .size(80.dp, 20.dp)
                            .offset(y = 70.dp)
                            .alpha(0.4f)
                    ) {
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            ),
                            size = size
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Animated text reveal: "BOUNCER"
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(300))
            ) {
                LetterByLetterText(
                    text = "BOUNCER",
                    shimmerOffset = shimmerOffset,
                    colors = colors
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tagline fade in
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
                                colors.goldPrimary.copy(alpha = 0.7f),
                                colors.textSecondary
                            )
                        )
                    )
                )
            }
        }
        
        // Bottom branding
        AnimatedVisibility(
            visible = showTagline,
            enter = fadeIn(tween(600)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

// ===============================================
// DRAWING FUNCTIONS
// ===============================================

/**
 * Draws the pitch texture background with gradient from green to brown-green
 */
private fun DrawScope.drawPitchBackground(colors: BouncerColors) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    
    // Base stadium gradient (dark edges, slightly lighter center)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1A1D22),
                Color(0xFF141619),
                colors.backgroundBase
            ),
            center = Offset(centerX, centerY * 0.8f),
            radius = size.width * 1.2f
        )
    )
    
    // Pitch texture gradient (green to brown-green)
    val pitchWidth = size.width * 0.15f
    val pitchHeight = size.height * 0.35f
    val pitchTop = centerY - pitchHeight / 2
    
    // Pitch outline glow
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A3D1A).copy(alpha = 0.3f), // Dark green
                Color(0xFF2D3A28).copy(alpha = 0.25f), // Brown-green
                Color(0xFF3D4A35).copy(alpha = 0.2f),  // Lighter brown-green
                Color(0xFF2D3A28).copy(alpha = 0.25f),
                Color(0xFF1A3D1A).copy(alpha = 0.3f)
            )
        ),
        topLeft = Offset(centerX - pitchWidth / 2, pitchTop),
        size = Size(pitchWidth, pitchHeight)
    )
    
    // Pitch crease lines
    val creaseColor = colors.goldLight.copy(alpha = 0.15f)
    val creaseY1 = pitchTop + pitchHeight * 0.15f
    val creaseY2 = pitchTop + pitchHeight * 0.85f
    
    drawLine(
        color = creaseColor,
        start = Offset(centerX - pitchWidth / 2 - 10.dp.toPx(), creaseY1),
        end = Offset(centerX + pitchWidth / 2 + 10.dp.toPx(), creaseY1),
        strokeWidth = 1.5.dp.toPx()
    )
    drawLine(
        color = creaseColor,
        start = Offset(centerX - pitchWidth / 2 - 10.dp.toPx(), creaseY2),
        end = Offset(centerX + pitchWidth / 2 + 10.dp.toPx(), creaseY2),
        strokeWidth = 1.5.dp.toPx()
    )
    
    // Gold bloom at top center
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.goldPrimary.copy(alpha = 0.08f),
                colors.goldDark.copy(alpha = 0.04f),
                Color.Transparent
            ),
            center = Offset(centerX, size.height * 0.15f),
            radius = size.width * 0.6f
        ),
        radius = size.width * 0.6f,
        center = Offset(centerX, size.height * 0.15f)
    )
}

/**
 * Draws subtle crowd silhouettes at the edges
 */
private fun DrawScope.drawCrowdSilhouettes(colors: BouncerColors, animPhase: Float) {
    val crowdColor = Color(0xFF15171A)
    val crowdHighlight = colors.goldDark.copy(alpha = 0.05f)
    
    // Left crowd silhouette
    val leftPath = Path().apply {
        moveTo(0f, size.height * 0.4f)
        lineTo(0f, size.height)
        lineTo(size.width * 0.15f, size.height)
        
        // Wavy crowd heads
        var x = size.width * 0.15f
        var phase = animPhase * 2 * PI.toFloat()
        while (x > 0) {
            val headHeight = size.height * (0.55f + 0.05f * sin(phase + x * 0.02f))
            lineTo(x, headHeight)
            x -= 8.dp.toPx()
            phase += 0.5f
        }
        close()
    }
    drawPath(leftPath, color = crowdColor)
    
    // Right crowd silhouette
    val rightPath = Path().apply {
        moveTo(size.width, size.height * 0.4f)
        lineTo(size.width, size.height)
        lineTo(size.width * 0.85f, size.height)
        
        var x = size.width * 0.85f
        var phase = animPhase * 2 * PI.toFloat()
        while (x < size.width) {
            val headHeight = size.height * (0.55f + 0.05f * sin(phase + x * 0.02f))
            lineTo(x, headHeight)
            x += 8.dp.toPx()
            phase += 0.5f
        }
        close()
    }
    drawPath(rightPath, color = crowdColor)
    
    // Subtle crowd glow
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                crowdHighlight,
                crowdHighlight.copy(alpha = 0.02f)
            ),
            startY = size.height * 0.5f,
            endY = size.height
        )
    )
}

/**
 * Draws atmospheric dust particles
 */
private fun DrawScope.drawDustParticles(colors: BouncerColors, phase: Float) {
    val particleCount = 30
    val random = Random(42) // Fixed seed for consistent positions
    
    for (i in 0 until particleCount) {
        val baseX = random.nextFloat() * size.width
        val baseY = random.nextFloat() * size.height
        val particleSize = random.nextFloat() * 2.dp.toPx() + 1.dp.toPx()
        val speed = random.nextFloat() * 0.5f + 0.5f
        val alpha = random.nextFloat() * 0.3f + 0.1f
        
        // Particles drift slowly upward and sideways
        val currentPhase = (phase * speed + i * 0.1f) % 1f
        val x = baseX + sin(currentPhase * 2 * PI.toFloat()) * 20.dp.toPx()
        val y = baseY - currentPhase * size.height * 0.3f
        
        if (y > 0 && y < size.height) {
            drawCircle(
                color = colors.goldLight.copy(alpha = alpha * (1 - currentPhase)),
                radius = particleSize,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Draws stadium floodlights with sweeping spotlight beams
 */
private fun DrawScope.drawFloodlights(colors: BouncerColors, angle: Float, glowIntensity: Float) {
    val corners = listOf(
        Offset(0f, 0f),                           // Top-left
        Offset(size.width, 0f),                   // Top-right
        Offset(0f, size.height),                  // Bottom-left
        Offset(size.width, size.height),          // Bottom-right
        Offset(size.width * 0.2f, 0f),            // Top-left offset
        Offset(size.width * 0.8f, 0f),            // Top-right offset
        Offset(0f, size.height * 0.3f),           // Left side
        Offset(size.width, size.height * 0.3f)    // Right side
    )
    
    val centerX = size.width / 2
    val centerY = size.height / 2
    
    corners.forEachIndexed { index, corner ->
        val baseAngle = atan2(centerY - corner.y, centerX - corner.x) * 180f / PI.toFloat()
        val sweepOffset = sin((angle + index * 45) * PI.toFloat() / 180f) * 15f
        val currentAngle = baseAngle + sweepOffset
        
        // Calculate beam direction
        val beamLength = size.width * 0.8f
        val endX = corner.x + cos(currentAngle * PI.toFloat() / 180f) * beamLength
        val endY = corner.y + sin(currentAngle * PI.toFloat() / 180f) * beamLength
        
        // Draw spotlight beam (cone shape)
        val beamPath = Path().apply {
            moveTo(corner.x, corner.y)
            val perpAngle = currentAngle + 90
            val spreadDist = beamLength * 0.15f
            val endLeft = Offset(
                endX + cos(perpAngle * PI.toFloat() / 180f) * spreadDist,
                endY + sin(perpAngle * PI.toFloat() / 180f) * spreadDist
            )
            val endRight = Offset(
                endX - cos(perpAngle * PI.toFloat() / 180f) * spreadDist,
                endY - sin(perpAngle * PI.toFloat() / 180f) * spreadDist
            )
            lineTo(endLeft.x, endLeft.y)
            lineTo(endRight.x, endRight.y)
            close()
        }
        
        val beamAlpha = (0.02f + glowIntensity * 0.02f) * (1f - index * 0.05f)
        drawPath(
            path = beamPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.goldLight.copy(alpha = beamAlpha),
                    colors.goldPrimary.copy(alpha = beamAlpha * 0.5f),
                    Color.Transparent
                ),
                center = corner,
                radius = beamLength
            )
        )
        
        // Lens flare at light source
        val flareSize = 15.dp.toPx() + glowIntensity * 10.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f + glowIntensity * 0.1f),
                    colors.goldLight.copy(alpha = 0.08f),
                    Color.Transparent
                )
            ),
            radius = flareSize,
            center = corner
        )
        
        // Inner bright point
        drawCircle(
            color = Color.White.copy(alpha = 0.3f + glowIntensity * 0.2f),
            radius = 3.dp.toPx(),
            center = corner
        )
    }
}

/**
 * Draws the ball trajectory animation (bouncer delivery path)
 */
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
    
    // Control points for bezier curve (bouncer trajectory)
    val control1X = size.width * 0.8f
    val control1Y = size.height * 0.1f
    val control2X = size.width * 0.6f
    val control2Y = size.height * 0.6f
    
    // Calculate current position on bezier curve
    val t = progress
    val t2 = t * t
    val t3 = t2 * t
    val mt = 1 - t
    val mt2 = mt * mt
    val mt3 = mt2 * mt
    
    val ballX = mt3 * startX + 3 * mt2 * t * control1X + 3 * mt * t2 * control2X + t3 * endX
    val ballY = mt3 * startY + 3 * mt2 * t * control1Y + 3 * mt * t2 * control2Y + t3 * endY + bounceOffset
    
    // Draw trajectory trail
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
                    colors.goldDark.copy(alpha = 0.2f),
                    colors.goldPrimary.copy(alpha = 0.4f)
                ),
                start = Offset(startX, startY),
                end = Offset(ballX, ballY)
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    
    // Draw the ball
    val ballRadius = 25.dp.toPx()
    
    // Ball shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.4f),
                Color.Transparent
            )
        ),
        radius = ballRadius * 1.2f,
        center = Offset(ballX + 5.dp.toPx(), ballY + 5.dp.toPx())
    )
    
    // Ball base - rich red with lighting
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE53935),
                Color(0xFFC62828),
                Color(0xFFB71C1C)
            ),
            center = Offset(ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f)
        ),
        radius = ballRadius,
        center = Offset(ballX, ballY)
    )
    
    // Rotating seam
    withTransform({
        rotate(rotation, Offset(ballX, ballY))
    }) {
        val seamPath = Path().apply {
            val seamRadius = ballRadius * 0.8f
            moveTo(ballX - seamRadius, ballY)
            cubicTo(
                ballX - seamRadius * 0.5f, ballY - seamRadius * 0.7f,
                ballX + seamRadius * 0.5f, ballY - seamRadius * 0.7f,
                ballX + seamRadius, ballY
            )
        }
        
        // Seam shadow
        drawPath(
            path = seamPath,
            color = Color(0xFF5D4037).copy(alpha = 0.6f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Seam main line
        drawPath(
            path = seamPath,
            color = Color(0xFFF5F5F5),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    
    // Highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = Offset(ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f),
            radius = ballRadius * 0.6f
        ),
        radius = ballRadius * 0.6f,
        center = Offset(ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f)
    )
}

/**
 * Draws a 3D cricket ball with visible seam stitching and gold shimmer
 */
private fun DrawScope.draw3DCricketBall(
    colors: BouncerColors,
    rotation: Float,
    shimmerOffset: Float
) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2
    
    // Ball shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = Offset(center.x + 6.dp.toPx(), center.y + 6.dp.toPx())
        ),
        radius = radius,
        center = Offset(center.x + 6.dp.toPx(), center.y + 6.dp.toPx())
    )
    
    // Ball base - rich red with 3D lighting
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE53935), // Bright red highlight
                Color(0xFFD32F2F),
                Color(0xFFC62828),
                Color(0xFFB71C1C)  // Deep red shadow
            ),
            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
            radius = radius * 1.5f
        ),
        radius = radius,
        center = center
    )
    
    // Main seam with rotation
    rotate(rotation, center) {
        // Primary seam curve
        val seamPath = Path().apply {
            val seamRadius = radius * 0.75f
            moveTo(center.x - seamRadius, center.y)
            cubicTo(
                center.x - seamRadius * 0.5f, center.y - seamRadius * 0.85f,
                center.x + seamRadius * 0.5f, center.y - seamRadius * 0.85f,
                center.x + seamRadius, center.y
            )
        }
        
        // Seam shadow
        drawPath(
            path = seamPath,
            color = Color(0xFF4E342E).copy(alpha = 0.7f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Seam main
        drawPath(
            path = seamPath,
            color = Color(0xFFFAFAFA),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Stitching dots
        val stitchCount = 10
        for (i in 0 until stitchCount) {
            val t = i.toFloat() / (stitchCount - 1)
            val x = center.x - radius * 0.75f + (radius * 1.5f * t)
            val offset = sin(t * PI).toFloat() * radius * 0.65f
            val y = center.y - offset
            
            // Upper stitch
            drawCircle(
                color = Color(0xFFFAFAFA),
                radius = 1.8.dp.toPx(),
                center = Offset(x, y - 5.dp.toPx())
            )
            
            // Lower stitch
            drawCircle(
                color = Color(0xFFFAFAFA),
                radius = 1.8.dp.toPx(),
                center = Offset(x, y + 5.dp.toPx())
            )
        }
        
        // Secondary seam (bottom)
        val seamPath2 = Path().apply {
            val seamRadius = radius * 0.75f
            moveTo(center.x - seamRadius, center.y)
            cubicTo(
                center.x - seamRadius * 0.5f, center.y + seamRadius * 0.85f,
                center.x + seamRadius * 0.5f, center.y + seamRadius * 0.85f,
                center.x + seamRadius, center.y
            )
        }
        
        drawPath(
            path = seamPath2,
            color = Color(0xFF4E342E).copy(alpha = 0.5f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        
        drawPath(
            path = seamPath2,
            color = Color(0xFFFAFAFA).copy(alpha = 0.6f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    
    // Main highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f),
            radius = radius * 0.55f
        ),
        radius = radius * 0.55f,
        center = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f)
    )
    
    // Gold shimmer highlight (follows rotation)
    val shimmerAngle = shimmerOffset * 180f
    val shimmerX = center.x + cos(shimmerAngle * PI.toFloat() / 180f) * radius * 0.3f
    val shimmerY = center.y + sin(shimmerAngle * PI.toFloat() / 180f) * radius * 0.3f
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.goldLight.copy(alpha = 0.3f),
                colors.goldPrimary.copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = Offset(shimmerX, shimmerY),
            radius = radius * 0.4f
        ),
        radius = radius * 0.4f,
        center = Offset(shimmerX, shimmerY)
    )
    
    // Rim light
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                colors.goldLight.copy(alpha = 0.15f),
                Color.Transparent,
                colors.goldLight.copy(alpha = 0.1f),
                Color.Transparent
            ),
            center = center
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * Letter-by-letter text reveal component with golden shimmer
 */
@Composable
private fun LetterByLetterText(
    text: String,
    shimmerOffset: Float,
    colors: BouncerColors
) {
    var visibleLetters by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        for (i in text.indices) {
            delay(80)
            visibleLetters = i + 1
        }
    }
    
    Row(
        horizontalArrangement = Arrangement.Center
    ) {
        text.forEachIndexed { index, char ->
            val isVisible = index < visibleLetters
            
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(150)) + scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            ) {
                Box {
                    // Base letter with gradient
                    androidx.compose.material3.Text(
                        text = char.toString(),
                        style = TextStyle(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            fontFamily = InterFontFamily,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    TextPrimary,
                                    colors.goldLight,
                                    colors.goldPrimary,
                                    colors.etchedGold,
                                    colors.goldDark
                                )
                            )
                        )
                    )
                    
                    // Shimmer overlay per letter
                    val letterShimmerOffset = shimmerOffset - index * 0.15f
                    if (letterShimmerOffset in 0f..1f) {
                        androidx.compose.material3.Text(
                            text = char.toString(),
                            style = TextStyle(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp,
                                fontFamily = InterFontFamily
                            ),
                            color = Color.Transparent,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            colors.goldLight.copy(alpha = 0.7f),
                                            Color.White.copy(alpha = 0.9f),
                                            colors.goldLight.copy(alpha = 0.7f),
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
