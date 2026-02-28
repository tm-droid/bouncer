package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Innings Break Card - Elite "Masterpiece" Edition.
 * Merges high-tech telemetry with luxury "Half-Time Report" aesthetics.
 */

data class BreakDetails(
    val breakType: String,
    val timeRemaining: Int,
    val totalDuration: Int,
    val team1Summary: String,
    val team2Summary: String,
    val nextBatter: String? = null,
    val nextBowler: String? = null,
)

@Composable
fun InningsBreakCard(
    breakInfo: BreakDetails,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "break_anim")
    
    val timerGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), repeatMode = RepeatMode.Reverse),
        label = "timer_glow"
    )

    val trophyRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2000), repeatMode = RepeatMode.Reverse),
        label = "trophy_bounce"
    )

    val minutes = breakInfo.timeRemaining / 60
    val seconds = breakInfo.timeRemaining % 60

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = ShinyGoldDeep.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BlackBackground),
        border = BorderStroke(1.dp, GoldHairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CardDarkBlue.copy(alpha = 0.4f),
                            BlackBackground,
                            BlackBackground
                        )
                    )
                )
        ) {
            // Background Decorative Elements
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(ShinyGoldDeep.copy(alpha = 0.05f), Color.Transparent)),
                    radius = size.width * 0.6f,
                    center = Offset(size.width, 0f)
                )
            }

            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Premium Badge
                Surface(
                    color = ShinyGoldDeep.copy(alpha = 0.1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ShinyGoldDeep.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents, 
                            null, 
                            tint = ShinyGoldDeep, 
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = trophyRotation }
                        )
                        Text(
                            text = "HALF-TIME STRATEGY REPORT",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // High-End Luxury Timer
                Box(contentAlignment = Alignment.Center) {
                    // Modern Progress Ring
                    Canvas(modifier = Modifier.size(180.dp)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(ShinyGoldDeep, ShinyGoldMid, ShinyGoldDeep)),
                            startAngle = -90f,
                            sweepAngle = (breakInfo.timeRemaining.toFloat() / breakInfo.totalDuration.toFloat()) * 360f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "RESUMING IN",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            String.format("%02d:%02d", minutes, seconds),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.shadow(12.dp * timerGlow, CircleShape, spotColor = ShinyGoldDeep)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Key Performance Indicators (KPIs)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EliteKpiBox(
                        label = "TEAM 1 TARGET",
                        value = breakInfo.team1Summary,
                        modifier = Modifier.weight(1f)
                    )
                    EliteKpiBox(
                        label = "RUN RATE REQ",
                        value = "9.42", // Mocked for Elite feel
                        modifier = Modifier.weight(1f),
                        isHighlight = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // AI Insight Module
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, GlassBorderLight)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "BOUNCER IQ STRATEGY",
                            color = ShinyGoldDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Surface tension is increasing. Expect a high degree of turn in the first 4 overs of the second innings. Defensive field placement is advised.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EliteKpiBox(label: String, value: String, modifier: Modifier, isHighlight: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isHighlight) ShinyGoldDeep.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (isHighlight) ShinyGoldDeep.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            value, 
            color = if (isHighlight) ShinyGoldDeep else Color.White, 
            fontSize = 20.sp, 
            fontWeight = FontWeight.Black
        )
    }
}
