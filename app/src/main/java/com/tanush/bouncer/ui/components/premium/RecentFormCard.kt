package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*

/**
 * Premium Sleek Recent Form Card.
 * Layered, technical, and data-dense.
 */

data class PastMatchResult(
    val team1Short: String,
    val team2Short: String,
    val team1Score: String,
    val team2Score: String,
    val winner: String,
    val margin: String,
    val date: String,
    val venue: String,
    val matchType: String,
)

data class RecentFormData(
    val teamName: String,
    val teamLogo: String,
    val recentResults: List<String>, // W, L, T, NR
    val recentMatches: List<PastMatchResult>,
)

@Composable
fun RecentFormCard(
    team1Form: RecentFormData,
    team2Form: RecentFormData,
    modifier: Modifier = Modifier,
) {
    var selectedTeam by remember { mutableIntStateOf(1) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = GoldDark,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RECENT FORM".uppercase(),
                    color = GoldDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Team Selector - Technical Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TeamFormTab(
                    form = team1Form,
                    isSelected = selectedTeam == 1,
                    onClick = { selectedTeam = 1 },
                    teamColor = ChartBlue,
                    modifier = Modifier.weight(1f)
                )
                TeamFormTab(
                    form = team2Form,
                    isSelected = selectedTeam == 2,
                    onClick = { selectedTeam = 2 },
                    teamColor = WicketRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Indicators - Sharp Visuals
            val selectedForm = if (selectedTeam == 1) team1Form else team2Form
            val selectedColor = if (selectedTeam == 1) ChartBlue else WicketRed

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                selectedForm.recentResults.takeLast(6).forEach { result ->
                    FormResultSquare(result = result)
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val wins = selectedForm.recentResults.count { it == "W" }
            Text(
                text = "$wins WINS IN LAST ${selectedForm.recentResults.size} MATCHES",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Matches List
            Text(
                text = "MATCH LOG",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                selectedForm.recentMatches.forEach { match ->
                    RecentMatchCard(
                        match = match,
                        focusTeam = selectedForm.teamName,
                        teamColor = selectedColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamFormTab(
    form: RecentFormData,
    isSelected: Boolean,
    onClick: () -> Unit,
    teamColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .border(
                0.5.dp, 
                if (isSelected) teamColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f), 
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            CoilImage(
                model = form.teamLogo,
                contentDescription = form.teamName,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = form.teamName.uppercase(),
                color = if (isSelected) TextPrimary else TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun FormResultSquare(result: String) {
    val color = when (result) {
        "W" -> SuccessGreen
        "L" -> ErrorRed
        "T" -> WarningYellow
        else -> TextTertiary
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = result, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RecentMatchCard(
    match: PastMatchResult,
    focusTeam: String,
    teamColor: Color,
    modifier: Modifier = Modifier,
) {
    val isWin = match.winner.contains(focusTeam, ignoreCase = true)
    
    GlassSurface(
        modifier = modifier.width(160.dp),
        cornerRadius = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = match.matchType, color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isWin) "WIN" else "LOSS",
                    color = if (isWin) SuccessGreen else ErrorRed,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "${match.team1Short} ${match.team1Score}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(text = "${match.team2Short} ${match.team2Score}", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = match.margin.uppercase(), color = teamColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}
