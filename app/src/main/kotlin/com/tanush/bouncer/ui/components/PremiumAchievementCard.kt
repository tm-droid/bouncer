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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Achievement rarity levels
 */
enum class AchievementRarity(
    val primaryColor: Color,
    val secondaryColor: Color,
    val glowColor: Color,
    val label: String
) {
    COMMON(
        primaryColor = Color(0xFF9CA3AF),
        secondaryColor = Color(0xFF6B7280),
        glowColor = Color(0xFF9CA3AF),
        label = "Common"
    ),
    RARE(
        primaryColor = Color(0xFF3B82F6),
        secondaryColor = Color(0xFF1D4ED8),
        glowColor = Color(0xFF3B82F6),
        label = "Rare"
    ),
    EPIC(
        primaryColor = Color(0xFF8B5CF6),
        secondaryColor = Color(0xFF6D28D9),
        glowColor = Color(0xFF8B5CF6),
        label = "Epic"
    ),
    LEGENDARY(
        primaryColor = ShinyGoldDeep,
        secondaryColor = ShinyGoldDark,
        glowColor = ShinyGoldLight,
        label = "Legendary"
    )
}

/**
 * Achievement data
 */
data class AchievementData(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val rarity: AchievementRarity,
    val progress: Float = 1f, // 0-1 for progress-based achievements
    val isUnlocked: Boolean = true,
    val unlockedDate: String? = null
)

/**
 * Premium Achievement Card with animated effects
 */
@Composable
fun PremiumAchievementCard(
    achievement: AchievementData,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_appear"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "achievement_effects")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
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
                elevation = if (achievement.isUnlocked) 20.dp else 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (achievement.isUnlocked) 
                    achievement.rarity.glowColor.copy(alpha = glowPulse) 
                else Color.Black.copy(alpha = 0.2f),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp),
        border = if (achievement.isUnlocked && achievement.rarity == AchievementRarity.LEGENDARY) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                Brush.linearGradient(
                    colors = listOf(
                        ShinyGoldLight,
                        ShinyGoldDeep,
                        ShinyGoldDark,
                        ShinyGoldDeep,
                        ShinyGoldLight
                    ),
                    start = Offset(shimmerOffset, 0f),
                    end = Offset(shimmerOffset + 200f, 200f)
                )
            )
        } else {
            androidx.compose.foundation.BorderStroke(
                0.5.dp,
                CardBorder.copy(alpha = 0.3f)
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Background particles for legendary
            if (achievement.isUnlocked && achievement.rarity == AchievementRarity.LEGENDARY) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { this.rotationZ = rotation }
                ) {
                    repeat(8) { i ->
                        val angle = (i * 45f + rotation) * PI.toFloat() / 180f
                        val distance = size.minDimension * 0.4f
                        val x = size.width / 2 + distance * cos(angle)
                        val y = size.height / 2 + distance * sin(angle)
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = 0.3f),
                                    Color.Transparent
                                ),
                                center = Offset(x, y)
                            ),
                            radius = 20f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Achievement Icon
                AchievementBadge(
                    icon = achievement.icon,
                    rarity = achievement.rarity,
                    isUnlocked = achievement.isUnlocked,
                    glowPulse = glowPulse
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Rarity badge
                    Surface(
                        color = achievement.rarity.primaryColor.copy(
                            alpha = if (achievement.isUnlocked) 0.15f else 0.05f
                        ),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            achievement.rarity.primaryColor.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = achievement.rarity.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (achievement.isUnlocked) 
                                achievement.rarity.primaryColor else TextTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Title
                    Text(
                        text = achievement.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (achievement.isUnlocked) TextPrimary else TextTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Description
                    Text(
                        text = achievement.description,
                        fontSize = 13.sp,
                        color = if (achievement.isUnlocked) TextSecondary else TextTertiary,
                        lineHeight = 18.sp
                    )
                    
                    // Progress bar for incomplete achievements
                    if (!achievement.isUnlocked && achievement.progress < 1f) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AchievementProgress(
                            progress = achievement.progress,
                            color = achievement.rarity.primaryColor
                        )
                    }
                    
                    // Unlock date
                    if (achievement.isUnlocked && achievement.unlockedDate != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Unlocked ${achievement.unlockedDate}",
                                fontSize = 11.sp,
                                color = NeonSuccess
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(
    icon: ImageVector,
    rarity: AchievementRarity,
    isUnlocked: Boolean,
    glowPulse: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        if (isUnlocked) {
            Canvas(
                modifier = Modifier
                    .size(72.dp)
                    .rotate(ringRotation)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 4.dp.toPx()
                
                // Gradient ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            rarity.primaryColor.copy(alpha = glowPulse),
                            Color.Transparent,
                            rarity.secondaryColor.copy(alpha = glowPulse * 0.5f),
                            Color.Transparent,
                            rarity.glowColor.copy(alpha = glowPulse),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        
        // Main badge
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = if (isUnlocked) 12.dp else 4.dp,
                    shape = CircleShape,
                    spotColor = if (isUnlocked) rarity.primaryColor.copy(alpha = 0.4f) else Color.Transparent
                )
                .clip(CircleShape)
                .background(
                    if (isUnlocked) {
                        Brush.radialGradient(
                            colors = listOf(
                                rarity.primaryColor.copy(alpha = 0.3f),
                                rarity.secondaryColor.copy(alpha = 0.2f)
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                CardBorder.copy(alpha = 0.3f)
                            )
                        )
                    }
                )
                .border(
                    2.dp,
                    if (isUnlocked) rarity.primaryColor.copy(alpha = 0.5f) else CardBorder.copy(alpha = 0.3f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isUnlocked) rarity.primaryColor else TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Lock overlay for locked achievements
        if (!isUnlocked) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryBackground.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AchievementProgress(
    progress: Float,
    color: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Progress",
                fontSize = 11.sp,
                color = TextTertiary
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardBorder.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.7f),
                                color
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Achievement Unlocked Celebration Overlay
 */
@Composable
fun AchievementUnlockedOverlay(
    achievement: AchievementData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        showContent = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "overlay_scale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rays_rotation"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        // Animated rays background
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .rotate(rotation)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            repeat(12) { i ->
                val angle = i * 30f * PI.toFloat() / 180f
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            achievement.rarity.primaryColor.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        start = center,
                        end = Offset(
                            center.x + 200f * cos(angle),
                            center.y + 200f * sin(angle)
                        )
                    ),
                    start = center,
                    end = Offset(
                        center.x + 200f * cos(angle),
                        center.y + 200f * sin(angle)
                    ),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Achievement Unlocked!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = achievement.rarity.primaryColor,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Large achievement badge
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow rings
                repeat(3) { i ->
                    val ringScale by animateFloatAsState(
                        targetValue = if (showContent) 1f + i * 0.2f else 0.5f,
                        animationSpec = tween(
                            durationMillis = 1000,
                            delayMillis = i * 200,
                            easing = FastOutSlowInEasing
                        ),
                        label = "ring_$i"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                                alpha = (1f - i * 0.3f) * 0.5f
                            }
                            .border(
                                2.dp,
                                achievement.rarity.primaryColor.copy(alpha = 0.5f),
                                CircleShape
                            )
                    )
                }
                
                // Main badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(20.dp, CircleShape, spotColor = achievement.rarity.primaryColor)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    achievement.rarity.primaryColor,
                                    achievement.rarity.secondaryColor
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = achievement.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = achievement.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = achievement.description,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = achievement.rarity.primaryColor
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Awesome!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
