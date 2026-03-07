package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * MEGA UI UPGRADE: GlassyMatchView (v3.2).
 * High-fidelity, broadcast-grade match card with dynamic neon glow and precision borders.
 * iOS-inspired with AI commentary display support.
 */
@Composable
fun GlassyMatchView(
    match: CricketMatch,
    onClick: () -> Unit,
    aiCommentary: String? = null // iOS-inspired: Display latest AI fused commentary
) {
    val colors = BouncerDesignSystem.colors
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)

    val team1Color: Color = remember(team1?.name) { getTeamColor(team1?.name) }
    val team2Color: Color = remember(team2?.name) { getTeamColor(team2?.name) }

    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = team1Color.copy(alpha = 0.5f),
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(20.dp))
            .background(colors.glassBorderHighlight)
            .border(
                0.5.dp,
                Brush.linearGradient(
                    colors = listOf(
                        GoldHairline,
                        GlassSurface,
                        team1Color.copy(alpha = 0.2f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Dynamic Ambient Background
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(team1Color.copy(alpha = glowIntensity), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f)
                    )
                )
        )

        Column(modifier = Modifier.padding(24.dp)) {
            // Match Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.matchTitle.uppercase(),
                    color = colors.goldPrimary.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                
                if (match.isLive) {
                    LivePulseBadge()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Teams Interaction
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TeamSleekInfo(team = team1, color = team1Color)
                
                // Minimalist VS Divider
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(GlassBorder)
                    )
                    Text(
                        text = "VS",
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(GlassBorder)
                    )
                }
                
                TeamSleekInfo(team = team2, isRightAligned = true, color = team2Color)
            }
            
            val statusInfo = getMatchStatusInfo(match.status)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(colors.goldPrimary.copy(alpha = 0.4f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.venue.take(28),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                
                // Status Badge with High-Contrast Text
                Surface(
                    color = statusInfo.color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, statusInfo.color.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusInfo.text.uppercase(),
                        color = statusInfo.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            // iOS-inspired: AI Commentary Display (like iOS GlassyMatchView)
            if (!aiCommentary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.08f), // Mint/Teal background
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.15f))
                ) {
                    Text(
                        text = aiCommentary,
                        color = Color(0xFF10B981).copy(alpha = 0.9f), // Mint text color
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TeamSleekInfo(team: CricketMatch.Team?, isRightAligned: Boolean = false, color: Color) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isRightAligned) Arrangement.End else Arrangement.Start,
        modifier = Modifier.width(130.dp)
    ) {
        if (!isRightAligned) {
            LogoContainer(team, color)
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start) {
            Text(
                text = team?.acronym ?: team?.name?.take(3)?.uppercase() ?: "---",
                color = colors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = (-1).sp
            )
            Text(
                text = team?.score?.ifEmpty { "Y.T.B" } ?: "---",
                color = if (team?.isBatting == true) colors.goldPrimary else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )
        }

        if (isRightAligned) {
            Spacer(modifier = Modifier.width(14.dp))
            LogoContainer(team, color)
        }
    }
}

@Composable
private fun LogoContainer(team: CricketMatch.Team?, color: Color) {
    val colors = BouncerDesignSystem.colors
    val url = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 256, 256)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .border(
                0.5.dp,
                Brush.verticalGradient(
                    colors = listOf(GlassBorder, Color.Transparent)
                ),
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        CoilImage(
            model = url,
            contentDescription = team?.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun LivePulseBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaValue by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Red.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .drawBehind {
                    drawCircle(color = Color.Red.copy(alpha = alphaValue))
                }
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "LIVE",
            color = Color.Red,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}
