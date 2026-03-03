package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import io.sentry.Sentry

/**
 * Match Countdown Timer with premium animated display
 */
@Composable
fun MatchCountdownTimer(
    days: Int,
    hours: Int,
    minutes: Int,
    seconds: Int,
    teamName1: String,
    teamName2: String,
    matchTitle: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "countdown_appear"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "countdown_effects")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animationProgress
                scaleX = 0.9f + 0.1f * animationProgress
                scaleY = 0.9f + 0.1f * animationProgress
            }
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.2f),
                ambientColor = Color.Black.copy(alpha = 0.4f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    colors.goldPrimary.copy(alpha = 0.3f),
                    colors.goldLight.copy(alpha = 0.1f),
                    colors.goldPrimary.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Match Title
            Text(
                text = matchTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // VS Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = teamName1,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                        .border(1.dp, colors.goldPrimary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.goldPrimary
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = teamName2,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Countdown Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Match starts in",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Countdown Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountdownUnit(
                    value = days,
                    label = "DAYS",
                    color = colors.goldPrimary,
                    scale = if (days > 0) pulseScale else 1f
                )
                CountdownSeparator()
                CountdownUnit(
                    value = hours,
                    label = "HOURS",
                    color = colors.goldDark,
                    scale = if (days == 0 && hours > 0) pulseScale else 1f
                )
                CountdownSeparator()
                CountdownUnit(
                    value = minutes,
                    label = "MINS",
                    color = colors.goldLight,
                    scale = if (days == 0 && hours == 0 && minutes > 0) pulseScale else 1f
                )
                CountdownSeparator()
                CountdownUnit(
                    value = seconds,
                    label = "SECS",
                    color = NeonSuccess,
                    scale = pulseScale
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Progress ring
            CountdownProgressRing(
                progress = calculateProgress(days, hours, minutes, seconds),
                modifier = Modifier.size(80.dp)
            )
        }
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String,
    color: Color,
    scale: Float = 1f
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = color.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    1.dp,
                    color.copy(alpha = 0.3f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textTertiary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CountdownSeparator() {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "separator")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "separator_alpha"
    )
    
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.height(56.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(colors.goldPrimary.copy(alpha = alpha), CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(colors.goldPrimary.copy(alpha = alpha), CircleShape)
        )
    }
}

@Composable
private fun CountdownProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val cardBorder = colors.glassBorder
    val goldPrimary = colors.goldPrimary
    val goldLight = colors.goldLight
    val goldDark = colors.goldDark
    val neonSuccess = colors.statusSuccess
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "ring_progress"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "ring_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        
        // Background ring
        drawCircle(
            color = cardBorder.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Progress arc with gradient
        val sweepAngle = 360f * animatedProgress
        
        // Glow effect
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    goldPrimary.copy(alpha = glowAlpha * 0.5f),
                    goldLight.copy(alpha = glowAlpha * 0.3f),
                    neonSuccess.copy(alpha = glowAlpha * 0.5f)
                ),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round),
            size = Size(radius * 2 + strokeWidth, radius * 2 + strokeWidth),
            topLeft = Offset(center.x - radius - strokeWidth / 2, center.y - radius - strokeWidth / 2)
        )
        
        // Main progress arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    goldLight,
                    goldPrimary,
                    goldDark,
                    neonSuccess
                ),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius, center.y - radius)
        )
        
        // End cap glow
        val endAngle = (-90f + sweepAngle) * PI.toFloat() / 180f
        val endX = center.x + radius * cos(endAngle)
        val endY = center.y + radius * sin(endAngle)
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.8f),
                    neonSuccess.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = Offset(endX, endY),
                radius = strokeWidth * 1.5f
            ),
            radius = strokeWidth,
            center = Offset(endX, endY)
        )
    }
}

private fun calculateProgress(days: Int, hours: Int, minutes: Int, seconds: Int): Float {
    try {
        // Assume max countdown is 7 days
        val maxSeconds = 7 * 24 * 60 * 60
        val currentSeconds = days * 24 * 60 * 60 + hours * 60 * 60 + minutes * 60 + seconds
        return (maxSeconds - currentSeconds).toFloat() / maxSeconds
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Circular Timer for quick countdowns
 */
@Composable
fun CircularCountdown(
    seconds: Int,
    totalSeconds: Int,
    label: String = "Time Left",
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val cardBorder = colors.glassBorder
    val goldPrimary = colors.goldPrimary
    val progress = seconds.toFloat() / totalSeconds
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "circular_progress"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "timer_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
        ) {
            val strokeWidth = 10.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            // Background
            drawCircle(
                color = cardBorder.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            
            // Progress
            val color = when {
                progress > 0.5f -> NeonSuccess
                progress > 0.25f -> goldPrimary
                else -> NeonError
            }
            
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(seconds),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = when {
                    progress > 0.5f -> NeonSuccess
                    progress > 0.25f -> goldPrimary
                    else -> NeonError
                }
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = colors.textTertiary
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    try {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Animated Clock Display
 */
@Composable
fun AnimatedClockDisplay(
    time: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "clock")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colon"
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        time.forEach { char ->
            if (char == ':') {
                Text(
                    text = ":",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.goldPrimary.copy(alpha = colonAlpha),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            0.5.dp,
                            colors.goldPrimary.copy(alpha = 0.2f),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}
