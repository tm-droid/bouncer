package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Premium bowler milestone card with celebration effects for wicket hauls
 */

data class BowlerMilestoneInfo(
    val playerName: String,
    val teamName: String,
    val playerImageUrl: String? = null,
    val wickets: Int,
    val runs: Int,
    val overs: Float,
    val maidens: Int,
    val economy: Float,
    val milestone: BowlerMilestoneType,
    val isNewMilestone: Boolean = false,
    val bestBowling: String? = null,
    val careerWickets: Int? = null,
    val wicketBreakdown: List<WicketBreakdown> = emptyList()
)

data class WicketBreakdown(
    val batsmanName: String,
    val dismissalType: String,
    val runs: Int
)

enum class BowlerMilestoneType(val displayName: String, val wickets: Int, val emoji: String) {
    FIVE_WICKETS("Five Wicket Haul", 5, "🔥"),
    SIX_WICKETS("Six Wickets", 6, "🎯"),
    SEVEN_WICKETS("Seven Wickets", 7, "💫"),
    EIGHT_WICKETS("Eight Wickets", 8, "⭐"),
    NINE_WICKETS("Nine Wickets", 9, "🌟"),
    TEN_WICKETS("Ten Wicket Haul", 10, "👑")
}

@Composable
fun BowlerMilestoneCard(
    milestoneInfo: BowlerMilestoneInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "bowler_milestone_animation")
    
    val flameRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_rotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val wicketProgress by animateFloatAsState(
        targetValue = milestoneInfo.wickets.toFloat() / 10f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "wicket_progress"
    )
    
    val milestoneColor = when (milestoneInfo.milestone) {
        BowlerMilestoneType.FIVE_WICKETS -> Color(0xFFFF6B6B)
        BowlerMilestoneType.SIX_WICKETS -> Color(0xFFFF8E3C)
        BowlerMilestoneType.SEVEN_WICKETS -> hierarchy.secondary.primary
        BowlerMilestoneType.EIGHT_WICKETS -> colors.statusSuccess
        BowlerMilestoneType.NINE_WICKETS -> colors.statusInfo
        BowlerMilestoneType.TEN_WICKETS -> Color(0xFFE040FB)
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
            // Fire background effect for celebration
            if (milestoneInfo.isNewMilestone) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val bottomY = size.height
                    
                    // Draw flame-like gradients rising from bottom
                    for (i in 0 until 8) {
                        val offsetX = (i - 4) * (size.width / 8)
                        val flameHeight = size.height * 0.6f * (1f - kotlin.math.abs(i - 4) * 0.15f)
                        
                        val path = Path().apply {
                            moveTo(centerX + offsetX - 30f, bottomY)
                            quadraticTo(
                                centerX + offsetX - 40f,
                                bottomY - flameHeight * 0.5f,
                                centerX + offsetX,
                                bottomY - flameHeight
                            )
                            quadraticTo(
                                centerX + offsetX + 40f,
                                bottomY - flameHeight * 0.5f,
                                centerX + offsetX + 30f,
                                bottomY
                            )
                            close()
                        }
                        
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    milestoneColor.copy(alpha = 0.1f * glowAlpha),
                                    milestoneColor.copy(alpha = 0.2f * glowAlpha)
                                ),
                                startY = bottomY - flameHeight,
                                endY = bottomY
                            )
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
                    Box(
                        modifier = Modifier
                            .rotate(if (milestoneInfo.isNewMilestone) flameRotation else 0f)
                    ) {
                        Text(
                            text = milestoneInfo.milestone.emoji,
                            fontSize = 36.sp
                        )
                    }
                    
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
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = milestoneColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(flameRotation)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestoneInfo.milestone.displayName.uppercase(),
                                color = milestoneColor,
                                fontSize = 12.sp,
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
                
                // Bowling figures display with wicket gauge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wicket gauge
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 10f
                            val radius = (size.minDimension - strokeWidth) / 2
                            val center = Offset(size.width / 2, size.height / 2)
                            
                            // Background circle
                            drawCircle(
                                color = Color.White.copy(alpha = 0.1f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )
                            
                            // Wicket segments
                            for (i in 0 until 10) {
                                val startAngle = -90f + (i * 36f)
                                val isActive = i < milestoneInfo.wickets
                                
                                drawArc(
                                    color = if (isActive) milestoneColor else Color.White.copy(alpha = 0.1f),
                                    startAngle = startAngle + 2f,
                                    sweepAngle = 32f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${milestoneInfo.wickets}",
                                color = milestoneColor,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "WKTS",
                                color = colors.textTertiary,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    // Bowling figures
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FIGURES",
                            color = colors.textTertiary,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${milestoneInfo.wickets}",
                                color = milestoneColor,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "/",
                                color = colors.textTertiary,
                                fontSize = 36.sp
                            )
                            Text(
                                text = "${milestoneInfo.runs}",
                                color = colors.textPrimary,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${milestoneInfo.overs} overs",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Bowling stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BowlingStatBox(
                        label = "MAIDENS",
                        value = "${milestoneInfo.maidens}",
                        color = colors.statusSuccess
                    )
                    BowlingStatBox(
                        label = "ECONOMY",
                        value = String.format("%.2f", milestoneInfo.economy),
                        color = if (milestoneInfo.economy < 4.0f) colors.statusSuccess 
                               else if (milestoneInfo.economy < 6.0f) colors.statusWarning 
                               else colors.statusLive
                    )
                    BowlingStatBox(
                        label = "AVG",
                        value = if (milestoneInfo.wickets > 0) 
                            String.format("%.1f", milestoneInfo.runs.toFloat() / milestoneInfo.wickets) 
                            else "-",
                        color = colors.statusInfo
                    )
                }
                
                // Wicket breakdown
                if (milestoneInfo.wicketBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "WICKET BREAKDOWN",
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    milestoneInfo.wicketBreakdown.forEachIndexed { index, wicket ->
                        WicketRow(
                            number = index + 1,
                            wicket = wicket,
                            color = milestoneColor
                        )
                        if (index < milestoneInfo.wicketBreakdown.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                // Career context
                if (milestoneInfo.bestBowling != null || milestoneInfo.careerWickets != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (milestoneInfo.bestBowling != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Best Bowling",
                                    color = colors.textTertiary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = milestoneInfo.bestBowling,
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (milestoneInfo.careerWickets != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Career Wickets",
                                    color = colors.textTertiary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${milestoneInfo.careerWickets}",
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
}

@Composable
private fun BowlingStatBox(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun WicketRow(
    number: Int,
    wicket: WicketBreakdown,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Wicket number badge
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wicket.batsmanName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = wicket.dismissalType,
                color = TextTertiary,
                fontSize = 11.sp
            )
        }
        
        Text(
            text = "${wicket.runs}",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
