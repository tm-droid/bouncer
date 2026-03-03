package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium animated splash screen for Bouncer app
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    
    var showContent by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showLoader by remember { mutableStateOf(false) }
    var animationComplete by remember { mutableStateOf(false) }
    
    // Trigger animations in sequence
    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
        delay(400)
        showLogo = true
        delay(600)
        showTagline = true
        delay(400)
        showLoader = true
        delay(1800)
        animationComplete = true
        delay(400)
        onSplashComplete()
    }
    
    // Infinite animations
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val orbRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBase),
        contentAlignment = Alignment.Center
    ) {
        // Animated background with stadium gradient and gold bloom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // Stadium-style radial gradient: lighter center to dark edges
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1D22),
                        Color(0xFF141619),
                        colors.backgroundBase
                    ),
                    center = Offset(centerX, centerY),
                    radius = size.width * 0.9f
                )
            )
            
            // Gold bloom at the top
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.goldPrimary.copy(alpha = 0.10f * glowAlpha),
                        colors.goldDark.copy(alpha = 0.04f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(centerX, size.height * 0.15f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(centerX, size.height * 0.15f)
            )
            
            // Large outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.goldDark.copy(alpha = 0.08f * glowAlpha),
                        colors.goldDark.copy(alpha = 0.03f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(centerX, centerY)
            )
            
            // Inner glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.goldPrimary.copy(alpha = 0.12f * glowAlpha),
                        colors.goldDark.copy(alpha = 0.05f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = size.width * 0.4f
                ),
                radius = size.width * 0.4f,
                center = Offset(centerX, centerY)
            )
            
            // Orbiting particles
            for (i in 0..5) {
                val angle = Math.toRadians((orbRotation + i * 60).toDouble())
                val radius = size.width * 0.35f
                val x = centerX + (cos(angle) * radius).toFloat()
                val y = centerY + (sin(angle) * radius).toFloat()
                
                drawCircle(
                    color = colors.goldLight.copy(alpha = 0.4f + (i * 0.1f)),
                    radius = 4.dp.toPx() + i * 1.5f,
                    center = Offset(x, y)
                )
                
                // Particle trail
                for (j in 1..3) {
                    val trailAngle = Math.toRadians((orbRotation + i * 60 - j * 15).toDouble())
                    val trailX = centerX + (cos(trailAngle) * radius).toFloat()
                    val trailY = centerY + (sin(trailAngle) * radius).toFloat()
                    
                    drawCircle(
                        color = colors.goldLight.copy(alpha = 0.15f / j),
                        radius = (4.dp.toPx() + i * 1.5f) * (1f - j * 0.2f),
                        center = Offset(trailX, trailY)
                    )
                }
            }
            
            // Subtle grid lines
            val gridAlpha = 0.03f
            for (i in 0..20) {
                val y = (size.height / 20) * i
                drawLine(
                    color = colors.goldDark.copy(alpha = gridAlpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            for (i in 0..20) {
                val x = (size.width / 20) * i
                drawLine(
                    color = colors.goldDark.copy(alpha = gridAlpha),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
        }
        
        // Main content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Cricket ball logo
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(tween(800)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(
                        durationMillis = 800,
                        easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .scale(pulseScale),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing gold glow / shimmer around logo
                    Box(
                        modifier = Modifier
                            .size(144.dp)
                            .drawBehind {
                                // Outer pulsing gold glow
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.goldPrimary.copy(alpha = glowAlpha * 0.35f),
                                            colors.goldDark.copy(alpha = glowAlpha * 0.15f),
                                            colors.goldDark.copy(alpha = glowAlpha * 0.05f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = size.width / 2 + 40.dp.toPx()
                                )
                                // Inner shimmer ring
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.goldLight.copy(alpha = glowAlpha * 0.2f),
                                            colors.goldPrimary.copy(alpha = glowAlpha * 0.1f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = size.width / 2 + 16.dp.toPx()
                                )
                            }
                    )
                    
                    // Cricket ball
                    CricketBallLogo(
                        modifier = Modifier.size(96.dp),
                        shimmerOffset = shimmerOffset
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name with etched gold gradient + shimmer
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(tween(800)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(
                        durationMillis = 800,
                        easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
                    )
                )
            ) {
                Box {
                    // Etched gold gradient text
                    Text(
                        text = "BOUNCER",
                        style = TextStyle(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 8.sp,
                            fontFamily = InterFontFamily,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White,
                                    colors.goldLight,
                                    colors.goldPrimary,
                                    colors.etchedGold,
                                    colors.goldDark
                                )
                            )
                        )
                    )
                    
                    // Shimmer overlay
                    Text(
                        text = "BOUNCER",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 8.sp,
                        fontFamily = InterFontFamily,
                        color = Color.Transparent,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.goldLight.copy(alpha = 0.5f),
                                        colors.goldPrimary.copy(alpha = 0.7f),
                                        colors.goldLight.copy(alpha = 0.5f),
                                        Color.Transparent
                                    ),
                                    startX = size.width * shimmerOffset - size.width * 0.5f,
                                    endX = size.width * shimmerOffset + size.width * 0.5f
                                ),
                                blendMode = BlendMode.SrcIn
                            )
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tagline with subtle gold accent
            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(500)) + slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(500)
                )
            ) {
                Text(
                    text = "Live Cricket. Elevated.",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.sp,
                        fontFamily = InterFontFamily,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.textSecondary,
                                colors.goldPrimary.copy(alpha = 0.6f),
                                colors.textSecondary
                            )
                        )
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Premium loading indicator
            AnimatedVisibility(
                visible = showLoader && !animationComplete,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                PremiumLoadingIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // Bottom branding + version
        AnimatedVisibility(
            visible = showTagline,
            enter = fadeIn(tween(800)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Premium Cricket Experience",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    fontFamily = InterFontFamily,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
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

@Composable
private fun CricketBallLogo(
    modifier: Modifier = Modifier,
    shimmerOffset: Float = 0f
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        
        // Ball shadow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = Offset(center.x + 5.dp.toPx(), center.y + 5.dp.toPx())
            ),
            radius = radius,
            center = Offset(center.x + 5.dp.toPx(), center.y + 5.dp.toPx())
        )
        
        // Ball base - rich red
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE53935),
                    Color(0xFFC62828),
                    Color(0xFFB71C1C)
                ),
                center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f)
            ),
            radius = radius,
            center = center
        )
        
        // Highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f),
                radius = radius * 0.6f
            ),
            radius = radius * 0.6f,
            center = Offset(center.x - radius * 0.4f, center.y - radius * 0.4f)
        )
        
        // Seam - main curve
        val seamPath = Path().apply {
            val seamRadius = radius * 0.75f
            moveTo(center.x - seamRadius, center.y)
            
            // Create a curved seam
            cubicTo(
                center.x - seamRadius * 0.5f, center.y - seamRadius * 0.8f,
                center.x + seamRadius * 0.5f, center.y - seamRadius * 0.8f,
                center.x + seamRadius, center.y
            )
        }
        
        // Seam shadow
        drawPath(
            path = seamPath,
            color = Color(0xFF5D4037).copy(alpha = 0.6f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Seam main
        drawPath(
            path = seamPath,
            color = Color(0xFFF5F5F5),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Stitching dots along the seam
        val stitchCount = 8
        for (i in 0 until stitchCount) {
            val t = i.toFloat() / (stitchCount - 1)
            val x = center.x - radius * 0.75f + (radius * 1.5f * t)
            val offset = sin(t * Math.PI) * radius * 0.6f
            val y = center.y - offset.toFloat()
            
            // Upper stitch
            drawCircle(
                color = Color(0xFFF5F5F5),
                radius = 2.dp.toPx(),
                center = Offset(x, y - 6.dp.toPx())
            )
            
            // Lower stitch
            drawCircle(
                color = Color(0xFFF5F5F5),
                radius = 2.dp.toPx(),
                center = Offset(x, y + 6.dp.toPx())
            )
        }
        
        // Shine overlay with shimmer
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f * (1f + shimmerOffset)),
                    Color.Transparent
                ),
                start = Offset(center.x + radius * shimmerOffset, center.y - radius),
                end = Offset(center.x - radius * shimmerOffset, center.y + radius)
            ),
            radius = radius,
            center = center
        )
    }
}

@Composable
private fun PremiumLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "loader")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        
        // Background ring
        drawCircle(
            color = colors.goldDark.copy(alpha = 0.2f),
            radius = radius,
            style = Stroke(width = strokeWidth)
        )
        
        // Animated arc
        rotate(rotation) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.goldLight.copy(alpha = 0.3f),
                        colors.goldPrimary,
                        colors.goldDark,
                        colors.goldPrimary,
                        colors.goldLight.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
            )
        }
    }
}
