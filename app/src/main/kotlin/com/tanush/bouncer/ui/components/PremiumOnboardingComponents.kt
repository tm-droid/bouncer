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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium splash screen with animated logo
 */
@Composable
fun PremiumSplashScreen(
    onComplete: () -> Unit
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animationStarted = true
        kotlinx.coroutines.delay(3000)
        onComplete()
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val fadeIn by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "fadeIn"
    )
    
    val slideUp by animateDpAsState(
        targetValue = if (animationStarted) 0.dp else 50.dp,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "slideUp"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground),
        contentAlignment = Alignment.Center
    ) {
        // Background effects
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Rotating gold rings
            rotate(rotation, pivot = center) {
                for (i in 0..2) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                ShinyGoldDeep.copy(alpha = 0.1f - i * 0.03f),
                                Color.Transparent,
                                ShinyGoldLight.copy(alpha = 0.15f - i * 0.03f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension * (0.3f + i * 0.15f),
                        center = center,
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
            
            // Radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ShinyGoldDeep.copy(alpha = 0.08f * pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.4f
                ),
                radius = size.minDimension * 0.4f,
                center = center
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    alpha = fadeIn
                    translationY = slideUp.toPx()
                }
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .drawBehind {
                        // Outer glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = 0.4f),
                                    ShinyGoldDeep.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.05f),
                                CardDarkBlue
                            )
                        ),
                        CircleShape
                    )
                    .border(
                        2.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                ShinyGoldLight,
                                ShinyGoldDeep,
                                ShinyGoldDark
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Cricket ball seam lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val seamColor = ShinyGoldDeep
                    
                    // Main vertical seam
                    drawArc(
                        color = seamColor,
                        startAngle = -30f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                        size = Size(size.width * 0.7f, size.height * 0.7f),
                        style = Stroke(2.dp.toPx())
                    )
                    
                    drawArc(
                        color = seamColor,
                        startAngle = 150f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                        size = Size(size.width * 0.7f, size.height * 0.7f),
                        style = Stroke(2.dp.toPx())
                    )
                }
                
                Text(
                    text = "B",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = ShinyGoldDeep
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name
            Text(
                text = "BOUNCER",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Live Cricket Experience",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = ShinyGoldDeep
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Loading indicator
            PremiumLoadingDots()
        }
    }
}

@Composable
private fun PremiumLoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 200
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotScale$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(
                        ShinyGoldDeep.copy(alpha = alpha),
                        CircleShape
                    )
            )
        }
    }
}

/**
 * Quick filter bar with animation
 */
@Composable
fun QuickFilterBar(
    filters: List<QuickFilter>,
    selectedFilter: QuickFilter?,
    onFilterSelected: (QuickFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "filterBar")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        filters.forEach { filter ->
            QuickFilterChip(
                filter = filter,
                isSelected = selectedFilter == filter,
                shimmerOffset = shimmerOffset,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

data class QuickFilter(
    val id: String,
    val label: String,
    val count: Int = 0
)

@Composable
private fun QuickFilterChip(
    filter: QuickFilter,
    isSelected: Boolean,
    shimmerOffset: Float,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chipScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) ShinyGoldDeep.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        animationSpec = tween(200),
        label = "chipBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ShinyGoldDeep.copy(alpha = 0.6f) else CardBorder.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "chipBorder"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) ShinyGoldDeep else TextSecondary,
        animationSpec = tween(200),
        label = "chipText"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .then(
                    if (isSelected) {
                        Modifier.drawWithContent {
                            drawContent()
                            val start = shimmerOffset * size.width * 2 - size.width
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.1f),
                                        Color.Transparent
                                    ),
                                    start = Offset(start, 0f),
                                    end = Offset(start + size.width * 0.5f, size.height)
                                )
                            )
                        }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = filter.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor
            )
            
            if (filter.count > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) ShinyGoldDeep.copy(alpha = 0.2f) else CardBorder.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = filter.count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Premium empty state with animation
 */
@Composable
fun PremiumEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Floating icon
        Box(
            modifier = Modifier
                .offset(y = (-float).dp)
                .size(100.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ShinyGoldDeep.copy(alpha = glowPulse),
                                Color.Transparent
                            )
                        ),
                        radius = size.maxDimension * 0.8f
                    )
                }
                .background(
                    Color.White.copy(alpha = 0.05f),
                    CircleShape
                )
                .border(
                    1.dp,
                    ShinyGoldDeep.copy(alpha = 0.3f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsBaseball,
                contentDescription = null,
                tint = ShinyGoldDeep,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        
        action?.invoke()
    }
}

/**
 * Premium floating action menu
 */
@Composable
fun PremiumQuickActionsMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    actions: List<OnboardingQuickAction>,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fabRotation"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabGlow"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action items
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(150, delayMillis = index * 50)) +
                        scaleIn(tween(200, delayMillis = index * 50), initialScale = 0.5f) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(200, delayMillis = index * 50)
                        ),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.5f)
            ) {
                OnboardingQuickActionItem(action = action, onToggle = onToggle)
            }
        }
        
        // Main FAB
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Glow
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension
                        )
                    }
            )
            
            FloatingActionButton(
                onClick = onToggle,
                containerColor = ShinyGoldDeep,
                contentColor = BlackBackground,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = ShinyGoldDeep.copy(alpha = 0.5f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Menu",
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

data class OnboardingQuickAction(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color = ShinyGoldDeep,
    val onClick: () -> Unit
)

@Composable
private fun OnboardingQuickActionItem(
    action: OnboardingQuickAction,
    onToggle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp
        ) {
            Text(
                text = action.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        
        // Mini FAB
        FloatingActionButton(
            onClick = {
                onToggle()
                action.onClick()
            },
            containerColor = action.color,
            contentColor = BlackBackground,
            shape = CircleShape,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
