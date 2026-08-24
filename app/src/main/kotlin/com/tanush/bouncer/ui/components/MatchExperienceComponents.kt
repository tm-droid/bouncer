package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/* ---------- Shared helpers ---------- */

private fun eventColorFor(
    runs: String?,
    isWicket: Boolean,
    colors: BouncerColors,
    hierarchy: ColorHierarchy
): Color = when {
    isWicket -> colors.statusLive
    runs == "6" -> hierarchy.secondary.primary
    runs == "4" -> colors.statusInfo
    runs == "0" || runs == "." -> DotBall
    else -> colors.textSecondary
}

@Composable
private fun rememberPulse(
    label: String,
    initial: Float,
    target: Float,
    durationMs: Int
): State<Float> {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = initial,
        targetValue = target,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "${label}Anim"
    )
}

/**
 * Premium live commentary card with animations
 */
@Composable
fun LiveCommentaryCardEnhanced(
    overNumber: String,
    ballNumber: String,
    commentary: String,
    runs: String?,
    isWicket: Boolean,
    isBoundary: Boolean,
    bowlerName: String?,
    batsmanName: String?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val eventColor = eventColorFor(runs, isWicket, colors, hierarchy)
    val highlighted = isWicket || isBoundary
    val glowPulse by rememberPulse("commentary", 0.3f, 0.7f, 1500)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (highlighted) commentaryGlowModifier(eventColor, glowPulse) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = if (highlighted) BorderStroke(1.dp, eventColor.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverBallIndicator(
                overNumber = overNumber,
                ballNumber = ballNumber,
                runs = runs,
                eventColor = eventColor
            )
            CommentaryContent(
                modifier = Modifier.weight(1f),
                commentary = commentary,
                runs = runs,
                isWicket = isWicket,
                bowlerName = bowlerName,
                batsmanName = batsmanName
            )
        }
    }
}

private fun commentaryGlowModifier(eventColor: Color, glowPulse: Float): Modifier =
    Modifier.drawBehind {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(eventColor.copy(alpha = glowPulse * 0.1f), Color.Transparent)
            )
        )
    }

@Composable
private fun OverBallIndicator(
    overNumber: String,
    ballNumber: String,
    runs: String?,
    eventColor: Color
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Text(
            text = overNumber,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = hierarchy.secondary.primary
        )
        Text(
            text = ".$ballNumber",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        runs?.let { RunsBadge(runs = it, eventColor = eventColor) }
    }
}

@Composable
private fun RunsBadge(runs: String, eventColor: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(eventColor.copy(alpha = 0.15f), CircleShape)
            .border(1.dp, eventColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (runs == ".") "0" else runs,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = eventColor
        )
    }
}

@Composable
private fun CommentaryContent(
    modifier: Modifier,
    commentary: String,
    runs: String?,
    isWicket: Boolean,
    bowlerName: String?,
    batsmanName: String?
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            bowlerName?.let {
                Text(
                    text = "to $it",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )
            }
            batsmanName?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            }
        }

        Text(
            text = commentary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = colors.textPrimary,
            lineHeight = 18.sp
        )

        CommentaryEventBadge(isWicket = isWicket, runs = runs, colors = colors, hierarchy = hierarchy)
    }
}

@Composable
private fun CommentaryEventBadge(
    isWicket: Boolean,
    runs: String?,
    colors: BouncerColors,
    hierarchy: ColorHierarchy
) {
    when {
        isWicket -> EventBadge(text = "WICKET", color = colors.statusLive)
        runs == "6" -> EventBadge(text = "SIX", color = hierarchy.secondary.primary)
        runs == "4" -> EventBadge(text = "FOUR", color = colors.statusInfo)
    }
}

@Composable
private fun EventBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Animated recent balls strip with detailed view
 */
@Composable
fun EnhancedRecentBallsStrip(
    balls: List<BallEvent>,
    modifier: Modifier = Modifier
) {
    var selectedBall by remember { mutableStateOf<BallEvent?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(balls, key = { it.id }) { ball ->
                AnimatedBallChip(
                    ball = ball,
                    isSelected = selectedBall == ball,
                    onClick = { selectedBall = if (selectedBall == ball) null else ball }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedBall != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedBall?.let { SelectedBallDetail(ball = it) }
        }
    }
}

@Composable
private fun SelectedBallDetail(ball: BallEvent) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface1) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Over ${ball.over}.${ball.ballInOver}",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
                Text(
                    text = ball.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            }
            ball.batsmanName?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = hierarchy.secondary.primary
                )
            }
        }
    }
}

data class BallEvent(
    val id: String,
    val over: Int,
    val ballInOver: Int,
    val runs: String,
    val isWicket: Boolean,
    val description: String,
    val batsmanName: String? = null,
    val bowlerName: String? = null
)

@Composable
private fun AnimatedBallChip(
    ball: BallEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val color = eventColorFor(ball.runs, ball.isWicket, colors, hierarchy)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "ballScale"
    )

    val glowAlpha by rememberPulse("ball_${ball.id}", 0.3f, 0.6f, 1000)
    val showGlow = isSelected || ball.isWicket || ball.runs == "6" || ball.runs == "4"

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(36.dp)
            .then(if (showGlow) ballGlowModifier(color, glowAlpha) else Modifier)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(
                if (isSelected) 2.dp else 1.dp,
                color.copy(alpha = if (isSelected) 0.8f else 0.4f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BallChipLabel(ball = ball, color = color)
    }
}

private fun ballGlowModifier(color: Color, glowAlpha: Float): Modifier =
    Modifier.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = glowAlpha * 0.4f), Color.Transparent)
            ),
            radius = size.maxDimension * 0.8f
        )
    }

@Composable
private fun BallChipLabel(ball: BallEvent, color: Color) {
    val label = when {
        ball.isWicket -> "W"
        ball.runs == "." -> "0"
        else -> ball.runs
    }
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (ball.isWicket) FontWeight.Black else FontWeight.Bold,
        color = color
    )
}

/**
 * Match key moments card
 */
@Composable
fun KeyMomentsCard(
    moments: List<KeyMoment>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KeyMomentsHeader(count = moments.size)
            moments.take(5).forEachIndexed { index, moment ->
                KeyMomentItem(moment = moment, index = index)
                if (index < moments.size - 1 && index < 4) {
                    HorizontalDivider(
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyMomentsHeader(count: Int) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "KEY MOMENTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = hierarchy.secondary.primary
        )
        Text(
            text = "$count moments",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted
        )
    }
}

data class KeyMoment(
    val id: String,
    val type: MomentType,
    val title: String,
    val description: String,
    val over: String,
    val impactScore: Float // 0-1
) {
    enum class MomentType {
        WICKET, BOUNDARY, MILESTONE, PARTNERSHIP, REVIEW, OTHER
    }
}

private fun momentColorFor(
    type: KeyMoment.MomentType,
    colors: BouncerColors,
    hierarchy: ColorHierarchy
): Color = when (type) {
    KeyMoment.MomentType.WICKET -> colors.statusLive
    KeyMoment.MomentType.BOUNDARY -> hierarchy.secondary.primary
    KeyMoment.MomentType.MILESTONE -> hierarchy.secondary.primary
    KeyMoment.MomentType.PARTNERSHIP -> colors.statusSuccess
    KeyMoment.MomentType.REVIEW -> colors.statusInfo
    KeyMoment.MomentType.OTHER -> colors.textSecondary
}

private fun momentIconFor(type: KeyMoment.MomentType): ImageVector = when (type) {
    KeyMoment.MomentType.WICKET -> Icons.Default.SportsCricket
    KeyMoment.MomentType.BOUNDARY -> Icons.Default.Whatshot
    KeyMoment.MomentType.MILESTONE -> Icons.Default.Star
    KeyMoment.MomentType.PARTNERSHIP -> Icons.Default.Handshake
    KeyMoment.MomentType.REVIEW -> Icons.Default.Visibility
    KeyMoment.MomentType.OTHER -> Icons.Default.FiberManualRecord
}

@Composable
private fun KeyMomentItem(
    moment: KeyMoment,
    index: Int
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    var isExpanded by remember { mutableStateOf(false) }

    val momentColor = momentColorFor(moment.type, colors, hierarchy)
    val momentIcon = momentIconFor(moment.type)

    val animatedEnter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300, delayMillis = index * 50),
        label = "momentEnter"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = animatedEnter; translationX = (1f - animatedEnter) * 50f }
            .clickable { isExpanded = !isExpanded }
    ) {
        KeyMomentRow(moment = moment, momentColor = momentColor, momentIcon = momentIcon)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = moment.description,
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 48.dp, top = 8.dp)
            )
        }
    }
}

@Composable
private fun KeyMomentRow(
    moment: KeyMoment,
    momentColor: Color,
    momentIcon: ImageVector
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(momentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = momentIcon,
                contentDescription = null,
                tint = momentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = moment.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = "Over ${moment.over}",
                fontSize = 11.sp,
                color = colors.textMuted
            )
        }
        ImpactIndicator(score = moment.impactScore, color = momentColor)
    }
}

@Composable
private fun ImpactIndicator(
    score: Float,
    color: Color
) {
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        val ringColor = GlassBorder
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = ringColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = score * 360f,
                useCenter = false,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(score * 10).toInt()}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Scorecard summary card
 */
@Composable
fun ScorecardSummaryCard(
    teamName: String,
    teamLogo: String?,
    score: String,
    overs: String,
    extras: String?,
    runRate: String?,
    topScorer: PlayerPerformance?,
    topBowler: PlayerPerformance?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "scorecard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 16.dp,
            shape = RoundedCornerShape(24.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Box {
            ScorecardShimmer(modifier = Modifier.matchParentSize(), shimmerOffset = shimmerOffset)
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ScorecardTeamHeader(
                    teamName = teamName,
                    teamLogo = teamLogo,
                    score = score,
                    overs = overs,
                    extras = extras,
                    runRate = runRate
                )
                HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.3f))
                TopPerformersRow(topScorer = topScorer, topBowler = topBowler)
            }
        }
    }
}

@Composable
private fun ScorecardShimmer(modifier: Modifier, shimmerOffset: Float) {
    Canvas(modifier = modifier) {
        val shimmerStart = shimmerOffset * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                start = Offset(shimmerStart, 0f),
                end = Offset(shimmerStart + size.width * 0.3f, size.height)
            )
        )
    }
}

@Composable
private fun ScorecardTeamHeader(
    teamName: String,
    teamLogo: String?,
    score: String,
    overs: String,
    extras: String?,
    runRate: String?
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamLogoBadge(teamName = teamName, teamLogo = teamLogo)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = teamName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                runRate?.let {
                    Text(text = "RR: $it", fontSize = 11.sp, color = colors.textMuted)
                }
                extras?.let {
                    Text(text = "Extras: $it", fontSize = 11.sp, color = colors.textMuted)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = score,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = hierarchy.secondary.primary
            )
            Text(
                text = "($overs overs)",
                fontSize = 11.sp,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun TeamLogoBadge(teamName: String, teamLogo: String?) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, hierarchy.secondary.primary.copy(alpha = 0.4f), CircleShape)
            .clip(CircleShape)
            .background(colors.surface1),
        contentAlignment = Alignment.Center
    ) {
        if (!teamLogo.isNullOrEmpty()) {
            CoilImage(
                model = teamLogo,
                contentDescription = teamName,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = teamName.take(3).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = hierarchy.secondary.primary
            )
        }
    }
}

@Composable
private fun TopPerformersRow(
    topScorer: PlayerPerformance?,
    topBowler: PlayerPerformance?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        topScorer?.let {
            PerformerMini(
                label = "Top Scorer",
                name = it.name,
                stat = it.stat,
                modifier = Modifier.weight(1f)
            )
        }
        topBowler?.let {
            PerformerMini(
                label = "Best Bowler",
                name = it.name,
                stat = it.stat,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

data class PlayerPerformance(
    val name: String,
    val stat: String
)

@Composable
private fun PerformerMini(
    label: String,
    name: String,
    stat: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted,
            letterSpacing = 0.5.sp
        )
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stat,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = hierarchy.secondary.primary
        )
    }
}
