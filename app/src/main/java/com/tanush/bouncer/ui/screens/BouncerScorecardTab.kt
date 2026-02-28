package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.EmptyStateView
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.strikeRow
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors

// ─────────────────────────────────────────────────────────────────────────────
//  Scorecard Tab — Glass UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScorecardTab(
    scorecard: Scorecard?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            ScorecardSkeleton(modifier = modifier)
        }
        scorecard == null || scorecard.innings.isEmpty() -> {
            EmptyStateView(
                icon = Icons.Outlined.Scoreboard,
                title = "Scorecard not available yet",
                message = "The scorecard will appear once the match data is ready.",
                modifier = modifier.padding(BouncerDesignSystem.dimens.xxl)
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = BouncerDesignSystem.dimens.screenHorizontal,
                    vertical = BouncerDesignSystem.dimens.md
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp) // More spacing between sections as per _22
            ) {
                // Score Summary Hero (Mini version from _22 top section)
                item {
                    ScoreSummaryHero(scorecard = scorecard)
                }

                val innings = scorecard.innings
                itemsIndexed(innings, key = { idx, inn -> "innings_${idx}_${inn.teamName}" }) { index, inn ->
                    InningsSection(
                        innings = inn,
                        initiallyExpanded = index == innings.lastIndex,
                    )
                }

                item { Spacer(modifier = Modifier.height(BouncerDesignSystem.dimens.xxl)) }
            }
        }
    }
}

@Composable
private fun ScoreSummaryHero(scorecard: Scorecard?, modifier: Modifier = Modifier) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    val lastInnings = scorecard?.innings?.lastOrNull()
    val team1 = scorecard?.innings?.getOrNull(0)
    val team2 = scorecard?.innings?.getOrNull(1)

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        borderOpacity = 0.15f
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Team 1
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TeamPrimaryColors.getTeamColor(team1?.teamName ?: "").copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = TeamPrimaryColors.getTeamEmoji(team1?.teamName ?: ""), fontSize = 18.sp)
                }
                Text(
                    text = team1?.teamName?.take(3)?.uppercase() ?: "T1",
                    style = typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = TeamPrimaryColors.getTeamColor(team1?.teamName ?: "").copy(alpha = 0.7f)
                )
            }

            // Score
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = lastInnings?.score?.split("/")?.getOrNull(0) ?: "0",
                        style = typography.headingMedium.copy(fontSize = 32.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "-${lastInnings?.score?.split("/")?.getOrNull(1) ?: "0"}",
                        style = typography.headingSmall.copy(fontSize = 18.sp),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = "INNINGS BREAK",
                    style = typography.labelSmall.copy(fontSize = 8.sp),
                    color = colors.goldPrimary,
                    letterSpacing = 1.sp
                )
            }

            // Team 2
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = team2?.teamName?.take(3)?.uppercase() ?: "T2",
                    style = typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = TeamPrimaryColors.getTeamColor(team2?.teamName ?: "").copy(alpha = 0.7f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TeamPrimaryColors.getTeamColor(team2?.teamName ?: "").copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = TeamPrimaryColors.getTeamEmoji(team2?.teamName ?: ""), fontSize = 18.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single Innings Section (expandable glass card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InningsSection(
    innings: Innings,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val colors = BouncerDesignSystem.colors
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.12f),
                    1f to Color.White.copy(alpha = 0.04f)
                ),
                RoundedCornerShape(24.dp)
            )
    ) {
        // Sticky Header from _22
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .background(
                    if (isExpanded) 
                        TeamPrimaryColors.getTeamColor(innings.teamName).copy(alpha = 0.08f) 
                    else Color.Transparent
                )
                .padding(dimens.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(TeamPrimaryColors.getTeamColor(innings.teamName).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = TeamPrimaryColors.getTeamEmoji(innings.teamName), fontSize = 14.sp)
                }
                
                Text(
                    text = innings.teamName.uppercase(),
                    style = typography.labelMedium,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = innings.score,
                    style = typography.headingSmall.copy(fontSize = 18.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.goldPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isExpanded) {
            Column {
                BattingTable(innings.batsmen, innings.teamName)
                
                // Extras & Total Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "EXTRAS: ${innings.extras}",
                        style = typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "TOTAL: ${innings.score} (${innings.overs} Ov)",
                        style = typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.goldPrimary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                BowlingTable(innings.bowlers)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Fall of Wickets section stylized
                if (innings.fallOfWickets.isNotEmpty()) {
                    Text(
                        text = "FALL OF WICKETS",
                        style = typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.goldPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        letterSpacing = 0.8.sp
                    )
                    
                    val fowText = innings.fallOfWickets.joinToString(separator = "  •  ") { fow ->
                        "${fow.score} (${fow.batsman}, ${fow.overs} ov)"
                    }

                    Text(
                        text = fowText,
                        style = typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(16.dp)
                            .padding(bottom = 8.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BattingTable(batsmen: List<BatsmanStats>, teamName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BATSMEN",
                style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            listOf("R", "B", "4s", "6s", "SR").forEachIndexed { idx, label ->
                Text(
                    text = label,
                    style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(if (idx == 4) 50.dp else 30.dp),
                    textAlign = if (idx == 4) TextAlign.End else TextAlign.Center
                )
            }
        }

        batsmen.forEachIndexed { index, batsman ->
            BatsmanStatsRow(
                batsman = batsman,
                isLast = index == batsmen.lastIndex,
                teamName = teamName
            )
        }
    }
}

@Composable
private fun BatsmanStatsRow(
    batsman: BatsmanStats,
    isLast: Boolean,
    teamName: String
) {
    val typography = BouncerDesignSystem.typography
    val colors = BouncerDesignSystem.colors

    val runs = batsman.runs.toIntOrNull() ?: 0
    val isNotable = runs >= 50
    val isNotOut = batsman.dismissal.lowercase().let { it == "not out" || it == "batting" }
    
    val teamCode = teamName.split(" ").firstOrNull() ?: teamName
    val teamColor = TeamPrimaryColors.getTeamColor(teamCode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                if (isNotOut) Modifier.background(teamColor.copy(alpha = 0.05f)) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Column
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Initial Circle (Placeholder for Photo from _22)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👤",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                Column {
                    Text(
                        text = batsman.name,
                        style = typography.playerName.copy(fontSize = 13.sp),
                        color = if (isNotable) colors.goldPrimary else Color.White,
                        fontWeight = if (isNotable || isNotOut) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = if (batsman.dismissal.isNotBlank()) batsman.dismissal else "not out",
                        style = typography.bodySmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Stats Columns (Weights from _22 layout)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = batsman.runs,
                    style = typography.labelSmall.copy(fontSize = 13.sp),
                    color = if (isNotable) colors.goldPrimary else Color.White,
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = batsman.balls,
                    style = typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = batsman.fours,
                    style = typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = batsman.sixes,
                    style = typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = batsman.strikeRate,
                    style = typography.bodySmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = colors.goldPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.width(50.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        if (!isLast) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.05f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun BowlingTable(bowlers: List<BowlerStats>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Bowling Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BOWLERS",
                style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            listOf("O", "M", "R", "W", "ECO").forEachIndexed { idx, label ->
                Text(
                    text = label,
                    style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(if (idx == 4) 45.dp else 28.dp),
                    textAlign = if (idx == 4) TextAlign.End else TextAlign.Center
                )
            }
        }

        bowlers.forEachIndexed { index, bowler ->
            BowlerStatsRow(
                bowler = bowler,
                isLast = index == bowlers.lastIndex
            )
        }
    }
}

@Composable
private fun BowlerStatsRow(
    bowler: BowlerStats,
    isLast: Boolean
) {
    val typography = BouncerDesignSystem.typography
    val colors = BouncerDesignSystem.colors

    val wickets = bowler.wickets.toIntOrNull() ?: 0
    val isNotable = wickets >= 3

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bowler.name,
                style = typography.playerName.copy(fontSize = 12.sp),
                color = if (isNotable) colors.goldPrimary else Color.White,
                fontWeight = if (isNotable) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bowler.overs,
                    style = typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bowler.maidens,
                    style = typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bowler.runs,
                    style = typography.statValue.copy(fontSize = 11.sp),
                    color = Color.White,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bowler.wickets,
                    style = typography.statValue.copy(fontSize = 12.sp),
                    color = colors.goldPrimary,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = bowler.economy,
                    style = typography.bodySmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(45.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        if (!isLast) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.05f),
                thickness = 0.5.dp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Skeleton Loader (Glass placeholders)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScorecardSkeleton(modifier: Modifier = Modifier) {
    val dimens = BouncerDesignSystem.dimens
    val shimmerColor = Color.White.copy(alpha = 0.06f)

    Column(
        modifier = modifier.padding(dimens.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        Spacer(modifier = Modifier.height(dimens.md))

        repeat(2) {
            GlassCardSmall(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.sm)
                ) {
                    // Header placeholder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerColor)
                        )
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerColor)
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.08f)
                    )

                    // Table header placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerColor)
                    )

                    // Player row placeholders
                    repeat(6) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(shimmerColor)
                            )
                            Spacer(modifier = Modifier.width(dimens.sm))
                            repeat(5) { i ->
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(shimmerColor)
                                )
                                if (i < 4) Spacer(modifier = Modifier.width(dimens.xs))
                            }
                        }
                    }
                }
            }
        }
    }
}
