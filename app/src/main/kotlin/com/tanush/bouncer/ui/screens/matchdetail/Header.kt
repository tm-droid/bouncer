package com.tanush.bouncer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.BgBase
import com.tanush.bouncer.ui.theme.BorderWidth
import com.tanush.bouncer.ui.theme.CornerRadius
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.theme.IconSize
import com.tanush.bouncer.ui.theme.LetterSpacing
import com.tanush.bouncer.ui.theme.LiveGreen
import com.tanush.bouncer.ui.theme.Spacing
import com.tanush.bouncer.ui.theme.TextMuted
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailHeader_Extracted(
    match: CricketMatch,
    onBackClick: () -> Unit,
    liveTeam1Score: String,
    liveTeam2Score: String,
    liveTeam1Overs: String,
    liveTeam2Overs: String,
    liveCurrentRunRate: String?,
    liveRequiredRunRate: String?
) {
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    val battingTeam = match.teams.find { it.isBatting } ?: team1
    val bowlingTeam = match.teams.find { !it.isBatting } ?: team2

    val team1Logo = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl)
    val team2Logo = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl)

    val score1 = liveTeam1Score.ifBlank { team1?.score ?: "-" }
    val score2 = liveTeam2Score.ifBlank { team2?.score ?: "-" }
    val overs1 = liveTeam1Overs.ifBlank { team1?.overs ?: "" }
    val overs2 = liveTeam2Overs.ifBlank { team2?.overs ?: "" }
    val team1IsBatting = team1?.isBatting == true
    val team2IsBatting = team2?.isBatting == true

    Column(modifier = Modifier.fillMaxWidth().background(BgBase)) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = match.matchTitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(BorderWidth.hairline, Gold400.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Gold400.copy(alpha = 0.8f), modifier = Modifier.size(IconSize.sm))
                }
            },
            actions = {
                IconButton(
                    onClick = { },
                    modifier = Modifier
                        .padding(end = Spacing.xs)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(BorderWidth.hairline, Gold400.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Share, null, tint = Gold400.copy(alpha = 0.8f), modifier = Modifier.size(IconSize.sm))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .clip(RoundedCornerShape(CornerRadius.lg))
                .background(GlassSurface)
                .border(BorderWidth.hairline, Color.White.copy(alpha = 0.12f), RoundedCornerShape(CornerRadius.lg))
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Gold400.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(CornerRadius.pill),
                    border = BorderStroke(BorderWidth.hairline, Gold400.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = match.matchType.ifBlank { "MATCH" }.uppercase(),
                        color = Gold400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }

                val isLive = match.status.contains("live", ignoreCase = true) ||
                    match.status.contains("batting", ignoreCase = true) ||
                    match.status.contains("bowling", ignoreCase = true)
                if (isLive) {
                    Surface(
                        color = LiveGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(CornerRadius.pill),
                        border = BorderStroke(BorderWidth.hairline, LiveGreen.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "LIVE",
                            color = LiveGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    CoilImage(
                        model = team1Logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                if (team1IsBatting) 2.dp else BorderWidth.hairline,
                                if (team1IsBatting) Gold400.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                                CircleShape
                            )
                    )
                    Text(team1?.acronym ?: "", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(score1, color = if (team1IsBatting) TextPrimary else TextSecondary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    if (overs1.isNotBlank()) {
                        Text("($overs1 Ov)", color = TextMuted, fontSize = 10.sp)
                    }
                }

                Text("VS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = Spacing.xs))

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    CoilImage(
                        model = team2Logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                if (team2IsBatting) 2.dp else BorderWidth.hairline,
                                if (team2IsBatting) Gold400.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                                CircleShape
                            )
                    )
                    Text(team2?.acronym ?: "", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(score2, color = if (team2IsBatting) TextPrimary else TextSecondary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    if (overs2.isNotBlank()) {
                        Text("($overs2 Ov)", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            Text(
                text = "Batting: ${battingTeam?.name ?: "-"} • Bowling: ${bowlingTeam?.name ?: "-"}",
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally)) {
                val battingOvers = if (team1IsBatting) overs1 else overs2
                val battingScore = if (team1IsBatting) score1 else score2
                if (battingOvers.isNotBlank()) StatPill_Extracted(label = "Overs", value = battingOvers)
                if (liveCurrentRunRate != null) {
                    StatPill_Extracted(label = "CRR", value = liveCurrentRunRate)
                } else {
                    val runs = battingScore.split("/", "-").firstOrNull()?.trim()?.toIntOrNull() ?: 0
                    val oversNum = battingOvers.toFloatOrNull() ?: 0f
                    if (oversNum > 0) StatPill_Extracted(label = "CRR", value = String.format("%.2f", runs / oversNum))
                }
                if (liveRequiredRunRate != null) StatPill_Extracted(label = "RRR", value = liveRequiredRunRate)
            }

            if (match.status.isNotBlank()) {
                Text(
                    text = match.status,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StatPill_Extracted(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerRadius.pill))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderWidth.hairline, Color.White.copy(alpha = 0.12f), RoundedCornerShape(CornerRadius.pill))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = LetterSpacing.relaxed)
        Text(text = value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EliteFloatingMatchNav_Extracted(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onArClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgBase.copy(alpha = 0.97f))
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navItems = remember {
                listOf(
                    Triple(Icons.Outlined.Home, "Home", 0),
                    Triple(Icons.Outlined.SportsCricket, "Cricket", 1),
                    Triple(Icons.Outlined.Leaderboard, "Leaderboard", 2),
                    Triple(Icons.Outlined.Settings, "Settings", 3)
                )
            }

            navItems.forEachIndexed { idx, (icon, label, _) ->
                val isActive = idx == 1
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(imageVector = icon, contentDescription = label, tint = if (isActive) Gold400 else TextMuted, modifier = Modifier.size(22.dp))
                    Text(text = label, color = if (isActive) Gold400 else TextMuted, fontSize = 10.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
