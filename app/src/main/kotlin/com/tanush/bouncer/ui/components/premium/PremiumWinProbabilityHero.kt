package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * MEGA UI UPGRADE: High-fidelity Win Probability Hero.
 * Features a glassmorphic depth, neon glow effects, and precision typography.
 */
@Composable
fun PremiumWinProbabilityHero(
    team1Name: String,
    team1Probability: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    val animatedProb by animateFloatAsState(
        targetValue = team1Probability,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "prob"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                0.5.dp,
                Brush.linearGradient(
                    colors = listOf(GoldHairline, Color.White.copy(alpha = 0.05f), GoldHairline.copy(alpha = 0.2f))
                ),
                RoundedCornerShape(28.dp)
            )
    ) {
        // Background Glow Layers
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.Center)
                .blur(60.dp)
                .background(ShinyGoldDeep.copy(alpha = 0.05f * glowAlpha), CircleShape)
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WIN PROBABILITY",
                        color = ShinyGoldDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "LIVE ANALYTICS FEED",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ShinyGoldDeep.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // The Probability Gauge
            Box(
                modifier = Modifier
                    .size(240.dp, 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val radius = (size.width / 2) - strokeWidth
                    val center = Offset(size.width / 2, size.height)

                    // Track Background
                    drawArc(
                        color = Color.White.copy(alpha = 0.03f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(center.x - radius, center.y - radius)
                    )

                    // Probability Arc with Glow
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(ShinyGoldDeep, ShinyGoldLight, ShinyGoldDeep)
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f * animatedProb,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(center.x - radius, center.y - radius)
                    )
                    
                    // Shadow/Depth Arc
                    drawArc(
                        color = Color.Black.copy(alpha = 0.2f),
                        startAngle = 180f,
                        sweepAngle = 180f * animatedProb,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        size = Size((radius + 6.dp.toPx()) * 2, (radius + 6.dp.toPx()) * 2),
                        topLeft = Offset(center.x - radius - 6.dp.toPx(), center.y - radius - 6.dp.toPx())
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "${(animatedProb * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = team1Name.uppercase(),
                        color = ShinyGoldDeep,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            // Percentage Ticks
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(2.dp).background(TextMuted, CircleShape))
                Text("50%", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(2.dp).background(TextMuted, CircleShape))
                Text("100%", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
