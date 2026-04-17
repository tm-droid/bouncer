package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import com.tanush.bouncer.ui.theme.GoldPrimary

@Composable
fun AnimatedCard(
    visible: Boolean,
    delay: Long = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible) {
        if (visible) {
            delay(delay)
            isVisible = true
        } else {
            isVisible = false
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 3 }
        ) + scaleIn(
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            initialScale = 0.92f
        ),
        exit = fadeOut(
            animationSpec = tween(300, easing = LinearOutSlowInEasing)
        ) + slideOutVertically(
            animationSpec = tween(300, easing = LinearOutSlowInEasing),
            targetOffsetY = { -it / 3 }
        ) + scaleOut(
            animationSpec = tween(300, easing = LinearOutSlowInEasing),
            targetScale = 0.92f
        )
    ) {
        content()
    }
}

@Composable
fun SuccessToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(2000)
            onDismiss()
        }
    }
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.statusSuccess,
                                colors.statusSuccess.copy(alpha = 0.9f)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text(
                    text = message,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

@Composable
fun ErrorToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(2500)
            onDismiss()
        }
    }
    
    val colors = BouncerDesignSystem.colors
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { -it }) + scaleIn(initialScale = 0.92f),
        exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.statusLive,
                                colors.statusLive.copy(alpha = 0.9f)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text(
                    text = message,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

@Composable
fun PulsingBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.statusInfo,
                        colors.statusInfo.copy(alpha = 0.85f)
                    )
                ),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/**
 * Animated score change - flashes and scales when score updates
 */
@Composable
fun AnimatedScore(
    score: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary,
    fontSize: Float = 28f
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var previousScore by remember { mutableStateOf(score) }
    var isAnimating by remember { mutableStateOf(false) }
    
    LaunchedEffect(score) {
        if (score != previousScore) {
            isAnimating = true
            delay(400)
            isAnimating = false
            previousScore = score
        }
    }
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isAnimating) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scoreScale"
    )
    
    val animatedColor by animateColorAsState(
        targetValue = if (isAnimating) hierarchy.secondary.primary else textColor,
        animationSpec = tween(300),
        label = "scoreColor"
    )
    
    Text(
        text = score,
        modifier = modifier.scale(animatedScale),
        color = animatedColor,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp
    )
}

/**
 * Bouncing arrow indicator for expandable sections
 */
@Composable
fun BouncingArrow(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = GoldPrimary
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "arrowBounce")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .offset(y = if (!isExpanded) bounce.dp else 0.dp)
            .scale(1f)
    ) {
        // Arrow icon would go here - using text for now
        Text(
            text = "▼",
            color = tint,
            fontSize = 12.sp,
            modifier = Modifier
                .scale(scaleX = 1f, scaleY = if (isExpanded) -1f else 1f)
        )
    }
}

/**
 * Shimmer loading text placeholder
 */
@Composable
fun ShimmerText(
    width: Int = 100,
    height: Int = 16,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerText")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    Box(
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.05f)
                    ),
                    startX = shimmerOffset * 300f - 100f,
                    endX = shimmerOffset * 300f + 100f
                ),
                RoundedCornerShape(height.dp / 2)
            )
    )
}

/**
 * Fade transition wrapper with staggered delay support
 */
@Composable
fun StaggeredFadeIn(
    index: Int,
    baseDelay: Long = 50L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(baseDelay * index)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it / 4 }
        ),
        exit = fadeOut(tween(200))
    ) {
        content()
    }
}
