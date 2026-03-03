package com.tanush.bouncer.ui.components.celebrations

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * Full-screen century explosion celebration overlay
 */
@Composable
fun CenturyExplosion(
    playerName: String,
    runs: Int = 100,
    balls: Int? = null,
    isVisible: Boolean = true,
    onDismiss: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showContent = true
            delay(4000L)
            showContent = false
            delay(500L)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(200)) + scaleIn(tween(400), initialScale = 0.5f),
        exit = fadeOut(tween(400)) + scaleOut(tween(300), targetScale = 1.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            // Confetti layer
            CenturyConfetti(isActive = showContent)
            
            // Radial glow effect
            RadialGlowEffect(
                color = colors.goldPrimary,
                modifier = Modifier.fillMaxSize()
            )
            
            // Central content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated "100" text
                AnimatedCenturyNumber()
                
                // Player name
                Text(
                    text = playerName.uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    letterSpacing = 4.sp
                )
                
                // Stats
                balls?.let {
                    Text(
                        text = "$runs runs off $balls balls",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
                
                // Strike rate badge
                balls?.let {
                    val strikeRate = (runs.toFloat() / balls * 100).toInt()
                    StrikeRateBadge(strikeRate = strikeRate)
                }
            }
        }
    }
}

@Composable
private fun AnimatedCenturyNumber() {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "century")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centuryScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer { 
            scaleX = scale
            scaleY = scale
        }
    ) {
        // Glow
        Text(
            text = "100",
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = colors.goldPrimary.copy(alpha = glowAlpha * 0.3f),
            modifier = Modifier.blur(20.dp)
        )
        
        // Main text
        Text(
            text = "100",
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            style = LocalTextStyle.current.copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.goldLight,
                        colors.goldPrimary,
                        colors.goldPrimary,
                        colors.goldPrimary
                    )
                )
            )
        )
    }
}

@Composable
private fun StrikeRateBadge(strikeRate: Int) {
    val colors = BouncerDesignSystem.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        colors.goldPrimary.copy(alpha = 0.3f),
                        colors.goldPrimary.copy(alpha = 0.3f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, colors.goldPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "SR: $strikeRate",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.goldPrimary
        )
    }
}

@Composable
private fun RadialGlowEffect(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.minDimension * pulseScale
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.3f),
                    color.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius
            ),
            radius = maxRadius,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Five wicket haul celebration animation
 */
@Composable
fun FiveWicketHaulCelebration(
    bowlerName: String,
    wickets: Int = 5,
    runs: Int,
    overs: String,
    isVisible: Boolean = true,
    onDismiss: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showContent = true
            delay(4000L)
            showContent = false
            delay(500L)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(200)) + scaleIn(tween(400), initialScale = 0.5f),
        exit = fadeOut(tween(400)) + scaleOut(tween(300), targetScale = 1.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            // Wicket confetti
            WicketConfetti(wicketCount = wickets, isActive = showContent)
            
            // Purple glow for 5-fer
            RadialGlowEffect(
                color = ChartPurple,
                modifier = Modifier.fillMaxSize()
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated wicket count
                AnimatedWicketNumber(wickets = wickets)
                
                Text(
                    text = "WICKETS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ChartPurple,
                    letterSpacing = 8.sp
                )
                
                Text(
                    text = bowlerName.uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    letterSpacing = 4.sp
                )
                
                // Bowling figures
                BowlingFiguresBadge(wickets = wickets, runs = runs, overs = overs)
            }
        }
    }
}

@Composable
private fun AnimatedWicketNumber(wickets: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "wickets")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wicketScale"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer { 
            scaleX = scale
            scaleY = scale
        }
    ) {
        Text(
            text = wickets.toString(),
            fontSize = 140.sp,
            fontWeight = FontWeight.Black,
            color = ChartPurple.copy(alpha = 0.3f),
            modifier = Modifier.blur(20.dp)
        )
        
        Text(
            text = wickets.toString(),
            fontSize = 140.sp,
            fontWeight = FontWeight.Black,
            style = LocalTextStyle.current.copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ChartPurple,
                        SixerPurple,
                        ChartPurple
                    )
                )
            )
        )
    }
}

@Composable
private fun BowlingFiguresBadge(wickets: Int, runs: Int, overs: String) {
    val colors = BouncerDesignSystem.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ChartPurple.copy(alpha = 0.3f),
                        SixerPurple.copy(alpha = 0.3f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, ChartPurple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$wickets/$runs ($overs)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
    }
}

/**
 * Match win fireworks celebration
 */
@Composable
fun MatchWinFireworks(
    winningTeam: String,
    marginText: String,
    teamColor: Color = StatusSuccess,
    isVisible: Boolean = true,
    onDismiss: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showContent = true
            delay(5000L)
            showContent = false
            delay(500L)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            // Fireworks layer
            FireworksDisplay(
                isActive = showContent,
                modifier = Modifier.fillMaxSize()
            )
            
            // Confetti
            ConfettiRain(
                particleCount = 100,
                colors = listOf(teamColor, colors.goldPrimary, Color.White),
                duration = 4500L,
                isActive = showContent
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Trophy icon
                AnimatedTrophy(color = teamColor)
                
                Text(
                    text = "VICTORY!",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    letterSpacing = 6.sp
                )
                
                Text(
                    text = winningTeam.uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = teamColor,
                    letterSpacing = 3.sp
                )
                
                Text(
                    text = marginText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AnimatedTrophy(color: Color) {
    val dsColors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "trophy")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophyBounce"
    )
    
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophyGlow"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.offset { IntOffset(0, -bounce.toInt()) }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            dsColors.goldPrimary.copy(alpha = glow * 0.5f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Trophy",
                tint = dsColors.goldPrimary,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@Composable
private fun FireworksDisplay(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val fireworkColors = listOf(
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFE66D),
        Color(0xFFFF9FF3),
        Color(0xFF54A0FF),
        GoldPrimary
    )
    
    var fireworks by remember { mutableStateOf<List<FireworkState>>(emptyList()) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            // Launch fireworks at intervals
            repeat(8) { i ->
                delay(i * 400L)
                fireworks = fireworks + FireworkState(
                    x = Random.nextFloat(),
                    y = Random.nextFloat() * 0.5f + 0.1f,
                    color = fireworkColors[i % fireworkColors.size],
                    startTime = System.currentTimeMillis()
                )
            }
        }
    }
    
    Canvas(modifier = modifier) {
        val currentTime = System.currentTimeMillis()
        
        fireworks.forEach { firework ->
            val elapsed = (currentTime - firework.startTime).toFloat()
            val progress = (elapsed / 1500f).coerceIn(0f, 1f)
            
            if (progress < 1f) {
                val centerX = firework.x * size.width
                val centerY = firework.y * size.height
                
                // Draw explosion particles
                val particleCount = 24
                repeat(particleCount) { i ->
                    val angle = (i.toFloat() / particleCount) * 2 * PI.toFloat()
                    val distance = progress * 100f
                    val particleX = centerX + cos(angle) * distance
                    val particleY = centerY + sin(angle) * distance + progress * 30f // gravity
                    val alpha = 1f - progress
                    val particleSize = 4f * (1f - progress * 0.5f)
                    
                    drawCircle(
                        color = firework.color.copy(alpha = alpha),
                        radius = particleSize,
                        center = Offset(particleX, particleY)
                    )
                    
                    // Trail
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                firework.color.copy(alpha = alpha * 0.5f),
                                Color.Transparent
                            ),
                            center = Offset(particleX, particleY),
                            radius = particleSize * 3
                        ),
                        radius = particleSize * 3,
                        center = Offset(particleX, particleY)
                    )
                }
            }
        }
    }
}

private data class FireworkState(
    val x: Float,
    val y: Float,
    val color: Color,
    val startTime: Long
)

/**
 * Record breaker animated sliding banner
 */
@Composable
fun RecordBreakerBanner(
    recordType: String,
    description: String,
    isVisible: Boolean = true,
    onDismiss: () -> Unit
) {
    var showBanner by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showBanner = true
            delay(4000L)
            showBanner = false
            delay(500L)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = showBanner,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.6f)
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(400)
        ) + fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            RecordBannerCard(recordType = recordType, description = description)
        }
    }
}

@Composable
private fun RecordBannerCard(recordType: String, description: String) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "record")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.5f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            2.dp,
            Brush.linearGradient(
                colors = listOf(
                    colors.goldPrimary.copy(alpha = 0.8f),
                    colors.goldPrimary.copy(alpha = 0.4f),
                    colors.goldPrimary.copy(alpha = 0.8f)
                ),
                start = Offset(shimmerOffset * 400, 0f),
                end = Offset(shimmerOffset * 400 + 400, 0f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Record icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "RECORD BROKEN!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.goldPrimary,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = recordType,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                
                Text(
                    text = description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }
        }
    }
}
/**
 * Hat-trick special celebration animation
 */
@Composable
fun HatTrickCelebration(
    bowlerName: String,
    isVisible: Boolean = true,
    onDismiss: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    var showContent by remember { mutableStateOf(false) }
    var showBall1 by remember { mutableStateOf(false) }
    var showBall2 by remember { mutableStateOf(false) }
    var showBall3 by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showContent = true
            delay(300L)
            showBall1 = true
            delay(400L)
            showBall2 = true
            delay(400L)
            showBall3 = true
            delay(2500L)
            showContent = false
            delay(500L)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            // Purple confetti for hat-trick
            WicketConfetti(wicketCount = 3, isActive = showContent)
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "HAT-TRICK!",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    style = LocalTextStyle.current.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(ChartPurple, SixerPurple, ChartPurple)
                        )
                    ),
                    letterSpacing = 6.sp
                )
                
                // Three balls animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AnimatedBallIcon(visible = showBall1, delay = 0)
                    AnimatedBallIcon(visible = showBall2, delay = 100)
                    AnimatedBallIcon(visible = showBall3, delay = 200)
                }
                
                Text(
                    text = bowlerName.uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = 4.sp
                )
                
                Text(
                    text = "3 WICKETS IN 3 BALLS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChartPurple
                )
            }
        }
    }
}

@Composable
private fun AnimatedBallIcon(visible: Boolean, delay: Int) {
    val colors = BouncerDesignSystem.colors
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(tween(200, delayMillis = delay))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(colors.statusLive, colors.statusLive.copy(alpha = 0.8f))
                    ),
                    CircleShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        ) {
            Text(
                text = "W",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary
            )
        }
    }
}

/**
 * Animated milestone badge appearance
 */
@Composable
fun MilestoneBadge(
    milestone: String,
    description: String,
    icon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(24.dp)
        )
    },
    badgeColor: Color = GoldPrimary,
    isVisible: Boolean = true,
    onDismiss: () -> Unit = {}
) {
    var showBadge by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showBadge = true
            delay(3500L)
            showBadge = false
            delay(400L)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = showBadge,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialScale = 0.3f
        ) + fadeIn(tween(200)),
        exit = scaleOut(tween(300), targetScale = 1.5f) + fadeOut(tween(250))
    ) {
        MilestoneBadgeCard(
            milestone = milestone,
            description = description,
            icon = icon,
            badgeColor = badgeColor
        )
    }
}

@Composable
private fun MilestoneBadgeCard(
    milestone: String,
    description: String,
    icon: @Composable () -> Unit,
    badgeColor: Color
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Card(
        modifier = Modifier
            .padding(32.dp)
            .shadow(
                elevation = 32.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = badgeColor.copy(alpha = glowAlpha)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, badgeColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon with glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                badgeColor.copy(alpha = glowAlpha),
                                badgeColor.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            badgeColor.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .border(1.dp, badgeColor.copy(alpha = 0.5f), CircleShape)
                ) {
                    icon()
                }
            }
            
            Text(
                text = milestone,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = badgeColor,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
