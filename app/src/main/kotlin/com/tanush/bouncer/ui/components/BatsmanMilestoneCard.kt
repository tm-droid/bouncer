package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Premium batsman milestone card with celebration effects
 */

data class BatsmanMilestoneInfo(
    val playerName: String,
    val teamName: String,
    val playerImageUrl: String? = null,
    val currentRuns: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val milestone: MilestoneType,
    val isNewMilestone: Boolean = false,
    val careerHighScore: Int? = null,
    val matchesWithMilestone: Int = 0
)

enum class MilestoneType(val displayName: String, val runs: Int, val emoji: String) {
    FIFTY("Half Century", 50, "🎯"),
    HUNDRED("Century", 100, "💯"),
    DOUBLE_HUNDRED("Double Century", 200, "🏆"),
    TRIPLE_HUNDRED("Triple Century", 300, "👑"),
    QUADRUPLE_HUNDRED("Quadruple Century", 400, "🔥")
}

@Composable
fun BatsmanMilestoneCard(
    milestoneInfo: BatsmanMilestoneInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "milestone_animation")
    
    // Star sparkle animation
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_rotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val goldGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gold_glow"
    )
    
    val milestoneColor = when (milestoneInfo.milestone) {
        MilestoneType.FIFTY -> colors.statusInfo
        MilestoneType.HUNDRED -> colors.goldPrimary
        MilestoneType.DOUBLE_HUNDRED -> colors.statusSuccess
        MilestoneType.TRIPLE_HUNDRED -> Color(0xFFFF6B6B)
        MilestoneType.QUADRUPLE_HUNDRED -> Color(0xFFE040FB)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box {
            // Celebration background effect
            if (milestoneInfo.isNewMilestone) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    
                    // Radiating lines
                    for (i in 0 until 12) {
                        val angle = (sparkleRotation + i * 30f) * (Math.PI / 180f).toFloat()
                        val startRadius = 50f
                        val endRadius = size.maxDimension
                        
                        val startX = centerX + startRadius * kotlin.math.cos(angle)
                        val startY = centerY + startRadius * kotlin.math.sin(angle)
                        val endX = centerX + endRadius * kotlin.math.cos(angle)
                        val endY = centerY + endRadius * kotlin.math.sin(angle)
                        
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    milestoneColor.copy(alpha = 0.3f * goldGlow),
                                    Color.Transparent
                                ),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY)
                            ),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3f
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with milestone badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = milestoneInfo.milestone.emoji,
                            fontSize = 32.sp
                        )
                    }
                    
                    // Animated milestone badge
                    Surface(
                        color = milestoneColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.scale(if (milestoneInfo.isNewMilestone) pulseScale else 1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = milestoneColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestoneInfo.milestone.displayName.uppercase(),
                                color = milestoneColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Player name
                Text(
                    text = milestoneInfo.playerName,
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = milestoneInfo.teamName,
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Score display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Star decorations
                    Box(
                        modifier = Modifier
                            .rotate(sparkleRotation)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = colors.goldPrimary.copy(alpha = goldGlow),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Runs display
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${milestoneInfo.currentRuns}*",
                            color = milestoneColor,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${milestoneInfo.balls} balls",
                            color = colors.textSecondary,
                            fontSize = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Star decorations
                    Box(
                        modifier = Modifier
                            .rotate(-sparkleRotation)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = colors.goldPrimary.copy(alpha = goldGlow),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Innings breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InningsStatBox(
                        label = "FOURS",
                        value = "${milestoneInfo.fours}",
                        icon = "4️⃣",
                        color = colors.statusInfo
                    )
                    InningsStatBox(
                        label = "SIXES",
                        value = "${milestoneInfo.sixes}",
                        icon = "6️⃣",
                        color = colors.goldPrimary
                    )
                    InningsStatBox(
                        label = "S/R",
                        value = String.format("%.1f", milestoneInfo.strikeRate),
                        icon = "⚡",
                        color = colors.goldPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Run composition bar
                RunCompositionBar(
                    totalRuns = milestoneInfo.currentRuns,
                    fours = milestoneInfo.fours,
                    sixes = milestoneInfo.sixes
                )
                
                // Career context
                if (milestoneInfo.careerHighScore != null || milestoneInfo.matchesWithMilestone > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (milestoneInfo.careerHighScore != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Career High",
                                    color = colors.textTertiary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${milestoneInfo.careerHighScore}",
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${milestoneInfo.milestone.displayName}s in Career",
                                color = colors.textTertiary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${milestoneInfo.matchesWithMilestone + 1}",
                                color = milestoneColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InningsStatBox(
    label: String,
    value: String,
    icon: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun RunCompositionBar(
    totalRuns: Int,
    fours: Int,
    sixes: Int
) {
    val colors = BouncerDesignSystem.colors
    val boundaryRuns = (fours * 4) + (sixes * 6)
    val runningRuns = totalRuns - boundaryRuns
    
    val runningPercentage = if (totalRuns > 0) runningRuns.toFloat() / totalRuns else 0f
    val fourPercentage = if (totalRuns > 0) (fours * 4).toFloat() / totalRuns else 0f
    val sixPercentage = if (totalRuns > 0) (sixes * 6).toFloat() / totalRuns else 0f
    
    Column {
        Text(
            text = "RUN COMPOSITION",
            color = colors.textTertiary,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            if (runningPercentage > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(runningPercentage)
                        .background(colors.textTertiary)
                )
            }
            if (fourPercentage > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(fourPercentage)
                        .background(colors.statusInfo)
                )
            }
            if (sixPercentage > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(sixPercentage)
                        .background(colors.goldPrimary)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(color = colors.textTertiary, label = "Running (${runningRuns})")
            LegendItem(color = colors.statusInfo, label = "Fours (${fours * 4})")
            LegendItem(color = colors.goldPrimary, label = "Sixes (${sixes * 6})")
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}
