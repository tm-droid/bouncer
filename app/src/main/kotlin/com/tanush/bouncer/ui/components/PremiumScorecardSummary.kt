package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.ui.theme.*

/**
 * MEGA UI UPGRADE: PremiumScorecardSummary (v2.1).
 * High-fidelity innings summary with technical telemetry aesthetics.
 */
@Composable
fun PremiumScorecardSummary(
    innings: Innings,
    modifier: Modifier = Modifier
) {
    val score = innings.score // Format "120/4"
    val overs = innings.overs
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f).copy(alpha = 0.4f))
            .border(
                0.5.dp,
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Technical Stats
            Column {
                Text(
                    text = innings.teamName.uppercase(),
                    color = ShinyGoldDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = score.substringBefore("/"),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    if (score.contains("/")) {
                        Text(
                            text = "/${score.substringAfter("/")}",
                            color = TextSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "($overs)",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // Right: Team Identity
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val logoUrl = TeamLogoManager.getBestLogoUrl(innings.teamName, null, 128, 128)
                CoilImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
