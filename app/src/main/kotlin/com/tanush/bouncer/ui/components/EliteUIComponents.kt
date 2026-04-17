package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.parseScore
import com.tanush.bouncer.utils.toDisplayString
import com.tanush.bouncer.utils.toFullDisplayString
import com.tanush.bouncer.utils.rememberHapticFeedback
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * UI Components - Bouncer App Component Library
 * 
 * This module contains reusable UI components following Material Design guidelines:
 * - Clean, data-focused layouts
 * - Professional typography and card designs
 * - Glassmorphism and modern visual effects
 * - Ambient glows for live/important elements
 * 
 * Color System:
 * - Dark theme with obsidian backgrounds
 * - Gold accent system for highlighting
 * - Glass panels with blur and subtle borders
 */

// ============================================================
// FLOATING NAVIGATION BAR - Premium Design
// ============================================================

@Composable
fun EliteFloatingNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Main floating container with blur effect
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(34.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(34.dp),
            color = GlassSurface,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        GlassBorder,
                        GlassSurface
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left items
                EliteNavItem(
                    icon = Icons.Default.Home,
                    label = "HOME",
                    isSelected = currentRoute == "home",
                    onClick = { 
                        haptic.click()
                        onNavigate("home") 
                    }
                )
                
                EliteNavItem(
                    icon = Icons.Default.Leaderboard,
                    label = "STATS",
                    isSelected = currentRoute == "stats",
                    onClick = { 
                        haptic.click()
                        onNavigate("stats") 
                    }
                )
                
                // Center FAB - Cricket Action
                Box(
                    modifier = Modifier
                        .offset(y = (-16).dp)
                        .size(56.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            spotColor = hierarchy.secondary.primary.copy(alpha = 0.5f)
                        )
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                            ),
                            shape = CircleShape
                        )
                        .border(
                            3.dp,
                            colors.backgroundBase,
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { 
                            haptic.click()
                            onNavigate("live") 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsCricket,
                        contentDescription = "Live",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Right items
                EliteNavItem(
                    icon = Icons.Default.EmojiEvents,
                    label = "TROPHY",
                    isSelected = currentRoute == "trophy_room",
                    onClick = { 
                        haptic.click()
                        onNavigate("trophy_room") 
                    }
                )
                
                EliteNavItem(
                    icon = Icons.Default.Person,
                    label = "PROFILE",
                    isSelected = currentRoute == "profile",
                    onClick = { 
                        haptic.click()
                        onNavigate("profile") 
                    }
                )
            }
        }
    }
}

@Composable
private fun EliteNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.5f,
        animationSpec = tween(200),
        label = "navAlpha"
    )
    
    Column(
        modifier = Modifier
            .width(60.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow effect for selected
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .blur(12.dp)
                        .background(hierarchy.secondary.primary.copy(alpha = 0.3f), CircleShape)
                )
            }
            
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) colors.goldPrimary else colors.textPrimary,
                modifier = Modifier
                    .size(if (isSelected) 24.dp else 22.dp)
                    .graphicsLayer { alpha = animatedAlpha }
            )
        }
        
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(hierarchy.secondary.primary, CircleShape)
                )
            }
        }
    }
}

// ============================================================
// HERO LIVE MATCH CARD - Featured Match Display
// Stunning iOS-inspired design with proper score display
// ============================================================

@Composable
fun HeroLiveMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Parse scores with fallback to raw display
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    
    // Use the new display functions for proper score rendering
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()
    
    val isLive = match.isLive || match.status.lowercase().let { s ->
        s.contains("live") || s.contains("batting") || s.contains("bowling") ||
        (s.contains("day") && !s.contains("won") && !s.contains("result"))
    }
    
    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "heroEffects")
    
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulse"
    )
    
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlow"
    )
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isLive) 28.dp else 16.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = if (isLive) hierarchy.secondary.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF161616),
                        Color(0xFF0D0D0D),
                        BackgroundBase
                    )
                )
            )
            .drawBehind {
                // Shimmer effect for live matches
                if (isLive) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                hierarchy.secondary.primary.copy(alpha = 0.03f),
                                Color.White.copy(alpha = 0.04f),
                                hierarchy.secondary.primary.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            start = Offset(shimmerOffset, 0f),
                            end = Offset(shimmerOffset + 400f, size.height)
                        )
                    )
                }
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = if (isLive) listOf(
                        hierarchy.secondary.primary.copy(alpha = borderGlow),
                        GlassSurface,
                        hierarchy.secondary.primary.copy(alpha = borderGlow * 0.6f)
                    ) else listOf(
                        GlassBorder,
                        GlassSurface
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .clickable { 
                haptic.click()
                onClick() 
            }
    ) {
        // Ambient glow overlay for live matches
        if (isLive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width * 0.7f,
                            center = Offset(size.width * 0.5f, size.height * -0.1f)
                        )
                    }
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header: Series & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.matchTitle.substringBefore(",").trim().uppercase(),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (match.venue.isNotEmpty()) {
                        Text(
                            text = match.venue,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Live Badge - Pill style with glow
                if (isLive) {
                    Surface(
                        color = StatusLive.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, StatusLive.copy(alpha = 0.5f)),
                        modifier = Modifier.shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = StatusLive.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = livePulse }
                                    .shadow(6.dp, CircleShape, spotColor = StatusLive)
                                    .background(StatusLive, CircleShape)
                            )
                            Text(
                                text = "LIVE",
                                color = StatusLive,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                } else {
                    // Status for non-live matches
                    val statusText = when {
                        match.status.lowercase().contains("won") -> "RESULT"
                        match.status.lowercase().contains("upcoming") -> "UPCOMING"
                        match.status.lowercase().contains("break") -> "BREAK"
                        else -> match.matchType.uppercase().take(8)
                    }
                    Surface(
                        color = GlassSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, GlassBorder)
                    ) {
                        Text(
                            text = statusText,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Main Score Display - Prominent Team vs Team Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1 Section
                HeroTeamSection(
                    team = team1,
                    score = score1Display,
                    overs = if (scoreInfo1.overs.isNotBlank() && scoreInfo1.overs != "0.0") "(${scoreInfo1.overs})" else "",
                    isLeft = true,
                    isBatting = team1?.isBatting == true,
                    modifier = Modifier.weight(1f)
                )
                
                // VS Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(44.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GlassSurface,
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                        .border(0.5.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                
                // Team 2 Section
                HeroTeamSection(
                    team = team2,
                    score = score2Display,
                    overs = if (scoreInfo2.overs.isNotBlank() && scoreInfo2.overs != "0.0") "(${scoreInfo2.overs})" else "",
                    isLeft = false,
                    isBatting = team2?.isBatting == true,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Win Probability Bar (for live matches)
            if (isLive && (score1Display != "-" || score2Display != "-")) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Win Probability",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${team1?.acronym?.takeIf { it.isNotEmpty() } ?: team1?.name?.take(3)?.uppercase() ?: "T1"} 52%",
                            color = hierarchy.secondary.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GlassSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.52f)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                                    ),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = team1?.acronym?.takeIf { it.isNotEmpty() } ?: team1?.name?.take(3)?.uppercase() ?: "T1",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = team2?.acronym?.takeIf { it.isNotEmpty() } ?: team2?.name?.take(3)?.uppercase() ?: "T2",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Status text / Commentary snippet
            if (match.currentStatus.isNotEmpty() || match.status.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    GlassSurface,
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val statusText = match.currentStatus.takeIf { it.isNotEmpty() } ?: match.status
                val statusColor = when {
                    statusText.lowercase().contains("won") -> StatusSuccess
                    statusText.lowercase().contains("need") -> hierarchy.secondary.primary
                    else -> TextSecondary
                }
                
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HeroTeamSection(
    team: CricketMatch.Team?,
    score: String,
    overs: String,
    isLeft: Boolean,
    isBatting: Boolean,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 128, 128)
    
    Column(
        modifier = modifier,
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End
    ) {
        // Team Logo with glow for batting team
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (isBatting) Modifier.shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        spotColor = hierarchy.secondary.primary.copy(alpha = 0.4f)
                    ) else Modifier
                )
                .background(
                    if (isBatting) GlassBorder else GlassSurface,
                    CircleShape
                )
                .border(
                    if (isBatting) 2.dp else 1.dp,
                    if (isBatting) hierarchy.secondary.primary.copy(alpha = 0.6f) else GlassBorder,
                    CircleShape
                )
                .padding(10.dp)
        ) {
            CoilImage(
                model = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = team?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Team Acronym
        Text(
            text = team?.acronym?.takeIf { it.isNotEmpty() } ?: team?.name?.take(3)?.uppercase() ?: "TBD",
            color = if (isBatting) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )
        
        // Score - Prominent display
        Text(
            text = score,
            color = if (isBatting) hierarchy.secondary.primary else TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.5).sp
        )
        
        // Overs
        if (overs.isNotEmpty()) {
            Text(
                text = overs,
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HeroTeamDisplay(
    team: CricketMatch.Team?,
    scoreRuns: String,
    scoreWickets: String,
    overs: String,
    isLeft: Boolean
) {
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 128, 128)
    
    Column(
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(8.dp, CircleShape, spotColor = GlassBorder)
                .background(GlassSurface, CircleShape)
                .border(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            GlassBorderHighlight,
                            GlassSurface
                        )
                    ),
                    CircleShape
                )
                .padding(8.dp)
        ) {
            CoilImage(
                model = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = team?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = team?.acronym?.takeIf { it.isNotEmpty() } ?: team?.name?.take(3)?.uppercase() ?: "TBD",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

// ============================================================
// PREMIUM MATCH LIST CARD - Compact Match Display
// iOS-inspired clean design with proper score visibility
// ============================================================

@Composable
fun PremiumMatchListCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Parse scores with proper fallback
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    
    // Use display strings for proper rendering
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()
    val score1Full = scoreInfo1.toFullDisplayString()
    val score2Full = scoreInfo2.toFullDisplayString()
    
    val statusLower = match.status.lowercase()
    val currentStatusLower = match.currentStatus.lowercase()
    val isCompleted = statusLower.contains("won") || statusLower.contains("result") ||
        statusLower.contains("drawn") || statusLower.contains("tied") ||
        currentStatusLower.contains("won") || currentStatusLower.contains("result")
    val hasScoreData = match.score != null || match.teams.any { it.score.isNotBlank() }
    val isLive = match.isLive || statusLower.contains("live") ||
        currentStatusLower.contains("live") ||
        currentStatusLower.contains("opt to") ||
        currentStatusLower.contains("opted to") ||
        currentStatusLower.contains("innings") ||
        currentStatusLower.contains("session") ||
        currentStatusLower.contains("break") ||
        currentStatusLower.contains("trail") ||
        currentStatusLower.contains("lead") ||
        currentStatusLower.contains("batting") ||
        currentStatusLower.contains("bowling") ||
        (hasScoreData && !isCompleted)
    
    val infiniteTransition = rememberInfiniteTransition(label = "cardEffects")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulse"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = if (isLive) 16.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isLive) hierarchy.secondary.primary.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = BorderStroke(
            0.5.dp,
            if (isLive) hierarchy.secondary.primary.copy(alpha = 0.3f) else GlassSurface
        ),
        onClick = { 
            haptic.click()
            onClick() 
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row - Match Info & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Match title with accent bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(3.dp, 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    isLive -> StatusLive
                                    isCompleted -> StatusSuccess
                                    else -> hierarchy.secondary.primary
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = match.matchTitle.substringBefore(",").trim(),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Status Badge
                val (statusText, statusColor) = when {
                    isLive -> "LIVE" to StatusLive
                    isCompleted -> "RESULT" to StatusSuccess
                    else -> "UPCOMING" to hierarchy.secondary.tint
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = if (isLive) BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { alpha = livePulse }
                                    .background(statusColor, CircleShape)
                            )
                        }
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Teams Row - Clean iOS-inspired layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val logo1Url = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl, 64, 64)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(GlassSurface, CircleShape)
                            .border(
                                if (team1?.isBatting == true) 1.5.dp else 0.5.dp,
                                if (team1?.isBatting == true) hierarchy.secondary.primary.copy(alpha = 0.6f) else GlassSurface,
                                CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        CoilImage(
                            model = logo1Url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = team1?.acronym?.takeIf { it.isNotEmpty() } ?: team1?.name?.take(3)?.uppercase() ?: "TBD",
                            color = if (team1?.isBatting == true) TextPrimary else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Always show score if available
                        if (score1Display != "-") {
                            Text(
                                text = score1Full,
                                color = if (team1?.isBatting == true) hierarchy.secondary.primary else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                
                // VS Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(32.dp)
                        .background(GlassSurface, CircleShape)
                        .border(0.5.dp, GlassSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                // Team 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = team2?.acronym?.takeIf { it.isNotEmpty() } ?: team2?.name?.take(3)?.uppercase() ?: "TBD",
                            color = if (team2?.isBatting == true) TextPrimary else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Always show score if available
                        if (score2Display != "-") {
                            Text(
                                text = score2Full,
                                color = if (team2?.isBatting == true) hierarchy.secondary.primary else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val logo2Url = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl, 64, 64)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(GlassSurface, CircleShape)
                            .border(
                                if (team2?.isBatting == true) 1.5.dp else 0.5.dp,
                                if (team2?.isBatting == true) hierarchy.secondary.primary.copy(alpha = 0.6f) else GlassSurface,
                                CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        CoilImage(
                            model = logo2Url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            
            // Status text - Result or current status
            val displayStatus = when {
                isCompleted && match.status.isNotEmpty() -> match.status
                match.currentStatus.isNotEmpty() -> match.currentStatus
                else -> null
            }
            
            if (displayStatus != null) {
                Spacer(modifier = Modifier.height(14.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = displayStatus,
                        color = when {
                            displayStatus.lowercase().contains("won") -> StatusSuccess
                            displayStatus.lowercase().contains("need") -> hierarchy.secondary.primary
                            else -> TextSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ============================================================
// VIP CAROUSEL CARD - Premium Featured Matches with Scores
// Compact horizontal scrolling cards with score display
// ============================================================

@Composable
fun VIPCarouselCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Parse scores for display
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()
    
    val statusLower = match.status.lowercase()
    val isLive = match.isLive || statusLower.contains("live") || 
                 statusLower.contains("batting") || statusLower.contains("bowling")
    val isCompleted = statusLower.contains("won") || statusLower.contains("result")
    
    // Subtle animation for live matches
    val infiniteTransition = rememberInfiniteTransition(label = "carouselCard")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlow"
    )
    
    Surface(
        modifier = modifier
            .width(280.dp)
            .height(160.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = if (isLive) hierarchy.secondary.primary.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.4f)
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        onClick = { 
            haptic.click()
            onClick() 
        }
    ) {
        Box {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF151515),
                                Color(0xFF0C1628),
                                Color(0xFF0A0A0A)
                            ),
                            start = Offset.Zero,
                            end = Offset(800f, 800f)
                        )
                    )
            )
            
            // Border
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            colors = if (isLive) listOf(
                                hierarchy.secondary.primary.copy(alpha = borderGlow),
                                Color.White.copy(alpha = 0.08f),
                                hierarchy.secondary.primary.copy(alpha = borderGlow * 0.5f)
                            ) else listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Match Type & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = match.matchType.uppercase().take(12),
                            color = if (isLive) StatusLive else hierarchy.secondary.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = match.matchTitle.substringBefore(",").take(24),
                            color = TextTertiary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Status indicator
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .shadow(4.dp, CircleShape, spotColor = StatusLive)
                                .background(StatusLive, CircleShape)
                        )
                    }
                }
                
                // Middle: Teams with Scores - Side by side layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val logo1Url = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl, 48, 48)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                                .padding(5.dp)
                        ) {
                            CoilImage(
                                model = logo1Url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = team1?.acronym?.takeIf { it.isNotEmpty() } ?: team1?.name?.take(3)?.uppercase() ?: "TBD",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (score1Display != "-") {
                                Text(
                                    text = score1Display,
                                    color = hierarchy.secondary.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    
                    // VS
                    Text(
                        text = "vs",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    // Team 2
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = team2?.acronym?.takeIf { it.isNotEmpty() } ?: team2?.name?.take(3)?.uppercase() ?: "TBD",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (score2Display != "-") {
                                Text(
                                    text = score2Display,
                                    color = hierarchy.secondary.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val logo2Url = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl, 48, 48)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                                .padding(5.dp)
                        ) {
                            CoilImage(
                                model = logo2Url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                // Bottom: Status text
                Text(
                    text = when {
                        match.currentStatus.isNotEmpty() -> match.currentStatus.take(40)
                        isCompleted -> match.status.take(40)
                        match.venue.isNotEmpty() -> match.venue.take(30)
                        else -> "Tap for details"
                    },
                    color = when {
                        match.status.lowercase().contains("won") -> StatusSuccess.copy(alpha = 0.9f)
                        match.currentStatus.lowercase().contains("need") -> hierarchy.secondary.primary.copy(alpha = 0.9f)
                        else -> TextTertiary
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================
// MARKET INSIGHTS GRID CARDS
// ============================================================

@Composable
fun MarketInsightCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeColor: Color = GoldPrimary,
    onClick: () -> Unit = {}
) {
    val haptic = rememberHapticFeedback()
    
    Surface(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(0.5.dp, GlassBorder),
        onClick = { 
            haptic.click()
            onClick() 
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                if (badgeText != null) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            
            Column {
                Text(
                    text = title.uppercase(),
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ============================================================
// SECTION HEADER
// ============================================================

@Composable
fun EliteSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(3.dp, 16.dp)
                    .background(GoldPrimary, RoundedCornerShape(2.dp))
            )
            Text(
                text = title.uppercase(),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
        }
        
        if (actionText != null) {
            Text(
                text = actionText,
                color = GoldPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onActionClick() }
            )
        }
    }
}

// ============================================================
// STATUS FILTER CHIPS - Cricbuzz Style
// ============================================================

@Composable
fun EliteStatusChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    
    Surface(
        modifier = modifier
            .shadow(
                elevation = if (isSelected) 8.dp else 2.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = if (isSelected) GoldPrimary.copy(alpha = 0.3f) else Color.Transparent
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) GoldPrimary else GlassSurface,
        border = BorderStroke(
            if (isSelected) 0.dp else 0.5.dp,
            GlassBorder
        ),
        onClick = { 
            haptic.click()
            onClick() 
        }
    ) {
        Text(
            text = text.uppercase(),
            color = if (isSelected) Color.Black else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

// ============================================================
// LOADING SKELETON
// ============================================================

@Composable
fun EliteMatchCardSkeleton(
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    
    // Premium gold shimmer sweep
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Subtle pulse for premium feel
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Skeleton placeholder color with pulse
    val skeletonColor = Color.White.copy(alpha = 0.08f).copy(alpha = pulseAlpha)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        BackgroundBase.copy(alpha = 0.8f)
                    )
                )
            )
            .border(
                0.5.dp,
                Brush.linearGradient(
                    colors = listOf(
                        hierarchy.secondary.primary.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.03f),
                        hierarchy.secondary.primary.copy(alpha = 0.1f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
    ) {
        // Premium gold shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Gold-tinted shimmer sweep
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                hierarchy.secondary.primary.copy(alpha = 0.04f),
                                Color.White.copy(alpha = 0.06f),
                                hierarchy.secondary.primary.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            start = Offset(shimmerTranslate, 0f),
                            end = Offset(shimmerTranslate + 400f, size.height)
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(10.dp)
                            .background(skeletonColor, RoundedCornerShape(5.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(20.dp)
                            .background(skeletonColor, RoundedCornerShape(10.dp))
                    )
                }
                
                // Teams skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Team 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(skeletonColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(16.dp)
                                    .background(skeletonColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(12.dp)
                                    .background(skeletonColor, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    
                    // VS Divider
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(skeletonColor, CircleShape)
                    )
                    
                    // Team 2
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(16.dp)
                                    .background(skeletonColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(12.dp)
                                    .background(skeletonColor, RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(skeletonColor, CircleShape)
                        )
                    }
                }
                
                // Status bar skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(skeletonColor, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * ELITE HERO MATCH SKELETON
 * Premium loading state for the hero/featured match card
 * Shows a beautiful animated skeleton with gold shimmer effects
 */
@Composable
fun EliteHeroMatchSkeleton(
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "heroSkeleton")
    
    // Premium gold shimmer sweep
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heroShimmer"
    )
    
    // Ambient glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    // Skeleton placeholder color with pulse
    val skeletonColor = Color.White.copy(alpha = 0.08f).copy(alpha = 0.6f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        BackgroundBase
                    )
                )
            )
            .drawBehind {
                // Ambient gold glow at top
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = glowPulse),
                            Color.Transparent
                        )
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.5f, size.height * -0.2f)
                )
            }
            .border(
                0.5.dp,
                Brush.linearGradient(
                    colors = listOf(
                        hierarchy.secondary.primary.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.05f),
                        hierarchy.secondary.primary.copy(alpha = 0.15f)
                    )
                ),
                RoundedCornerShape(28.dp)
            )
    ) {
        // Premium gold shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                hierarchy.secondary.primary.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.08f),
                                hierarchy.secondary.primary.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            start = Offset(shimmerTranslate, 0f),
                            end = Offset(shimmerTranslate + 500f, size.height)
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(12.dp)
                            .background(skeletonColor, RoundedCornerShape(6.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(26.dp)
                            .background(skeletonColor, RoundedCornerShape(13.dp))
                    )
                }
                
                // Teams skeleton - larger for hero
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team 1
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(skeletonColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(20.dp)
                                .background(skeletonColor, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(16.dp)
                                .background(skeletonColor, RoundedCornerShape(4.dp))
                        )
                    }
                    
                    // VS Circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(skeletonColor, CircleShape)
                    )
                    
                    // Team 2
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(skeletonColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(20.dp)
                                .background(skeletonColor, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(16.dp)
                                .background(skeletonColor, RoundedCornerShape(4.dp))
                        )
                    }
                }
                
                // Status bar skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(skeletonColor, RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

// ============================================================
// COMPONENT ALIASES - Standard names for improved readability
// These provide cleaner naming conventions following Android design guidelines
// ============================================================

/**
 * SectionHeader - Standard section header component
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) = EliteSectionHeader(title, modifier, actionText, onActionClick)

/**
 * StatusFilterChip - Filter chip for status selection
 */
@Composable
fun StatusFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = EliteStatusChip(text, isSelected, onClick, modifier)

/**
 * MatchCarouselCard - Carousel card for match display
 */
@Composable
fun MatchCarouselCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = VIPCarouselCard(match, onClick, modifier)

/**
 * HeroMatchSkeleton - Loading skeleton for hero match
 */
@Composable
fun HeroMatchSkeleton(
    modifier: Modifier = Modifier
) = EliteHeroMatchSkeleton(modifier)
