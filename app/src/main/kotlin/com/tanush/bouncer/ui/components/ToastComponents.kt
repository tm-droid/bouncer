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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * Toast types for different notifications
 */
enum class ToastType {
    ACHIEVEMENT,
    MILESTONE,
    INFO,
    SUCCESS,
    WARNING
}

/**
 * Premium toast notification component
 */
@Composable
fun AchievementToast(
    title: String,
    description: String,
    isVisible: Boolean,
    type: ToastType = ToastType.ACHIEVEMENT,
    icon: ImageVector = Icons.Default.EmojiEvents,
    onDismiss: () -> Unit = {}
) {
    val density = LocalDensity.current
    
    val infiniteTransition = rememberInfiniteTransition(label = "toastGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Colors based on type
    val colors = BouncerDesignSystem.colors
    val (accentColor, bgGradient) = when (type) {
        ToastType.ACHIEVEMENT -> colors.goldPrimary to listOf(colors.goldPrimary.copy(alpha = 0.15f), colors.goldPrimary.copy(alpha = 0.08f))
        ToastType.MILESTONE -> colors.statusWarning to listOf(colors.statusWarning.copy(alpha = 0.15f), colors.statusWarning.copy(alpha = 0.08f))
        ToastType.INFO -> colors.statusInfo to listOf(colors.statusInfo.copy(alpha = 0.15f), colors.statusInfo.copy(alpha = 0.08f))
        ToastType.SUCCESS -> Color(0xFF10B981) to listOf(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981).copy(alpha = 0.08f))
        ToastType.WARNING -> Color(0xFFF59E0B) to listOf(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B).copy(alpha = 0.08f))
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { -it },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it }
    ) {
        Box(
            modifier = Modifier.padding(16.dp)
        ) {
            // Glow effect behind card
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glowPulse }
            ) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.maxDimension
                    ),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
            }
            
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = GlassSurface.copy(alpha = 0.98f),
                shadowElevation = 20.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.4f + shimmerOffset * 0.2f),
                            accentColor.copy(alpha = 0.1f),
                            accentColor.copy(alpha = 0.3f)
                        ),
                        start = Offset(shimmerOffset * 400, 0f),
                        end = Offset(shimmerOffset * 400 + 200, 100f)
                    )
                )
            ) {
                // Top gradient line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accentColor.copy(alpha = 0.6f),
                                    accentColor,
                                    accentColor.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                Row(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(bgGradient)
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Icon with glow
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(accentColor, accentColor.copy(alpha = 0.8f))
                                    ),
                                    CircleShape
                                )
                                .shadow(8.dp, CircleShape, spotColor = accentColor.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = BackgroundBase,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            color = accentColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini confetti effect for toasts
 */
@Composable
fun ToastConfetti(
    isActive: Boolean,
    color: Color = GoldPrimary,
    particleCount: Int = 20
) {
    if (!isActive) return
    
    val density = LocalDensity.current
    var particles by remember { mutableStateOf<List<MiniConfettiParticle>>(emptyList()) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            particles = (0 until particleCount).map { i ->
                MiniConfettiParticle(
                    id = i,
                    x = Random.nextFloat(),
                    y = 0f,
                    velocityX = (Random.nextFloat() - 0.5f) * 0.02f,
                    velocityY = Random.nextFloat() * 0.015f + 0.005f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 10f,
                    size = Random.nextFloat() * 8f + 4f,
                    color = if (Random.nextBoolean()) color else color.copy(alpha = 0.6f)
                )
            }
            
            // Animate particles
            repeat(60) {
                delay(16)
                particles = particles.map { p ->
                    p.copy(
                        x = p.x + p.velocityX,
                        y = p.y + p.velocityY,
                        rotation = p.rotation + p.rotationSpeed,
                        velocityY = p.velocityY + 0.001f // Gravity
                    )
                }.filter { it.y < 1.5f }
            }
        }
    }
    
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        particles.forEach { p ->
            val x = p.x * size.width
            val y = p.y * size.height
            
            rotate(p.rotation, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.size / 2, y - p.size / 2),
                    size = androidx.compose.ui.geometry.Size(p.size, p.size)
                )
            }
        }
    }
}

private data class MiniConfettiParticle(
    val id: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color
)

/**
 * Score change notification
 */
@Composable
fun ScoreChangeNotification(
    runs: Int,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val isBoundary = runs in listOf(4, 6)
    val color = when (runs) {
        6 -> colors.statusWarning
        4 -> colors.statusWarning
        else -> colors.goldPrimary
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "scorePulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scoreScale"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(tween(150)) + fadeIn(tween(100)),
        exit = scaleOut(tween(200)) + fadeOut(tween(150))
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { 
                    scaleX = scale
                    scaleY = scale
                }
                .size(60.dp)
                .shadow(12.dp, CircleShape, spotColor = color.copy(alpha = 0.5f))
                .background(
                    Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0.8f))
                    ),
                    CircleShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+$runs",
                color = if (isBoundary) BackgroundBase else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        if (isBoundary) {
            ToastConfetti(isActive = true, color = color)
        }
    }
}

/**
 * Wicket notification with dramatic effect
 */
@Composable
fun WicketNotification(
    batsmanName: String,
    score: String,
    bowlerName: String,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wicketPulse")
    
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(100)) + scaleIn(tween(200)),
        exit = fadeOut(tween(300)) + scaleOut(tween(200))
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = StatusInfo.copy(alpha = 0.4f))
                .background(GlassSurface, RoundedCornerShape(28.dp))
                .border(
                    3.dp,
                    StatusInfo.copy(alpha = borderPulse),
                    RoundedCornerShape(28.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big W icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(StatusInfo, StatusInfo.copy(alpha = 0.8f))
                        ),
                        CircleShape
                    )
                    .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "W",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                "WICKET!",
                color = StatusInfo,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Batsman info
            Text(
                batsmanName,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                score,
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Bowler credit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "b.",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    bowlerName,
                    color = GoldPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        ToastConfetti(isActive = true, color = StatusInfo, particleCount = 30)
    }
}

/**
 * Compact info notification bar
 */
@Composable
fun InfoBar(
    message: String,
    icon: ImageVector = Icons.Default.Info,
    color: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(
                message,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
