package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import kotlinx.coroutines.isActive
import kotlin.random.Random

// Gold palette for boundary celebrations
private val ConfettiColors = listOf(
    Color(0xFFD4AF37),
    Color(0xFFC9A830),
    Color(0xFFE8C547),
    Color(0xFFF0D060),
    Color(0xFFB8941F)
)

data class BoundaryConfettiParticle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val color: Color,
    val alpha: Float
)

enum class ConfettiIntensity(val particleCount: Int, val durationMs: Int) {
    FOUR(particleCount = 8, durationMs = 800),
    SIX(particleCount = 14, durationMs = 1200)
}

@Composable
fun ConfettiEffect(
    trigger: Boolean,
    intensity: ConfettiIntensity = ConfettiIntensity.FOUR,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()

    if (reducedMotion) {
        ReducedMotionFlash(trigger = trigger, modifier = modifier)
    } else {
        ParticleConfetti(trigger = trigger, intensity = intensity, modifier = modifier)
    }
}

@Composable
private fun ReducedMotionFlash(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        flashAlpha.snapTo(0f)
        flashAlpha.animateTo(0.15f, animationSpec = tween(durationMillis = 150))
        flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 150))
    }

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        if (flashAlpha.value > 0f) {
            drawRect(
                color = Color(0xFFD4AF37),
                alpha = flashAlpha.value
            )
        }
    }
}

@Composable
private fun ParticleConfetti(
    trigger: Boolean,
    intensity: ConfettiIntensity,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val particles = remember { mutableStateListOf<BoundaryConfettiParticle>() }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    // 980 dp/s² gravity converted to px/s²
    val gravityPxPerS2 = with(density) { 980.dp.toPx() }

    LaunchedEffect(trigger) {
        if (!trigger) {
            particles.clear()
            elapsedMs = 0L
            return@LaunchedEffect
        }

        particles.clear()
        elapsedMs = 0L
        var startTime = 0L

        withFrameMillis { frameTime -> startTime = frameTime }

        while (isActive) {
            withFrameMillis { frameTime ->
                val prevElapsed = elapsedMs
                elapsedMs = frameTime - startTime
                val dtSec = (elapsedMs - prevElapsed) / 1000f

                if (elapsedMs >= intensity.durationMs) {
                    particles.clear()
                    return@withFrameMillis
                }

                if (particles.isEmpty() && elapsedMs < 100) {
                    return@withFrameMillis
                }

                val fadeStartMs = intensity.durationMs * 0.7f
                val fadeDurationMs = intensity.durationMs * 0.3f
                val updatedParticles = particles.map { p ->
                    val newVy = p.velocityY + gravityPxPerS2 * dtSec
                    val newX = p.x + p.velocityX * dtSec
                    val newY = p.y + newVy * dtSec
                    val newAlpha = if (elapsedMs > fadeStartMs) {
                        (1f - ((elapsedMs - fadeStartMs) / fadeDurationMs)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    p.copy(
                        x = newX,
                        y = newY,
                        velocityY = newVy,
                        alpha = newAlpha
                    )
                }
                particles.clear()
                particles.addAll(updatedParticles)
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        // Spawn particles on first draw if empty and triggered
        if (trigger && particles.isEmpty() && elapsedMs < 100) {
            spawnParticles(particles, intensity, size, density.density)
        }

        for (particle in particles) {
            if (particle.alpha <= 0f) continue
            drawCircle(
                color = particle.color,
                radius = particle.size / 2f,
                center = androidx.compose.ui.geometry.Offset(particle.x, particle.y),
                alpha = particle.alpha
            )
        }
    }
}

private fun spawnParticles(
    particles: MutableList<BoundaryConfettiParticle>,
    intensity: ConfettiIntensity,
    canvasSize: Size,
    density: Float
) {
    val minSizePx = 3.dp.value * density
    val maxSizePx = 6.dp.value * density
    val topZone = canvasSize.height * 0.3f

    repeat(intensity.particleCount) {
        val x = Random.nextFloat() * canvasSize.width
        val y = Random.nextFloat() * topZone
        val velocityX = (Random.nextFloat() - 0.5f) * 200f * density  // px/s
        val velocityY = -(Random.nextFloat() * 300f + 100f) * density  // px/s upward
        val size = minSizePx + Random.nextFloat() * (maxSizePx - minSizePx)
        val color = ConfettiColors[Random.nextInt(ConfettiColors.size)]

        particles.add(
            BoundaryConfettiParticle(
                x = x,
                y = y,
                velocityX = velocityX,
                velocityY = velocityY,
                size = size,
                color = color,
                alpha = 1f
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun ConfettiEffectPreview() {
    Box(modifier = Modifier.size(200.dp)) {
        // Static snapshot: manually placed gold circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val colors = ConfettiColors
            val positions = listOf(
                Triple(0.2f, 0.15f, 4.dp.toPx()),
                Triple(0.5f, 0.10f, 5.dp.toPx()),
                Triple(0.8f, 0.20f, 3.dp.toPx()),
                Triple(0.3f, 0.25f, 6.dp.toPx()),
                Triple(0.6f, 0.18f, 4.dp.toPx()),
                Triple(0.15f, 0.22f, 5.dp.toPx()),
                Triple(0.75f, 0.12f, 3.dp.toPx()),
                Triple(0.45f, 0.28f, 4.dp.toPx())
            )
            positions.forEachIndexed { index, (xFrac, yFrac, radius) ->
                drawCircle(
                    color = colors[index % colors.size],
                    radius = radius / 2f,
                    center = androidx.compose.ui.geometry.Offset(
                        size.width * xFrac,
                        size.height * yFrac
                    ),
                    alpha = 0.85f
                )
            }
        }
    }
}
