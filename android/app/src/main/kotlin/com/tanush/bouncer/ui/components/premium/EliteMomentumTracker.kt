package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.*

/**
 * Elite Match Momentum Tracker.
 * High-fidelity vertical stream of match-defining events.
 * Ported from Quantum Prototype (code.html).
 */

data class MomentumEvent(
    val over: String,
    val title: String,
    val description: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun EliteMomentumTracker(
    events: List<MomentumEvent> = listOf(
        MomentumEvent("18.4", "WICKET", "Bowled · 142kph Yorker", "W", Icons.Default.HeartBroken, StatusLive),
        MomentumEvent("18.3", "MASSIVE SIX", "Pull Shot · 94 Meters", "6", Icons.Default.Flare, StatusSuccess),
        MomentumEvent("17.6", "TIGHT OVER", "Only 3 runs conceded", "3", Icons.Default.Security, StatusInfo)
    )
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.share_match_momentum_stream),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        
        events.forEach { event ->
            MomentumItem(event)
        }
    }
}

@Composable
private fun MomentumItem(event: MomentumEvent) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurface)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Visual Value Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CornerRadius.md))
                    .background(event.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = event.value,
                    color = event.color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = event.title,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Box(modifier = Modifier.size(3.dp).background(TextMuted, CircleShape))
                    Text(
                        text = "OVER ${event.over}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = event.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Icon(
                imageVector = event.icon,
                contentDescription = null,
                tint = event.color.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
