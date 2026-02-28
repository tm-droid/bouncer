package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Premium trophy, celebration, and milestone components
 */

@Composable
fun TrophyDisplay(
    trophyType: TrophyType,
    title: String,
    subtitle: String = "",
    isUnlocked: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "trophy")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val trophyColor = when (trophyType) {
        TrophyType.GOLD -> ShinyGoldMid
        TrophyType.SILVER -> Color(0xFFC0C0C0)
        TrophyType.BRONZE -> Color(0xFFCD7F32)
        TrophyType.PLATINUM -> Color(0xFFE5E4E2)
        TrophyType.DIAMOND -> Color(0xFFB9F2FF)
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow
            if (isUnlocked) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .blur(16.dp)
                        .alpha(glowAlpha)
                        .background(trophyColor, CircleShape)
                )
            }
            
            // Trophy container
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .rotate(if (isUnlocked) rotation else 0f)
                    .clip(CircleShape)
                    .background(
                        brush = if (isUnlocked) {
                            Brush.linearGradient(
                                colors = listOf(
                                    trophyColor.copy(alpha = 0.3f),
                                    trophyColor.copy(alpha = 0.1f)
                                ),
                                start = Offset(shimmer * 100, 0f),
                                end = Offset((shimmer + 1) * 100, 100f)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray.copy(alpha = 0.2f),
                                    Color.Gray.copy(alpha = 0.1f)
                                )
                            )
                        }
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = if (isUnlocked) {
                                listOf(trophyColor, trophyColor.copy(alpha = 0.5f))
                            } else {
                                listOf(Color.Gray.copy(alpha = 0.5f), Color.Gray.copy(alpha = 0.2f))
                            }
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = title,
                    tint = if (isUnlocked) trophyColor else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = if (isUnlocked) trophyColor else Color.Gray.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

enum class TrophyType {
    BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
}

@Composable
fun CelebrationOverlay(
    isVisible: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    val particles = remember {
        List(50) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat() - 0.5f,
                size = Random.nextFloat() * 8f + 4f,
                color = listOf(
                    ShinyGoldMid,
                    ShinyGoldLight,
                    ShinyGoldDeep,
                    Color.White
                ).random(),
                speedY = Random.nextFloat() * 0.02f + 0.01f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    val textScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBackground.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        // Confetti particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { particle ->
                val y = ((particle.y + time * particle.speedY * 100) % 1.2f)
                val x = particle.x + sin(time * 10 + particle.x * 10).toFloat() * 0.02f
                
                rotate(
                    degrees = particle.rotation + time * 360,
                    pivot = Offset(size.width * x, size.height * y)
                ) {
                    drawRect(
                        color = particle.color,
                        topLeft = Offset(
                            size.width * x - particle.size / 2,
                            size.height * y - particle.size / 2
                        ),
                        size = androidx.compose.ui.geometry.Size(particle.size, particle.size * 0.6f)
                    )
                }
            }
        }
        
        // Celebration text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.scale(textScale)
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = ShinyGoldMid,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = message,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val speedY: Float,
    val rotation: Float
)

@Composable
fun MilestoneCard(
    milestone: String,
    description: String,
    progress: Float,
    icon: ImageVector = Icons.Default.Star,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "milestone")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (isCompleted) {
                    Brush.linearGradient(
                        colors = listOf(
                            ShinyGoldDeep.copy(alpha = 0.2f),
                            ShinyGoldMid.copy(alpha = 0.1f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))
                    )
                }
            )
            .border(
                width = if (isCompleted) 2.dp else 1.dp,
                brush = if (isCompleted) {
                    Brush.linearGradient(
                        colors = listOf(ShinyGoldDeep, ShinyGoldMid, ShinyGoldLight)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            ShinyGoldMid.copy(alpha = 0.3f),
                            ShinyGoldLight.copy(alpha = 0.1f)
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with glow
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .blur(12.dp)
                            .alpha(glowAlpha)
                            .background(ShinyGoldMid, CircleShape)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted) ShinyGoldMid.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            width = 2.dp,
                            color = if (isCompleted) ShinyGoldMid else ShinyGoldMid.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Verified else icon,
                        contentDescription = null,
                        tint = if (isCompleted) ShinyGoldMid else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = milestone,
                    color = if (isCompleted) ShinyGoldMid else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ShinyGoldMid.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(ShinyGoldDeep, ShinyGoldMid)
                                )
                            )
                    )
                }
                
                Text(
                    text = "${(animatedProgress * 100).toInt()}% Complete",
                    color = ShinyGoldMid.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun StreakCounter(
    currentStreak: Int,
    maxStreak: Int,
    unit: String = "days",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streak")
    
    val flame by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame"
    )
    
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF6B00).copy(alpha = 0.2f),
                        Color(0xFFFF9500).copy(alpha = 0.1f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF6B00),
                        Color(0xFFFF9500)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flame icon with glow
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect
            Canvas(modifier = Modifier.size(48.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF6B00).copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    radius = glowRadius.dp.toPx()
                )
            }
            
            // Flame shape
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .scale(flame)
            ) {
                val path = Path()
                val width = size.width
                val height = size.height
                
                path.moveTo(width * 0.5f, 0f)
                path.cubicTo(
                    width * 0.7f, height * 0.3f,
                    width, height * 0.5f,
                    width * 0.5f, height
                )
                path.cubicTo(
                    0f, height * 0.5f,
                    width * 0.3f, height * 0.3f,
                    width * 0.5f, 0f
                )
                
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFD700),
                            Color(0xFFFF6B00),
                            Color(0xFFFF4500)
                        )
                    )
                )
            }
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$currentStreak $unit",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Current Streak",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$maxStreak $unit",
                color = ShinyGoldMid,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Best Streak",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun BadgeDisplay(
    name: String,
    icon: ImageVector,
    isEarned: Boolean = true,
    tier: Int = 1,
    modifier: Modifier = Modifier
) {
    val tierColor = when (tier) {
        1 -> Color(0xFFCD7F32) // Bronze
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> ShinyGoldMid // Gold
        else -> Color(0xFFB9F2FF) // Diamond
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // Badge hexagon background
            Canvas(
                modifier = Modifier
                    .size(56.dp)
                    .alpha(if (isEarned) 1f else 0.4f)
            ) {
                val path = Path()
                val radius = size.minDimension / 2
                val centerX = size.width / 2
                val centerY = size.height / 2
                
                for (i in 0..5) {
                    val angle = Math.toRadians((60.0 * i - 30))
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    
                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                path.close()
                
                // Fill
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tierColor.copy(alpha = 0.3f),
                            tierColor.copy(alpha = 0.1f)
                        ),
                        start = Offset(shimmer * 100, 0f),
                        end = Offset((shimmer + 1) * 100, 100f)
                    )
                )
                
                // Border
                drawPath(
                    path = path,
                    color = tierColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (isEarned) tierColor else Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        Text(
            text = name,
            color = if (isEarned) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun XPProgressBar(
    currentXP: Int,
    maxXP: Int,
    level: Int,
    modifier: Modifier = Modifier
) {
    val progress = (currentXP.toFloat() / maxXP).coerceIn(0f, 1f)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "xp"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "xpBar")
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level badge
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ShinyGoldMid.copy(alpha = 0.3f),
                            ShinyGoldDeep.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(ShinyGoldDeep, ShinyGoldMid)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LVL",
                    color = ShinyGoldMid.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$level",
                    color = ShinyGoldMid,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Progress section
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Experience",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "$currentXP / $maxXP XP",
                    color = ShinyGoldMid,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // XP bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ShinyGoldMid.copy(alpha = 0.15f))
            ) {
                // Progress fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(ShinyGoldDeep, ShinyGoldMid, ShinyGoldLight)
                            )
                        )
                )
                
                // Shimmer overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .alpha(0.3f)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White,
                                    Color.Transparent
                                ),
                                start = Offset(shimmer * 500, 0f),
                                end = Offset((shimmer + 0.3f) * 500, 0f)
                            )
                        )
                )
            }
        }
    }
}
