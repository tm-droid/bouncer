package com.tanush.bouncer.ui.components.celebrations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random
import io.sentry.Sentry
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.GoldLight

/**
 * Shape types for confetti particles
 */
enum class ConfettiShape {
    CIRCLE,
    SQUARE,
    RECTANGLE,
    STAR,
    DIAMOND,
    TRIANGLE
}

/**
 * Data class representing a single confetti particle with physics properties
 */
data class ConfettiParticle(
    val id: Int,
    val position: Offset,
    val velocity: Offset,
    val rotation: Float,
    val rotationVelocity: Float,
    val color: Color,
    val shape: ConfettiShape,
    val size: Float,
    val alpha: Float = 1f,
    val lifetime: Float = 1f
)

/**
 * Configuration for confetti emission
 */
data class ConfettiConfig(
    val particleCount: Int = 100,
    val colors: List<Color> = listOf(
        GoldPrimary,
        GoldLight,
        ChartPurple,
        StatusInfo,
        StatusSuccess
    ),
    val minSpeed: Float = 5f,
    val maxSpeed: Float = 15f,
    val minSize: Float = 6f,
    val maxSize: Float = 14f,
    val gravity: Float = 0.3f,
    val drag: Float = 0.02f,
    val spread: Float = 360f,
    val duration: Long = 3000L
)

/**
 * Base confetti emitter composable with configurable particle count and colors
 */
@Composable
fun ConfettiEmitter(
    modifier: Modifier = Modifier,
    config: ConfettiConfig = ConfettiConfig(),
    emitPoint: Offset? = null,
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    var animationProgress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            // Generate particles
            particles = (0 until config.particleCount).map { i ->
                val angle = Random.nextFloat() * config.spread - config.spread / 2
                val speed = Random.nextFloat() * (config.maxSpeed - config.minSpeed) + config.minSpeed
                val radians = Math.toRadians(angle.toDouble() - 90.0)
                
                ConfettiParticle(
                    id = i,
                    position = Offset.Zero,
                    velocity = Offset(
                        (cos(radians) * speed).toFloat(),
                        (sin(radians) * speed).toFloat()
                    ),
                    rotation = Random.nextFloat() * 360f,
                    rotationVelocity = Random.nextFloat() * 20f - 10f,
                    color = config.colors[Random.nextInt(config.colors.size)],
                    shape = ConfettiShape.entries[Random.nextInt(ConfettiShape.entries.size)],
                    size = Random.nextFloat() * (config.maxSize - config.minSize) + config.minSize
                )
            }
            
            // Animate particles
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < config.duration) {
                val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                animationProgress = (elapsed / config.duration).coerceIn(0f, 1f)
                delay(16L)
            }
            animationProgress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = emitPoint?.x ?: (size.width / 2)
        val centerY = emitPoint?.y ?: (size.height / 2)
        
        particles.forEach { particle ->
            val time = animationProgress * config.duration / 16f
            val gravityEffect = 0.5f * config.gravity * time * time
            val drag = 1f - (config.drag * time).coerceAtMost(0.9f)
            
            val x = centerX + particle.velocity.x * time * drag
            val y = centerY + particle.velocity.y * time * drag + gravityEffect
            val alpha = (1f - animationProgress).coerceIn(0f, 1f)
            val scale = 1f - animationProgress * 0.3f
            val rotation = particle.rotation + particle.rotationVelocity * time
            
            if (y < size.height + 50 && alpha > 0.01f) {
                rotate(rotation, Offset(x, y)) {
                    drawConfettiShape(
                        shape = particle.shape,
                        center = Offset(x, y),
                        size = particle.size * scale,
                        color = particle.color.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}

/**
 * Helper extension to draw different confetti shapes
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConfettiShape(
    shape: ConfettiShape,
    center: Offset,
    size: Float,
    color: Color
) {
    when (shape) {
        ConfettiShape.CIRCLE -> {
            drawCircle(color = color, radius = size / 2, center = center)
        }
        ConfettiShape.SQUARE -> {
            drawRect(
                color = color,
                topLeft = Offset(center.x - size / 2, center.y - size / 2),
                size = Size(size, size)
            )
        }
        ConfettiShape.RECTANGLE -> {
            drawRect(
                color = color,
                topLeft = Offset(center.x - size / 2, center.y - size / 4),
                size = Size(size, size / 2)
            )
        }
        ConfettiShape.STAR -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = size
                ),
                radius = size,
                center = center
            )
        }
        ConfettiShape.DIAMOND -> {
            val path = Path().apply {
                moveTo(center.x, center.y - size / 2)
                lineTo(center.x + size / 2, center.y)
                lineTo(center.x, center.y + size / 2)
                lineTo(center.x - size / 2, center.y)
                close()
            }
            drawPath(path, color)
        }
        ConfettiShape.TRIANGLE -> {
            val path = Path().apply {
                moveTo(center.x, center.y - size / 2)
                lineTo(center.x + size / 2, center.y + size / 2)
                lineTo(center.x - size / 2, center.y + size / 2)
                close()
            }
            drawPath(path, color)
        }
    }
}

/**
 * Confetti burst explosion effect from a specific point
 */
@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    burstPoint: Offset,
    particleCount: Int = 80,
    colors: List<Color> = listOf(GoldPrimary, GoldLight, StatusInfo, ChartPurple),
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    ConfettiEmitter(
        modifier = modifier,
        config = ConfettiConfig(
            particleCount = particleCount,
            colors = colors,
            minSpeed = 8f,
            maxSpeed = 20f,
            spread = 360f,
            gravity = 0.4f,
            duration = 2500L
        ),
        emitPoint = burstPoint,
        isActive = isActive,
        onComplete = onComplete
    )
}

/**
 * Confetti rain falling from the top of the screen
 */
@Composable
fun ConfettiRain(
    modifier: Modifier = Modifier,
    particleCount: Int = 150,
    colors: List<Color> = listOf(GoldPrimary, GoldLight, StatusSuccess, StatusInfo),
    duration: Long = 4000L,
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    var animationProgress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            particles = (0 until particleCount).map { i ->
                val startDelay = Random.nextFloat() * 0.5f // Staggered drop
                
                ConfettiParticle(
                    id = i,
                    position = Offset(Random.nextFloat(), -startDelay),
                    velocity = Offset(
                        Random.nextFloat() * 2f - 1f,
                        Random.nextFloat() * 3f + 2f
                    ),
                    rotation = Random.nextFloat() * 360f,
                    rotationVelocity = Random.nextFloat() * 10f - 5f,
                    color = colors[Random.nextInt(colors.size)],
                    shape = ConfettiShape.entries[Random.nextInt(ConfettiShape.entries.size)],
                    size = Random.nextFloat() * 8f + 4f,
                    lifetime = startDelay
                )
            }
            
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < duration) {
                val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                animationProgress = (elapsed / duration).coerceIn(0f, 1f)
                delay(16L)
            }
            animationProgress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val adjustedProgress = (animationProgress - particle.lifetime).coerceIn(0f, 1f - particle.lifetime) / (1f - particle.lifetime)
            if (adjustedProgress <= 0f) return@forEach
            
            val x = particle.position.x * size.width + particle.velocity.x * adjustedProgress * 50f
            val y = particle.position.y * size.height + size.height * adjustedProgress * 1.2f
            val wobble = sin(adjustedProgress * 10f + particle.id) * 20f
            val alpha = when {
                adjustedProgress < 0.1f -> adjustedProgress * 10f
                adjustedProgress > 0.8f -> (1f - adjustedProgress) * 5f
                else -> 1f
            }
            val rotation = particle.rotation + particle.rotationVelocity * adjustedProgress * 360f
            
            if (y < size.height + 20) {
                rotate(rotation, Offset(x + wobble, y)) {
                    drawConfettiShape(
                        shape = particle.shape,
                        center = Offset(x + wobble, y),
                        size = particle.size,
                        color = particle.color.copy(alpha = alpha.coerceIn(0f, 1f))
                    )
                }
            }
        }
    }
}

/**
 * Confetti cannon shooting confetti from the sides
 */
@Composable
fun ConfettiCannon(
    modifier: Modifier = Modifier,
    fromLeft: Boolean = true,
    particleCount: Int = 60,
    colors: List<Color> = listOf(GoldPrimary, GoldLight, ChartPurple, StatusSuccess),
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    var animationProgress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            val baseAngle = if (fromLeft) -45.0 else -135.0
            
            particles = (0 until particleCount).map { i ->
                val angleSpread = Random.nextFloat() * 60f - 30f
                val speed = Random.nextFloat() * 15f + 10f
                val radians = Math.toRadians(baseAngle + angleSpread)
                
                ConfettiParticle(
                    id = i,
                    position = Offset.Zero,
                    velocity = Offset(
                        (cos(radians) * speed).toFloat(),
                        (sin(radians) * speed).toFloat()
                    ),
                    rotation = Random.nextFloat() * 360f,
                    rotationVelocity = Random.nextFloat() * 15f - 7.5f,
                    color = colors[Random.nextInt(colors.size)],
                    shape = ConfettiShape.entries[Random.nextInt(ConfettiShape.entries.size)],
                    size = Random.nextFloat() * 10f + 5f
                )
            }
            
            val startTime = System.currentTimeMillis()
            val duration = 2500L
            while (System.currentTimeMillis() - startTime < duration) {
                val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                animationProgress = (elapsed / duration).coerceIn(0f, 1f)
                delay(16L)
            }
            animationProgress = 1f
            onComplete()
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val startX = if (fromLeft) 0f else size.width
        val startY = size.height * 0.7f
        
        particles.forEach { particle ->
            val time = animationProgress * 150f
            val gravity = 0.25f * time * time
            
            val x = startX + particle.velocity.x * time
            val y = startY + particle.velocity.y * time + gravity
            val alpha = (1f - animationProgress * 0.8f).coerceIn(0f, 1f)
            val scale = 1f - animationProgress * 0.3f
            val rotation = particle.rotation + particle.rotationVelocity * time
            
            if (y < size.height + 30 && y > -30 && x > -30 && x < size.width + 30) {
                rotate(rotation, Offset(x, y)) {
                    drawConfettiShape(
                        shape = particle.shape,
                        center = Offset(x, y),
                        size = particle.size * scale,
                        color = particle.color.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}

/**
 * Special confetti preset for century (100 runs) celebration
 */
@Composable
fun CenturyConfetti(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    val centuryColors = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFFA500), // Orange Gold
        GoldPrimary,
        GoldPrimary,
        GoldLight,
        Color.White
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        // Main burst from center
        ConfettiBurst(
            burstPoint = Offset.Unspecified, // Will use center
            particleCount = 100,
            colors = centuryColors,
            isActive = isActive
        )
        
        // Rain effect
        ConfettiRain(
            particleCount = 80,
            colors = centuryColors,
            duration = 3500L,
            isActive = isActive
        )
        
        // Side cannons
        ConfettiCannon(
            fromLeft = true,
            particleCount = 40,
            colors = centuryColors,
            isActive = isActive
        )
        
        ConfettiCannon(
            fromLeft = false,
            particleCount = 40,
            colors = centuryColors,
            isActive = isActive,
            onComplete = onComplete
        )
    }
}

/**
 * Special confetti preset for wicket (bowling achievement) celebration
 */
@Composable
fun WicketConfetti(
    modifier: Modifier = Modifier,
    wicketCount: Int = 1,
    teamColor: Color = StatusInfo,
    isActive: Boolean = true,
    onComplete: () -> Unit = {}
) {
    val wicketColors = when {
        wicketCount >= 5 -> listOf(
            Color(0xFFFFD700), // Gold for 5-wicket haul
            Color(0xFFFFA500),
            teamColor,
            ChartPurple,
            Color.White
        )
        wicketCount >= 3 -> listOf(
            ChartPurple, // Purple for hat-trick
            SixerPurple,
            teamColor,
            Color.White
        )
        else -> listOf(
            StatusLive,
            StatusLive,
            teamColor,
            Color.White
        )
    }
    
    val particleMultiplier = when {
        wicketCount >= 5 -> 2.0f
        wicketCount >= 3 -> 1.5f
        else -> 1.0f
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        ConfettiBurst(
            burstPoint = Offset.Unspecified,
            particleCount = (60 * particleMultiplier).toInt(),
            colors = wicketColors,
            isActive = isActive,
            onComplete = onComplete
        )
        
        if (wicketCount >= 3) {
            ConfettiRain(
                particleCount = (50 * particleMultiplier).toInt(),
                colors = wicketColors,
                duration = 3000L,
                isActive = isActive
            )
        }
        
        if (wicketCount >= 5) {
            ConfettiCannon(
                fromLeft = true,
                particleCount = 30,
                colors = wicketColors,
                isActive = isActive
            )
            ConfettiCannon(
                fromLeft = false,
                particleCount = 30,
                colors = wicketColors,
                isActive = isActive
            )
        }
    }
}
