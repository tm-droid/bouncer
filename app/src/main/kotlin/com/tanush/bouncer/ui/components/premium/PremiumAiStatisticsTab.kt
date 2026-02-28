package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*

/**
 * MEGA UI UPGRADE: PremiumAiStatisticsTab (v5.0).
 * Ultra-fidelity AI telemetry dashboard with cinematic animations and broadcast aesthetics.
 */
@Composable
fun PremiumAiStatisticsTab(matchId: String) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Hero Metric: AI Impact Score
        AiImpactHero()

        // Insight Pill
        InsightPill("Match momentum is peaking in the current phase.")

        // Value Added Bar Chart
        ValueAddedChart()

        // Duel Metrics
        DuelMetricsRow()

        // Predictive Insight
        PredictiveInsightCard()
        
        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
private fun AiImpactHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rings")
        
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(20000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "rotation"
        )
        
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(animation = tween(2500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "pulse"
        )

        Canvas(modifier = Modifier.size(240.dp)) {
            // High-fidelity background glow
            drawCircle(
                brush = Brush.radialGradient(listOf(ShinyGoldDeep.copy(alpha = 0.2f), Color.Transparent)),
                radius = 120.dp.toPx()
            )
            
            withTransform({
                rotate(rotation)
                scale(pulseScale, pulseScale)
            }) {
                // Technical Grid Rings
                drawCircle(
                    color = ShinyGoldDeep.copy(alpha = 0.15f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f))),
                    radius = 105.dp.toPx()
                )
            }
            
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                style = Stroke(width = 1.dp.toPx()),
                radius = 85.dp.toPx()
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "BOUNCER AI IMPACT SCORE",
                color = ShinyGoldDeep,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp
            )
            Text(
                "94.8",
                color = Color.White,
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3).sp
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.1f))
                    .border(0.5.dp, SuccessGreen.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TrendingUp, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Text("ELITE STATUS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightPill(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                0.5.dp,
                Brush.horizontalGradient(
                    listOf(Color.Transparent, GoldHairline, Color.Transparent)
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(1.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = ShinyGoldDeep, modifier = Modifier.size(20.dp))
            Text(
                text = text.uppercase(), 
                color = Color.White, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ValueAddedChart() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    "ANALYTIC CONTRIBUTION", 
                    color = ShinyGoldDeep, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black, 
                    letterSpacing = 2.sp
                )
                Text(
                    "Projected Run Differential", 
                    color = TextSecondary, 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("+42", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("PTS", color = ShinyGoldDeep, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lines = 5
                val spacing = size.height / lines
                for (i in 0..lines) {
                    val y = i * spacing
                    drawLine(
                        color = Color.White.copy(alpha = 0.03f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize().padding(top = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val data = listOf(0.45f, 0.30f, 0.90f, 0.65f, 0.25f, 0.75f)
                data.forEachIndexed { index, targetValue ->
                    val isHighlighted = index == 2
                    val isProjected = index == 5
                    
                    val animatedHeight = remember { Animatable(0f) }
                    LaunchedEffect(targetValue) {
                        animatedHeight.animateTo(targetValue, tween(1500 + index * 100, easing = FastOutSlowInEasing))
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animatedHeight.value)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isProjected) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent))
                                    else Brush.verticalGradient(listOf(ShinyGoldDeep, ShinyGoldDeep.copy(alpha = 0.2f)))
                                )
                                .then(
                                    if (isHighlighted) Modifier.shadow(16.dp, CircleShape, spotColor = ShinyGoldDeep.copy(alpha = 0.4f)) else Modifier
                                )
                        )
                        Text(
                            text = "${10 + index * 5}",
                            color = if (isHighlighted) ShinyGoldDeep else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuelMetricsRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DuelCard(
            modifier = Modifier.weight(1f),
            label = "STRATEGY BAT",
            name = "PRIMARY TARGET",
            value = "82.4",
            subLabel = "PRESSURE",
            color = ShinyGoldDeep
        )
        DuelCard(
            modifier = Modifier.weight(1f),
            label = "STRATEGY BOWL",
            name = "LIMITING FACTOR",
            value = "14.2",
            subLabel = "RISK",
            color = ErrorRed
        )
    }
}

@Composable
private fun DuelCard(modifier: Modifier, label: String, name: String, value: String, subLabel: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text(label, color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            Column {
                Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                Text(subLabel, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            
            val sparklineAnim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                sparklineAnim.animateTo(1f, tween(2000, easing = LinearOutSlowInEasing))
            }
            
            Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
                val barCount = 10
                val barWidth = (size.width - (barCount - 1) * 4.dp.toPx()) / barCount
                val heights = listOf(0.4f, 0.6f, 0.3f, 0.8f, 0.5f, 0.9f, 0.4f, 0.7f, 0.5f, 0.8f)
                
                heights.forEachIndexed { i, h ->
                    val actualH = h * sparklineAnim.value
                    drawRoundRect(
                        color = color.copy(alpha = if (i == 5) 1f else 0.2f),
                        topLeft = Offset(i * (barWidth + 4.dp.toPx()), size.height * (1 - actualH)),
                        size = Size(barWidth, size.height * actualH),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictiveInsightCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Psychology, null, tint = ShinyGoldDeep, modifier = Modifier.size(22.dp))
                    Text("BOUNCER PREDICTIVE ENGINE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ShinyGoldDeep.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("CONFIDENCE: 92%", color = ShinyGoldDeep, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Strategic shift detected: Anticipating high-frequency short-pitch deliveries in the next 12 balls.", 
                        color = Color.White, 
                        fontSize = 15.sp, 
                        fontWeight = FontWeight.Bold, 
                        lineHeight = 24.sp
                    )
                    Text(
                        "Model correlates field movement with historical bowler patterns.", 
                        color = TextMuted, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RISK", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("HIGH", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
