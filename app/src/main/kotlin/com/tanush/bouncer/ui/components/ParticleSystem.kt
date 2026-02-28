package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Floating particle system that creates an ambient, luxurious atmosphere
 */
@Composable
fun FloatingParticleSystem(
    modifier: Modifier = Modifier,
    particleCount: Int = 25,
    baseColor: Color = ShinyGoldDeep,
    secondaryColor: Color = ChartPurple,
    speed: Float = 1f
) {
    val particles = remember {
        List(particleCount) {
            FloatingParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * 3f + 1f,
                speed = (Random.nextFloat() * 0.3f + 0.1f) * speed,
                phase = Random.nextFloat() * 2 * PI.toFloat(),
                isSecondary = Random.nextFloat() > 0.7f
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleTime"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val xOffset = sin(time * particle.speed + particle.phase) * size.width * 0.05f
            val yOffset = cos(time * particle.speed * 0.7f + particle.phase) * size.height * 0.03f
            
            val x = (particle.x * size.width + xOffset) % size.width
            val y = ((particle.y * size.height + yOffset + time * particle.speed * 20) % size.height)
            
            val alpha = (sin(time * 2 + particle.phase) * 0.3f + 0.4f).coerceIn(0.2f, 0.7f)
            val color = if (particle.isSecondary) secondaryColor else baseColor
            
            // Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = particle.radius * 4
                ),
                radius = particle.radius * 4,
                center = Offset(x, y)
            )
            
            // Core
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = particle.radius,
                center = Offset(x, y)
            )
        }
    }
}

private data class FloatingParticle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float,
    val isSecondary: Boolean
)

/**
 * Meteor-like shooting star effect for premium feel
 */
@Composable
fun ShootingStarsEffect(
    modifier: Modifier = Modifier,
    starCount: Int = 3,
    color: Color = ShinyGoldLight
) {
    val stars = remember {
        List(starCount) {
            ShootingStar(
                delay = Random.nextInt(5000),
                duration = Random.nextInt(1500, 3000),
                startY = Random.nextFloat() * 0.5f,
                angle = Random.nextFloat() * 30f + 15f
            )
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        stars.forEachIndexed { index, star ->
            AnimatedShootingStar(
                star = star,
                color = color,
                index = index
            )
        }
    }
}

private data class ShootingStar(
    val delay: Int,
    val duration: Int,
    val startY: Float,
    val angle: Float
)

@Composable
private fun AnimatedShootingStar(
    star: ShootingStar,
    color: Color,
    index: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shootingStar_$index")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = star.duration,
                delayMillis = star.delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "starProgress_$index"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (progress > 0.01f && progress < 0.99f) {
            val angleRad = star.angle * PI.toFloat() / 180f
            val length = size.width * 0.15f * (1f - progress * 0.5f)
            
            val x = progress * size.width * 1.2f - size.width * 0.1f
            val y = star.startY * size.height + (x - size.width * 0.1f) * tan(angleRad)
            
            val endX = x - length * cos(angleRad)
            val endY = y - length * sin(angleRad)
            
            val alpha = if (progress < 0.2f) progress * 5f 
                       else if (progress > 0.8f) (1f - progress) * 5f 
                       else 1f
            
            // Trail gradient
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    start = Offset(x, y),
                    end = Offset(endX, endY)
                ),
                start = Offset(x, y),
                end = Offset(endX, endY),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            
            // Head glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha * 0.8f),
                        color.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = 8f
                ),
                radius = 8f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Ambient wave effect for backgrounds
 */
@Composable
fun AmbientWaveBackground(
    modifier: Modifier = Modifier,
    waveColor: Color = ShinyGoldDeep,
    waveCount: Int = 3,
    intensity: Float = 0.08f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        repeat(waveCount) { waveIndex ->
            val wavePhaseOffset = (waveIndex.toFloat() / waveCount) * 2 * PI.toFloat()
            val waveAmplitude = size.height * 0.03f * (waveCount - waveIndex)
            val baseY = size.height * (0.3f + waveIndex * 0.2f)
            val waveAlpha = intensity * (1f - waveIndex * 0.2f)
            
            val path = Path().apply {
                moveTo(0f, baseY)
                
                for (x in 0..size.width.toInt() step 2) {
                    val xFloat = x.toFloat()
                    val y = baseY + sin(xFloat / size.width * 4 * PI.toFloat() + phase + wavePhaseOffset) * waveAmplitude
                    lineTo(xFloat, y)
                }
                
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        waveColor.copy(alpha = waveAlpha),
                        waveColor.copy(alpha = waveAlpha * 0.3f),
                        Color.Transparent
                    ),
                    startY = baseY - waveAmplitude,
                    endY = size.height
                )
            )
        }
    }
}

/**
 * Orb ambient glow effect
 */
@Composable
fun AmbientOrbsBackground(
    modifier: Modifier = Modifier,
    orbCount: Int = 4,
    primaryColor: Color = ShinyGoldDeep,
    secondaryColor: Color = ChartPurple,
    tertiaryColor: Color = LiveBlue
) {
    val orbs = remember {
        List(orbCount) {
            AmbientOrb(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * 0.3f + 0.2f,
                color = when (it % 3) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> tertiaryColor
                },
                speed = Random.nextFloat() * 0.5f + 0.3f,
                phase = Random.nextFloat() * 2 * PI.toFloat()
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbTime"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        orbs.forEach { orb ->
            val xOffset = sin(time * orb.speed + orb.phase) * size.width * 0.1f
            val yOffset = cos(time * orb.speed * 0.7f + orb.phase) * size.height * 0.08f
            
            val x = orb.x * size.width + xOffset
            val y = orb.y * size.height + yOffset
            val radius = orb.radius * size.minDimension
            
            val alpha = (sin(time + orb.phase) * 0.2f + 0.4f).coerceIn(0.2f, 0.6f)
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orb.color.copy(alpha = alpha * 0.15f),
                        orb.color.copy(alpha = alpha * 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = radius
                ),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}

private data class AmbientOrb(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val speed: Float,
    val phase: Float
)

/**
 * Cricket ball seam pattern for cricket-themed backgrounds
 */
@Composable
fun CricketSeamPattern(
    modifier: Modifier = Modifier,
    color: Color = ShinyGoldDeep.copy(alpha = 0.05f),
    seamCount: Int = 8
) {
    val infiniteTransition = rememberInfiniteTransition(label = "seam")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "seamRotation"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width * 0.85f
        val centerY = size.height * 0.15f
        val radius = size.minDimension * 0.4f
        
        // Main circle (ball outline)
        drawCircle(
            color = color.copy(alpha = 0.03f),
            radius = radius,
            center = Offset(centerX, centerY)
        )
        
        // Seam lines
        repeat(seamCount) { i ->
            val angle = (rotation + i * 360f / seamCount) * PI.toFloat() / 180f
            val innerRadius = radius * 0.3f
            val outerRadius = radius * 0.95f
            
            drawLine(
                color = color,
                start = Offset(
                    centerX + cos(angle) * innerRadius,
                    centerY + sin(angle) * innerRadius
                ),
                end = Offset(
                    centerX + cos(angle) * outerRadius,
                    centerY + sin(angle) * outerRadius
                ),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Premium animated background combining multiple effects
 */
@Composable
fun PremiumAnimatedBackground(
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    showOrbs: Boolean = true,
    showSeam: Boolean = false,
    particleColor: Color = ShinyGoldDeep,
    orbIntensity: Float = 0.5f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        // Base gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BlackBackground,
                            CardDarkBlue.copy(alpha = 0.3f),
                            BlackBackground
                        )
                    )
                )
        )
        
        // Ambient orbs
        if (showOrbs) {
            AmbientOrbsBackground(
                primaryColor = particleColor.copy(alpha = 0.08f * orbIntensity),
                secondaryColor = ChartPurple.copy(alpha = 0.05f * orbIntensity),
                tertiaryColor = LiveBlue.copy(alpha = 0.04f * orbIntensity)
            )
        }
        
        // Floating particles
        if (showParticles) {
            FloatingParticleSystem(
                particleCount = 15,
                baseColor = particleColor.copy(alpha = 0.4f),
                secondaryColor = ChartPurple.copy(alpha = 0.3f)
            )
        }
        
        // Cricket seam pattern
        if (showSeam) {
            CricketSeamPattern(
                color = particleColor.copy(alpha = 0.03f)
            )
        }
    }
}
