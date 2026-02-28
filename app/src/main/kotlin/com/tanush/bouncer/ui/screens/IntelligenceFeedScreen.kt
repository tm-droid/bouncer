package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun IntelligenceFeedScreen(onBackClick: () -> Unit) {
    val alerts = remember {
        listOf(
            ActivityAlert("1", "Wicket Fall", "V. Kohli c. Smith b. Cummins", "OUT", "2m ago", AlertType.WICKET),
            ActivityAlert("2", "Century", "R. Sharma (52 balls)", "100*", "5m ago", AlertType.MILESTONE),
            ActivityAlert("3", "Pitch Report", "Heavy abrasive patch detected on good length", "INFO", "12m ago", AlertType.INFO),
            ActivityAlert("4", "DRS Review", "Impact: In Line | Wickets: Hitting", "DRS", "15m ago", AlertType.WARNING)
        )
    }

    Scaffold(
        containerColor = BlackBackground,
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
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                    Text("Today", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
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
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlackBackground.copy(alpha = 0.8f))
            .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(SuccessGreen, CircleShape)
                )
                Text("Live", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Surface(
                color = ShinyGoldDeep.copy(alpha = 0.05f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, ShinyGoldDeep.copy(alpha = 0.2f)),
                onClick = {}
            ) {
                Text("Clear", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Activity",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActivityAlertCard(alert: ActivityAlert) {
    val (accentColor, icon) = when (alert.type) {
        AlertType.WICKET -> WicketRed to Icons.Default.Warning
        AlertType.MILESTONE -> ShinyGoldDeep to Icons.Default.MilitaryTech
        AlertType.INFO -> ChartBlue to Icons.Default.CloudSync
        AlertType.WARNING -> WarningYellow to Icons.Default.Warning
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
    ) {
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
                    Text(alert.time, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(alert.subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = alert.value,
                color = if (alert.type == AlertType.MILESTONE) ShinyGoldDeep else Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
