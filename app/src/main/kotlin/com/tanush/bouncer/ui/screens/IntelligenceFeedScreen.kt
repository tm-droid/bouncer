package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.PulsingBadge
import com.tanush.bouncer.ui.theme.*

/**
 * Activity Feed Screen
 * Match activity log with alerts and key events.
 */

data class ActivityAlert(
    val id: String,
    val title: String,
    val subtitle: String,
    val value: String,
    val time: String,
    val type: AlertType
)

enum class AlertType {
    WICKET, MILESTONE, INFO, WARNING
}

@Composable
fun IntelligenceFeedScreen(
    match: CricketMatch? = null,
    liveActivityViewModel: LiveActivityViewModel? = null,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors

    // Derive alerts from real match data
    val vmEvents by liveActivityViewModel?.probabilityEvents?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }
    val aiCommentary by liveActivityViewModel?.aiCommentary?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    val alerts = remember(match, vmEvents, aiCommentary) {
        val alertList = mutableListOf<ActivityAlert>()
        val matchId = match?.id ?: ""

        // Generate alerts from probability events (wickets, boundaries)
        val events = vmEvents[matchId] ?: emptyList()
        events.takeLast(5).reversed().forEachIndexed { idx, event ->
            val type = when (event.eventType) {
                com.tanush.bouncer.viewmodels.ProbabilityEventType.WICKET -> AlertType.WICKET
                com.tanush.bouncer.viewmodels.ProbabilityEventType.BOUNDARY -> AlertType.MILESTONE
                else -> AlertType.INFO
            }
            alertList.add(
                ActivityAlert(
                    id = "event_$idx",
                    title = event.description.take(30),
                    subtitle = "Over ${event.over}, Ball ${event.ball % 6 + 1}",
                    value = when (event.eventType) {
                        com.tanush.bouncer.viewmodels.ProbabilityEventType.WICKET -> "OUT"
                        com.tanush.bouncer.viewmodels.ProbabilityEventType.BOUNDARY -> "4/6"
                        else -> "•"
                    },
                    time = "${idx + 1}m ago",
                    type = type
                )
            )
        }

        // Add AI commentary alert if available
        val commentary = aiCommentary[matchId]
        if (!commentary.isNullOrBlank()) {
            alertList.add(
                ActivityAlert("ai_commentary", "AI Analysis", commentary.take(80), "AI", "Now", AlertType.INFO)
            )
        }

        // Add match status alert from match data
        if (match != null) {
            val battingTeam = match.teams.firstOrNull { it.isBatting }
            val bowler = match.currentBowler
            if (battingTeam != null) {
                alertList.add(
                    ActivityAlert(
                        "match_status",
                        "${battingTeam.name} Batting",
                        "${battingTeam.score ?: "--"} in ${battingTeam.overs ?: "--"} overs",
                        match.status.take(6),
                        "Live",
                        AlertType.INFO
                    )
                )
            }
            if (bowler != null) {
                alertList.add(
                    ActivityAlert(
                        "bowler_status",
                        "Current Bowler",
                        "${bowler.name}: ${bowler.wickets}/${bowler.runs} (${bowler.overs} ov)",
                        "BOWL",
                        "Now",
                        AlertType.INFO
                    )
                )
            }
        }

        // If no data at all, show empty state alert
        if (alertList.isEmpty()) {
            alertList.add(
                ActivityAlert("empty", "No Activity", "Waiting for match events...", "--", "Now", AlertType.INFO)
            )
        }

        alertList.toList()
    }

    Scaffold(
        containerColor = colors.backgroundBase,
        topBar = {
            ActivityFeedHeader(onBackClick)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.glassSurface))
                    Text("Today", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.glassSurface))
                }
            }

            items(alerts) { alert ->
                ActivityAlertCard(alert = alert)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityFeedHeader(onBackClick: () -> Unit) {
    val colors = BouncerDesignSystem.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundBase.copy(alpha = 0.8f))
            .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.textPrimary)
                }
                PulsingBadge(text = "Live")
            }
            Surface(
                color = colors.goldPrimary.copy(alpha = 0.05f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, colors.goldPrimary.copy(alpha = 0.2f)),
                onClick = {}
            ) {
                Text("Clear", color = colors.goldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Activity",
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActivityAlertCard(alert: ActivityAlert) {
    val colors = BouncerDesignSystem.colors
    val (accentColor, icon) = when (alert.type) {
        AlertType.WICKET -> colors.statusLive to Icons.Default.Warning
        AlertType.MILESTONE -> colors.goldPrimary to Icons.Default.MilitaryTech
        AlertType.INFO -> colors.statusInfo to Icons.Default.CloudSync
        AlertType.WARNING -> colors.statusWarning to Icons.Default.Warning
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Lateral Accent Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(accentColor)
            )

            Row(
                modifier = Modifier.padding(16.dp).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(alert.title.uppercase(), color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(alert.time, color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(alert.subtitle, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = alert.value,
                    color = if (alert.type == AlertType.MILESTONE) colors.goldPrimary else colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
