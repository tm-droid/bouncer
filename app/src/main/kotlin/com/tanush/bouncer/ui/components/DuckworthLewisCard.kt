package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.*

/**
 * DLS Method card with par score calculations and projections
 */

data class DLSInfo(
    val targetScore: Int,
    val currentScore: Int,
    val currentOvers: Float,
    val maxOvers: Float,
    val wicketsLost: Int,
    val parScore: Int,
    val isRainAffected: Boolean = false,
    val revisedTarget: Int? = null,
    val revisedOvers: Float? = null,
    val resourcesRemaining: Float = 100f,
    val team1Resources: Float = 100f,
    val team2Resources: Float = 100f,
    val projectedScore: Int? = null
)

@Composable
fun DuckworthLewisCard(
    dlsInfo: DLSInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "dls_animation")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val resourceProgress by animateFloatAsState(
        targetValue = dlsInfo.resourcesRemaining / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "resource_progress"
    )
    
    val isAheadOfPar = dlsInfo.currentScore >= dlsInfo.parScore
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Calculate,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DLS Method",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (dlsInfo.isRainAffected) {
                    Surface(
                        color = colors.statusWarning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🌧️",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RAIN AFFECTED",
                                color = colors.statusWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Main target display with resource gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resource gauge
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12f
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)
                        
                        // Background arc
                        drawArc(
                            color = colors.glassSurface,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        
                        // Resource progress arc
                        val resourceAngle = 270f * resourceProgress
                        val resourceColor = when {
                            resourceProgress > 0.7f -> StatusSuccess
                            resourceProgress > 0.4f -> StatusWarning
                            else -> StatusLive
                        }
                        
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    resourceColor.copy(alpha = 0.5f),
                                    resourceColor
                                )
                            ),
                            startAngle = 135f,
                            sweepAngle = resourceAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${dlsInfo.resourcesRemaining.toInt()}%",
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Resources",
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Target info
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TARGET",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${dlsInfo.revisedTarget ?: dlsInfo.targetScore}",
                        color = hierarchy.secondary.primary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (dlsInfo.revisedTarget != null && dlsInfo.revisedTarget != dlsInfo.targetScore) {
                        Text(
                            text = "Original: ${dlsInfo.targetScore}",
                            color = colors.textTertiary,
                            fontSize = 11.sp,
                            style = LocalTextStyle.current.copy(
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        )
                    }
                    if (dlsInfo.revisedOvers != null) {
                        Text(
                            text = "in ${dlsInfo.revisedOvers} overs",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Par score comparison
            Surface(
                color = if (isAheadOfPar) colors.statusSuccess.copy(alpha = 0.15f) else colors.statusLive.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PAR SCORE",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${dlsInfo.parScore}",
                            color = colors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CURRENT",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${dlsInfo.currentScore}/${dlsInfo.wicketsLost}",
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${dlsInfo.currentOvers} overs",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        val diff = dlsInfo.currentScore - dlsInfo.parScore
                        Text(
                            text = if (isAheadOfPar) "AHEAD" else "BEHIND",
                            color = if (isAheadOfPar) colors.statusSuccess else colors.statusLive,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${if (diff >= 0) "+" else ""}$diff",
                            color = if (isAheadOfPar) colors.statusSuccess else colors.statusLive,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Resources comparison bar
            Column {
                Text(
                    text = "RESOURCE COMPARISON",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team 1 resources
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Team 1",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${dlsInfo.team1Resources.toInt()}%",
                            color = colors.statusInfo,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Resource bar visualization
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.glassSurface)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(dlsInfo.team1Resources / 200f)
                                    .background(colors.statusInfo.copy(alpha = 0.7f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(dlsInfo.team2Resources / 200f)
                                    .background(hierarchy.secondary.primary.copy(alpha = 0.7f))
                            )
                        }
                    }
                    
                    // Team 2 resources
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Team 2",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${dlsInfo.team2Resources.toInt()}%",
                            color = hierarchy.secondary.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Projected score
            if (dlsInfo.projectedScore != null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    color = hierarchy.secondary.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = hierarchy.secondary.primary.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Projected Score: ",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${dlsInfo.projectedScore}",
                            color = hierarchy.secondary.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Overs remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(
                    label = "Overs Bowled",
                    value = "${dlsInfo.currentOvers}",
                    icon = Icons.Filled.Timer
                )
                StatBox(
                    label = "Overs Remaining",
                    value = String.format("%.1f", (dlsInfo.revisedOvers ?: dlsInfo.maxOvers) - dlsInfo.currentOvers),
                    icon = Icons.Filled.Timer
                )
                StatBox(
                    label = "Runs Needed",
                    value = "${(dlsInfo.revisedTarget ?: dlsInfo.targetScore) - dlsInfo.currentScore}",
                    icon = Icons.Filled.Calculate
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = colors.textTertiary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}
