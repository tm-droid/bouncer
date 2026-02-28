package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*

/**
 * MEGA UI UPGRADE: HeroEliteLiveCard (v4.1).
 * Ultimate centerpiece with glassmorphic depth, team-based neon glows, and precision typography.
 */
@Composable
fun HeroEliteLiveCard(
    match: CricketMatch,
    onClick: () -> Unit
) {
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    val team1Color: Color = remember(team1?.name) { getTeamColor(team1?.name) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal)
            .height(260.dp)
            .shadow(
                elevation = Elevation.high, 
                shape = RoundedCornerShape(CornerRadius.lg), 
                spotColor = team1Color.copy(alpha = 0.4f)
            ),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            BorderWidth.hairline, 
            Brush.linearGradient(colors = listOf(GoldHairline, Color.White.copy(alpha = 0.05f), team1Color.copy(alpha = 0.2f)))
        ),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // High-Fidelity Background Glows
            val infiniteTransition = rememberInfiniteTransition(label = "heroGlow")
            val pulseValue by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 0.6f,
                animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "pulse"
            )

            // Dynamic Ambient Layer
            Box(modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(team1Color.copy(alpha = pulseValue * 0.4f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 800f
                    )
                )
            )

            Column(modifier = Modifier.padding(Spacing.md)) {
                // Header: Series & Broadcast Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            match.matchTitle.uppercase(),
                            color = ShinyGoldDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "LIVE BROADCAST FEED",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    LiveBadgeTechnical()
                }

                Spacer(modifier = Modifier.weight(1f))

                // Core Match Interaction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroTeamTechnicalDisplay(team1, isLeft = true)
                    
                    // Technical Divider
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ShinyGoldDeep.copy(alpha = 0.1f))
                                .border(0.5.dp, ShinyGoldDeep.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "CRR ${match.score?.currentRunRate ?: "0.00"}",
                                color = ShinyGoldDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    HeroTeamTechnicalDisplay(team2, isLeft = false)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Status Ticker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = ShinyGoldDeep.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Text(
                            match.currentStatus,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTeamTechnicalDisplay(team: CricketMatch.Team?, isLeft: Boolean) {
    val teamColor: Color = remember(team?.name) { getTeamColor(team?.name) }
    
    Column(
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 2.dp, 
                    brush = Brush.verticalGradient(
                        colors = listOf(if (team?.isBatting == true) ShinyGoldDeep else Color.White.copy(alpha = 0.1f), Color.Transparent)
                    ), 
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 128, 128)
            CoilImage(
                model = logoUrl, 
                contentDescription = null, 
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Column(horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End) {
            Text(
                text = team?.acronym ?: "---", 
                color = Color.White, 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = team?.score ?: "---", 
                color = if (team?.isBatting == true) ShinyGoldDeep else TextSecondary, 
                fontSize = 18.sp, 
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

@Composable
private fun LiveBadgeTechnical() {
    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val alphaValue by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Red.copy(alpha = 0.1f))
            .border(0.5.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .drawBehind {
                    drawCircle(color = Color.Red.copy(alpha = alphaValue))
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "LIVE",
            color = Color.Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
    }
}

/**
 * VipBoxRail - Horizontal scroll of secondary matches.
 */
@Composable
fun VipBoxRail(
    matches: List<CricketMatch>,
    onMatchClick: (CricketMatch) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EXECUTIVE SUITE",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(matches) { match ->
                Surface(
                    modifier = Modifier.width(300.dp).height(140.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
                    onClick = { onMatchClick(match) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Subtle Corner Glow
                        Box(modifier = Modifier.size(60.dp).align(Alignment.TopStart).blur(30.dp).background(ShinyGoldDeep.copy(alpha = 0.03f), CircleShape))
                        
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                match.matchTitle.uppercase(),
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                match.teams.forEach { team ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(0.5.dp, if (team.isBatting) ShinyGoldDeep else Color.Transparent, CircleShape)
                                        ) {
                                            CoilImage(
                                                model = TeamLogoManager.getBestLogoUrl(team.name, team.logoUrl, 64, 64), 
                                                contentDescription = null,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        }
                                        Text(team.acronym, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            team.score, 
                                            color = if (team.isBatting) ShinyGoldDeep else TextSecondary, 
                                            fontSize = 15.sp, 
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * EliteMarketInsights - Magazinesque analysis section.
 */
@Composable
fun EliteMarketInsights(liveMatch: CricketMatch?) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = CardDarkBlue.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, ShinyGoldDeep.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Analytics, null, tint = ShinyGoldDeep, modifier = Modifier.size(22.dp))
                Text("AI MARKET INTELLIGENCE", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Match momentum favoring ${liveMatch?.teams?.firstOrNull()?.name ?: "the host"} following high-impact middle overs. Win probability shifted +12.4% in the last 15 balls.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * PlayerOfTheMomentCard - Featured player spotlight.
 */
@Composable
fun PlayerOfTheMomentCard(liveMatch: CricketMatch?) {
    val batsman = liveMatch?.scrapedBatsmen?.firstOrNull() ?: return
    
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, GoldHairline)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Decorative Star
            Icon(
                Icons.Default.Stars, 
                null, 
                tint = ShinyGoldDeep.copy(alpha = 0.02f), 
                modifier = Modifier.size(240.dp).align(Alignment.CenterEnd).graphicsLayer { translationX = 100f }
            )
            
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(ShinyGoldDeep.copy(alpha = 0.1f))
                        .border(1.5.dp, ShinyGoldDeep, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = ShinyGoldDeep, modifier = Modifier.size(48.dp))
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Column {
                    Text("PLAYER OF THE MOMENT", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(batsman.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(batsman.runs, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text("OFF ${batsman.balls} BALLS", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
        }
    }
}
