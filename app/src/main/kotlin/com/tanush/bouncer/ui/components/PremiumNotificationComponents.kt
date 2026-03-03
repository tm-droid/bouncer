package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Premium snackbar types
 */
enum class SnackbarType {
    SUCCESS, ERROR, WARNING, INFO, LIVE
}

/**
 * Premium snackbar with animations and styling
 */
@Composable
fun PremiumSnackbar(
    message: String,
    type: SnackbarType,
    visible: Boolean,
    onDismiss: () -> Unit,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    duration: Long = 3000L,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "snackbar")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Auto dismiss
    LaunchedEffect(visible) {
        if (visible) {
            delay(duration)
            onDismiss()
        }
    }

    val accentColor = when (type) {
        SnackbarType.SUCCESS -> ChartGreen
        SnackbarType.ERROR -> StatusLive
        SnackbarType.WARNING -> GoldPrimary
        SnackbarType.INFO -> StatusInfo
        SnackbarType.LIVE -> BoundarySix
    }
    
    val icon = when (type) {
        SnackbarType.SUCCESS -> Icons.Default.CheckCircle
        SnackbarType.ERROR -> Icons.Default.Error
        SnackbarType.WARNING -> Icons.Default.Warning
        SnackbarType.INFO -> Icons.Default.Info
        SnackbarType.LIVE -> Icons.Default.Circle
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(200)
        ) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GlassSurface,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.6f),
                        accentColor.copy(alpha = 0.2f)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = accentColor.copy(alpha = 0.3f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon with glow
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            accentColor.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (type == SnackbarType.LIVE) {
                        // Pulsing live indicator
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { alpha = pulseAlpha }
                                .background(accentColor, CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                // Message
                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Action button
                if (action != null && onAction != null) {
                    Text(
                        text = action.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clickable(onClick = onAction)
                            .padding(8.dp)
                    )
                }
                
                // Dismiss button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(GlassBorder.copy(alpha = 0.2f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Mini notification badge for icons
 */
@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                BoundarySix,
                CircleShape
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.sp
        )
    }
}

/**
 * Floating action button with premium styling
 */
@Composable
fun PremiumFAB(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String = "",
    extended: Boolean = false,
    text: String = "",
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "fabScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabGlow"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = glowAlpha }
                .blur(16.dp)
                .background(
                    GoldPrimary.copy(alpha = 0.5f),
                    if (extended) RoundedCornerShape(28.dp) else CircleShape
                )
        )
        
        Surface(
            shape = if (extended) RoundedCornerShape(28.dp) else CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                GoldPrimary,
                                GoldPrimary,
                                GoldDark
                            )
                        ),
                        if (extended) RoundedCornerShape(28.dp) else CircleShape
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (extended) 8.dp else 0.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = BackgroundBase,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    if (extended && text.isNotEmpty()) {
                        Text(
                            text = text.uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BackgroundBase,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated counter for scores
 */
@Composable
fun AnimatedScoreCounter(
    score: Int,
    label: String = "",
    textSize: Int = 32,
    modifier: Modifier = Modifier
) {
    var previousScore by remember { mutableStateOf(score) }
    var isAnimating by remember { mutableStateOf(false) }
    
    LaunchedEffect(score) {
        if (score != previousScore) {
            isAnimating = true
            delay(300)
            isAnimating = false
            previousScore = score
        }
    }
    
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "scoreAnim"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scoreScale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = tween(300),
        label = "scoreGlow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box {
            // Glow effect when animating
            if (glowAlpha > 0) {
                Text(
                    text = animatedScore.toString(),
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary.copy(alpha = glowAlpha),
                    modifier = Modifier
                        .blur(8.dp)
                        .graphicsLayer { alpha = glowAlpha }
                )
            }
            
            Text(
                text = animatedScore.toString(),
                fontSize = textSize.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
        
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Bouncy press effect wrapper
 */
@Composable
fun BouncyPressEffect(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bounceScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        content()
    }
}

/**
 * Shimmer loading effect that wraps content
 */
@Composable
fun ShimmerLoadingEffect(
    isLoading: Boolean,
    contentAfterLoading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(modifier = modifier) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                GlassSurface,
                                GlassBorderHighlight,
                                GlassSurface
                            ),
                            start = Offset(shimmerOffset * 300, 0f),
                            end = Offset((shimmerOffset + 1) * 300, 0f)
                        )
                    )
            )
        } else {
            contentAfterLoading()
        }
    }
}

/**
 * Pulse ring animation for live indicators
 */
@Composable
fun PulseRingIndicator(
    color: Color = BoundarySix,
    size: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        // Expanding ring
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(color, CircleShape)
        )
        
        // Center dot
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

/**
 * Confetti particle effect for celebrations
 */
@Composable
fun ConfettiEffect(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val particles = remember { (0..30).map { SimpleConfettiParticle() } }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            drawCircle(
                color = particle.color,
                radius = particle.size.toPx(),
                center = Offset(
                    particle.x * size.width,
                    particle.y * size.height
                )
            )
        }
    }
}

private data class SimpleConfettiParticle(
    val x: Float = (0..100).random() / 100f,
    val y: Float = (0..100).random() / 100f,
    val size: Dp = ((4..12).random()).dp,
    val color: Color = listOf(
        GoldPrimary,
        GoldLight,
        ChartGreen,
        StatusInfo,
        BoundarySix
    ).random()
)
