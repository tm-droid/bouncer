package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.GoldLight
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.utils.getMatchStatusInfo
import com.tanush.bouncer.utils.parseScore
import com.tanush.bouncer.utils.rememberHapticFeedback
import com.tanush.bouncer.utils.toDisplayString

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

    val cardDescription = "Match card for ${team1?.name ?: "Team 1"} vs ${team2?.name ?: "Team 2"}. " +
        "Tap to ${if (isExpanded) "collapse" else "expand"} details."

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
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            ExpandableCardHeader(
                title = match.matchTitle.substringBefore(",").trim().uppercase(),
                isLive = isLive,
                isExpanded = isExpanded,
                expandProgress = expandProgress
            )
            Spacer(modifier = Modifier.height(16.dp))
            ExpandableCardScoreRow(team1 = team1, team2 = team2)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                ExpandableCardExpandedContent(match = match, onClick = onClick)
            }
        }
    }
}

@Composable
private fun ExpandableCardHeader(
    title: String,
    isLive: Boolean,
    isExpanded: Boolean,
    expandProgress: Float
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
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
}

@Composable
private fun ExpandableCardScoreRow(team1: CricketMatch.Team?, team2: CricketMatch.Team?) {
    val colors = BouncerDesignSystem.colors
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)

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
}

@Composable
private fun ExpandableCardExpandedContent(match: CricketMatch, onClick: () -> Unit) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))

        if (!match.scrapedBatsmen.isNullOrEmpty()) {
            BattingSection(batsmen = match.scrapedBatsmen.orEmpty())
            Spacer(modifier = Modifier.height(12.dp))
        }

        match.currentBowler?.let { bowler ->
            BowlingSection(bowler = bowler)
            Spacer(modifier = Modifier.height(12.dp))
        }

        match.recentBalls?.takeIf { it.isNotEmpty() }?.let { balls ->
            RecentBallsTimeline(
                balls = balls.takeLast(12),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ViewFullScorecardButton(onClick = onClick)
    }
}

@Composable
private fun BattingSection(batsmen: List<ScrapedBatsman>) {
    val colors = BouncerDesignSystem.colors
    SectionLabel("BATTING", colors.textMuted)
    Spacer(modifier = Modifier.height(8.dp))

    batsmen.take(2).forEach { batsman ->
        val runsInt = batsman.runs.toIntOrNull() ?: 0
        val ballsInt = batsman.balls.toIntOrNull() ?: 0
        MiniPlayerRow(
            name = batsman.name,
            stat = "${batsman.runs}(${batsman.balls})",
            isHighlighted = ballsInt > 0 && runsInt.toFloat() / ballsInt > 1.5
        )
    }
}

@Composable
private fun BowlingSection(bowler: ScrapedBowler) {
    val colors = BouncerDesignSystem.colors
    SectionLabel("BOWLING", colors.textMuted)
    Spacer(modifier = Modifier.height(8.dp))

    MiniPlayerRow(
        name = bowler.name,
        stat = "${bowler.overs}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}",
        isHighlighted = (bowler.wickets.toIntOrNull() ?: 0) >= 2
    )
}

@Composable
private fun ViewFullScorecardButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
private fun MiniPlayerRow(name: String, stat: String, isHighlighted: Boolean = false) {
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
