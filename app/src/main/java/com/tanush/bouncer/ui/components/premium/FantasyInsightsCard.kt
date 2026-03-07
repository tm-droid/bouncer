package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Fantasy Insights Card (v1.0).
 * Inspired by Cricbuzz Plus Fantasy Handbook.
 * High-fidelity recommendations for fantasy cricket players.
 */

data class FantasyPlayer(
    val name: String,
    val points: Int,
    val selectionPct: Float,
    val role: String,
    val isCaptaincyPick: Boolean = false
)

@Composable
fun FantasyInsightsCard(
    modifier: Modifier = Modifier,
    topPicks: List<FantasyPlayer> = emptyList()
) {
    val colors = BouncerDesignSystem.colors
    val picks = if (topPicks.isEmpty()) {
        listOf(
            FantasyPlayer("Virat Kohli", 145, 0.88f, "BAT", true),
            FantasyPlayer("Pat Cummins", 112, 0.72f, "BOWL"),
            FantasyPlayer("Hardik Pandya", 98, 0.65f, "ALL"),
            FantasyPlayer("Glenn Maxwell", 85, 0.54f, "ALL")
        )
    } else topPicks

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.STANDARD,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(ChartPurple.copy(alpha = 0.1f), CircleShape)
                            .border(0.5.dp, ChartPurple.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoGraph, null, tint = ChartPurple, modifier = Modifier.size(14.dp))
                    }
                    Column {
                        Text(
                            "FANTASY HANDBOOK",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                        Text("PREMIUM STRATEGIC PICKS", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ChartPurple.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("HOT PICKS", color = ChartPurple, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }

            // Player List
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                picks.forEach { player ->
                    FantasyPlayerRow(player = player)
                }
            }

            // Footer Tip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = colors.goldPrimary, modifier = Modifier.size(14.dp))
                    Text(
                        "Kohli averages 64.2 against Starc at this venue. Ideal captaincy choice.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FantasyPlayerRow(player: FantasyPlayer) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Text(player.name.take(1), color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(player.name, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (player.isCaptaincyPick) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.goldPrimary)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("C", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text("${player.role} • ${(player.selectionPct * 100).toInt()}% Selected", color = TextMuted, fontSize = 10.sp)
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text("${player.points}", color = colors.statusSuccess, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("PTS", color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}
