package com.tanush.bouncer.ui.animations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// Color Constants
private val GoldPrimary = Color(0xFFD4AF37)
private val GoldBright = Color(0xFFFFD700)
private val BackgroundBase = Color(0xFF0F1115)
private val StatusLive = Color(0xFFFF3B30)

// a) CelebrationBurst - confetti-like particle burst
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var alpha: Float,
    var color: Color,
    var size: Float
)

@Composable
fun CelebrationBurst(
    modifier: Modifier = Modifier,
    centerX: Dp = 0.dp,
    centerY: Dp = 0.dp,
    particleCount: Int = 50,
    trigger: Boolean = false
) {
    val particles = remember { mutableStateListOf<Particle>() }
    var isAnimating by remember { mutableStateOf(false) }
    
    LaunchedEffect(trigger) {
        if (trigger && !isAnimating) {
            isAnimating = true
            particles.clear()
            
            repeat(particleCount) {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val speed = Random.nextFloat() * 300f + 200f
                particles.add(
                    Particle(
                        x = 0f,
                        y = 0f,
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed - Random.nextFloat() * 100f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 720f - 360f,
                        alpha = 1f,
                        color = if (Random.nextBoolean()) GoldPrimary else Color.White,
                        size = Random.nextFloat() * 8f + 4f
                    )
                )
            }
            
            val startTime = System.currentTimeMillis()
            val duration = 2000f
            
            while (System.currentTimeMillis() - startTime < duration) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                particles.forEachIndexed { _, particle ->
                    particle.x += particle.vx * 0.016f
                    particle.y += particle.vy * 0.016f
                    particle.vy += 400f * 0.016f // gravity
                    particle.rotation += particle.rotationSpeed * 0.016f
                    particle.alpha = 1f - (elapsed / (duration / 1000f))
                }
                delay(16)
            }
            
            particles.clear()
            isAnimating = false
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = centerX.toPx()
        val cy = centerY.toPx()
        
        particles.forEach { particle ->
            rotate(particle.rotation, pivot = Offset(cx + particle.x, cy + particle.y)) {
                drawRect(
                    color = particle.color,
                    topLeft = Offset(
                        cx + particle.x - particle.size / 2,
                        cy + particle.y - particle.size / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(particle.size, particle.size),
                    alpha = particle.alpha.coerceIn(0f, 1f)
                )
            }
        }
    }
}

// b) SuccessCheckmark - animated checkmark that draws itself
@Composable
fun SuccessCheckmark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    trigger: Boolean = false
) {
    val pathProgress by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "checkmark_path"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        ),
        label = "checkmark_scale"
    )
    
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val strokeWidth = 6.dp.toPx()
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.7f)
            lineTo(this@Canvas.size.width * 0.8f, this@Canvas.size.height * 0.3f)
        }
        
        val outPath = Path()
        val pathMeasure = PathMeasure().apply { setPath(path, false) }
        pathMeasure.getSegment(0f, pathMeasure.length * pathProgress, outPath, true)
        
        drawPath(
            path = outPath,
            color = GoldPrimary,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// c) ScorePop - number that pops in with overshoot spring
@Composable
fun ScorePop(
    score: Int,
    modifier: Modifier = Modifier,
    isWicket: Boolean = false,
    trigger: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 300f
        ),
        label = "score_scale"
    )
    
    val shake by animateFloatAsState(
        targetValue = if (trigger && isWicket) 1f else 0f,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "score_shake"
    )
    
    val color by animateColorAsState(
        targetValue = if (trigger) GoldPrimary else GoldBright,
        animationSpec = tween<Color>(400),
        label = "score_color"
    )
    
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = if (isWicket) shake * 10f else 0f
        },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score.toString(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// d) PulseRing - expanding ring animation
@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    color: Color = GoldPrimary,
    ringCount: Int = 3,
    isAnimating: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    
    val rings = (0 until ringCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing, delayMillis = index * 600),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_$index"
        )
    }
    
    if (isAnimating) {
        Canvas(modifier = modifier.size(size)) {
            rings.forEach { ring ->
                val progress = ring.value
                val radius = (this.size.minDimension / 2) * progress
                val alpha = 1f - progress
                
                drawCircle(
                    color = color,
                    radius = radius,
                    center = this.center,
                    alpha = alpha,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

// e) BounceInComposable - wrapper that makes any content bounce in
@Composable
fun BounceInComposable(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var trigger by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        trigger = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 300f
        ),
        label = "bounce_in"
    )
    
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}

// f) SlideUpReveal - content slides up and fades in elegantly
@Composable
fun SlideUpReveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var trigger by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        trigger = true
    }
    
    val offsetY by animateDpAsState(
        targetValue = if (trigger) 0.dp else 60.dp,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "slide_offset"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "slide_alpha"
    )
    
    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetY.toPx()
            this.alpha = this@graphicsLayer.alpha * alpha
        }
    ) {
        content()
    }
}

// g) WicketAnimation - dramatic wicket celebration
@Composable
fun WicketAnimation(
    modifier: Modifier = Modifier,
    trigger: Boolean = false,
    onComplete: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var showBurst by remember { mutableStateOf(false) }
    
    val shake by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = repeatable(
            iterations = 5,
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wicket_shake"
    )
    
    val flash by animateFloatAsState(
        targetValue = if (trigger) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "wicket_flash"
    )
    
    LaunchedEffect(trigger) {
        if (trigger) {
            showBurst = true
            delay(2000)
            showBurst = false
            onComplete()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = shake * 15f * sin(shake * PI.toFloat() * 10f)
            }
    ) {
        if (trigger) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.White, alpha = flash * 0.3f)
            }
        }
        
        if (showBurst) {
            CelebrationBurst(
                modifier = Modifier.fillMaxSize(),
                centerX = 180.dp,
                centerY = 300.dp,
                particleCount = 80,
                trigger = showBurst
            )
        }
    }
}

// h) SixAnimation - celebrating a six
@Composable
fun SixAnimation(
    modifier: Modifier = Modifier,
    trigger: Boolean = false
) {
    var showBurst by remember { mutableStateOf(false) }
    
    val ballProgress by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "ball_trajectory"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 200f
        ),
        label = "six_scale"
    )
    
    LaunchedEffect(trigger) {
        if (trigger) {
            delay(600)
            showBurst = true
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = size.width * 0.1f
            val endX = size.width * 0.9f
            val startY = size.height * 0.8f
            val endY = size.height * 0.2f
            
            val currentX = startX + (endX - startX) * ballProgress
            val arc = -300f * ballProgress * (1f - ballProgress)
            val currentY = startY + (endY - startY) * ballProgress + arc
            
            if (ballProgress > 0f && ballProgress < 1f) {
                drawCircle(
                    color = StatusLive,
                    radius = 12.dp.toPx(),
                    center = Offset(currentX, currentY)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Text(
                text = "6",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )
        }
        
        if (showBurst) {
            CelebrationBurst(
                modifier = Modifier.fillMaxSize(),
                centerX = 180.dp,
                centerY = 200.dp,
                particleCount = 60,
                trigger = showBurst
            )
        }
    }
}

// i) CountUpAnimation - smoothly counts from 0 to target number
@Composable
fun CountUpAnimation(
    targetValue: Int,
    durationMillis: Int = 1000
): Int {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "count_up"
    )
    return animatedValue
}

// j) ShakeModifier - Modifier extension for shake effect
fun Modifier.shake(trigger: Boolean, intensity: Float = 10f, duration: Int = 400): Modifier {
    return this.graphicsLayer {
        if (trigger) {
            val time = System.currentTimeMillis() % duration
            val progress = time.toFloat() / duration
            translationX = intensity * sin(progress * PI.toFloat() * 8f) * (1f - progress)
        }
    }
}
