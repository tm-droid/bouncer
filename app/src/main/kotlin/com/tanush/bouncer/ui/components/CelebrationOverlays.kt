package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import io.sentry.Sentry

/**
 * Event types for celebrations
 */
enum class CelebrationEventType {
    BOUNDARY_FOUR,
    BOUNDARY_SIX,
    WICKET,
    FIFTY,
    CENTURY,
    HAT_TRICK,
    MATCH_WIN
}

/**
 * Data class for confetti particles
 */
data class ConfettiParticle(
    val id: Int,
    val startPosition: Offset,
    var currentPosition: Offset,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val velocity: Offset,
    val shape: ParticleShape
)

enum class ParticleShape {
    CIRCLE, SQUARE, STAR, DIAMOND
}

/**
 * Premium celebration overlay composable
 */
@Composable
fun CelebrationOverlay(
    eventType: CelebrationEventType,
    team1Color: Color = BouncerDesignSystem.colors.goldDark,
    team2Color: Color = StatusInfo,
    description: String? = null,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(2500)
        isVisible = false
        delay(400)
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + scaleIn(tween(300), initialScale = 0.8f),
        exit = fadeOut(tween(400)) + scaleOut(tween(300), targetScale = 1.2f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            // Confetti Layer
            ConfettiAnimation(
                eventType = eventType,
                color1 = team1Color,
                color2 = team2Color
            )
            
            // Central Event Card
            EventCard(
                eventType = eventType,
                description = description
            )
        }
    }
}

@Composable
private fun ConfettiAnimation(
    eventType: CelebrationEventType,
    color1: Color,
    color2: Color
) {
    val density = LocalDensity.current
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    var animationProgress by remember { mutableFloatStateOf(0f) }
    
    val transition = rememberInfiniteTransition(label = "confetti")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(Unit) {
        // Generate particles
        val newParticles = (0 until 60).map { i ->
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 15f + 8f
            val radians = Math.toRadians(angle.toDouble())
            
            ConfettiParticle(
                id = i,
                startPosition = Offset(0f, 0f),
                currentPosition = Offset(0f, 0f),
                color = if (i % 2 == 0) color1 else color2,
                size = Random.nextFloat() * 12f + 6f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 10f - 5f,
                velocity = Offset(
                    (cos(radians) * speed).toFloat(),
                    (sin(radians) * speed).toFloat()
                ),
                shape = ParticleShape.entries[Random.nextInt(ParticleShape.entries.size)]
            )
        }
        particles = newParticles
        
        // Animate
        while (animationProgress < 1f) {
            animationProgress += 0.016f
            delay(16)
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        particles.forEach { particle ->
            val progress = animationProgress.coerceIn(0f, 1f)
            val gravity = 0.5f * progress * progress * 300f
            
            val x = centerX + particle.velocity.x * progress * 40f
            val y = centerY + particle.velocity.y * progress * 40f + gravity
            val alpha = 1f - progress
            val scale = 1f - progress * 0.5f
            val rotation = particle.rotation + particle.rotationSpeed * progress * 360f
            
            rotate(rotation, Offset(x, y)) {
                when (particle.shape) {
                    ParticleShape.CIRCLE -> {
                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = particle.size * scale,
                            center = Offset(x, y)
                        )
                    }
                    ParticleShape.SQUARE -> {
                        drawRect(
                            color = particle.color.copy(alpha = alpha),
                            topLeft = Offset(x - particle.size * scale / 2, y - particle.size * scale / 2),
                            size = androidx.compose.ui.geometry.Size(particle.size * scale, particle.size * scale)
                        )
                    }
                    ParticleShape.STAR -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    particle.color.copy(alpha = alpha),
                                    particle.color.copy(alpha = alpha * 0.3f),
                                    Color.Transparent
                                ),
                                center = Offset(x, y),
                                radius = particle.size * scale * 2
                            ),
                            radius = particle.size * scale * 2,
                            center = Offset(x, y)
                        )
                    }
                    ParticleShape.DIAMOND -> {
                        val path = Path().apply {
                            moveTo(x, y - particle.size * scale)
                            lineTo(x + particle.size * scale * 0.6f, y)
                            lineTo(x, y + particle.size * scale)
                            lineTo(x - particle.size * scale * 0.6f, y)
                            close()
                        }
                        drawPath(path, particle.color.copy(alpha = alpha))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    eventType: CelebrationEventType,
    description: String?
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val eventInfo = getEventInfo(eventType)
    
    Card(
        modifier = Modifier
            .graphicsLayer { 
                scaleX = pulseScale 
                scaleY = pulseScale 
            }
            .shadow(
                elevation = 32.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = eventInfo.color.copy(alpha = 0.5f),
                ambientColor = eventInfo.color.copy(alpha = 0.2f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        ),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            2.dp,
            Brush.linearGradient(
                colors = listOf(
                    eventInfo.color.copy(alpha = 0.8f),
                    eventInfo.color.copy(alpha = 0.3f),
                    eventInfo.color.copy(alpha = 0.8f)
                ),
                start = Offset(shimmerOffset * 300, 0f),
                end = Offset(shimmerOffset * 300 + 300, 300f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Glowing Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                eventInfo.color.copy(alpha = 0.3f),
                                eventInfo.color.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    eventInfo.color.copy(alpha = 0.25f),
                                    GlassSurface
                                )
                            ),
                            CircleShape
                        )
                        .border(1.dp, eventInfo.color.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = eventInfo.icon,
                        contentDescription = null,
                        tint = eventInfo.color,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Title
            Text(
                text = eventInfo.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = eventInfo.color,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            
            // Description
            description?.let {
                Text(
                    text = it,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

private data class EventInfo(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private fun getEventInfo(eventType: CelebrationEventType): EventInfo {
    try {
        return when (eventType) {
            CelebrationEventType.BOUNDARY_FOUR -> EventInfo(
                title = "FOUR!",
                icon = Icons.Default.Looks4,
                color = StatusBoundary
            )
            CelebrationEventType.BOUNDARY_SIX -> EventInfo(
                title = "SIX!",
                icon = Icons.Default.Looks6,
                color = GoldPrimary
            )
            CelebrationEventType.WICKET -> EventInfo(
                title = "WICKET!",
                icon = Icons.Default.SportsCricket,
                color = StatusLive
            )
            CelebrationEventType.FIFTY -> EventInfo(
                title = "FIFTY!",
                icon = Icons.Default.Star,
                color = GoldDark
            )
            CelebrationEventType.CENTURY -> EventInfo(
                title = "CENTURY!",
                icon = Icons.Default.EmojiEvents,
                color = GoldDark
            )
            CelebrationEventType.HAT_TRICK -> EventInfo(
                title = "HAT-TRICK!",
                icon = Icons.Default.Whatshot,
                color = StatusInfo
            )
            CelebrationEventType.MATCH_WIN -> EventInfo(
                title = "VICTORY!",
                icon = Icons.Default.EmojiEvents,
                color = StatusSuccess
            )
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Animated ball result indicator for recent balls
 */
@Composable
fun AnimatedBallResult(
    result: String,
    isLatest: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ballPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isLatest) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val ballInfo = getBallInfo(result)
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { 
                scaleX = scale 
                scaleY = scale 
            }
            .size(36.dp)
            .then(
                if (isLatest && ballInfo.isHighlight) {
                    Modifier
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ballInfo.color.copy(alpha = glowAlpha * 0.5f),
                                    ballInfo.color.copy(alpha = glowAlpha * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                } else Modifier
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .shadow(
                    elevation = if (isLatest) 8.dp else 2.dp,
                    shape = CircleShape,
                    spotColor = ballInfo.color.copy(alpha = 0.5f)
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ballInfo.color.copy(alpha = 0.9f),
                            ballInfo.color.copy(alpha = 0.7f)
                        )
                    ),
                    CircleShape
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            ballInfo.color.copy(alpha = 0.6f)
                        )
                    ),
                    CircleShape
                )
        ) {
            Text(
                text = ballInfo.display,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (ballInfo.isHighlight) BackgroundBase else TextPrimary,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

private data class BallInfo(
    val display: String,
    val color: Color,
    val isHighlight: Boolean
)

private fun getBallInfo(result: String): BallInfo {
    try {
        return when (result.uppercase().trim()) {
            "6" -> BallInfo("6", GoldPrimary, true)
            "4" -> BallInfo("4", StatusBoundary, true)
            "W" -> BallInfo("W", StatusLive, true)
            "0", "." -> BallInfo("0", TextMuted.copy(alpha = 0.5f), false)
            "1" -> BallInfo("1", Color.White.copy(alpha = 0.08f), false)
            "2" -> BallInfo("2", Color.White.copy(alpha = 0.08f), false)
            "3" -> BallInfo("3", Color.White.copy(alpha = 0.08f), false)
            "NB" -> BallInfo("NB", StatusWarning, false)
            "WD" -> BallInfo("WD", StatusWarning, false)
            "LB" -> BallInfo("LB", StatusInfo.copy(alpha = 0.6f), false)
            "B" -> BallInfo("B", StatusInfo.copy(alpha = 0.6f), false)
            else -> BallInfo(result.take(2), Color.White.copy(alpha = 0.05f), false)
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Animated recent balls row with slide-in effect
 */
@Composable
fun AnimatedRecentBallsRow(
    balls: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        balls.forEachIndexed { index, ball ->
            val isLatest = index == balls.lastIndex
            
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, delayMillis = index * 50)
                ) + fadeIn(tween(200, delayMillis = index * 50))
            ) {
                AnimatedBallResult(
                    result = ball,
                    isLatest = isLatest
                )
            }
        }
    }
}

/**
 * Achievement toast overlay
 */
@Composable
fun AchievementToast(
    title: String,
    description: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    LaunchedEffect(visible) {
        if (visible) {
            delay(3000)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.7f)
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = StatusSuccess.copy(alpha = 0.4f)
                    ),
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                StatusSuccess.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Achievement Unlocked!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusSuccess,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
