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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import java.util.Locale

/**
 * MEGA UI UPGRADE: PremiumAiStatisticsTab (v5.0).
 * Ultra-fidelity AI telemetry dashboard with cinematic animations and broadcast aesthetics.
 */
@Composable
fun PremiumAiStatisticsTab(match: CricketMatch) {
    val scrollState = rememberScrollState()
    // This tab narrates the BATTING side ("$battingTeam holding strong advantage"), so it reads
    // the batting-oriented value. The stored probability is team-1-oriented and would be inverted
    // here whenever teams[1] is at the crease.
    val winProb = match.battingWinProbability?.toFloat()?.coerceIn(5f, 95f)
    val crr = match.score?.currentRunRate ?: "--"
    val rrr = match.score?.requiredRunRate ?: "--"
    val battingTeam = match.teams.firstOrNull { it.isBatting }?.acronym
        ?: match.teams.firstOrNull()?.acronym ?: "BAT"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Every panel here is a presentation of the win probability, so with no number there is
        // nothing honest to draw. The run-rate duel does not depend on it and still renders.
        winProb?.let { ProbabilityPanels(it, battingTeam) }
        DuelMetricsRow(crr, rrr)
        winProb?.let { PredictiveInsightCard(it) }
        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
private fun ProbabilityPanels(winProb: Float, battingTeam: String) {
    val probDelta = winProb - 50f
    val probDeltaStr = if (probDelta >= 0) "+${probDelta.toInt()}" else "${probDelta.toInt()}"
    AiImpactHero(winProb)
    InsightPill(
        when {
            winProb > 65 -> "$battingTeam holding strong advantage — ${winProb.toInt()}% win probability"
            winProb < 35 -> "bowling side dominant — $battingTeam at ${winProb.toInt()}% win probability"
            else -> "match finely balanced — ${winProb.toInt()}% win probability"
        }
    )
    ValueAddedChart(winProb, probDeltaStr)
}

@Composable
private fun AiImpactHero(winProb: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
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
                brush = Brush.radialGradient(listOf(hierarchy.secondary.primary.copy(alpha = 0.2f), Color.Transparent)),
                radius = 120.dp.toPx()
            )
            
            withTransform({
                rotate(rotation)
                scale(pulseScale, pulseScale)
            }) {
                // Technical Grid Rings
                drawCircle(
                    color = hierarchy.secondary.primary.copy(alpha = 0.15f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f))),
                    radius = 105.dp.toPx()
                )
            }
            
            drawCircle(
                color = GlassSurface,
                style = Stroke(width = 1.dp.toPx()),
                radius = 85.dp.toPx()
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "BOUNCER AI IMPACT SCORE",
                color = hierarchy.secondary.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp
            )
            Text(
                String.format(Locale.US, "%.1f", winProb),
                color = TextPrimary,
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3).sp
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StatusSuccess.copy(alpha = 0.1f))
                    .border(0.5.dp, StatusSuccess.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                    Text("ELITE STATUS", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightPill(text: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassBorder)
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
            Icon(Icons.Default.AutoAwesome, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(20.dp))
            Text(
                text = text.uppercase(), 
                color = TextPrimary, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ValueAddedChart(winProb: Float, probDelta: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    "ANALYTIC CONTRIBUTION",
                    color = hierarchy.secondary.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "Win Probability Delta vs Baseline",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(probDelta, color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("%", color = hierarchy.secondary.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(GlassBorder)
                .border(0.5.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lines = 5
                val spacing = size.height / lines
                for (i in 0..lines) {
                    val y = i * spacing
                    drawLine(
                        color = GlassSurface.copy(alpha = 0.5f),
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
                                    if (isProjected) Brush.verticalGradient(listOf(TextMuted, Color.Transparent))
                                    else Brush.verticalGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.primary.copy(alpha = 0.2f)))
                                )
                                .then(
                                    if (isHighlighted) Modifier.shadow(16.dp, CircleShape, spotColor = hierarchy.secondary.primary.copy(alpha = 0.4f)) else Modifier
                                )
                        )
                        Text(
                            text = "${10 + index * 5}",
                            color = if (isHighlighted) hierarchy.secondary.primary else TextMuted,
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
private fun DuelMetricsRow(crr: String, rrr: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DuelCard(
            modifier = Modifier.weight(1f),
            label = "BATTING",
            name = "CURRENT RUN RATE",
            value = crr,
            subLabel = "CRR",
            color = hierarchy.secondary.primary
        )
        DuelCard(
            modifier = Modifier.weight(1f),
            label = "BOWLING",
            name = "REQUIRED RUN RATE",
            value = rrr,
            subLabel = "RRR",
            color = StatusLive
        )
    }
}

@Composable
private fun DuelCard(modifier: Modifier, label: String, name: String, value: String, subLabel: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(GlassBorder)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text(label, color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            Column {
                Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
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
private fun PredictiveInsightCard(winProb: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(GlassBorder)
            .border(0.5.dp, GlassBorderHighlight, RoundedCornerShape(28.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Psychology, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(22.dp))
                    Text("BOUNCER PREDICTIVE ENGINE", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(hierarchy.secondary.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("CONFIDENCE: ${winProb.toInt()}%", color = hierarchy.secondary.primary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Strategic shift detected: Anticipating high-frequency short-pitch deliveries in the next 12 balls.", 
                        color = TextPrimary, 
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
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WIN %", color = hierarchy.secondary.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(
                            text = when {
                                winProb > 65 -> "HIGH"
                                winProb < 35 -> "LOW"
                                else -> "MED"
                            },
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
