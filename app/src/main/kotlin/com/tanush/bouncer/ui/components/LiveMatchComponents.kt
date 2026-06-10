package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun AnimatedBallCircle(
    ball: String,
    isNewest: Boolean,
    index: Int
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "ballGlow")
    
    // Entry animation
    val entryScale by animateFloatAsState(
        targetValue = if (isNewest) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "entryScale"
    )
    
    // Glow animation for special balls
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val isSpecialBall = ball in listOf("W", "4", "6", "WD", "NB")
    
    val (bgColor, textColor, glowColor) = when (ball) {
        "W" -> Triple(StatusInfo, Color.White, StatusInfo)
        "4" -> Triple(hierarchy.secondary.primary, BackgroundBase, hierarchy.secondary.primary)
        "6" -> Triple(hierarchy.secondary.primary, BackgroundBase, hierarchy.secondary.primary)
        "WD" -> Triple(Color(0xFFF59E0B), BackgroundBase, Color(0xFFF59E0B))
        "NB" -> Triple(Color(0xFFF59E0B), BackgroundBase, Color(0xFFF59E0B))
        else -> Triple(Color.White.copy(alpha = 0.08f), TextSecondary, Color.Transparent)
    }

    Box(
        modifier = Modifier
            .graphicsLayer { 
                scaleX = entryScale
                scaleY = entryScale
            }
    ) {
        // Outer glow for special balls
        if (isSpecialBall) {
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isNewest) glowAlpha else 0.2f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2
                )
            }
        }
        
        // Main ball
        Box(
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.Center)
                .shadow(
                    elevation = if (isSpecialBall) 8.dp else 2.dp,
                    shape = CircleShape,
                    spotColor = bgColor.copy(alpha = 0.5f),
                    ambientColor = bgColor.copy(alpha = 0.3f)
                )
                .background(bgColor, CircleShape)
                .then(
                    if (isNewest) Modifier.border(
                        2.dp,
                        hierarchy.secondary.primary,
                        CircleShape
                    ) else if (!isSpecialBall) Modifier.border(
                        0.5.dp,
                        GlassBorder.copy(alpha = 0.3f),
                        CircleShape
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ball,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }
    }
}

/**
 * Animated run rate display with trend indicator
 */
@Composable
fun AnimatedRunRate(
    currentRunRate: Float,
    requiredRunRate: Float?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "runRate")
    
    val trendArrowOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trendArrow"
    )
    
    // Animated value display
    var displayedCRR by remember { mutableFloatStateOf(0f) }
    var displayedRRR by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(currentRunRate) {
        val start = displayedCRR
        val change = currentRunRate - start
        for (i in 0..20) {
            displayedCRR = start + (change * i / 20)
            delay(15)
        }
        displayedCRR = currentRunRate
    }
    
    LaunchedEffect(requiredRunRate) {
        if (requiredRunRate != null) {
            val start = displayedRRR
            val change = requiredRunRate - start
            for (i in 0..20) {
                displayedRRR = start + (change * i / 20)
                delay(15)
            }
            displayedRRR = requiredRunRate
        }
    }
    
    val isOnTrack = requiredRunRate == null || currentRunRate >= requiredRunRate
    val trendColor = if (isOnTrack) hierarchy.secondary.primary else StatusInfo

    Row(
        modifier = modifier
            .background(
                colors.glassSurface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CRR
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CRR",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Text(
                String.format("%.2f", displayedCRR),
                color = hierarchy.secondary.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }
        
        // Trend indicator
        if (requiredRunRate != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(trendColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isOnTrack) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { translationY = if (isOnTrack) -trendArrowOffset else trendArrowOffset }
                )
            }
            
            // RRR
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "RRR",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Text(
                    String.format("%.2f", displayedRRR),
                    color = if (isOnTrack) colors.textPrimary else StatusInfo,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }
    }
}

/**
 * Live indicator with pulsing animation
 */
@Composable
fun LiveIndicator(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLive) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .background(
                Color(0xFFDC2626).copy(alpha = 0.12f),
                RoundedCornerShape(6.dp)
            )
            .border(
                0.5.dp,
                Color(0xFFDC2626).copy(alpha = 0.3f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { 
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(Color(0xFFDC2626), CircleShape)
        )
        Text(
            "LIVE",
            color = Color(0xFFDC2626),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Animated partnership display
 */
@Composable
fun PartnershipDisplay(
    runs: Int,
    balls: Int,
    batsman1: String,
    batsman2: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var displayedRuns by remember { mutableStateOf(0) }
    
    LaunchedEffect(runs) {
        val start = displayedRuns
        val change = runs - start
        if (change != 0) {
            for (i in 0..20) {
                displayedRuns = start + (change * i / 20)
                delay(20)
            }
            displayedRuns = runs
        }
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "PARTNERSHIP",
                color = colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "$displayedRuns",
                    color = hierarchy.secondary.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    "($balls)",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    batsman1,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, hierarchy.secondary.primary.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            RoundedCornerShape(50)
                        )
                )
                Text(
                    batsman2,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Bowling figures display with animation
 */
@Composable
fun BowlingFiguresDisplay(
    bowlerName: String,
    overs: String,
    maidens: Int,
    runs: Int,
    wickets: Int,
    economy: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "bowling")
    
    val wicketGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wicketGlow"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    bowlerName,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Wickets highlight
                Box(
                    modifier = Modifier
                        .background(
                            colors.statusInfo.copy(alpha = wicketGlow),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$wickets-$runs",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Overs", overs)
                StatItem("Maidens", maidens.toString())
                StatItem("Economy", String.format("%.2f", economy))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = colors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
