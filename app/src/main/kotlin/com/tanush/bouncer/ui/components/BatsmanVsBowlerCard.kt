package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Batsman vs Bowler head-to-head card with matchup analysis
 */

data class BatsmanVsBowlerInfo(
    val batsmanName: String,
    val bowlerName: String,
    val batsmanImageUrl: String? = null,
    val bowlerImageUrl: String? = null,
    val ballsFaced: Int,
    val runsScored: Int,
    val dismissals: Int,
    val fours: Int = 0,
    val sixes: Int = 0,
    val dotBalls: Int = 0,
    val strikeRate: Float = 0f,
    val average: Float? = null,
    val advantageText: String = "",
    val isLiveMatchup: Boolean = false,
    val thisMatchBalls: Int = 0,
    val thisMatchRuns: Int = 0
)

@Composable
fun BatsmanVsBowlerCard(
    matchupInfo: BatsmanVsBowlerInfo,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "matchup_animation")
    
    val vsRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vs_rotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    // Determine advantage
    val batsmanAdvantage = matchupInfo.strikeRate > 100f && matchupInfo.dismissals == 0
    val bowlerAdvantage = matchupInfo.strikeRate < 80f || matchupInfo.dismissals >= 2
    val colors = BouncerDesignSystem.colors
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box {
            // Battle effect background
            if (matchupInfo.isLiveMatchup) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height * 0.4f)
                    
                    // Clash effect
                    for (i in 0 until 6) {
                        val angle = (i * 60f + vsRotation * 2) * (PI / 180f).toFloat()
                        val length = 80f
                        
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = 0.4f * glowAlpha),
                                    Color.Transparent
                                ),
                                start = center,
                                end = Offset(
                                    center.x + length * cos(angle),
                                    center.y + length * sin(angle)
                                )
                            ),
                            start = center,
                            end = Offset(
                                center.x + length * cos(angle),
                                center.y + length * sin(angle)
                            ),
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Gavel,
                            contentDescription = null,
                            tint = colors.goldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Head to Head",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (matchupInfo.isLiveMatchup) {
                        Surface(
                            color = colors.statusInfo.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.statusInfo.copy(alpha = glowAlpha))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE",
                                    color = colors.statusInfo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Players face-off
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Batsman
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (batsmanAdvantage) 
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.statusSuccess.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        ) 
                                    else 
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.statusInfo.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusInfo.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (matchupInfo.batsmanImageUrl != null) {
                                    CoilImage(
                                        model = matchupInfo.batsmanImageUrl,
                                        contentDescription = matchupInfo.batsmanName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = "🏏",
                                        fontSize = 28.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = matchupInfo.batsmanName.split(" ").lastOrNull() ?: matchupInfo.batsmanName,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Text(
                            text = "BATSMAN",
                            color = colors.statusInfo,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    // VS
                    Box(
                        modifier = Modifier
                            .rotate(vsRotation)
                            .size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(colors.goldPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "VS",
                                color = colors.goldPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    
                    // Bowler
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (bowlerAdvantage) 
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.statusSuccess.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        ) 
                                    else 
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.statusLive.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusLive.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (matchupInfo.bowlerImageUrl != null) {
                                    CoilImage(
                                        model = matchupInfo.bowlerImageUrl,
                                        contentDescription = matchupInfo.bowlerName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.SportsCricket,
                                        contentDescription = null,
                                        tint = colors.statusLive,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = matchupInfo.bowlerName.split(" ").lastOrNull() ?: matchupInfo.bowlerName,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Text(
                            text = "BOWLER",
                            color = colors.statusLive,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Career stats
                Surface(
                    color = colors.glassSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "CAREER RECORD",
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Main stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MatchupStatColumn(
                                label = "BALLS",
                                value = "${matchupInfo.ballsFaced}",
                                color = colors.textPrimary
                            )
                            MatchupStatColumn(
                                label = "RUNS",
                                value = "${matchupInfo.runsScored}",
                                color = colors.statusInfo
                            )
                            MatchupStatColumn(
                                label = "OUTS",
                                value = "${matchupInfo.dismissals}",
                                color = colors.statusLive
                            )
                            MatchupStatColumn(
                                label = "S/R",
                                value = String.format("%.1f", matchupInfo.strikeRate),
                                color = colors.goldPrimary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Boundary breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BoundaryBox(
                        label = "4s",
                        value = matchupInfo.fours,
                        color = colors.statusInfo
                    )
                    BoundaryBox(
                        label = "6s",
                        value = matchupInfo.sixes,
                        color = colors.goldPrimary
                    )
                    BoundaryBox(
                        label = "Dots",
                        value = matchupInfo.dotBalls,
                        color = Color.Gray
                    )
                }
                
                // This match stats
                if (matchupInfo.isLiveMatchup && matchupInfo.thisMatchBalls > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = colors.statusInfo.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "THIS MATCH",
                                color = colors.statusInfo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${matchupInfo.thisMatchRuns}",
                                    color = colors.textPrimary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " runs off ",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${matchupInfo.thisMatchBalls}",
                                    color = colors.textPrimary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " balls",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                
                // Advantage text
                if (matchupInfo.advantageText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val advantageColor = when {
                        batsmanAdvantage -> colors.statusSuccess
                        bowlerAdvantage -> colors.statusLive
                        else -> colors.goldPrimary
                    }
                    
                    Surface(
                        color = advantageColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = matchupInfo.advantageText,
                            color = advantageColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchupStatColumn(
    label: String,
    value: String,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun BoundaryBox(
    label: String,
    value: Int,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$value",
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = color.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}
