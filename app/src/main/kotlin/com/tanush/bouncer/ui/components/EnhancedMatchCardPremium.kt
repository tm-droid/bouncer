package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Enhanced premium match card with advanced animations and glass effects
 */
@Composable
fun EnhancedPremiumMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    team1Color: Color = GoldPrimary,
    team2Color: Color = StatusInfo
) {
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    val isLive = match.status.contains("live", ignoreCase = true)
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "matchCard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val liveGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveGlow"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isLive) 20.dp else 12.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isLive) colors.statusLive.copy(alpha = 0.3f * liveGlow) else hierarchy.secondary.primary.copy(alpha = 0.2f),
                ambientColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.glassBorderHighlight,
                        colors.glassSurface,
                        colors.surface1
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(800f, 600f)
                )
            )
            .then(
                if (isLive) {
                    Modifier.border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.statusLive.copy(alpha = liveGlow * 0.6f),
                                colors.statusLive.copy(alpha = liveGlow * 0.3f),
                                Color.Transparent,
                                colors.statusLive.copy(alpha = liveGlow * 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                } else {
                    Modifier.border(
                        width = 0.5.dp,
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Background effects
        Canvas(modifier = Modifier.matchParentSize()) {
            // Team 1 glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        team1Color.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.3f),
                    radius = size.width * 0.4f
                ),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.1f, size.height * 0.3f)
            )
            
            // Team 2 glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        team2Color.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.9f, size.height * 0.7f),
                    radius = size.width * 0.4f
                ),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.9f, size.height * 0.7f)
            )
            
            // Shimmer effect
            val shimmerStart = shimmerOffset * size.width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.glassBorderHighlight.copy(alpha = 0.03f),
                        colors.glassBorderHighlight.copy(alpha = 0.08f),
                        colors.glassBorderHighlight.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    start = Offset(shimmerStart, 0f),
                    end = Offset(shimmerStart + size.width * 0.3f, size.height)
                )
            )
        }
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Top row: Status + Match info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live status badge
                if (isLive) {
                    LiveBadgeEnhanced(glowIntensity = liveGlow)
                } else {
                    MatchTypeBadge(matchType = match.matchType)
                }
                
                // Match time/info
                Text(
                    text = match.matchTime.ifEmpty { match.date },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary,
                    letterSpacing = 0.3.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                TeamDisplay(
                    team = team1,
                    accentColor = team1Color,
                    isLeading = true
                )
                
                // VS divider
                VsDivider(isLive = isLive, liveGlow = liveGlow)
                
                // Team 2
                TeamDisplay(
                    team = team2,
                    accentColor = team2Color,
                    isLeading = false
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Match status
            MatchStatusBar(
                status = match.currentStatus.ifEmpty { match.status },
                isLive = isLive
            )
            
            // Recent balls (if live)
            if (isLive && !match.recentBalls.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                RecentBallsStrip(balls = match.recentBalls ?: emptyList())
            }
        }
    }
}

@Composable
private fun LiveBadgeEnhanced(glowIntensity: Float) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .background(
                colors.statusLive.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Pulsing dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .drawBehind {
                    drawCircle(
                        color = colors.statusLive.copy(alpha = glowIntensity * 0.4f),
                        radius = size.maxDimension * (0.7f + glowIntensity * 0.5f)
                    )
                }
                .background(colors.statusLive, CircleShape)
        )
        
        Text(
            text = "LIVE",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.statusLive,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun MatchTypeBadge(matchType: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        color = hierarchy.secondary.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = matchType.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = hierarchy.secondary.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TeamDisplay(
    team: CricketMatch.Team?,
    accentColor: Color,
    isLeading: Boolean
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = if (isLeading) Alignment.Start else Alignment.End
    ) {
        // Team logo
        Box(
            modifier = Modifier
                .size(56.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        radius = size.maxDimension * 0.7f
                    )
                }
                .border(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.5f),
                            accentColor.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
                .clip(CircleShape)
                .background(colors.surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!team?.logoUrl.isNullOrEmpty()) {
                CoilImage(
                    model = team?.logoUrl,
                    contentDescription = team?.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = team?.name?.take(3)?.uppercase() ?: "TBA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Team name
        Text(
            text = team?.name ?: "TBA",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isLeading) TextAlign.Start else TextAlign.End
        )
        
        // Score
        if (!team?.score.isNullOrEmpty() && team?.score != "0" && team?.score != "-") {
            Text(
                text = team?.score?.replace("-", "/") ?: "",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = hierarchy.secondary.primary,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

@Composable
private fun VsDivider(isLive: Boolean, liveGlow: Float) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isLive) colors.statusLive.copy(alpha = liveGlow * 0.5f) else colors.glassBorder,
                            Color.Transparent
                        )
                    )
                )
        )
        
        Text(
            text = "vs",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isLive) colors.statusLive.copy(alpha = liveGlow * 0.5f) else colors.glassBorder,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun MatchStatusBar(
    status: String,
    isLive: Boolean
) {
    val colors = BouncerDesignSystem.colors
    Surface(
        color = if (isLive) colors.statusLive.copy(alpha = 0.08f) else colors.surface1.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = status,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isLive) colors.textPrimary else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RecentBallsStrip(balls: List<String>) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent: ",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            balls.takeLast(6).forEach { ball ->
                BallChip(ball = ball)
            }
        }
    }
}

@Composable
private fun BallChip(ball: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val (backgroundColor, textColor) = when {
        ball == "6" -> hierarchy.secondary.primary.copy(alpha = 0.2f) to hierarchy.secondary.primary
        ball == "4" -> colors.statusInfo.copy(alpha = 0.2f) to colors.statusInfo
        ball == "W" -> colors.statusLive.copy(alpha = 0.2f) to colors.statusLive
        ball == "0" || ball == "." -> DotBall.copy(alpha = 0.15f) to DotBall
        else -> colors.glassBorder.copy(alpha = 0.2f) to colors.textSecondary
    }
    
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (ball == ".") "0" else ball,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Quick action floating pill for match card
 */
@Composable
fun QuickActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = GoldPrimary
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "pillScale"
    )
    
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun EnhancedPremiumMatchCardPreview() {
    com.tanush.bouncer.ui.theme.BouncerPreviewTheme {
        val mockMatch = com.tanush.bouncer.models.CricketMatch(
            id = "preview-emc",
            seriesId = "",
            matchTitle = "India vs England",
            matchType = "T20",
            status = "Live",
            teams = listOf(
                com.tanush.bouncer.models.CricketMatch.Team("India", "192/3", "", true, null, "IND", "17.4"),
                com.tanush.bouncer.models.CricketMatch.Team("England", "168/5", "", false, null, "ENG", "16.0")
            ),
            venue = "Wankhede Stadium, Mumbai",
            date = "Today",
            matchTime = "7:30 PM",
            score = null,
            currentStatus = "India lead by 24 runs",
            info = "Live",
            winProbability = 72.0
        )
        EnhancedPremiumMatchCard(match = mockMatch, onClick = {})
    }
}
