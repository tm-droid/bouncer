package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium splash/loading screen with cricket-themed animations
 */
@Composable
fun PremiumLoadingScreen(
    isLoading: Boolean,
    loadingProgress: Float = 0f,
    loadingMessage: String = "Loading elite analytics..."
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    val ballRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ballRotation"
    )
    
    val logoPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulse"
    )
    
    // Capture colors for Canvas/drawBehind scopes
    val goldPrimaryColor = colors.goldPrimary
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.backgroundBase
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background ambient glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    goldPrimaryColor.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width,
                            center = Offset(size.width / 2, size.height / 2)
                        )
                    }
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated Icon
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Rotating outer ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(ballRotation) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        goldPrimaryColor.copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                ),
                                startAngle = 0f,
                                sweepAngle = 280f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                    
                    // Center ball
                    Icon(
                        painter = painterResource(com.tanush.bouncer.R.drawable.ic_cricket_ball),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = logoPulse
                                scaleY = logoPulse
                            },
                        tint = colors.goldPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "BOUNCER",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = colors.goldDark,
                    modifier = Modifier.graphicsLayer { alpha = logoPulse }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "ELITE CRICKET ANALYTICS",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                
                Spacer(modifier = Modifier.height(60.dp))
                
                // Loading bar
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.surface1)
                    ) {
                        val progressWidth by animateFloatAsState(
                            targetValue = if (isLoading) 1f else loadingProgress,
                            animationSpec = if (isLoading) infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ) else tween(500),
                            label = "progress"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isLoading) 0.3f else progressWidth)
                                .fillMaxHeight()
                                .then(
                                    if (isLoading) Modifier.graphicsLayer { 
                                        translationX = (progressWidth * 180).dp.toPx() 
                                    } else Modifier
                                )
                                .background(colors.goldPrimary)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = loadingMessage.uppercase(),
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
/**
 * Enhanced Skeleton loader for match cards
 */
@Composable
private fun SkeletonTeam(isRight: Boolean = false) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = if (isRight) Alignment.End else Alignment.Start) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.surface1, CircleShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(14.dp)
                .background(colors.surface1, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(24.dp)
                .background(colors.surface1, RoundedCornerShape(4.dp))
        )
    }
}
