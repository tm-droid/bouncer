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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium empty state, error, and placeholder components
 */

@Composable
fun PremiumErrorState(
    title: String = "Something went wrong",
    message: String = "Please try again later",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "error")
    
    val shake by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0, StartOffsetType.FastForward)
        ),
        label = "shake"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Error icon
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing ring
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale)
                    .alpha(0.3f)
                    .border(
                        width = 3.dp,
                        color = Color(0xFFEF4444),
                        shape = CircleShape
                    )
            )
            
            // Icon container
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .rotate(shake)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEF4444).copy(alpha = 0.2f),
                                GlassSurface
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = Color(0xFFEF4444),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        // Text content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        // Retry button
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = Color(0xFFEF4444),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Try Again",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun NoInternetState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "noInternet")
    
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Animated signal icon
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Signal waves
            Canvas(modifier = Modifier.size(120.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                
                for (i in 1..3) {
                    val radius = 20f + i * 15f
                    val alpha = (0.3f - i * 0.08f).coerceAtLeast(0.1f)
                    val offset = (waveOffset + i * 30) % 360
                    
                    drawArc(
                        color = Color(0xFFEF4444).copy(alpha = alpha),
                        startAngle = -60f + offset * 0.1f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius - 20),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .border(
                        width = 2.dp,
                        color = Color(0xFFEF4444).copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No Internet Connection",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Check your connection and try again",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.goldDark,
                contentColor = colors.backgroundBase
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Retry",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun NoMatchesState(
    message: String = "No matches scheduled",
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "noMatches")
    
    val ballRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Cricket ball animation
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(y = floatOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .blur(20.dp)
                    .alpha(0.3f)
                    .background(hierarchy.secondary.primary, CircleShape)
            )
            
            // Ball
            Canvas(
                modifier = Modifier
                    .size(64.dp)
                    .rotate(ballRotation)
            ) {
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                
                // Ball body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDC2626),
                            Color(0xFF7F1D1D)
                        ),
                        center = center.copy(x = center.x - radius * 0.2f, y = center.y - radius * 0.2f)
                    ),
                    radius = radius
                )
                
                // Seam
                val seamPath = Path()
                seamPath.moveTo(center.x - radius * 0.6f, center.y)
                seamPath.cubicTo(
                    center.x - radius * 0.3f, center.y - radius * 0.3f,
                    center.x + radius * 0.3f, center.y + radius * 0.3f,
                    center.x + radius * 0.6f, center.y
                )
                
                drawPath(
                    path = seamPath,
                    color = Color(0xFFF5F5DC),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "All Quiet on the Pitch",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun NoSearchResultsState(
    query: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(GlassSurface)
                .border(
                    width = 1.dp,
                    color = hierarchy.secondary.primary.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = hierarchy.secondary.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No results for \"$query\"",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Try adjusting your search or filters",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Button(
            onClick = onClearSearch,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.goldDark.copy(alpha = 0.2f),
                contentColor = hierarchy.secondary.primary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(
                width = 1.dp,
                color = hierarchy.secondary.primary,
                shape = RoundedCornerShape(12.dp)
            )
        ) {
            Text(
                text = "Clear Search",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun ComingSoonState(
    feature: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "comingSoon")
    
    val sparkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Sparkle effects
            val goldLight = hierarchy.secondary.secondary
            Canvas(modifier = Modifier.size(100.dp)) {
                val points = listOf(
                    Offset(size.width * 0.2f, size.height * 0.3f),
                    Offset(size.width * 0.8f, size.height * 0.2f),
                    Offset(size.width * 0.75f, size.height * 0.8f),
                    Offset(size.width * 0.15f, size.height * 0.75f)
                )
                
                points.forEachIndexed { index, point ->
                    val animOffset = (sparkle + index * 0.25f) % 1f
                    val alpha = (kotlin.math.sin(animOffset * Math.PI) * 0.8f).toFloat()
                    val starSize = 4f + animOffset * 4f
                    
                    // Draw 4-point star
                    val starPath = Path()
                    for (i in 0..3) {
                        val angle = Math.toRadians((i * 90.0) - 45)
                        val x = point.x + (cos(angle) * starSize).toFloat()
                        val y = point.y + (sin(angle) * starSize).toFloat()
                        if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
                        
                        val innerAngle = Math.toRadians((i * 90.0))
                        val innerX = point.x + (cos(innerAngle) * starSize * 0.3f).toFloat()
                        val innerY = point.y + (sin(innerAngle) * starSize * 0.3f).toFloat()
                        starPath.lineTo(innerX, innerY)
                    }
                    starPath.close()
                    
                    drawPath(
                        path = starPath,
                        color = goldLight.copy(alpha = alpha)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                hierarchy.secondary.primary.copy(alpha = 0.2f),
                                GlassSurface
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Coming Soon",
                color = hierarchy.secondary.primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = feature,
                color = TextPrimary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "We're working hard to bring you this feature",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
