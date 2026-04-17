package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.ui.theme.*
import kotlin.math.min
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * MEGA UI UPGRADE: BatsmanStatsCard (v2.0).
 * High-fidelity player telemetry with broadcast-grade cinematic visuals.
 */
@Composable
fun BatsmanStatsCard(
    name: String,
    runs: Int,
    balls: Int,
    fours: Int,
    sixes: Int,
    isOnStrike: Boolean = false,
    isOut: Boolean = false,
    dismissalType: String? = null,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val strikeRate = if (balls > 0) (runs * 100.0 / balls) else 0.0
    val infiniteTransition = rememberInfiniteTransition(label = "batsman")
    val strikePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isOut) colors.glassSurface.copy(alpha = 0.5f) else colors.glassBorderHighlight)
            .border(
                0.5.dp,
                if (isOnStrike) Brush.linearGradient(listOf(hierarchy.secondary.primary, Color.Transparent)) else Brush.linearGradient(listOf(colors.glassSurface, Color.Transparent)),
                RoundedCornerShape(20.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Identity
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isOnStrike) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .blur(12.dp)
                                .background(hierarchy.secondary.primary.copy(alpha = 0.15f * strikePulse), CircleShape)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.glassSurface)
                            .border(0.5.dp, if (isOnStrike) hierarchy.secondary.primary.copy(alpha = 0.5f) else colors.glassSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUrl != null) {
                            CoilImage(model = imageUrl, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(name.take(1), color = if (isOnStrike) hierarchy.secondary.primary else colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = name.uppercase(),
                            color = if (isOut) colors.textMuted else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isOnStrike) Box(modifier = Modifier.size(6.dp).background(hierarchy.secondary.primary, CircleShape))
                    }
                    if (isOut && dismissalType != null) {
                        Text(dismissalType, color = colors.statusLive.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Stats Matrix
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = runs.toString(),
                        color = if (runs >= 50) hierarchy.secondary.primary else colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text("RUNS", color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(balls.toString(), color = colors.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("BALLS", color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStatBadge(fours.toString(), "4S", BoundaryFour)
                    MiniStatBadge(sixes.toString(), "6S", SixerPurple)
                }
            }
        }
    }
}

@Composable
private fun MiniStatBadge(value: String, label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(label, color = color.copy(alpha = 0.6f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * MEGA UI UPGRADE: BowlerStatsCard (v2.0).
 */
@Composable
fun BowlerStatsCard(
    name: String,
    overs: String,
    maidens: Int,
    runs: Int,
    wickets: Int,
    economy: Double,
    isCurrentBowler: Boolean = false,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.glassBorderHighlight)
            .border(
                0.5.dp,
                if (isCurrentBowler) Brush.linearGradient(listOf(ChartCyan, Color.Transparent)) else Brush.linearGradient(listOf(colors.glassSurface, Color.Transparent)),
                RoundedCornerShape(20.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.glassSurface)
                        .border(0.5.dp, if (isCurrentBowler) ChartCyan.copy(alpha = 0.5f) else colors.glassSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        CoilImage(model = imageUrl, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(name.take(1), color = if (isCurrentBowler) ChartCyan else colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }

                Column {
                    Text(
                        text = name.uppercase(),
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Text("$overs OVERS", color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$wickets-$runs",
                        color = if (wickets >= 3) colors.statusSuccess else colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text("FIGURES", color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format("%.2f", economy), color = ChartCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("ECON", color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                
                if (maidens > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(hierarchy.secondary.primary.copy(alpha = 0.1f))
                            .border(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("M: $maidens", color = hierarchy.secondary.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/**
 * Circular progress stat indicator - Technical Edition
 */
@Composable
fun CircularStatIndicator(
    value: Float,
    maxValue: Float,
    label: String,
    displayValue: String,
    color: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val progress = (value / maxValue).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1500), label = "progress")
    
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                drawCircle(color = colors.glassSurface, style = Stroke(width = strokeWidth))
                drawArc(
                    brush = Brush.sweepGradient(listOf(color, color.copy(alpha = 0.3f), color)),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(displayValue, fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = colors.textMuted, letterSpacing = 1.sp)
    }
}
