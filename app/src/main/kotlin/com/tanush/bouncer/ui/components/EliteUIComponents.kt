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
import com.tanush.bouncer.utils.rememberHapticFeedback
import com.tanush.bouncer.utils.toDisplayString
import com.tanush.bouncer.utils.toFullDisplayString

private fun teamAcronym(team: CricketMatch.Team?): String =
    team?.acronym?.takeIf { it.isNotEmpty() } ?: team?.name?.take(3)?.uppercase() ?: "TBD"

@Composable
private fun TeamLogoCircle(
    team: CricketMatch.Team?,
    size: Dp,
    logoSize: Int,
    padding: Dp,
    background: Color,
    borderWidth: Dp,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, logoSize, logoSize)
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .padding(padding)
    ) {
        CoilImage(
            model = ImageRequest.Builder(context).data(logoUrl).crossfade(true).build(),
            contentDescription = team?.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SkeletonBox(width: Dp, height: Dp, color: Color, corner: Dp = 4.dp) {
    Box(modifier = Modifier.width(width).height(height).background(color, RoundedCornerShape(corner)))
}

@Composable
fun EliteFloatingNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
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
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(GlassBorder, GlassSurface)))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val nav: (String) -> () -> Unit = { route -> { haptic.click(); onNavigate(route) } }

                EliteNavItem(Icons.Default.Home, "HOME", currentRoute == "home", nav("home"))
                EliteNavItem(Icons.Default.Leaderboard, "STATS", currentRoute == "stats", nav("stats"))

                Box(
                    modifier = Modifier
                        .offset(y = (-16).dp)
                        .size(56.dp)
                        .shadow(16.dp, CircleShape, spotColor = hierarchy.secondary.primary.copy(alpha = 0.5f))
                        .background(
                            Brush.linearGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)),
                            CircleShape
                        )
                        .border(3.dp, colors.backgroundBase, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = nav("live")
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsCricket,
                        contentDescription = "Live",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }

                EliteNavItem(Icons.Default.EmojiEvents, "TROPHY", currentRoute == "trophy_room", nav("trophy_room"))
                EliteNavItem(Icons.Default.Person, "PROFILE", currentRoute == "profile", nav("profile"))
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
                Box(modifier = Modifier.size(4.dp).background(hierarchy.secondary.primary, CircleShape))
            }
        }
    }
}

@Composable
fun HeroLiveMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)

    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()

    val isLive = match.isLive || match.status.lowercase().let { s ->
        s.contains("live") || s.contains("batting") || s.contains("bowling") ||
            (s.contains("day") && !s.contains("won") && !s.contains("result"))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "heroEffects")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "livePulse"
    )
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -500f, targetValue = 1500f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
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
                Brush.verticalGradient(listOf(Color(0xFF161616), Color(0xFF0D0D0D), BackgroundBase))
            )
            .drawBehind {
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
                    if (isLive) listOf(
                        hierarchy.secondary.primary.copy(alpha = borderGlow),
                        GlassSurface,
                        hierarchy.secondary.primary.copy(alpha = borderGlow * 0.6f)
                    ) else listOf(GlassBorder, GlassSurface)
                ),
                RoundedCornerShape(28.dp)
            )
            .clickable { haptic.click(); onClick() }
    ) {
        if (isLive) {
            Box(
                modifier = Modifier.fillMaxSize().drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(hierarchy.secondary.primary.copy(alpha = 0.1f), Color.Transparent)
                        ),
                        radius = size.width * 0.7f,
                        center = Offset(size.width * 0.5f, size.height * -0.1f)
                    )
                }
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
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
                    val statusLower = match.status.lowercase()
                    val statusText = when {
                        statusLower.contains("won") -> "RESULT"
                        statusLower.contains("upcoming") -> "UPCOMING"
                        statusLower.contains("break") -> "BREAK"
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroTeamSection(
                    team = team1,
                    score = score1Display,
                    overs = if (scoreInfo1.overs.isNotBlank() && scoreInfo1.overs != "0.0") "(${scoreInfo1.overs})" else "",
                    isLeft = true,
                    isBatting = team1?.isBatting == true,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(44.dp)
                        .background(
                            Brush.radialGradient(listOf(GlassSurface, Color.Transparent)),
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

                HeroTeamSection(
                    team = team2,
                    score = score2Display,
                    overs = if (scoreInfo2.overs.isNotBlank() && scoreInfo2.overs != "0.0") "(${scoreInfo2.overs})" else "",
                    isLeft = false,
                    isBatting = team2?.isBatting == true,
                    modifier = Modifier.weight(1f)
                )
            }

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
                            text = "${teamAcronym(team1)} 52%",
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
                                        listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                                    ),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = teamAcronym(team1),
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = teamAcronym(team2),
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (match.currentStatus.isNotEmpty() || match.status.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, GlassSurface, Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                val statusText = match.currentStatus.takeIf { it.isNotEmpty() } ?: match.status
                val statusLower = statusText.lowercase()
                val statusColor = when {
                    statusLower.contains("won") -> StatusSuccess
                    statusLower.contains("need") -> hierarchy.secondary.primary
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

    Column(
        modifier = modifier,
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End
    ) {
        TeamLogoCircle(
            team = team,
            size = 56.dp,
            logoSize = 128,
            padding = 10.dp,
            background = if (isBatting) GlassBorder else GlassSurface,
            borderWidth = if (isBatting) 2.dp else 1.dp,
            borderColor = if (isBatting) hierarchy.secondary.primary.copy(alpha = 0.6f) else GlassBorder,
            modifier = if (isBatting) Modifier.shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.4f)
            ) else Modifier
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = teamAcronym(team),
            color = if (isBatting) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )

        Text(
            text = score,
            color = if (isBatting) hierarchy.secondary.primary else TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.5).sp
        )

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
fun PremiumMatchListCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)

    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
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
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
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
        onClick = { haptic.click(); onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val accentColor = when {
                        isLive -> StatusLive
                        isCompleted -> StatusSuccess
                        else -> hierarchy.secondary.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(3.dp, 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListTeamRow(
                    team = team1,
                    scoreDisplay = score1Display,
                    scoreFull = score1Full,
                    isLeft = true,
                    modifier = Modifier.weight(1f),
                    hierarchy = hierarchy
                )

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

                ListTeamRow(
                    team = team2,
                    scoreDisplay = score2Display,
                    scoreFull = score2Full,
                    isLeft = false,
                    modifier = Modifier.weight(1f),
                    hierarchy = hierarchy
                )
            }

            val displayStatus = when {
                isCompleted && match.status.isNotEmpty() -> match.status
                match.currentStatus.isNotEmpty() -> match.currentStatus
                else -> null
            }

            if (displayStatus != null) {
                Spacer(modifier = Modifier.height(14.dp))

                val displayLower = displayStatus.lowercase()
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
                            displayLower.contains("won") -> StatusSuccess
                            displayLower.contains("need") -> hierarchy.secondary.primary
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

@Composable
private fun ListTeamRow(
    team: CricketMatch.Team?,
    scoreDisplay: String,
    scoreFull: String,
    isLeft: Boolean,
    modifier: Modifier,
    hierarchy: Hierarchy
) {
    val isBatting = team?.isBatting == true
    val logoBox: @Composable () -> Unit = {
        TeamLogoCircle(
            team = team,
            size = 40.dp,
            logoSize = 64,
            padding = 8.dp,
            background = GlassSurface,
            borderWidth = if (isBatting) 1.5.dp else 0.5.dp,
            borderColor = if (isBatting) hierarchy.secondary.primary.copy(alpha = 0.6f) else GlassSurface
        )
    }
    val labelCol: @Composable () -> Unit = {
        Column(horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End) {
            Text(
                text = teamAcronym(team),
                color = if (isBatting) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (scoreDisplay != "-") {
                Text(
                    text = scoreFull,
                    color = if (isBatting) hierarchy.secondary.primary else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = modifier
    ) {
        if (isLeft) {
            logoBox()
            Spacer(modifier = Modifier.width(12.dp))
            labelCol()
        } else {
            labelCol()
            Spacer(modifier = Modifier.width(12.dp))
            logoBox()
        }
    }
}

private typealias Hierarchy = com.tanush.bouncer.ui.theme.ColorHierarchy

@Composable
fun VIPCarouselCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)

    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()

    val statusLower = match.status.lowercase()
    val isLive = match.isLive || statusLower.contains("live") ||
        statusLower.contains("batting") || statusLower.contains("bowling")
    val isCompleted = statusLower.contains("won") || statusLower.contains("result")

    val infiniteTransition = rememberInfiniteTransition(label = "carouselCard")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
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
        onClick = { haptic.click(); onClick() }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF151515), Color(0xFF0C1628), Color(0xFF0A0A0A)),
                            start = Offset.Zero,
                            end = Offset(800f, 800f)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            if (isLive) listOf(
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
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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

                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .shadow(4.dp, CircleShape, spotColor = StatusLive)
                                .background(StatusLive, CircleShape)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CarouselTeamRow(
                        team = team1,
                        scoreDisplay = score1Display,
                        isLeft = true,
                        modifier = Modifier.weight(1f),
                        hierarchy = hierarchy
                    )

                    Text(
                        text = "vs",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    CarouselTeamRow(
                        team = team2,
                        scoreDisplay = score2Display,
                        isLeft = false,
                        modifier = Modifier.weight(1f),
                        hierarchy = hierarchy
                    )
                }

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

@Composable
private fun CarouselTeamRow(
    team: CricketMatch.Team?,
    scoreDisplay: String,
    isLeft: Boolean,
    modifier: Modifier,
    hierarchy: Hierarchy
) {
    val logoBox: @Composable () -> Unit = {
        TeamLogoCircle(
            team = team,
            size = 32.dp,
            logoSize = 48,
            padding = 5.dp,
            background = Color.White.copy(alpha = 0.08f),
            borderWidth = 0.5.dp,
            borderColor = Color.White.copy(alpha = 0.1f)
        )
    }
    val labelCol: @Composable () -> Unit = {
        Column(horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End) {
            Text(
                text = teamAcronym(team),
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (scoreDisplay != "-") {
                Text(
                    text = scoreDisplay,
                    color = hierarchy.secondary.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = modifier
    ) {
        if (isLeft) {
            logoBox()
            Spacer(modifier = Modifier.width(8.dp))
            labelCol()
        } else {
            labelCol()
            Spacer(modifier = Modifier.width(8.dp))
            logoBox()
        }
    }
}

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
        modifier = modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp),
            spotColor = Color.Black.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(0.5.dp, GlassBorder),
        onClick = { haptic.click(); onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                Text(text = subtitle, color = TextTertiary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun EliteSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
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

@Composable
fun EliteStatusChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()

    Surface(
        modifier = modifier.shadow(
            elevation = if (isSelected) 8.dp else 2.dp,
            shape = RoundedCornerShape(12.dp),
            spotColor = if (isSelected) GoldPrimary.copy(alpha = 0.3f) else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) GoldPrimary else GlassSurface,
        border = BorderStroke(if (isSelected) 0.dp else 0.5.dp, GlassBorder),
        onClick = { haptic.click(); onClick() }
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

@Composable
private fun ShimmerSweepBox(
    modifier: Modifier,
    cornerDp: Dp,
    glowAlpha: Float?,
    shimmerStart: Float,
    shimmerWidth: Float,
    content: @Composable () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = modifier
            .drawBehind {
                if (glowAlpha != null) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(hierarchy.secondary.primary.copy(alpha = glowAlpha), Color.Transparent)
                        ),
                        radius = size.width * 0.6f,
                        center = Offset(size.width * 0.5f, size.height * -0.2f)
                    )
                }
            }
            .border(
                0.5.dp,
                Brush.linearGradient(
                    listOf(
                        hierarchy.secondary.primary.copy(alpha = if (glowAlpha != null) 0.15f else 0.1f),
                        Color.White.copy(alpha = if (glowAlpha != null) 0.05f else 0.03f),
                        hierarchy.secondary.primary.copy(alpha = if (glowAlpha != null) 0.15f else 0.1f)
                    )
                ),
                RoundedCornerShape(cornerDp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                hierarchy.secondary.primary.copy(alpha = if (glowAlpha != null) 0.05f else 0.04f),
                                Color.White.copy(alpha = if (glowAlpha != null) 0.08f else 0.06f),
                                hierarchy.secondary.primary.copy(alpha = if (glowAlpha != null) 0.05f else 0.04f),
                                Color.Transparent
                            ),
                            start = Offset(shimmerStart, 0f),
                            end = Offset(shimmerStart + shimmerWidth, size.height)
                        )
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun EliteMatchCardSkeleton(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -500f, targetValue = 1500f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val skeletonColor = Color.White.copy(alpha = 0.08f).copy(alpha = pulseAlpha)

    ShimmerSweepBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.05f), BackgroundBase.copy(alpha = 0.8f))
                )
            ),
        cornerDp = 24.dp,
        glowAlpha = null,
        shimmerStart = shimmerTranslate,
        shimmerWidth = 400f
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(100.dp, 10.dp, skeletonColor, 5.dp)
                SkeletonBox(50.dp, 20.dp, skeletonColor, 10.dp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).background(skeletonColor, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        SkeletonBox(40.dp, 16.dp, skeletonColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBox(60.dp, 12.dp, skeletonColor)
                    }
                }

                Box(modifier = Modifier.size(28.dp).background(skeletonColor, CircleShape))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        SkeletonBox(40.dp, 16.dp, skeletonColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBox(60.dp, 12.dp, skeletonColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(44.dp).background(skeletonColor, CircleShape))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(skeletonColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun EliteHeroMatchSkeleton(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroSkeleton")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -600f, targetValue = 1800f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "heroShimmer"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.05f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )
    val skeletonColor = Color.White.copy(alpha = 0.08f).copy(alpha = 0.6f)

    ShimmerSweepBox(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), BackgroundBase))
            ),
        cornerDp = 28.dp,
        glowAlpha = glowPulse,
        shimmerStart = shimmerTranslate,
        shimmerWidth = 500f
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(120.dp, 12.dp, skeletonColor, 6.dp)
                SkeletonBox(70.dp, 26.dp, skeletonColor, 13.dp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    if (index == 1) {
                        Box(modifier = Modifier.size(40.dp).background(skeletonColor, CircleShape))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(64.dp).background(skeletonColor, CircleShape))
                        Spacer(modifier = Modifier.height(12.dp))
                        SkeletonBox(50.dp, 20.dp, skeletonColor, 6.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        SkeletonBox(80.dp, 16.dp, skeletonColor)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(skeletonColor, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) = EliteSectionHeader(title, modifier, actionText, onActionClick)

@Composable
fun StatusFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = EliteStatusChip(text, isSelected, onClick, modifier)

@Composable
fun MatchCarouselCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = VIPCarouselCard(match, onClick, modifier)

@Composable
fun HeroMatchSkeleton(modifier: Modifier = Modifier) = EliteHeroMatchSkeleton(modifier)
