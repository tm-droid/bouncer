package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.*
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException

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
    val otherTeam = match.teams.find { !it.isBatting } ?: team2

    val team1Logo = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl)
    val team2Logo = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl)

    // Use live scores (updated from polling) with fallback to initial match data
    val score1 = liveTeam1Score.ifBlank { team1?.score ?: "" }
    val score2 = liveTeam2Score.ifBlank { team2?.score ?: "" }
    val overs1 = liveTeam1Overs.ifBlank { team1?.overs ?: "" }
    val overs2 = liveTeam2Overs.ifBlank { team2?.overs ?: "" }

    // Determine which team is batting for highlighting
    val team1IsBatting = team1?.isBatting == true
    val team2IsBatting = team2?.isBatting == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BgBase,
                        BgBase.copy(alpha = 0.95f),
                        Color.Transparent
                    )
                )
            )
            .drawBehind {
                // Gold bloom radial gradient behind score header
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Gold400.copy(alpha = 0.12f), Gold400.copy(alpha = 0.04f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.6f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width / 2f, size.height * 0.6f),
                    radius = size.width * 0.7f
                )
            }
    ) {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = match.matchTitle,
                        color = Gold400.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // Glass Score Section (omitted inner details kept identical to original for parity)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .clip(RoundedCornerShape(CornerRadius.xl))
                .background(GlassSurface)
                .border(
                    BorderWidth.medium,
                    Brush.verticalGradient(
                        colors = listOf(
                            Gold400.copy(alpha = 0.25f),
                            Gold400.copy(alpha = 0.10f),
                            Gold400.copy(alpha = 0.15f)
                        )
                    ),
                    RoundedCornerShape(CornerRadius.xl)
                )
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status badges, teams and stat pills reused from original implementation
            // For brevity the interior is intentionally the same as original implementation to preserve behavior.

            // Status Badge
            if (match.status.contains("Delayed", ignoreCase = true) || match.status.contains("Interrupted", ignoreCase = true)) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(CornerRadius.xs),
                    border = BorderStroke(BorderWidth.hairline, ErrorRed.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Gavel, null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                        Text("Match Delayed", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (match.status.contains("live", ignoreCase = true) || match.status.contains("batting", ignoreCase = true) || match.status.contains("bowling", ignoreCase = true)) {
                Surface(
                    color = LiveGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(CornerRadius.pill),
                    border = BorderStroke(BorderWidth.hairline, LiveGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(LiveGreen)
                        )
                        Text("LIVE", color = LiveGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            // Teams + Score Row — Each team shows its own score
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    CoilImage(model = team1Logo, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape).border(if (team1IsBatting) 2.dp else BorderWidth.hairline, if (team1IsBatting) Gold400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f), CircleShape))
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = team1?.acronym ?: "", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = LetterSpacing.relaxed)
                    if (score1.isNotBlank()) {
                        Text(text = score1, style = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, brush = if (team1IsBatting) Brush.verticalGradient(colors = listOf(Color.White, Gold400, GoldDark)) else Brush.verticalGradient(colors = listOf(TextSecondary, TextMuted))))
                        if (overs1.isNotBlank()) {
                            Text(text = "($overs1 Ov)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = LetterSpacing.relaxed)
                        }
                    } else {
                        Text(text = "-", color = TextMuted, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Text(text = "vs", color = Gold400.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    CoilImage(model = team2Logo, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape).border(if (team2IsBatting) 2.dp else BorderWidth.hairline, if (team2IsBatting) Gold400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f), CircleShape))
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = team2?.acronym ?: "", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = LetterSpacing.relaxed)
                    if (score2.isNotBlank()) {
                        Text(text = score2, style = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, brush = if (team2IsBatting) Brush.verticalGradient(colors = listOf(Color.White, Gold400, GoldDark)) else Brush.verticalGradient(colors = listOf(TextSecondary, TextMuted))))
                        if (overs2.isNotBlank()) {
                            Text(text = "($overs2 Ov)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = LetterSpacing.relaxed)
                        }
                    } else {
                        Text(text = "-", color = TextMuted, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Gold gradient separator line
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(1.dp).background(Brush.horizontalGradient(colors = listOf(Color.Transparent, Gold400.copy(alpha = 0.3f), Gold400.copy(alpha = 0.5f), Gold400.copy(alpha = 0.3f), Color.Transparent))))

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Stat Pills Row — using live data
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)) {
                val battingOvers = if (team1IsBatting) overs1 else overs2
                val battingScore = if (team1IsBatting) score1 else score2
                if (battingOvers.isNotBlank()) {
                    StatPill_Extracted(label = "Overs", value = battingOvers)
                }
                if (liveCurrentRunRate != null) {
                    StatPill_Extracted(label = "CRR", value = liveCurrentRunRate)
                } else {
                    val scoreParts = battingScore.split("/", "-")
                    val runs = scoreParts.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                    val oversNum = battingOvers.toFloatOrNull() ?: 0f
                    if (oversNum > 0) {
                        val crr = runs / oversNum
                        StatPill_Extracted(label = "CRR", value = String.format("%.2f", crr))
                    }
                }
                if (liveRequiredRunRate != null) {
                    StatPill_Extracted(label = "RRR", value = liveRequiredRunRate)
                }
                val targetScore = if (team1IsBatting) score2 else score1
                if (targetScore.isNotBlank() && (team1IsBatting && !team2IsBatting || team2IsBatting && !team1IsBatting)) {
                    val targetRuns = targetScore.split("/", "-").firstOrNull()?.trim()?.toIntOrNull()
                    if (targetRuns != null && targetRuns > 0) {
                        StatPill_Extracted(label = "Target", value = "${targetRuns + 1}")
                    }
                }
            }

            if (match.status.isNotBlank() && !match.status.contains("Delayed", ignoreCase = true)) {
                Text(
                    text = match.status,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = LetterSpacing.relaxed, brush = Brush.verticalGradient(colors = listOf(TextPrimary.copy(alpha = 0.9f), Gold400.copy(alpha = 0.7f)))),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

@Composable
fun StatPill_Extracted(label: String, value: String) {
    Row(modifier = Modifier.clip(RoundedCornerShape(CornerRadius.pill)).background(Brush.linearGradient(colors = listOf(GlassSurface, Color.Black.copy(alpha = 0.3f)))).border(BorderWidth.hairline, Gold400.copy(alpha = 0.15f), RoundedCornerShape(CornerRadius.pill)).padding(horizontal = Spacing.sm, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = Gold400.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = LetterSpacing.relaxed)
        Text(text = value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = LetterSpacing.none)
    }
}

@Composable
fun EliteFloatingMatchNav_Extracted(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onArClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Brush.horizontalGradient(colors = listOf(Color.Transparent, Gold400.copy(alpha = 0.15f), Gold400.copy(alpha = 0.25f), Gold400.copy(alpha = 0.15f), Color.Transparent))))

        Row(modifier = Modifier.fillMaxWidth().background(BgBase.copy(alpha = 0.97f)).navigationBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm).height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val navItems = listOf(Triple(Icons.Outlined.Home, "Home", 0), Triple(Icons.Outlined.SportsCricket, "Cricket", 1), Triple(Icons.Outlined.Leaderboard, "Leaderboard", 2), Triple(Icons.Outlined.Settings, "Settings", 3))

            navItems.forEachIndexed { idx, (icon, label, _) ->
                val isActive = idx == 1
                Column(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* Navigate to section */ }.padding(horizontal = 16.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isActive) {
                            Box(modifier = Modifier.size(32.dp).background(Brush.radialGradient(colors = listOf(Gold400.copy(alpha = 0.15f), Color.Transparent)), CircleShape))
                        }
                        Icon(imageVector = icon, contentDescription = label, tint = if (isActive) Gold400 else TextMuted, modifier = Modifier.size(22.dp))
                    }
                    Text(text = label, color = if (isActive) Gold400 else TextMuted, fontSize = 10.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}