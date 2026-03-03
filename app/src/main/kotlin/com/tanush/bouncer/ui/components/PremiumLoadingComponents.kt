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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium shimmer effect brush
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    showShimmer: Boolean = true
): Brush {
    val colors = BouncerDesignSystem.colors
    return if (showShimmer) {
        val shimmerColors = listOf(
            Surface1.copy(alpha = 0.3f),
            colors.goldPrimary.copy(alpha = 0.15f),
            Surface1.copy(alpha = 0.3f)
        )
        
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translate"
        )
        
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 200f, translateAnimation - 200f),
            end = Offset(translateAnimation, translateAnimation)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    }
}

/**
 * Premium Shimmer Box with shape support
 */
@Composable
fun PremiumShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val brush = shimmerBrush()
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(Surface1)
            .background(brush)
    )
}

/**
 * Premium Match Card Skeleton
 */
@Composable
fun PremiumMatchCardSkeleton(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.08f),
                ambientColor = Color.Black.copy(alpha = 0.2f)
            ),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumShimmerBox(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                PremiumShimmerBox(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Teams section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumShimmerBox(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        PremiumShimmerBox(
                            modifier = Modifier
                                .width(80.dp)
                                .height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PremiumShimmerBox(
                            modifier = Modifier
                                .width(50.dp)
                                .height(20.dp)
                        )
                    }
                }
                
                // VS
                PremiumShimmerBox(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape
                )
                
                // Team 2
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        PremiumShimmerBox(
                            modifier = Modifier
                                .width(80.dp)
                                .height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PremiumShimmerBox(
                            modifier = Modifier
                                .width(50.dp)
                                .height(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    PremiumShimmerBox(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status bar
            PremiumShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

/**
 * Commentary Skeleton
 */
@Composable
fun CommentarySkeleton(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.05f)
            ),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            repeat(5) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PremiumShimmerBox(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        PremiumShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(12.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PremiumShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 1f else 0.7f)
                                .height(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Statistics Graph Skeleton
 */
@Composable
fun GraphSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 200.dp
) {
    val colors = BouncerDesignSystem.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.08f)
            ),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PremiumShimmerBox(
                    modifier = Modifier
                        .width(120.dp)
                        .height(18.dp)
                )
                PremiumShimmerBox(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Graph area
            PremiumShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * Premium Pulsing Loader
 */
@Composable
fun PulsingLoader(
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.colors.goldPrimary,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = 1f - alpha
                }
                .border(
                    width = 2.dp,
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0f))
                    ),
                    shape = CircleShape
                )
        )
        
        // Inner dot
        Box(
            modifier = Modifier
                .size(size * 0.4f)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0.5f))
                    ),
                    CircleShape
                )
        )
    }
}

/**
 * Cricket Ball Spinner
 */
@Composable
fun CricketBallSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val colors = BouncerDesignSystem.colors
    val goldLight = colors.goldLight
    val goldPrimary = colors.goldPrimary
    val infiniteTransition = rememberInfiniteTransition(label = "ball_spin")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ball_rotation"
    )
    
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ball_bounce"
    )
    
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                translationY = -bounce * 12f
            }
    ) {
        val center = Offset(size.toPx() / 2, size.toPx() / 2)
        val radius = size.toPx() / 2 - 4.dp.toPx()
        
        // Ball body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    goldLight,
                    goldPrimary
                ),
                center = center.copy(
                    x = center.x - radius * 0.2f,
                    y = center.y - radius * 0.2f
                )
            ),
            radius = radius,
            center = center
        )
        
        // Seam
        val seamPath = Path().apply {
            // Draw curved seam
            val startAngle = 30f
            val endAngle = 150f
            moveTo(
                center.x + radius * 0.6f * cos(startAngle * PI.toFloat() / 180f),
                center.y + radius * 0.6f * sin(startAngle * PI.toFloat() / 180f)
            )
            for (angle in startAngle.toInt()..endAngle.toInt() step 5) {
                val rad = angle * PI.toFloat() / 180f
                val r = radius * 0.6f * (1f + 0.1f * sin(angle * 6f * PI.toFloat() / 180f))
                lineTo(
                    center.x + r * cos(rad),
                    center.y + r * sin(rad)
                )
            }
        }
        
        drawPath(
            path = seamPath,
            color = Color.White.copy(alpha = 0.8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
        
        // Shine
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = center.copy(
                    x = center.x - radius * 0.4f,
                    y = center.y - radius * 0.4f
                ),
                radius = radius * 0.5f
            ),
            radius = radius * 0.5f,
            center = center.copy(
                x = center.x - radius * 0.3f,
                y = center.y - radius * 0.3f
            )
        )
    }
}

/**
 * Dot Wave Loader
 */
@Composable
fun DotWaveLoader(
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.colors.goldPrimary,
    dotCount: Int = 5,
    dotSize: Dp = 10.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val delay = index * 100
            
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = offsetY
                        this.alpha = alpha
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Loading Overlay with blur
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(PrimaryBackground.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CricketBallSpinner()
                    Spacer(modifier = Modifier.height(24.dp))
                    DotWaveLoader()
                }
            }
        }
    }
}

/**
 * Full Screen Loading View
 */
@Composable
fun FullScreenLoader(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CricketBallSpinner(size = 72.dp)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            androidx.compose.material3.Text(
                text = message,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DotWaveLoader(
                color = colors.goldPrimary,
                dotCount = 3,
                dotSize = 8.dp
            )
        }
    }
}

/**
 * Pull to Refresh Indicator
 */
@Composable
fun PremiumRefreshIndicator(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val goldPrimary = colors.goldPrimary
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = if (isRefreshing) {
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(300)
        },
        label = "refresh_rotation"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        goldPrimary.copy(alpha = 0.2f),
                        goldPrimary.copy(alpha = 0.05f)
                    )
                ),
                CircleShape
            )
            .border(1.dp, goldPrimary.copy(alpha = 0.3f), CircleShape)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Refreshing",
            tint = goldPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}
