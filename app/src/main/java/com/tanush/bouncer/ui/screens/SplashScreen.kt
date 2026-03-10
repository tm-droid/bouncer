package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ══════════════════════════════════════════════════════════════════════════════
// Elite Splash Screen — Bouncer Launch Experience
// ══════════════════════════════════════════════════════════════════════════════

private val DarkBackground = Color(0xFF0A0C10)
private val GoldPrimary = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFE8C84A)
private val TextWhite = Color(0xFFF5F5F5)
private val TextSubtitle = Color(0xFFAAAAAA)

/**
 * Premium splash screen with animated logo and loading states.
 * 
 * @param onSplashComplete Callback invoked after 2.5s animation completes.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogo by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showPulse by remember { mutableStateOf(false) }

    val logoScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Stage 1: Logo appears (0-600ms)
        showLogo = true
        logoScale.animateTo(
            targetValue = 1.1f,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 200f
            )
        )
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )

        // Stage 2: Title + pulse (600-1200ms)
        delay(200)
        showTitle = true
        showPulse = true

        // Stage 3: Subtitle (1200-1500ms)
        delay(600)
        showSubtitle = true

        // Stage 4: Hold and complete (1500-2500ms)
        delay(1000)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldPrimary.copy(alpha = 0.08f),
                        DarkBackground,
                        DarkBackground
                    ),
                    center = Offset(0.5f, 0.4f),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo + pulse ring
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Pulse ring animation
                if (showPulse) {
                    PulseRing()
                }

                // Cricket ball logo
                if (showLogo) {
                    CricketBallIcon(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                scaleX = logoScale.value
                                scaleY = logoScale.value
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOUNCER title with letter-by-letter animation
            if (showTitle) {
                AnimatedTitle()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                        )
            ) {
                Text(
                    text = "Live Cricket Intelligence",
                    color = TextSubtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.5.sp
                )
            }
        }

        // Loading indicator at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            LoadingDots()
        }
    }
}

/**
 * Cricket ball icon with seam lines.
 */
@Composable
private fun CricketBallIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Main ball circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GoldLight, GoldPrimary),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Seam lines (cricket ball stitching)
        val seamOffset = radius * 0.3f
        val seamLength = radius * 1.4f

        // Left seam curve
        drawArc(
            color = Color.White.copy(alpha = 0.4f),
            startAngle = -120f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(center.x - seamOffset - seamLength / 2, center.y - seamLength / 2),
            size = androidx.compose.ui.geometry.Size(seamLength, seamLength),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Right seam curve
        drawArc(
            color = Color.White.copy(alpha = 0.4f),
            startAngle = 60f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(center.x + seamOffset - seamLength / 2, center.y - seamLength / 2),
            size = androidx.compose.ui.geometry.Size(seamLength, seamLength),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Inner highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = radius * 0.6f,
            center = Offset(center.x - radius * 0.15f, center.y - radius * 0.15f)
        )
    }
}

/**
 * Pulsing gold ring that emanates from the logo.
 */
@Composable
private fun PulseRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Canvas(
        modifier = Modifier
            .size(150.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        drawCircle(
            color = GoldPrimary.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * BOUNCER text with staggered letter animation.
 */
@Composable
private fun AnimatedTitle() {
    val letters = "BOUNCER".toList()
    var visibleLetters by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        letters.indices.forEach { index ->
            delay(50L * index)
            visibleLetters = index + 1
        }
    }

    Row {
        letters.forEachIndexed { index, letter ->
            AnimatedVisibility(
                visible = index < visibleLetters,
                enter = fadeIn(animationSpec = tween(200)) +
                        slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        )
            ) {
                Text(
                    text = letter.toString(),
                    color = TextWhite,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * Three-dot loading indicator with sequential pulse.
 */
@Composable
private fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingDots")

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 200,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha$index"
            )

            Canvas(
                modifier = Modifier.size(8.dp)
            ) {
                drawCircle(
                    color = GoldPrimary.copy(alpha = alpha),
                    radius = size.minDimension / 2
                )
            }

            if (index < 2) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Preview
// ══════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true)
@Composable
private fun PreviewSplashScreen() {
    SplashScreen(onSplashComplete = {})
}
