package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.TextSecondary
import com.tanush.bouncer.ui.theme.TextTertiary
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Premium Sleek Pitch Map.
 * Technical precision for ball-tracking visualization.
 * Updated: visual hierarchy is now STANDARD for a professional analytical feel.
 */

data class PitchBall(
    val x: Float, // 0-1 horizontal position
    val y: Float, // 0-1 vertical position
    val result: BallResult,
    val bowlerName: String,
    val speed: Float? = null,
)

enum class BallResult {
    DOT, SINGLE, DOUBLE, THREE, FOUR, SIX, WICKET, WIDE, NO_BALL
}

@Composable
fun PitchMapCard(
    balls: List<PitchBall>,
    currentBowler: String,
    batsmanName: String,
    isRightHanded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(balls) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val zoneStats = remember(balls) {
        val fullBalls = balls.filter { it.y <= 0.33f }
        val goodLengthBalls = balls.filter { it.y > 0.33f && it.y <= 0.66f }
        val shortBalls = balls.filter { it.y > 0.66f }
        mapOf("FULL" to fullBalls, "GOOD" to goodLengthBalls, "SHORT" to shortBalls)
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.STANDARD,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Technical & Subdued
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(colors.glassSurface, CircleShape)
                            .border(0.5.dp, colors.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "PITCH ANALYTICS".uppercase(),
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = currentBowler.uppercase(),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Ball count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .border(0.5.dp, colors.glassBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${balls.size} BALLS",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Visualization Layer
            // Capture color for Canvas scope
            val goldPrimary = hierarchy.secondary.primary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Technical Pitch Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(0.5.dp, colors.glassBorder, RoundedCornerShape(8.dp)),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val pitchWidth = size.width
                        val pitchHeight = size.height

                        // Pitch Body - Technical Obsidian
                        drawRoundRect(
                            color = Color(0xFF121212),
                            size = Size(pitchWidth, pitchHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        )

                        // Precision Crease Lines
                        val lineAlpha = 0.15f
                        drawLine(color = Color.White.copy(alpha = lineAlpha), start = Offset(0f, pitchHeight * 0.15f), end = Offset(pitchWidth, pitchHeight * 0.15f), strokeWidth = 1f)
                        drawLine(color = Color.White.copy(alpha = lineAlpha), start = Offset(0f, pitchHeight * 0.85f), end = Offset(pitchWidth, pitchHeight * 0.85f), strokeWidth = 1f)

                        // Dotted Zone Markers
                        listOf(0.33f, 0.66f).forEach { fraction ->
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(0f, pitchHeight * fraction),
                                end = Offset(pitchWidth, pitchHeight * fraction),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                            )
                        }

                        // Stumps - Minimalist White Lines
                        val stumpsX = pitchWidth / 2
                        for (i in -1..1) {
                            val x = stumpsX + (i * 5.dp.toPx())
                            drawLine(
                                color = Color.White.copy(alpha = 0.6f),
                                start = Offset(x, pitchHeight - 10.dp.toPx()),
                                end = Offset(x, pitchHeight - 2.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }

                        // Balls - Technical Markers
                        balls.forEachIndexed { index, ball ->
                            val ballX = ball.x * pitchWidth
                            val ballY = ball.y * (pitchHeight * 0.7f) + (pitchHeight * 0.15f)
                            val ballColor = when (ball.result) {
                                BallResult.WICKET -> colors.statusLive
                                BallResult.FOUR, BallResult.SIX -> goldPrimary
                                BallResult.DOT -> TextTertiary
                                else -> colors.statusSuccess
                            }

                            val radius = 3.5.dp.toPx() * animatedProgress.value
                            drawCircle(color = ballColor.copy(alpha = 0.9f), radius = radius, center = Offset(ballX, ballY))
                            
                            // Last ball highlight - Pulse Ring
                            if (index == balls.size - 1) {
                                drawCircle(color = Color.White, radius = radius + 1.5.dp.toPx(), center = Offset(ballX, ballY), style = Stroke(width = 1.dp.toPx()))
                            }
                        }
                    }
                }

                // Zone Breakdown - Compact & Professional
                Column(
                    modifier = Modifier.width(85.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    zoneStats.forEach { (name, zoneBalls) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.glassSurface)
                                .border(0.5.dp, colors.glassBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(text = name, color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "${zoneBalls.size}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                if (zoneBalls.any { it.result == BallResult.WICKET }) {
                                    Text(text = "W", color = colors.statusLive, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Legend - Technical & Subdued
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = TextTertiary, text = "DOT")
                LegendItem(color = colors.statusSuccess, text = "RUNS")
                LegendItem(color = hierarchy.secondary.primary, text = "BDRY")
                LegendItem(color = colors.statusLive, text = "WKT")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = 0.9f)))
        Text(text = text, color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}
