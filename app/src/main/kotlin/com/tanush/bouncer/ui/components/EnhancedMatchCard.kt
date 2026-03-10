package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription as semanticContentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.getMatchStatusInfo
import com.tanush.bouncer.utils.MatchStatusInfo
import com.tanush.bouncer.utils.parseScore
import com.tanush.bouncer.utils.toDisplayString
import com.tanush.bouncer.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.sentry.Sentry

@Composable
fun EnhancedMatchCard(
    match: CricketMatch,
    onClick: () -> Unit = {},
    onFavorite: ((Boolean) -> Unit)? = null,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier,
    elevation: Dp = 16.dp
) {
    val haptic = rememberHapticFeedback()
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Use improved score parsing with proper fallback
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    
    // Use display strings for proper rendering
    val score1 = scoreInfo1.toDisplayString()
    val overs1 = if (scoreInfo1.overs.isNotBlank() && scoreInfo1.overs != "0.0") scoreInfo1.overs else ""
    val score2 = scoreInfo2.toDisplayString()
    val overs2 = if (scoreInfo2.overs.isNotBlank() && scoreInfo2.overs != "0.0") scoreInfo2.overs else ""
    
    val statusInfo = getMatchStatusInfo(match.status)
    val isLive = statusInfo.isLive || match.isLive
    
    // Interaction state
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Enhanced press animation with Material Motion
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 400f
        ),
        label = "pressScale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) elevation.value * 0.7f else elevation.value,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "pressElevation"
    )
    
    // Live pulse animation with improved Material Motion
    val infiniteTransition = rememberInfiniteTransition(label = "liveEffects")
    
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulse"
    )
    
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1200f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Win Prob calculation (placeholder logic)
    var winProb by remember { mutableStateOf(0.5f) }
    LaunchedEffect(match.id, isLive) {
        if (isLive) {
            winProb = withContext(Dispatchers.Default) {
                // Simulate calculation or fetch
                (45..55).random().toFloat() / 100f
            }
        }
    }
    
    val matchDescription = "Match: ${team1?.name ?: "Team 1"} $score1 versus ${team2?.name ?: "Team 2"} $score2. Status: ${statusInfo.text}"
    val colors = BouncerDesignSystem.colors
    
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = if (isLive) colors.statusInfo.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                ambientColor = if (isLive) colors.statusInfo.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.glassSurface,
                        colors.surface1.copy(alpha = 0.3f),
                        colors.backgroundBase
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = if (isLive) colors.statusInfo.copy(alpha = 0.2f) else colors.goldPrimary.copy(alpha = 0.2f)
                )
            ) {
                haptic.click()
                onClick()
            }
            .semantics {
                semanticContentDescription = matchDescription
            }
    ) {
        // Animated background effects for live matches
        if (isLive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val shimmerPath = Path().apply {
                            moveTo(shimmerTranslate, 0f)
                            lineTo(shimmerTranslate + 300f, 0f)
                            lineTo(shimmerTranslate + 450f, size.height)
                            lineTo(shimmerTranslate + 150f, size.height)
                            close()
                        }
                        
                        drawPath(
                            path = shimmerPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.goldPrimary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
            )
        }
        
        // Premium border for live
        if (isLive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.statusInfo.copy(alpha = 0.4f),
                                colors.statusInfo,
                                colors.statusInfo.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
            )
        }
        
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            // Header Row
            MatchCardHeader(
                match = match,
                isLive = isLive,
                livePulse = livePulse,
                onFavorite = onFavorite,
                isFavorite = isFavorite,
                winProb = winProb
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Main Content: Teams & Scores
            MatchCardTeams(
                team1 = team1,
                team2 = team2,
                score1 = score1,
                score2 = score2,
                overs1 = overs1,
                overs2 = overs2,
                statusInfo = statusInfo,
                isLive = isLive,
                livePulse = livePulse
            )
            
            // Live match details - Enhanced
            if (isLive && (match.currentBowler != null || !match.recentBalls.isNullOrEmpty() || !match.scrapedBatsmen.isNullOrEmpty())) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.goldPrimary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                MatchCardLiveDetails(
                    match = match,
                    isLive = isLive
                )
            } else if (!isLive && (match.currentStatus.isNotEmpty() || match.status.isNotEmpty())) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = colors.glassSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = match.currentStatus.takeIf { it.isNotEmpty() } ?: match.status,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCardHeader(
    match: CricketMatch,
    isLive: Boolean,
    livePulse: Float,
    onFavorite: ((Boolean) -> Unit)?,
    isFavorite: Boolean,
    winProb: Float
) {
    val haptic = rememberHapticFeedback()
    val colors = BouncerDesignSystem.colors
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.matchTitle.substringBefore(",").trim().uppercase(),
                color = colors.goldLight.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = match.venue.takeIf { it.isNotEmpty() } ?: "Tournament Match",
                color = colors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLive) {
                MiniProbabilityBadge(
                    team1Name = match.teams.firstOrNull()?.name ?: "T1",
                    team1Probability = winProb
                )
            }
            
            if (onFavorite != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.glassSurface)
                        .clickable {
                            haptic.click()
                            onFavorite(!isFavorite)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (isFavorite) colors.goldPrimary else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Live Badge
            Surface(
                color = if (isLive) colors.statusInfo.copy(alpha = 0.15f) else colors.glassSurface,
                shape = RoundedCornerShape(10.dp),
                border = if (isLive) androidx.compose.foundation.BorderStroke(1.dp, colors.statusInfo.copy(alpha = 0.4f)) else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.statusLive.copy(alpha = livePulse))
                        )
                    }
                    Text(
                        text = if (isLive) "LIVE" else "MATCH",
                        color = if (isLive) colors.textPrimary else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCardTeams(
    team1: CricketMatch.Team?,
    team2: CricketMatch.Team?,
    score1: String,
    score2: String,
    overs1: String,
    overs2: String,
    statusInfo: MatchStatusInfo,
    isLive: Boolean,
    livePulse: Float
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        EnhancedTeamSection(
            team = team1,
            score = score1,
            overs = overs1,
            isLeft = true,
            modifier = Modifier.weight(1f)
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.glassSurface, CircleShape)
                    .border(0.5.dp, colors.glassBorder.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VS",
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        
        EnhancedTeamSection(
            team = team2,
            score = score2,
            overs = overs2,
            isLeft = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EnhancedTeamSection(
    team: CricketMatch.Team?,
    score: String,
    overs: String,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 256, 256)
    val colors = BouncerDesignSystem.colors
    
    Column(
        modifier = modifier,
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = colors.goldPrimary.copy(alpha = 0.3f)
                )
                .background(colors.glassSurface, CircleShape)
                .border(1.dp, colors.goldPrimary.copy(alpha = 0.2f), CircleShape)
                .padding(12.dp)
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = team?.acronym ?: team?.name?.take(3)?.uppercase() ?: "TBD",
            color = colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        
        AnimatedScore(
            score = score,
            fontSize = 24f,
            textColor = colors.textPrimary
        )
        
        if (overs.isNotEmpty()) {
            Text(
                text = overs,
                color = colors.textTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MatchCardLiveDetails(
    match: CricketMatch,
    isLive: Boolean
) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Batsmen
            Column(modifier = Modifier.weight(1.2f)) {
                match.scrapedBatsmen?.take(2)?.forEach { batsman ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = batsman.name,
                            color = colors.textPrimary.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${batsman.runs}(${batsman.balls})",
                            color = colors.goldPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            // Bowler
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                match.currentBowler?.let { bowler ->
                    Text(
                        text = bowler.name,
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${bowler.overs}-${bowler.runs}-${bowler.wickets}",
                        color = colors.goldPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        
        match.recentBalls?.takeIf { it.isNotEmpty() }?.let { balls ->
            Spacer(modifier = Modifier.height(16.dp))
            EnhancedBallsSequence(balls = balls.takeLast(6))
        }
    }
}

@Composable
private fun EnhancedBallsSequence(balls: List<String>) {
    val colors = BouncerDesignSystem.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECENT",
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 4.dp)
        )
        
        balls.forEach { ball ->
            val (bgColor, textColor) = getEnhancedBallColors(ball)
            
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .shadow(4.dp, CircleShape, spotColor = bgColor.copy(alpha = 0.4f))
                    .background(bgColor, CircleShape)
                    .border(0.5.dp, colors.glassBorderHighlight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ball,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

private fun getEnhancedBallColors(ball: String): Pair<Color, Color> {
    try {
        return when {
            ball.contains("W") -> Pair(StatusLive, TextPrimary)
            ball.contains("6") -> Pair(GoldPrimary, BackgroundBase)
            ball.contains("4") -> Pair(GoldPrimary, BackgroundBase)
            ball == "0" || ball == "." -> Pair(GlassSurface, TextMuted)
            else -> Pair(GlassSurface, TextPrimary)
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
