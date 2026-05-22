package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.tanush.bouncer.utils.getMatchStatusInfo
import com.tanush.bouncer.utils.parseScore
import com.tanush.bouncer.utils.rememberHapticFeedback
import com.tanush.bouncer.utils.toDisplayString
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.GoldLight

@Composable
private fun rememberPulse(
    initial: Float = 0.6f,
    target: Float = 1f,
    durationMillis: Int = 1000,
    label: String = "pulse"
): Float {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = initial,
        targetValue = target,
        animationSpec = infiniteRepeatable(tween(durationMillis), RepeatMode.Reverse),
        label = label
    ).value
}

@Composable
private fun SectionLabel(text: String, color: Color, fontSize: Int = 10, letterSpacing: Float = 1f) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = letterSpacing.sp
    )
}

private fun teamShortLabel(acronym: String?, name: String?): String =
    acronym ?: name?.take(3)?.uppercase() ?: "TBD"

/**
 * ExpandableMatchCard with tap-to-expand mini scorecard preview
 * Shows a compact card that expands to reveal detailed scorecard information
 */
@Composable
fun ExpandableMatchCardV2(
    match: CricketMatch,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val haptic = rememberHapticFeedback()

    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    val statusInfo = getMatchStatusInfo(match.status)
    val isLive = statusInfo.isLive || match.isLive

    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "expandProgress"
    )

    val cardElevation by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "elevation"
    )

    val cardDescription = "Match card for ${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"}. Tap to ${if (isExpanded) "collapse" else "expand"} details."

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = cardElevation,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isLive) colors.statusInfo.copy(alpha = 0.4f) else GoldPrimary.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = GoldPrimary.copy(alpha = 0.2f))
            ) {
                haptic.click()
                isExpanded = !isExpanded
            }
            .semantics { contentDescription = cardDescription },
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.matchTitle.substringBefore(",").trim().uppercase(),
                    color = GoldLight.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLive) LiveBadge()

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = expandProgress * 180f }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamScoreColumn(
                    label = teamShortLabel(team1?.acronym, team1?.name),
                    score = scoreInfo1.toDisplayString(),
                    alignEnd = false,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(GlassSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                TeamScoreColumn(
                    label = teamShortLabel(team2?.acronym, team2?.name),
                    score = scoreInfo2.toDisplayString(),
                    alignEnd = true,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!match.scrapedBatsmen.isNullOrEmpty()) {
                        SectionLabel("BATTING", colors.textMuted)
                        Spacer(modifier = Modifier.height(8.dp))

                        match.scrapedBatsmen?.take(2)?.forEach { batsman ->
                            val runsInt = batsman.runs.toIntOrNull() ?: 0
                            val ballsInt = batsman.balls.toIntOrNull() ?: 0
                            MiniPlayerRow(
                                name = batsman.name,
                                stat = "${batsman.runs}(${batsman.balls})",
                                isHighlighted = ballsInt > 0 && runsInt.toFloat() / ballsInt > 1.5
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    match.currentBowler?.let { bowler ->
                        SectionLabel("BOWLING", colors.textMuted)
                        Spacer(modifier = Modifier.height(8.dp))

                        MiniPlayerRow(
                            name = bowler.name,
                            stat = "${bowler.overs}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}",
                            isHighlighted = (bowler.wickets.toIntOrNull() ?: 0) >= 2
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    match.recentBalls?.takeIf { it.isNotEmpty() }?.let { balls ->
                        RecentBallsTimeline(
                            balls = balls.takeLast(12),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View Full Scorecard",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamScoreColumn(
    label: String,
    score: String,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = score,
            color = GoldPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun MiniPlayerRow(
    name: String,
    stat: String,
    isHighlighted: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isHighlighted) colors.textPrimary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stat,
            color = if (isHighlighted) hierarchy.secondary.primary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LiveBadge() {
    val colors = BouncerDesignSystem.colors
    val pulse = rememberPulse(label = "liveBadge")

    Surface(
        color = colors.statusLive.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.statusLive.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = pulse }
                    .background(colors.statusLive, CircleShape)
            )
            Text(
                text = "LIVE",
                color = colors.statusLive,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * LiveCommentaryPeek - Scrollable recent commentary overlay
 * Shows the most recent commentary entries in a compact scrollable view
 */
@Composable
fun LiveCommentaryPeek(
    commentaryItems: List<String>,
    modifier: Modifier = Modifier,
    maxVisibleItems: Int = 3
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()
    val infiniteTransition = rememberInfiniteTransition(label = "commentaryPeek")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    LaunchedEffect(commentaryItems.size) {
        if (commentaryItems.isNotEmpty()) {
            scrollState.animateScrollTo(0)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = (maxVisibleItems * 60).dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface1.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    hierarchy.secondary.primary.copy(alpha = shimmer * 0.3f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("COMMENTARY", hierarchy.secondary.primary, fontSize = 11)

                Text(
                    text = "${commentaryItems.size} entries",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ((maxVisibleItems - 1) * 50).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                commentaryItems.take(maxVisibleItems).forEachIndexed { index, item ->
                    val alpha = 1f - (index * 0.2f).coerceAtMost(0.5f)

                    Text(
                        text = item,
                        color = colors.textPrimary.copy(alpha = alpha),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    if (index < minOf(commentaryItems.size - 1, maxVisibleItems - 1)) {
                        HorizontalDivider(
                            color = colors.glassBorder.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            if (commentaryItems.size > maxVisibleItems) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "+ ${commentaryItems.size - maxVisibleItems} more",
                        color = colors.textMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * TeamMomentumIndicator with animated arrows
 * Visualizes team momentum using animated directional indicators
 */
@Composable
fun TeamMomentumIndicator(
    team1Name: String,
    team2Name: String,
    momentumValue: Float,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedMomentum by animateFloatAsState(
        targetValue = momentumValue,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "momentum"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "momentumArrows")

    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowPulse"
    )

    val momentumDescription = when {
        animatedMomentum < -0.3f -> "$team1Name has strong momentum"
        animatedMomentum > 0.3f -> "$team2Name has strong momentum"
        else -> "Momentum is even between both teams"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = momentumDescription },
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionLabel("MOMENTUM", colors.textMuted)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = team1Name.take(3).uppercase(),
                    color = if (animatedMomentum < 0) hierarchy.secondary.primary else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = team2Name.take(3).uppercase(),
                    color = if (animatedMomentum > 0) hierarchy.secondary.primary else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GlassSurface)
                )

                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .align(Alignment.Center)
                        .background(colors.textMuted)
                )

                val indicatorOffset = ((animatedMomentum + 1) / 2)

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.1f)
                        .height(24.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = ((indicatorOffset * 0.9f) * 300).dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (animatedMomentum < 0)
                                    listOf(colors.statusSuccess, colors.statusSuccess.copy(alpha = 0.5f))
                                else
                                    listOf(colors.statusInfo.copy(alpha = 0.5f), colors.statusInfo)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                if (abs(animatedMomentum) > 0.2f) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = (animatedMomentum * 60).dp +
                                    (if (animatedMomentum < 0) -arrowOffset else arrowOffset).dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(minOf(3, (abs(animatedMomentum) * 5).toInt().coerceAtLeast(1))) {
                            Icon(
                                imageVector = if (animatedMomentum < 0)
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                                else
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (animatedMomentum < 0) colors.statusSuccess else colors.statusInfo,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    abs(animatedMomentum) < 0.2f -> "BALANCED"
                    abs(animatedMomentum) < 0.5f -> "SLIGHT ADVANTAGE"
                    abs(animatedMomentum) < 0.75f -> "MOMENTUM SHIFT"
                    else -> "DOMINANCE"
                },
                color = when {
                    abs(animatedMomentum) < 0.2f -> colors.textMuted
                    animatedMomentum < 0 -> colors.statusSuccess
                    else -> colors.statusInfo
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * WeatherPitchOverlay showing conditions icons
 * Displays weather and pitch conditions with relevant icons
 */
@Composable
fun WeatherPitchOverlay(
    weather: String = "Sunny",
    temperature: Int? = null,
    humidity: Int? = null,
    pitchType: String = "Balanced",
    windSpeed: String? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val weatherKey = weather.lowercase()
    val weatherIcon = when (weatherKey) {
        "sunny", "clear" -> Icons.Default.WbSunny
        "cloudy", "overcast" -> Icons.Default.Cloud
        "rainy", "rain" -> Icons.Default.WaterDrop
        "partly cloudy" -> Icons.Default.FilterDrama
        else -> Icons.Default.WbSunny
    }

    val weatherColor = when (weatherKey) {
        "sunny", "clear" -> hierarchy.secondary.primary
        "cloudy", "overcast" -> colors.textSecondary
        "rainy", "rain" -> colors.statusInfo
        else -> hierarchy.secondary.primary
    }

    val pitchColor = when (pitchType.lowercase()) {
        "batting", "flat" -> colors.statusSuccess
        "bowling", "green" -> ChartGreen
        "spin", "turning" -> ChartPurple
        "seaming" -> ChartCyan
        else -> colors.textSecondary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface1.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = weatherIcon,
                    contentDescription = "Weather: $weather",
                    tint = weatherColor,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = weather.uppercase(),
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    if (temperature != null) {
                        Text(
                            text = "${temperature}°C",
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(colors.glassBorder)
            )

            if (humidity != null) {
                ConditionChip(
                    icon = Icons.Default.WaterDrop,
                    iconTint = colors.statusInfo,
                    text = "$humidity%",
                    textColor = colors.textSecondary,
                    contentDescription = "Humidity"
                )
            }

            if (windSpeed != null) {
                ConditionChip(
                    icon = Icons.Default.Air,
                    iconTint = ChartCyan,
                    text = windSpeed,
                    textColor = colors.textSecondary,
                    contentDescription = "Wind"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .background(pitchColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, pitchColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = pitchType.uppercase(),
                    color = pitchColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun ConditionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color,
    contentDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * MatchCountdown animated timer for upcoming matches
 * Displays countdown to match start with animated digits
 */
@Composable
fun MatchCountdown(
    targetTimeMillis: Long,
    matchTitle: String = "",
    onMatchStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    var remainingTime by remember { mutableLongStateOf(targetTimeMillis - System.currentTimeMillis()) }

    LaunchedEffect(targetTimeMillis) {
        while (remainingTime > 0) {
            delay(1000)
            remainingTime = (targetTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
            if (remainingTime <= 0) {
                onMatchStart()
            }
        }
    }

    val days = (remainingTime / (1000 * 60 * 60 * 24)).toInt()
    val hours = ((remainingTime / (1000 * 60 * 60)) % 24).toInt()
    val minutes = ((remainingTime / (1000 * 60)) % 60).toInt()
    val seconds = ((remainingTime / 1000) % 60).toInt()

    val pulse = rememberPulse(initial = 1f, target = 1.05f, durationMillis = 500, label = "countdown")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    GoldPrimary.copy(alpha = 0.3f),
                    GoldPrimary.copy(alpha = 0.1f),
                    GoldPrimary.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (matchTitle.isNotEmpty()) {
                Text(
                    text = matchTitle,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "MATCH STARTS IN",
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (days > 0) {
                    CountdownUnit(value = days, label = "DAYS", pulse = pulse)
                    CountdownSeparator()
                }

                CountdownUnit(
                    value = hours,
                    label = "HRS",
                    pulse = if (days == 0) pulse else 1f
                )
                CountdownSeparator()

                CountdownUnit(
                    value = minutes,
                    label = "MIN",
                    pulse = if (days == 0 && hours == 0) pulse else 1f
                )
                CountdownSeparator()

                CountdownUnit(
                    value = seconds,
                    label = "SEC",
                    pulse = pulse,
                    highlight = remainingTime < 60000
                )
            }

            if (remainingTime < 3600000) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Starting Soon!",
                    color = colors.statusSuccess,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String,
    pulse: Float = 1f,
    highlight: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .background(
                    if (highlight) hierarchy.secondary.primary.copy(alpha = 0.2f) else GlassSurface,
                    RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    if (highlight) hierarchy.secondary.primary.copy(alpha = 0.5f) else colors.glassBorder.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                color = if (highlight) hierarchy.secondary.primary else colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun CountdownSeparator() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val alpha = rememberPulse(initial = 0.3f, target = 1f, durationMillis = 500, label = "separator")

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(hierarchy.secondary.primary, CircleShape)
            )
        }
    }
}

/**
 * QuickStatsRow - Horizontal compact stats display
 * Shows key match statistics in a compact horizontal scrollable row
 */
@Composable
fun QuickStatsRowEnhanced(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            stats.forEach { (label, value) ->
                QuickStatItem(label = label, value = value)
            }
        }
    }
}

@Composable
private fun QuickStatItem(
    label: String,
    value: String
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 60.dp)
    ) {
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label.uppercase(),
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * RecentBallsTimeline - Horizontal ball-by-ball display
 * Shows recent deliveries in a timeline format
 */
@Composable
fun RecentBallsTimeline(
    balls: List<String>,
    modifier: Modifier = Modifier,
    showOverMarkers: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECENT",
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        balls.forEachIndexed { index, ball ->
            if (showOverMarkers && index > 0 && index % 6 == 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    hierarchy.secondary.primary.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            TimelineBall(
                ball = ball,
                isLatest = index == balls.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineBall(
    ball: String,
    isLatest: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val (bgColor, textColor) = when {
        ball.contains("W", ignoreCase = true) -> colors.statusLive to Color.White
        ball == "6" -> hierarchy.secondary.primary to colors.backgroundBase
        ball == "4" -> colors.statusInfo to colors.backgroundBase
        ball == "0" || ball == "." -> DotBall to colors.textMuted
        ball.contains("WD") || ball.contains("NB") -> colors.statusWarning to colors.backgroundBase
        else -> GlassSurface to colors.textPrimary
    }

    val pulse = rememberPulse(initial = 1f, target = 1.15f, durationMillis = 400, label = "latestBall")

    Box(
        modifier = Modifier
            .size(if (isLatest) 32.dp else 28.dp)
            .graphicsLayer {
                if (isLatest) {
                    scaleX = pulse
                    scaleY = pulse
                }
            }
            .shadow(
                elevation = if (isLatest) 8.dp else 2.dp,
                shape = CircleShape,
                spotColor = bgColor.copy(alpha = 0.5f)
            )
            .background(bgColor, CircleShape)
            .border(
                if (isLatest) 2.dp else 0.5.dp,
                if (isLatest) hierarchy.secondary.primary else GlassBorder,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                ball == "0" -> "•"
                ball == "." -> "•"
                ball.length > 2 -> ball.take(2)
                else -> ball
            },
            color = textColor,
            fontSize = if (isLatest) 12.sp else 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}
