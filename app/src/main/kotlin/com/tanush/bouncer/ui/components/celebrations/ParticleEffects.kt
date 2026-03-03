package com.tanush.bouncer.ui.components.celebrations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random
import io.sentry.Sentry

/**
 * Data class representing a particle with physics properties
 */
data class Particle(
    val id: Int,
    val position: Offset,
    val velocity: Offset,
    val acceleration: Offset = Offset.Zero,
    val size: Float,
    val color: Color,
    val alpha: Float = 1f,
    val rotation: Float = 0f,
    val rotationVelocity: Float = 0f,
    val lifetime: Float = 1f,
    val age: Float = 0f,
    val trail: List<Offset> = emptyList()
)

/**
 * Configuration for particle emitters
 */
data class ParticleEmitterConfig(
    val particleCount: Int = 50,
    val emissionRate: Float = 10f, // particles per second
    val particleLifetime: Float = 2f, // seconds
    val initialSpeed: ClosedFloatingPointRange<Float> = 5f..15f,
    val initialSize: ClosedFloatingPointRange<Float> = 4f..10f,
    val gravity: Float = 0.2f,
    val drag: Float = 0.01f,
    val fadeOut: Boolean = true,
    val shrink: Boolean = true,
    val colors: List<Color> = listOf(GoldPrimary, GoldPrimary),
    val spread: Float = 360f, // degrees
    val direction: Float = -90f // degrees, -90 = upward
)

/**
 * Base particle emitter composable
 */
@Composable
fun ParticleEmitter(
    modifier: Modifier = Modifier,
    config: ParticleEmitterConfig = ParticleEmitterConfig(),
    emitPoint: Offset? = null,
    isActive: Boolean = true,
    continuous: Boolean = false,
    onParticleRender: DrawScope.(Particle) -> Unit = { particle ->
        drawCircle(
            color = particle.color.copy(alpha = particle.alpha),
            radius = particle.size,
            center = particle.position
        )
    }
) {
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    var animationTime by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            if (!continuous) {
                // Burst mode - emit all particles at once
                particles = (0 until config.particleCount).map { i ->
                    createParticle(i, config, Offset.Zero)
                }
            }
            
            // Animation loop
            val startTime = System.currentTimeMillis()
            while (if (continuous) isActive else animationTime < config.particleLifetime) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                animationTime = elapsed
                
                if (continuous && elapsed > 0) {
                    // Emit new particles based on emission rate
                    val particlesToEmit = (config.emissionRate * 0.016f).toInt() // ~60fps
                    if (particlesToEmit > 0 && particles.size < config.particleCount * 2) {
                        val newParticles = (0 until particlesToEmit).map { i ->
                            createParticle(particles.size + i, config, Offset.Zero)
                        }
                        particles = particles + newParticles
                    }
                }
                
                // Update particles
                particles = particles.mapNotNull { particle ->
                    updateParticle(particle, config, 0.016f)
                }
                
                delay(16L)
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = emitPoint ?: Offset(size.width / 2, size.height / 2)
        
        particles.forEach { particle ->
            val screenPosition = Offset(
                center.x + particle.position.x,
                center.y + particle.position.y
            )
            onParticleRender(particle.copy(position = screenPosition))
        }
    }
}

private fun createParticle(id: Int, config: ParticleEmitterConfig, origin: Offset): Particle {
    try {
        val angle = config.direction + (Random.nextFloat() - 0.5f) * config.spread
        val speed = Random.nextFloat() * (config.initialSpeed.endInclusive - config.initialSpeed.start) + config.initialSpeed.start
        val radians = Math.toRadians(angle.toDouble())
    
        return Particle(
            id = id,
            position = origin,
            velocity = Offset(
                (cos(radians) * speed).toFloat(),
                (sin(radians) * speed).toFloat()
            ),
            size = Random.nextFloat() * (config.initialSize.endInclusive - config.initialSize.start) + config.initialSize.start,
            color = config.colors[Random.nextInt(config.colors.size)],
            rotation = Random.nextFloat() * 360f,
            rotationVelocity = Random.nextFloat() * 10f - 5f,
            lifetime = config.particleLifetime * (0.8f + Random.nextFloat() * 0.4f)
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

private fun updateParticle(particle: Particle, config: ParticleEmitterConfig, deltaTime: Float): Particle? {
    try {
        val newAge = particle.age + deltaTime
        if (newAge > particle.lifetime) return null
    
        val progress = newAge / particle.lifetime
        val drag = 1f - config.drag
    
        val newVelocity = Offset(
            particle.velocity.x * drag,
            particle.velocity.y * drag + config.gravity
        )
    
        val newPosition = Offset(
            particle.position.x + newVelocity.x,
            particle.position.y + newVelocity.y
        )
    
        val newAlpha = if (config.fadeOut) 1f - progress else particle.alpha
        val newSize = if (config.shrink) particle.size * (1f - progress * 0.5f) else particle.size
        val newRotation = particle.rotation + particle.rotationVelocity
    
        return particle.copy(
            position = newPosition,
            velocity = newVelocity,
            age = newAge,
            alpha = newAlpha.coerceIn(0f, 1f),
            size = newSize.coerceAtLeast(0.5f),
            rotation = newRotation
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Spark particles for milestone moments
 */
@Composable
fun SparkParticles(
    modifier: Modifier = Modifier,
    sparkCount: Int = 30,
    colors: List<Color> = listOf(
        Color(0xFFFFD700),
        Color(0xFFFFA500),
        GoldLight,
        Color.White
    ),
    isActive: Boolean = true,
    emitPoint: Offset? = null,
    onComplete: () -> Unit = {}
) {
    var sparks by remember { mutableStateOf<List<SparkState>>(emptyList()) }
    var animationProgress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            sparks = (0 until sparkCount).map { i ->
                val angle = Random.nextFloat() * 360f
                val speed = Random.nextFloat() * 12f + 6f
                val radians = Math.toRadians(angle.toDouble())
                
                SparkState(
                    id = i,
                    velocity = Offset(
                        (cos(radians) * speed).toFloat(),
                        (sin(radians) * speed).toFloat()
                    ),
                    length = Random.nextFloat() * 15f + 8f,
                    color = colors[Random.nextInt(colors.size)],
                    brightness = Random.nextFloat() * 0.5f + 0.5f
                )
            }
            
            val startTime = System.currentTimeMillis()
            val duration = 1500L
            while (System.currentTimeMillis() - startTime < duration) {
                animationProgress = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                delay(16L)
            }
            animationProgress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = emitPoint ?: Offset(size.width / 2, size.height / 2)
        
        sparks.forEach { spark ->
            val time = animationProgress * 100f
            val gravity = 0.2f * time * time
            
            val x = center.x + spark.velocity.x * time
            val y = center.y + spark.velocity.y * time + gravity
            val alpha = (1f - animationProgress).coerceIn(0f, 1f) * spark.brightness
            
            // Draw spark line
            val angle = atan2(spark.velocity.y, spark.velocity.x)
            val tailX = x - cos(angle) * spark.length * (1f - animationProgress * 0.5f)
            val tailY = y - sin(angle) * spark.length * (1f - animationProgress * 0.5f)
            
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        spark.color.copy(alpha = alpha),
                        spark.color.copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    ),
                    start = Offset(x, y),
                    end = Offset(tailX, tailY)
                ),
                start = Offset(x, y),
                end = Offset(tailX, tailY),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            
            // Draw spark head glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha),
                        spark.color.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = 6f
                ),
                radius = 6f,
                center = Offset(x, y)
            )
        }
    }
}

private data class SparkState(
    val id: Int,
    val velocity: Offset,
    val length: Float,
    val color: Color,
    val brightness: Float
)

/**
 * Firework particle with trail effects
 */
@Composable
fun FireworkParticle(
    modifier: Modifier = Modifier,
    launchPoint: Offset? = null,
    explosionPoint: Offset? = null,
    color: Color = Color(0xFFFF6B6B),
    particleCount: Int = 40,
    trailLength: Int = 5,
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    var phase by remember { mutableStateOf(FireworkPhase.LAUNCH) }
    var launchProgress by remember { mutableFloatStateOf(0f) }
    var explosionProgress by remember { mutableFloatStateOf(0f) }
    var particles by remember { mutableStateOf<List<FireworkParticleState>>(emptyList()) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            // Launch phase
            phase = FireworkPhase.LAUNCH
            val launchDuration = 600L
            val launchStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - launchStart < launchDuration) {
                launchProgress = ((System.currentTimeMillis() - launchStart).toFloat() / launchDuration).coerceIn(0f, 1f)
                delay(16L)
            }
            launchProgress = 1f
            
            // Explosion phase
            phase = FireworkPhase.EXPLODE
            particles = (0 until particleCount).map { i ->
                val angle = (i.toFloat() / particleCount) * 360f
                val speed = Random.nextFloat() * 8f + 4f
                val radians = Math.toRadians(angle.toDouble())
                
                FireworkParticleState(
                    id = i,
                    velocity = Offset(
                        (cos(radians) * speed).toFloat(),
                        (sin(radians) * speed).toFloat()
                    ),
                    trail = mutableListOf()
                )
            }
            
            val explosionDuration = 1500L
            val explosionStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - explosionStart < explosionDuration) {
                explosionProgress = ((System.currentTimeMillis() - explosionStart).toFloat() / explosionDuration).coerceIn(0f, 1f)
                delay(16L)
            }
            explosionProgress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val startPoint = launchPoint ?: Offset(size.width / 2, size.height)
        val targetPoint = explosionPoint ?: Offset(size.width / 2, size.height * 0.3f)
        
        when (phase) {
            FireworkPhase.LAUNCH -> {
                // Draw launching rocket
                val currentY = startPoint.y + (targetPoint.y - startPoint.y) * launchProgress
                val currentX = startPoint.x + (targetPoint.x - startPoint.x) * launchProgress
                
                // Trail
                val trailStartY = currentY + 30f
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.8f),
                            color.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startY = currentY,
                        endY = trailStartY
                    ),
                    start = Offset(currentX, currentY),
                    end = Offset(currentX, trailStartY),
                    strokeWidth = 3f
                )
                
                // Rocket head
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(currentX, currentY)
                )
            }
            FireworkPhase.EXPLODE -> {
                val time = explosionProgress * 80f
                val gravity = 0.15f * time * time
                val alpha = (1f - explosionProgress).coerceIn(0f, 1f)
                
                particles.forEach { particle ->
                    val x = targetPoint.x + particle.velocity.x * time
                    val y = targetPoint.y + particle.velocity.y * time + gravity
                    
                    // Draw particle glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = alpha),
                                color.copy(alpha = alpha * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(x, y),
                            radius = 8f
                        ),
                        radius = 8f,
                        center = Offset(x, y)
                    )
                    
                    // Core
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 2f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

private enum class FireworkPhase {
    LAUNCH,
    EXPLODE
}

private data class FireworkParticleState(
    val id: Int,
    val velocity: Offset,
    val trail: MutableList<Offset>
)

/**
 * Burst particle effect for explosion-like visuals
 */
@Composable
fun BurstParticle(
    modifier: Modifier = Modifier,
    burstPoint: Offset? = null,
    particleCount: Int = 50,
    colors: List<Color> = listOf(
        ChartPurple,
        SixerPurple,
        StatusInfo,
        Color.White
    ),
    maxRadius: Float = 200f,
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    var particles by remember { mutableStateOf<List<BurstState>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            particles = (0 until particleCount).map { i ->
                val angle = Random.nextFloat() * 360f
                val speed = Random.nextFloat() * 0.8f + 0.2f
                
                BurstState(
                    id = i,
                    angle = angle,
                    speed = speed,
                    size = Random.nextFloat() * 8f + 4f,
                    color = colors[Random.nextInt(colors.size)],
                    rotationSpeed = Random.nextFloat() * 10f - 5f
                )
            }
            
            val duration = 1200L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < duration) {
                progress = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                delay(16L)
            }
            progress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = burstPoint ?: Offset(size.width / 2, size.height / 2)
        
        // Shockwave ring
        if (progress < 0.5f) {
            val ringProgress = progress * 2f
            val ringRadius = maxRadius * ringProgress
            val ringAlpha = 1f - ringProgress
            
            drawCircle(
                color = Color.White.copy(alpha = ringAlpha * 0.3f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 3f * (1f - ringProgress))
            )
        }
        
        particles.forEach { particle ->
            val easedProgress = FastOutSlowInEasing.transform(progress)
            val distance = maxRadius * easedProgress * particle.speed
            val radians = Math.toRadians(particle.angle.toDouble())
            
            val x = center.x + (cos(radians) * distance).toFloat()
            val y = center.y + (sin(radians) * distance).toFloat()
            val alpha = (1f - progress).coerceIn(0f, 1f)
            val scale = 1f - progress * 0.5f
            val rotation = particle.rotationSpeed * progress * 360f
            
            rotate(rotation, Offset(x, y)) {
                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = particle.size * scale,
                    center = Offset(x, y)
                )
            }
        }
    }
}

private data class BurstState(
    val id: Int,
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float
)

/**
 * Glitter particles - small shimmering particles
 */
@Composable
fun GlitterParticle(
    modifier: Modifier = Modifier,
    particleCount: Int = 100,
    colors: List<Color> = listOf(
        GoldLight,
        GoldPrimary,
        Color.White,
        Color.White.copy(alpha = 0.8f)
    ),
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glitter")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glitterTime"
    )
    
    val particles = remember {
        (0 until particleCount).map { i ->
            GlitterState(
                id = i,
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                color = colors[Random.nextInt(colors.size)],
                phase = Random.nextFloat() * 2 * PI.toFloat(),
                twinkleSpeed = Random.nextFloat() * 2f + 1f
            )
        }
    }
    
    if (!isActive) return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val twinkle = (sin(time * particle.twinkleSpeed + particle.phase) + 1f) / 2f
            val alpha = twinkle * 0.8f + 0.2f
            
            val x = particle.x * size.width
            val y = particle.y * size.height
            
            // Draw glitter with 4-point star shape
            val starSize = particle.size * (0.8f + twinkle * 0.4f)
            
            // Horizontal line
            drawLine(
                color = particle.color.copy(alpha = alpha),
                start = Offset(x - starSize, y),
                end = Offset(x + starSize, y),
                strokeWidth = 1f
            )
            
            // Vertical line
            drawLine(
                color = particle.color.copy(alpha = alpha),
                start = Offset(x, y - starSize),
                end = Offset(x, y + starSize),
                strokeWidth = 1f
            )
            
            // Center glow
            drawCircle(
                color = particle.color.copy(alpha = alpha * 0.5f),
                radius = starSize * 0.5f,
                center = Offset(x, y)
            )
        }
    }
}

private data class GlitterState(
    val id: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val phase: Float,
    val twinkleSpeed: Float
)

/**
 * Ball trail effect for animated cricket ball paths
 */
@Composable
fun BallTrailEffect(
    modifier: Modifier = Modifier,
    startPoint: Offset,
    endPoint: Offset,
    trailColor: Color = StatusInfo,
    ballColor: Color = StatusLive,
    isActive: Boolean = true,
    duration: Long = 800L,
    onComplete: () -> Unit = {}
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var trailPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            val startTime = System.currentTimeMillis()
            val maxTrailLength = 15
            
            while (System.currentTimeMillis() - startTime < duration) {
                progress = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                
                // Calculate current position with arc
                val arcHeight = 50f
                val currentX = startPoint.x + (endPoint.x - startPoint.x) * progress
                val arcOffset = sin(progress * PI.toFloat()) * arcHeight
                val currentY = startPoint.y + (endPoint.y - startPoint.y) * progress - arcOffset
                
                trailPoints = (trailPoints + Offset(currentX, currentY)).takeLast(maxTrailLength)
                
                delay(16L)
            }
            progress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw trail
        if (trailPoints.size >= 2) {
            trailPoints.forEachIndexed { index, point ->
                val alpha = (index.toFloat() / trailPoints.size) * 0.6f
                val size = (index.toFloat() / trailPoints.size) * 8f + 2f
                
                drawCircle(
                    color = trailColor.copy(alpha = alpha),
                    radius = size,
                    center = point
                )
            }
        }
        
        // Draw ball at current position
        if (trailPoints.isNotEmpty()) {
            val currentPos = trailPoints.last()
            
            // Ball shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ballColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = currentPos,
                    radius = 20f
                ),
                radius = 20f,
                center = currentPos
            )
            
            // Ball
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ballColor,
                        ballColor.copy(alpha = 0.8f)
                    ),
                    center = Offset(currentPos.x - 3f, currentPos.y - 3f),
                    radius = 12f
                ),
                radius = 12f,
                center = currentPos
            )
            
            // Ball seam (simplified)
            drawArc(
                color = Color.White.copy(alpha = 0.6f),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(currentPos.x - 8f, currentPos.y - 8f),
                size = Size(16f, 16f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}
