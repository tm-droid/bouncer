package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * MEGA UI UPGRADE: LiveMatchDashboard (v5.2).
 * High-fidelity, broadcast-grade dashboard header with technical telemetry aesthetics.
 */
@Composable
fun LiveMatchDashboard(
    team1Name: String,
    team1Logo: String?,
    team1Score: String,
    team1Overs: String,
    team2Name: String,
    team2Logo: String?,
    team2Score: String?,
    team2Overs: String?,
    matchStatus: String,
    isLive: Boolean,
    winProbability: Float?, 
    currentRunRate: String?,
    requiredRunRate: String?,
    target: Int?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "dashboard")
    
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(
                0.5.dp,
                Brush.linearGradient(colors = listOf(hierarchy.secondary.primary.copy(alpha = 0.3f), colors.glassSurface)),
                RoundedCornerShape(32.dp)
            )
    ) {
        // Ambient Background Glow
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(hierarchy.secondary.primary.copy(alpha = 0.04f), Color.Transparent),
                        center = Offset(0f, 0f)
                    )
                )
        )

        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Row: Status & Beacon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE TELEMETRY STREAM",
                        color = hierarchy.secondary.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = matchStatus.uppercase(),
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (isLive) {
                    BroadcastBeacon(pulse = livePulse)
                }
            }

            // Central Score Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamTechnicalBlock(
                    name = team1Name,
                    logo = team1Logo,
                    score = team1Score,
                    overs = team1Overs,
                    isBatting = team2Score == null,
                    modifier = Modifier.weight(1f)
                )
                
                // Central Indicator
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (winProbability != null && isLive) {
                        PrecisionProbabilityGauge(prob = winProbability)
                    } else {
                        Text(
                            text = "VS",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                TeamTechnicalBlock(
                    name = team2Name,
                    logo = team2Logo,
                    score = team2Score ?: "Y.T.B",
                    overs = team2Overs,
                    isBatting = team2Score != null,
                    modifier = Modifier.weight(1f),
                    isRightAligned = true
                )
            }

            // Bottom Analytics Strip
            if (isLive && (currentRunRate != null || requiredRunRate != null || target != null)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.glassSurface)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    currentRunRate?.let { TechStatMini(label = "CRR", value = it) }
                    requiredRunRate?.let { TechStatMini(label = "RRR", value = it, color = if ((it.toDoubleOrNull() ?: 0.0) > 12.0) colors.statusLive else colors.statusSuccess) }
                    target?.let { TechStatMini(label = "TARGET", value = it.toString()) }
                }
            }
        }
    }
}

@Composable
private fun BroadcastBeacon(pulse: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Red.copy(alpha = 0.1f))
            .border(0.5.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = pulse }
                .background(Color.Red, CircleShape)
                .shadow(12.dp, CircleShape, spotColor = Color.Red)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "LIVE",
            color = Color.Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun TeamTechnicalBlock(
    name: String,
    logo: String?,
    score: String,
    overs: String?,
    isBatting: Boolean,
    modifier: Modifier = Modifier,
    isRightAligned: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier,
        horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.glassSurface)
                .border(
                    width = 1.dp,
                    brush = if (isBatting) Brush.verticalGradient(listOf(hierarchy.secondary.primary, Color.Transparent)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val logoUrl = TeamLogoManager.getBestLogoUrl(name, logo, 128, 128)
            CoilImage(
                model = logoUrl,
                contentDescription = name,
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Column(horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start) {
            Text(
                text = name.uppercase(),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = score,
                color = if (isBatting) hierarchy.secondary.primary else colors.textSecondary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            overs?.let {
                Text(
                    text = "($it)",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PrecisionProbabilityGauge(prob: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedProb by animateFloatAsState(targetValue = prob, animationSpec = tween(1500), label = "prob")
    
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val strokeWidth = 4.dp.toPx()
            drawCircle(color = colors.glassSurface, style = Stroke(width = strokeWidth))
            
            drawArc(
                brush = Brush.sweepGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary, hierarchy.secondary.primary)),
                startAngle = -90f,
                sweepAngle = animatedProb * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(animatedProb * 100).toInt()}%",
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun TechStatMini(label: String, value: String, color: Color = GoldPrimary) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * MEGA UI UPGRADE: InningsSummaryCard (v2.2).
 */
@Composable
fun InningsSummaryCard(
    inningsNumber: Int,
    teamName: String,
    score: String,
    overs: String,
    extras: String?,
    runRate: String?,
    fallOfWickets: List<String>?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassSurface)
            .border(
                0.5.dp,
                Brush.linearGradient(colors = listOf(colors.glassBorderHighlight, Color.Transparent)),
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Header: Innings ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INNINGS 0$inningsNumber",
                        color = hierarchy.secondary.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = teamName.uppercase(),
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = score,
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "$overs OVERS",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                extras?.let { TechSummaryItem(label = "EXTRAS", value = it) }
                runRate?.let { TechSummaryItem(label = "RUN RATE", value = it, color = hierarchy.secondary.primary) }
            }
            
            // Fall of Wickets: Dynamic Rail
            fallOfWickets?.takeIf { it.isNotEmpty() }?.let { wickets ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "FALL OF WICKETS",
                        color = colors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wickets) { wicket ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.statusLive.copy(alpha = 0.1f))
                                    .border(0.5.dp, colors.statusLive.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = wicket,
                                    color = colors.statusLive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
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
private fun TechSummaryItem(label: String, value: String, color: Color = TextPrimary) {
    val colors = BouncerDesignSystem.colors
    Column {
        Text(label, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * MEGA UI UPGRADE: LiveMatchMiniWidget (v2.2).
 */
@Composable
fun LiveMatchMiniWidget(
    team1Name: String,
    team1Score: String,
    team2Name: String,
    team2Score: String?,
    statusText: String,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val team1Color: Color = remember(team1Name) { getTeamColor(team1Name) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glassSurface)
            .border(
                0.5.dp,
                if (isLive) team1Color.copy(alpha = 0.3f) else colors.glassSurface,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTeamRow(name = team1Name, score = team1Score, isLive = isLive)
                MiniTeamRow(name = team2Name, score = team2Score ?: "---", isLive = false)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.Red, CircleShape)
                        .shadow(8.dp, CircleShape, spotColor = Color.Red)
                )
            } else {
                Text(
                    text = statusText.uppercase(),
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MiniTeamRow(name: String, score: String, isLive: Boolean) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.take(18),
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = score,
            color = if (isLive) hierarchy.secondary.primary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}
