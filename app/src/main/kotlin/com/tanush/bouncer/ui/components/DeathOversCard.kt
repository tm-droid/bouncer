package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Death overs analysis card with premium visualization
 */

data class DeathOversInfo(
    val teamName: String,
    val runs: Int,
    val wickets: Int,
    val balls: Int,
    val runRate: Float,
    val boundaries: Int,
    val sixes: Int,
    val dots: Int,
    val runsByOver: List<Int>,
    val targetDeathOversRR: Float = 12f,
    val projectedTotal: Int? = null
)

@Composable
fun DeathOversCard(
    deathInfo: DeathOversInfo,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "death_animation")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_scale"
    )
    
    val progressAnimation by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    val rrColor = when {
        deathInfo.runRate >= 12f -> colors.statusSuccess
        deathInfo.runRate >= 9f -> colors.statusWarning
        deathInfo.runRate >= 7f -> Color(0xFFFF8E3C)
        else -> colors.statusLive
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box {
            // Fire effect background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bottom = size.height
                val centerX = size.width / 2
                
                // Flame gradients
                for (i in 0 until 5) {
                    val offsetX = (i - 2) * (size.width / 5)
                    val flameHeight = size.height * 0.3f * (1f - abs(i - 2) * 0.2f)
                    
                    val path = Path().apply {
                        moveTo(centerX + offsetX - 40f, bottom)
                        quadraticTo(
                            centerX + offsetX - 50f,
                            bottom - flameHeight * 0.6f,
                            centerX + offsetX,
                            bottom - flameHeight * flameScale
                        )
                        quadraticTo(
                            centerX + offsetX + 50f,
                            bottom - flameHeight * 0.6f,
                            centerX + offsetX + 40f,
                            bottom
                        )
                        close()
                    }
                    
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.statusLive.copy(alpha = 0.05f * pulseAlpha),
                                Color(0xFFFF8E3C).copy(alpha = 0.1f * pulseAlpha)
                            ),
                            startY = bottom - flameHeight,
                            endY = bottom
                        )
                    )
                }
            }
            
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
                            imageVector = Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFFF8E3C).copy(alpha = pulseAlpha),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DEATH OVERS",
                                color = Color(0xFFFF8E3C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = deathInfo.teamName,
                                color = colors.textTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    // Overs indicator
                    Surface(
                        color = Color(0xFFFF8E3C).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = Color(0xFFFF8E3C),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ov 16-20",
                                color = Color(0xFFFF8E3C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Main score display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${deathInfo.runs}/${deathInfo.wickets}",
                            color = colors.textPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${String.format("%.1f", deathInfo.balls / 6f)} overs",
                            color = colors.textTertiary,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(32.dp))
                    
                    // Run rate gauge
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 8f
                            val radius = (size.minDimension - strokeWidth) / 2
                            val center = Offset(size.width / 2, size.height / 2)
                            
                            // Background
                            drawArc(
                                color = colors.glassBorder,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            
                            // Progress (scaled 0-18 RR)
                            val rrProgress = (deathInfo.runRate / 18f).coerceIn(0f, 1f)
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        colors.statusLive,
                                        colors.statusWarning,
                                        colors.statusSuccess
                                    ),
                                    center = center
                                ),
                                startAngle = 135f,
                                sweepAngle = 270f * rrProgress * progressAnimation,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f", deathInfo.runRate),
                                color = rrColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "RR",
                                color = colors.textMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Over-by-over bars
                if (deathInfo.runsByOver.isNotEmpty()) {
                    Text(
                        text = "RUNS PER OVER",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val maxRuns = deathInfo.runsByOver.maxOrNull()?.coerceAtLeast(1) ?: 1
                        
                        deathInfo.runsByOver.forEachIndexed { index, runs ->
                            val barHeight = (runs.toFloat() / maxRuns) * 60.dp.value
                            val animatedHeight by animateFloatAsState(
                                targetValue = barHeight * progressAnimation,
                                animationSpec = tween(1000, delayMillis = index * 100, easing = FastOutSlowInEasing),
                                label = "bar_height_$index"
                            )
                            
                            val barColor = when {
                                runs >= 15 -> colors.statusSuccess
                                runs >= 10 -> colors.statusWarning
                                runs >= 6 -> Color(0xFFFF8E3C)
                                else -> colors.statusLive
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(animatedHeight.dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    barColor,
                                                    barColor.copy(alpha = 0.6f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (animatedHeight > 20) {
                                        Text(
                                            text = "$runs",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${16 + index}",
                                    color = colors.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DeathStatBox(
                        label = "BOUNDARIES",
                        value = "${deathInfo.boundaries}",
                        color = colors.statusInfo
                    )
                    DeathStatBox(
                        label = "SIXES",
                        value = "${deathInfo.sixes}",
                        color = hierarchy.secondary.primary
                    )
                    DeathStatBox(
                        label = "DOTS",
                        value = "${deathInfo.dots}",
                        color = TextSecondary
                    )
                }
                
                // Projected total
                if (deathInfo.projectedTotal != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = rrColor.copy(alpha = 0.1f),
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
                            Text(
                                text = "Projected Total: ",
                                color = colors.textSecondary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${deathInfo.projectedTotal}",
                                color = rrColor,
                                fontSize = 20.sp,
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
private fun DeathStatBox(
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
